#!/usr/bin/env python3
"""Proxy local para fallos de transporte deterministas; nunca registra JWT ni cuerpos.

DROP_AFTER: manda la operación a Productos y pierde únicamente la respuesta.
DROP_BEFORE: cierra la conexión sin entregar la petición al upstream.
Todos los fallos solo se aplican a la siguiente operación del método indicado.
"""
from collections import Counter, defaultdict, deque
from http.client import HTTPConnection, HTTPException
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import re
import socket
import threading


DROP_AFTER = "drop-after-upstream"
DROP_BEFORE = "drop-before-upstream"
# El cliente no puede elegir el destino ni pasar una URL absoluta.
ROUTE = re.compile(r"/api/v1/interno/reservas/[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")


class FaultProxy:
    def __init__(self, port=8090, upstream_port=8080):
        self.port = port
        self.upstream_port = upstream_port
        self._lock = threading.Lock()
        self._planned = defaultdict(deque)
        self._seen = Counter()
        self._faults = Counter()
        self._upstream_status = Counter()
        owner = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, format, *args):
                # No imprimir ni accidentalmente registrar cabeceras de autorización.
                return

            def do_GET(self):
                self.forward("GET")

            def do_PUT(self):
                self.forward("PUT")

            def do_DELETE(self):
                self.forward("DELETE")

            def read_payload(self):
                # HttpURLConnection/Spring Boot puede enviar Transfer-Encoding: chunked,
                # no Content-Length. urllib vuelve a enviar el cuerpo normalizado.
                encoding = self.headers.get("Transfer-Encoding", "").lower()
                if "chunked" in encoding:
                    data = bytearray()
                    while True:
                        line = self.rfile.readline(128)
                        if not line:
                            raise ConnectionError("Chunk HTTP cortado")
                        length = int(line.split(b";", 1)[0].strip(), 16)
                        if length < 0 or length > 1024 * 1024 or len(data) + length > 1024 * 1024:
                            raise ValueError("Cuerpo HTTP de prueba excede el limite")
                        if length == 0:
                            while True:
                                trailer = self.rfile.readline(8192)
                                if trailer in (b"\r\n", b"\n", b""):
                                    break
                            return bytes(data)
                        piece = self.rfile.read(length)
                        if len(piece) != length or self.rfile.read(2) != b"\r\n":
                            raise ConnectionError("Chunk HTTP incompleto")
                        data.extend(piece)
                length = int(self.headers.get("Content-Length", "0"))
                return self.rfile.read(length) if length else None

            def forward(self, method):
                if ROUTE.fullmatch(self.path) is None:
                    self.send_error(400, "Ruta interna no autorizada")
                    return
                with owner._lock:
                    owner._seen[method] += 1
                    action = (owner._planned[method].popleft()
                              if owner._planned[method] else None)
                    if action:
                        owner._faults[(method, action)] += 1
                body = self.read_payload()
                if action == DROP_BEFORE:
                    self.disconnect()
                    return
                headers = {}
                if self.headers.get("Authorization"):
                    headers["Authorization"] = self.headers["Authorization"]
                if self.headers.get("Content-Type"):
                    headers["Content-Type"] = self.headers["Content-Type"]
                if self.headers.get("Accept"):
                    headers["Accept"] = self.headers["Accept"]
                # Upstream fijo por socket en loopback, sin redirect HTTP automático.
                connection = HTTPConnection("127.0.0.1", owner.upstream_port, timeout=10)
                try:
                    connection.request(method, self.path, body=body, headers=headers)
                    response = connection.getresponse()
                    status = response.status
                    response_body = response.read()
                    mime = response.getheader("Content-Type", "application/json")
                except (OSError, HTTPException):
                    self.disconnect()
                    return
                finally:
                    connection.close()
                with owner._lock:
                    owner._upstream_status[(method, status)] += 1
                if action == DROP_AFTER:
                    self.disconnect()
                    return
                self.send_response(status)
                self.send_header("Content-Type", mime)
                self.send_header("Content-Length", str(len(response_body)))
                self.end_headers()
                if response_body:
                    try:
                        self.wfile.write(response_body)
                    except (BrokenPipeError, ConnectionResetError):
                        pass

            def disconnect(self):
                self.close_connection = True
                try:
                    self.connection.shutdown(socket.SHUT_RDWR)
                except OSError:
                    pass
                self.connection.close()

        self._server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
        self._server.daemon_threads = True
        self._thread = threading.Thread(target=self._server.serve_forever,
                                        name="fault-proxy", daemon=True)

    def start(self):
        self._thread.start()

    def stop(self):
        self._server.shutdown()
        self._server.server_close()
        self._thread.join(timeout=5)

    def inject(self, method, action):
        if method not in ("GET", "PUT", "DELETE") or action not in (DROP_AFTER, DROP_BEFORE):
            raise ValueError("Solo fallos HTTP deterministas admitidos")
        with self._lock:
            self._planned[method].append(action)

    def count(self, method):
        with self._lock:
            return self._seen[method]

    def injected(self, method, action):
        with self._lock:
            return self._faults[(method, action)]

    def statuses(self, method):
        with self._lock:
            return {status: count for (m, status), count in self._upstream_status.items()
                    if m == method}

    def assert_empty(self):
        with self._lock:
            pending = {method: len(actions) for method, actions in self._planned.items()
                       if actions}
        if pending:
            raise AssertionError("Inyecciones no ejercitadas: " + str(pending))
