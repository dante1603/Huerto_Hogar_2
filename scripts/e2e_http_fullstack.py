#!/usr/bin/env python3
"""Prueba E2E de cinco procesos Spring reales con H2 efimera y HTTP REST."""
import base64
import json
import os
import secrets
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import uuid
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICIOS = [
    ("productos", "catalogo", 8080),
    ("usuarios", "usuarios", 8081),
    ("pedidos", "pedidos", 8082),
    ("boleta", "boleta", 8083),
    ("detalle", "detalle-boleta", 8084),
]


def check(cond, desc):
    if not cond:
        raise AssertionError(desc)


def http(port, method, path, *, token=None, payload=None, key=None, timeout=8):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {"Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    if key:
        headers["Idempotency-Key"] = key
    req = urllib.request.Request(
        "http://127.0.0.1:" + str(port) + path,
        data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as res:
            raw = res.read()
            return res.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as exc:
        exc.read()
        return exc.code, None


def expect(port, method, path, wanted, **kwargs):
    code, body = http(port, method, path, **kwargs)
    check(code == wanted, method + " " + path + " -> " + str(code)
          + ", esperado " + str(wanted))
    return body


def rut(numero):
    factor, total = 2, 0
    for digito in reversed(str(numero)):
        total += int(digito) * factor
        factor = 2 if factor == 7 else factor + 1
    resto = 11 - total % 11
    dv = "0" if resto == 11 else "K" if resto == 10 else str(resto)
    return str(numero) + "-" + dv


def dinero(valor):
    return Decimal(str(valor))


def cliente(numero, password, sufijo):
    email = "test-" + sufijo + "-" + str(numero) + "@example.test"
    usuario = expect(8081, "POST", "/api/v1/usuarios", 201, payload={
        "nombreUsuario": "Usuario E2E " + str(numero),
        "rutUsuario": rut(12345678 + numero),
        "direccionUsuario": "Direccion ficticia en Bruma",
        "emailUsuario": email,
        "contrasena": password,
    })
    check(usuario["rolUsuario"] == "CLIENTE", "Registro debe limitar el rol")
    login = expect(8081, "POST", "/api/v1/auth/login", 200,
                   payload={"email": email, "contrasena": password})
    check(login["idUsuario"] == usuario["idUsuario"], "Identidad JWT incorrecta")
    check(login["rolUsuario"] == "CLIENTE", "Rol incorrecto en JWT")
    check(bool(login["accessToken"]), "JWT ausente")
    return usuario["idUsuario"], login["accessToken"]


def prueba():
    print("[HTTP] Usuarios: registro real y dos JWT distintos")
    passwd = secrets.token_urlsafe(20)
    sufijo = uuid.uuid4().hex[:10]
    comprador, jwt = cliente(1, passwd, sufijo)
    ajeno, otro_jwt = cliente(2, passwd, sufijo)
    check(comprador != ajeno, "Clientes duplicados")
    expect(8082, "GET", "/api/v1/pedidos/mios", 401)

    print("[HTTP] Productos: catalogo y stock previo")
    catalogo = expect(8080, "GET", "/api/v1/productos", 200)
    por_sku = {p["sku"]: p for p in catalogo}
    a, b = por_sku["BRU-TUB-001"], por_sku["ARI-FRU-002"]
    check(a["stock"] >= 2 and b["stock"] >= 1, "Stock demo insuficiente")
    total = 2 * dinero(a["precio"]) + dinero(b["precio"])

    print("[HTTP] Pedidos -> Productos: reserva real, JWT, idempotencia")
    body = {"planetaDestino": "Bruma", "productos": [
        {"productoId": a["id"], "cantidad": 2},
        {"productoId": b["id"], "cantidad": 1},
    ]}
    key = str(uuid.uuid4())
    pedido = expect(8082, "POST", "/api/v1/pedidos", 201,
                    token=jwt, key=key, payload=body)
    check(pedido["clienteId"] == comprador and pedido["estado"] == "PENDIENTE",
          "Pedido no confirmado")
    check(dinero(pedido["total"]) == total, "Total del pedido incorrecto")
    check(len(pedido["productos"]) == 2, "Productos del pedido incompletos")
    pid = pedido["id"]
    repetido = expect(8082, "POST", "/api/v1/pedidos", 200,
                      token=jwt, key=key, payload=body)
    check(repetido["id"] == pid, "Reintento creo pedido duplicado")
    expect(8082, "POST", "/api/v1/pedidos", 409, token=jwt, key=key,
           payload={"planetaDestino": "Bruma", "productos": [
               {"productoId": a["id"], "cantidad": 1}]})
    expect(8082, "GET", "/api/v1/pedidos/" + str(pid), 200, token=jwt)
    expect(8082, "GET", "/api/v1/pedidos/" + str(pid), 403, token=otro_jwt)

    print("[HTTP] Boleta -> Pedidos: emision desde snapshot verificado")
    boleta = expect(8083, "POST", "/api/v1/boletas", 201,
                    token=jwt, payload={"pedidoId": pid})
    bid = boleta["id"]
    check(boleta["pedidoId"] == pid and boleta["clienteId"] == comprador,
          "Boleta vinculada a otro pedido/cliente")
    check(dinero(boleta["total"]) == total, "Monto boleta distinto a pedido")
    check(boleta["tipoDocumento"] == "COMPROBANTE_ACADEMICO_NO_TRIBUTARIO",
          "Documento debe ser academico")
    otra_boleta = expect(8083, "POST", "/api/v1/boletas", 200,
                         token=jwt, payload={"pedidoId": pid})
    check(otra_boleta["id"] == bid, "Emision duplicada")
    expect(8083, "GET", "/api/v1/boletas/" + str(bid), 404, token=otro_jwt)

    print("[HTTP] Detalle -> Boleta + Pedidos: lineas, total e inmutabilidad")
    lineas = expect(8084, "POST", "/api/v1/detalles-boleta", 201,
                    token=jwt, payload={"boletaId": bid})
    check(len(lineas) == 2 and all(l["boletaId"] == bid for l in lineas),
          "Lineas incompletas o de otra boleta")
    check(sorted(l["numeroLinea"] for l in lineas) == [1, 2],
          "Indices de detalle duplicados")
    check(sum((dinero(l["totalVenta"]) for l in lineas), Decimal(0)) == total,
          "Total de detalles no cuadra con Boleta")
    for l in lineas:
        check(dinero(l["precioUnitario"]) * l["cantidadProducto"]
              == dinero(l["totalVenta"]), "Subtotal no coincide")
    repetidas = expect(8084, "POST", "/api/v1/detalles-boleta", 200,
                       token=jwt, payload={"boletaId": bid, "totalVenta": 999999})
    check([l["id"] for l in repetidas] == [l["id"] for l in lineas],
          "Reintento altero detalle")
    guardadas = expect(8084, "GET", "/api/v1/detalles-boleta/boleta/" + str(bid),
                       200, token=jwt)
    check([l["id"] for l in guardadas] == [l["id"] for l in lineas],
          "GET de detalle desalineado")
    expect(8084, "GET", "/api/v1/detalles-boleta/boleta/" + str(bid),
           404, token=otro_jwt)
    expect(8084, "GET", "/api/v1/detalles-boleta/" + str(lineas[0]["id"]),
           404, token=otro_jwt)
    expect(8084, "POST", "/api/v1/detalles-boleta", 401,
           payload={"boletaId": bid})

    print("[HTTP] Comprobando stock despues de los reintentos")
    final = {p["id"]: p for p in expect(8080, "GET", "/api/v1/productos", 200)}
    check(final[a["id"]]["stock"] == a["stock"] - 2,
          "Producto A descontado mas de una vez")
    check(final[b["id"]]["stock"] == b["stock"] - 1,
          "Producto B descontado mas de una vez")
    print("[PASS] 5 procesos / H2 separadas / REST real / JWT / stock /"
          " boleta / detalle / idempotencia")


def listo(port):
    try:
        code, docs = http(port, "GET", "/v3/api-docs", timeout=2)
        return code == 200 and isinstance(docs, dict) and "paths" in docs
    except (urllib.error.URLError, OSError, TimeoutError, ValueError):
        return False


def ejecutar():
    secreto = base64.b64encode(secrets.token_bytes(48)).decode("ascii")
    for _, _, port in SERVICIOS:
        with socket.socket() as sock:
            check(sock.connect_ex(("127.0.0.1", port)) != 0,
                  "Puerto ocupado: " + str(port))
    processes = []
    with tempfile.TemporaryDirectory(prefix="fullstack-rest-") as carpeta:
        registros = {}
        try:
            for nombre, modulo, port in SERVICIOS:
                jars = [p for p in (ROOT / "backend" / modulo / "target").glob("*.jar")
                        if not p.name.endswith(".original")]
                check(len(jars) == 1, "No se encontro jar unico: " + modulo)
                registro = Path(carpeta) / (nombre + ".log")
                registros[nombre] = registro
                env = os.environ.copy()
                env.update({
                    "JWT_SECRET_BASE64": secreto,
                    "JWT_ISSUER": "huertohogar-usuarios",
                    "RECONCILIACION_HABILITADA": "false",
                    "PORT": str(port), "SERVER_PORT": str(port),
                })
                with registro.open("wb") as salida:
                    proc = subprocess.Popen(
                        ["java", "-Xms32m", "-Xmx256m", "-jar", str(jars[0]),
                         "--spring.profiles.active=demo"],
                        cwd=ROOT, stdout=salida, stderr=subprocess.STDOUT,
                        env=env)
                processes.append(proc)
                deadline = time.monotonic() + 90
                while time.monotonic() < deadline and proc.poll() is None:
                    if listo(port):
                        print("[READY]", nombre, ":", port, flush=True)
                        break
                    time.sleep(0.7)
                else:
                    raise RuntimeError("No arranco servicio: " + nombre)
            prueba()
        except Exception:
            print("[ERROR] Ultimas lineas de startup:", file=sys.stderr)
            for nombre, registro in registros.items():
                print("-- " + nombre + " --", file=sys.stderr)
                if registro.exists():
                    print("\n".join(registro.read_text(errors="replace")
                                    .splitlines()[-15:]), file=sys.stderr)
            raise
        finally:
            for proc in processes:
                if proc.poll() is None:
                    proc.terminate()
            for proc in processes:
                try:
                    proc.wait(timeout=6)
                except subprocess.TimeoutExpired:
                    proc.kill()
                    proc.wait(timeout=5)


if __name__ == "__main__":
    ejecutar()
