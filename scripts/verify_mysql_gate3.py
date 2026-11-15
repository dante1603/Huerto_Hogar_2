#!/usr/bin/env python3
"""
Verificacion de Gate 3: Persistencia transaccional real de Full Stack con MySQL 8.4.
Cinco microservicios independientes sobre HTTP real, esquemas MySQL reales y reinicio con persistencia.
Auditoria y correcciones:
- Gestion estricta y exclusiva de procesos propios (nunca cerrar procesos ajenos).
- Aislamiento de datos de prueba por corrida (SKU y contrasenas dinamicas, sin consumir stock de trabajo).
- Consultas SQL directas como huerto_app (no root) con filtrado por IDs/referencias exactas y cardinalidades.
- Cero exposicion de hashes o secretos en logs de ejecucion.
"""
import base64
import hashlib
import json
import os
import secrets
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

PORTS = [8080, 8081, 8082, 8083, 8084]

JAVA_BIN = r"C:\Program Files\Java\jdk-21.0.12\bin\java.exe"
if not os.path.exists(JAVA_BIN):
    JAVA_BIN = "java"


def load_env():
    env = dict(os.environ)
    env_file = ROOT / ".env"
    if env_file.exists():
        with open(env_file, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line and not line.startswith("#") and "=" in line:
                    k, v = line.split("=", 1)
                    env[k.strip()] = v.strip()
    secret_file = ROOT / ".jwt_local.secret"
    if secret_file.exists():
        with open(secret_file, "r", encoding="utf-8") as f:
            env["JWT_SECRET_BASE64"] = f.read().strip()
    return env


ENV_MAP = load_env()


def check(cond, desc):
    if not cond:
        print(f"[-] FALLO: {desc}", file=sys.stderr, flush=True)
        raise AssertionError(desc)
    print(f"[+] OK: {desc}", flush=True)


def mysql_exec(sql):
    """Ejecuta SQL conectando exclusivamente como usuario huerto_app (no root) sin exponer contraseñas en argv."""
    db_pass = ENV_MAP.get("DB_PASSWORD", "")
    env = os.environ.copy()
    env["MYSQL_PWD"] = db_pass
    cmd = [
        "docker", "compose", "exec", "-T", "-e", "MYSQL_PWD",
        "mysql", "mysql", "-uhuerto_app", "-s", "-N"
    ]
    proc = subprocess.run(
        cmd,
        input=sql,
        text=True,
        capture_output=True,
        cwd=str(ROOT),
        env=env
    )
    if proc.returncode != 0:
        raise RuntimeError(f"Error MySQL (huerto_app): {proc.stderr.strip()}")
    return proc.stdout.strip()


def http(port, method, path, *, token=None, payload=None, key=None, timeout=10):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {"Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    if key:
        headers["Idempotency-Key"] = key
    req = urllib.request.Request(
        f"http://127.0.0.1:{port}{path}",
        data=data,
        headers=headers,
        method=method
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as res:
            raw = res.read()
            body = None
            if raw:
                try:
                    body = json.loads(raw)
                except Exception:
                    body = raw.decode("utf-8", errors="replace")
            return res.status, body
    except urllib.error.HTTPError as exc:
        raw = exc.read()
        body = None
        if raw:
            try:
                body = json.loads(raw)
            except Exception:
                body = raw.decode("utf-8", errors="replace")
        return exc.code, body


def expect(port, method, path, wanted, **kwargs):
    code, body = http(port, method, path, **kwargs)
    check(
        code == wanted,
        f"{method} http://127.0.0.1:{port}{path} -> {code} (esperado {wanted})"
    )
    return body


def rut_valido(numero):
    factor, total = 2, 0
    for digito in reversed(str(numero)):
        total += int(digito) * factor
        factor = 2 if factor == 7 else factor + 1
    resto = 11 - total % 11
    dv = "0" if resto == 11 else "K" if resto == 10 else str(resto)
    return f"{numero}-{dv}"


def sha256_hex(val):
    return hashlib.sha256(val.encode("utf-8")).hexdigest()


class ProcessManager:
    """Gestiona de forma estricta unicamente los procesos iniciados por este runner."""
    def __init__(self):
        self.managed_pids = []

    def check_ports_free_before_start(self):
        occupied = []
        for port in PORTS:
            try:
                with socket.create_connection(("127.0.0.1", port), timeout=0.8):
                    occupied.append(port)
            except Exception:
                pass
        if occupied:
            raise RuntimeError(
                f"Puertos ocupados previamente por procesos externos: {occupied}. "
                "Por seguridad del entorno, este runner NUNCA cierra procesos ajenos. "
                "Libere dichos puertos manualmente antes de ejecutar esta verificacion."
            )

    def start_all(self):
        self.check_ports_free_before_start()
        print("[*] Iniciando los 5 microservicios Spring Boot con perfiles MySQL...", flush=True)
        db_pass = ENV_MAP.get("DB_PASSWORD", "")
        jwt_secret = ENV_MAP.get("JWT_SECRET_BASE64", "")
        jwt_issuer = "huertohogar-usuarios"

        configs = [
            ("catalogo", "backend/catalogo", "backend/catalogo/target/catalogo-service-0.1.0-SNAPSHOT.jar", {
                "PORT": "8080",
                "DB_URL": "jdbc:mysql://localhost:3306/huertohogar_catalogo",
                "DB_USER": "huerto_app",
                "DB_PASSWORD": db_pass,
                "JWT_SECRET_BASE64": jwt_secret,
                "JWT_ISSUER": jwt_issuer,
            }),
            ("usuarios", "backend/usuarios", "backend/usuarios/target/usuarios-service-0.1.0-SNAPSHOT.jar", {
                "SERVER_PORT": "8081",
                "DB_URL": "jdbc:mysql://localhost:3306/huertohogar_usuarios",
                "DB_USER": "huerto_app",
                "DB_PASSWORD": db_pass,
                "JWT_SECRET_BASE64": jwt_secret,
                "JWT_ISSUER": jwt_issuer,
            }),
            ("pedidos", "backend/pedidos", "backend/pedidos/target/pedidos-service-0.1.0-SNAPSHOT.jar", {
                "PORT": "8082",
                "DB_URL": "jdbc:mysql://localhost:3306/huertohogar_pedidos",
                "DB_USER": "huerto_app",
                "DB_PASSWORD": db_pass,
                "JWT_SECRET_BASE64": jwt_secret,
                "JWT_ISSUER": jwt_issuer,
                "PRODUCTOS_BASE_URL": "http://localhost:8080",
                "RECONCILIACION_HABILITADA": "false",
            }),
            ("boleta", "backend/boleta", "backend/boleta/target/boleta-service-0.1.0-SNAPSHOT.jar", {
                "PORT": "8083",
                "DB_URL": "jdbc:mysql://localhost:3306/huertohogar_boleta",
                "DB_USER": "huerto_app",
                "DB_PASSWORD": db_pass,
                "JWT_SECRET_BASE64": jwt_secret,
                "JWT_ISSUER": jwt_issuer,
                "PEDIDOS_BASE_URL": "http://localhost:8082",
            }),
            ("detalle-boleta", "backend/detalle-boleta", "backend/detalle-boleta/target/detalle-boleta-service-0.1.0-SNAPSHOT.jar", {
                "PORT": "8084",
                "DB_URL": "jdbc:mysql://localhost:3306/huertohogar_detalle_boleta",
                "DB_USER": "huerto_app",
                "DB_PASSWORD": db_pass,
                "JWT_SECRET_BASE64": jwt_secret,
                "JWT_ISSUER": jwt_issuer,
                "BOLETAS_BASE_URL": "http://localhost:8083",
                "PEDIDOS_BASE_URL": "http://localhost:8082",
            }),
        ]

        for name, mod_dir, jar_rel, env_vars in configs:
            jar_path = ROOT / jar_rel
            if not jar_path.exists():
                raise FileNotFoundError(f"JAR no encontrado: {jar_path}")
            svc_env = dict(os.environ)
            svc_env.update(env_vars)
            cmd = [JAVA_BIN, "-Xms64m", "-Xmx256m", "-jar", str(jar_path)]
            log_file = ROOT / f"{mod_dir}/target/service.log"
            log_file.parent.mkdir(parents=True, exist_ok=True)
            out_f = open(log_file, "a", encoding="utf-8")
            flags = (0x00000008 | 0x00000200) if os.name == "nt" else 0
            proc = subprocess.Popen(
                cmd,
                env=svc_env,
                cwd=str(ROOT),
                stdout=out_f,
                stderr=out_f,
                creationflags=flags
            )
            self.managed_pids.append(proc.pid)
            print(f"    Lanzado {name} (PID propio: {proc.pid})", flush=True)

        print("[*] Esperando que los 5 microservicios propios respondan HTTP (hasta 90s)...", flush=True)
        for port in PORTS:
            ready = False
            start_t = time.time()
            while time.time() - start_t < 90:
                try:
                    with socket.create_connection(("127.0.0.1", port), timeout=1):
                        code, _ = http(port, "GET", "/swagger-ui.html", timeout=2)
                        if code in (200, 302, 401, 404):
                            ready = True
                            break
                except Exception:
                    pass
                time.sleep(1)
            if not ready:
                raise TimeoutError(f"Servicio en puerto {port} no respondio en 90s")
            print(f"    Puerto {port} listo.", flush=True)

    def stop_managed(self):
        if not self.managed_pids:
            return
        print(f"[*] Deteniendo UNICAMENTE procesos propios del runner: {self.managed_pids}...", flush=True)
        for pid in self.managed_pids:
            try:
                subprocess.run(["taskkill", "/F", "/T", "/PID", str(pid)], capture_output=True)
            except Exception as e:
                print(f"    Error al terminar PID {pid}: {e}", flush=True)
        self.managed_pids.clear()

        # Esperar liberacion de puertos
        for _ in range(25):
            any_busy = False
            for port in PORTS:
                try:
                    with socket.create_connection(("127.0.0.1", port), timeout=0.5):
                        any_busy = True
                        break
                except Exception:
                    pass
            if not any_busy:
                print("[+] Todos los puertos liberados tras terminar procesos propios.", flush=True)
                return
            time.sleep(1)
        raise RuntimeError("No se liberaron los puertos tras detener procesos propios.")

    def restart_all(self):
        print("\n[*] Ejecutando reinicio en frio de los cinco microservicios (preservando BD)...", flush=True)
        self.stop_managed()
        self.start_all()


def rotate_compromised_admin():
    """Invalida y deshabilita permanentemente la cuenta de prueba antigua expuesta en git."""
    sql = (
        "UPDATE huertohogar_usuarios.usuario "
        "SET contrasena_usuario = '$2a$10$ROTATED_DISABLED_ACCOUNT_EXPIRED', rol_usuario = 'CLIENTE' "
        "WHERE email_usuario = 'admin.gate3@huertohogar.cl';"
    )
    mysql_exec(sql)


def provision_ephemeral_admin(run_id):
    """
    Aprovisiona un administrador de laboratorio efímero para este run_id con credencial
    aleatoria en memoria, sin contraseñas literales ni hashes fijos en el repositorio.
    """
    admin_email = f"admin.gate3.{run_id}@huertohogar.test"
    admin_pass = secrets.token_urlsafe(24) + "A1!"
    admin_rut = rut_valido(10000000 + secrets.randbelow(5000000))

    # Registro vía endpoint público (Spring Security calcula el hash BCrypt dinámicamente con su PasswordEncoder)
    admin_reg = expect(8081, "POST", "/api/v1/usuarios", 201, payload={
        "nombreUsuario": f"Admin Lab {run_id}",
        "rutUsuario": admin_rut,
        "direccionUsuario": "Laboratorio Controlado Gate 3",
        "emailUsuario": admin_email,
        "contrasena": admin_pass,
    })
    check(admin_reg["rolUsuario"] == "CLIENTE", "Admin efímero registrado inicialmente como CLIENTE")

    # Elevación de rol controlada en BD local (procedimiento de laboratorio, nunca por endpoint público)
    sql_elevate = f"UPDATE huertohogar_usuarios.usuario SET rol_usuario = 'ADMINISTRADOR' WHERE email_usuario = '{admin_email}';"
    mysql_exec(sql_elevate)
    print("[+] Administrador efímero aprovisionado de forma controlada en BD con credencial dinámica en memoria", flush=True)

    return admin_email, admin_pass


def run_full_gate3():
    print("======================================================================", flush=True)
    print("  GATE 3: VALIDACION TRANSACCIONAL REAL FULL STACK CON MYSQL 8.4", flush=True)
    print("  Auditoria de seguridad: procesos propios, fixtures aisladas, SQL exacto", flush=True)
    print("======================================================================", flush=True)

    pm = ProcessManager()
    run_id = uuid.uuid4().hex[:8]
    admin_email = None

    try:
        # Arranque seguro de servicios
        pm.start_all()

        # 1. Comprobacion de esquemas en MySQL como huerto_app (no root)
        print("\n--- PASO 1: Comprobacion de esquemas en MySQL (usuario huerto_app) ---", flush=True)
        dbs = mysql_exec("SHOW DATABASES LIKE 'huertohogar_%';").splitlines()
        print(f"Esquemas accesibles para huerto_app: {dbs}", flush=True)
        for expected_db in [
            "huertohogar_catalogo", "huertohogar_usuarios", "huertohogar_pedidos",
            "huertohogar_boleta", "huertohogar_detalle_boleta"
        ]:
            check(expected_db in dbs, f"Esquema {expected_db} presente y accesible para huerto_app")

        # 2. Aprovisionamiento de Administrador y Creacion de Fixtures Aisladas
        print("\n--- PASO 2: Aislamiento de fixtures y productos de prueba ---", flush=True)
        rotate_compromised_admin()
        admin_email, admin_pass = provision_ephemeral_admin(run_id)

        # Login de admin para obtener JWT con rol ADMINISTRADOR
        admin_login = expect(8081, "POST", "/api/v1/auth/login", 200, payload={
            "email": admin_email,
            "contrasena": admin_pass
        })
        admin_jwt = admin_login["accessToken"]
        check(admin_login["rolUsuario"] == "ADMINISTRADOR", "JWT administrativo obtenido")

        # Snapshot de productos de trabajo previos (para verificar que NO se alteran)
        catalogo_previo = expect(8080, "GET", "/api/v1/productos", 200)
        stock_previo_compartido = {p["sku"]: p["stock"] for p in catalogo_previo if not p["sku"].startswith("GATE3-")}
        print(f"    Productos de trabajo compartidos identificados (a preservar intactos): {list(stock_previo_compartido.keys())}", flush=True)

        # Crear productos sinteticos con SKU unico para este run (evita agotar stock persistente)
        sku_a = f"GATE3-A-{run_id}"
        sku_b = f"GATE3-B-{run_id}"
        stock_init_a = 15
        stock_init_b = 20
        precio_a = 120
        precio_b = 250

        prod_a = expect(8080, "POST", "/api/v1/productos", 201, token=admin_jwt, payload={
            "sku": sku_a,
            "nombre": f"Tuberculo Aislado {run_id}",
            "descripcion": "Cultivo sintetico exclusivo para prueba transaccional Gate 3",
            "precio": precio_a,
            "stock": stock_init_a,
            "planetaOrigen": "Bruma"
        })
        prod_b = expect(8080, "POST", "/api/v1/productos", 201, token=admin_jwt, payload={
            "sku": sku_b,
            "nombre": f"Fruta Aislada {run_id}",
            "descripcion": "Fruta sintetica exclusiva para prueba transaccional Gate 3",
            "precio": precio_b,
            "stock": stock_init_b,
            "planetaOrigen": "Árido"
        })
        print(f"    Fixtures creadas: Prod A ID={prod_a['id']} ({sku_a}, stock={stock_init_a}), Prod B ID={prod_b['id']} ({sku_b}, stock={stock_init_b})", flush=True)

        # 3. Flujo completo: registro -> login -> pedido -> reserva -> boleta -> detalle
        print("\n--- PASO 3: Registro y Login de Cliente con credenciales dinamicas ---", flush=True)
        client_rut = rut_valido(25000000 + secrets.randbelow(5000000))
        client_email = f"cliente.gate3.{run_id}@huertohogar.test"
        client_pass = secrets.token_urlsafe(16)  # Contrasena generada dinamicamente

        usuario_reg = expect(8081, "POST", "/api/v1/usuarios", 201, payload={
            "nombreUsuario": f"Cliente Gate3 {run_id}",
            "rutUsuario": client_rut,
            "direccionUsuario": "Avda Galaxia 42, Base Norte",
            "emailUsuario": client_email,
            "contrasena": client_pass,
        })
        cliente_id = usuario_reg["idUsuario"]
        check(usuario_reg["rolUsuario"] == "CLIENTE", "Rol asignado por registro publico es estrictamente CLIENTE")

        # Login
        login_data = expect(8081, "POST", "/api/v1/auth/login", 200, payload={
            "email": client_email,
            "contrasena": client_pass,
        })
        jwt_cliente = login_data["accessToken"]
        refresh_token_pre = login_data["refreshToken"]
        refresh_hash = sha256_hex(refresh_token_pre)
        check(bool(jwt_cliente), "JWT de cliente emitido")
        check(bool(refresh_token_pre), "Refresh token emitido")
        check(login_data["idUsuario"] == cliente_id, "ID de usuario coincide")

        # Segundo cliente para verificar aislamiento
        otro_rut = rut_valido(30000000 + secrets.randbelow(5000000))
        otro_email = f"otro.gate3.{run_id}@huertohogar.test"
        otro_pass = secrets.token_urlsafe(16)
        otro_reg = expect(8081, "POST", "/api/v1/usuarios", 201, payload={
            "nombreUsuario": f"Otro Cliente {run_id}",
            "rutUsuario": otro_rut,
            "direccionUsuario": "Colonia Lunar Sur",
            "emailUsuario": otro_email,
            "contrasena": otro_pass,
        })
        otro_login = expect(8081, "POST", "/api/v1/auth/login", 200, payload={
            "email": otro_email,
            "contrasena": otro_pass,
        })
        jwt_otro = otro_login["accessToken"]

        # 4. Creacion de Pedido y Reserva de Stock
        print("\n--- PASO 4: Creacion de Pedido y Reserva de Stock ---", flush=True)
        cant_a = 2
        cant_b = 1
        total_esperado = Decimal(str(cant_a * precio_a + cant_b * precio_b))
        idempotency_key = str(uuid.uuid4())
        pedido_payload = {
            "planetaDestino": "Bruma",
            "productos": [
                {"productoId": prod_a["id"], "cantidad": cant_a},
                {"productoId": prod_b["id"], "cantidad": cant_b},
            ]
        }

        pedido = expect(
            8082, "POST", "/api/v1/pedidos", 201,
            token=jwt_cliente, key=idempotency_key, payload=pedido_payload
        )
        pedido_id = pedido["id"]
        check(pedido["clienteId"] == cliente_id, "Pedido asignado al cliente correcto")
        check(pedido["estado"] == "PENDIENTE", "Pedido en estado PENDIENTE")
        check(Decimal(str(pedido["total"])) == total_esperado, f"Total pedido {pedido['total']} == {total_esperado}")
        check(len(pedido["productos"]) == 2, "Pedido incluye las 2 lineas")

        # 5. Idempotencia en creacion de pedido
        print("\n--- PASO 5: Verificacion de Idempotencia en Pedidos ---", flush=True)
        # Mismo UUID + mismo payload -> 200 OK con mismo pedido
        pedido_reintento = expect(
            8082, "POST", "/api/v1/pedidos", 200,
            token=jwt_cliente, key=idempotency_key, payload=pedido_payload
        )
        check(pedido_reintento["id"] == pedido_id, "Reintento idempotente devuelve el mismo ID de pedido")

        # Mismo UUID + diferente payload -> 409 Conflict
        expect(
            8082, "POST", "/api/v1/pedidos", 409,
            token=jwt_cliente, key=idempotency_key, payload={
                "planetaDestino": "Yermo",
                "productos": [{"productoId": prod_a["id"], "cantidad": 1}]
            }
        )

        # Verificar que el stock se desconto exactamente UNA vez
        cat_post = {p["id"]: p for p in expect(8080, "GET", "/api/v1/productos", 200)}
        check(
            cat_post[prod_a["id"]]["stock"] == stock_init_a - cant_a,
            f"Stock Prod A: {stock_init_a} - {cant_a} = {stock_init_a - cant_a} (real: {cat_post[prod_a['id']]['stock']})"
        )
        check(
            cat_post[prod_b["id"]]["stock"] == stock_init_b - cant_b,
            f"Stock Prod B: {stock_init_b} - {cant_b} = {stock_init_b - cant_b} (real: {cat_post[prod_b['id']]['stock']})"
        )

        # 6. Emision de Boleta academica
        print("\n--- PASO 6: Emision de Boleta Academica ---", flush=True)
        boleta = expect(
            8083, "POST", "/api/v1/boletas", 201,
            token=jwt_cliente, payload={"pedidoId": pedido_id}
        )
        boleta_id = boleta["id"]
        check(boleta["pedidoId"] == pedido_id, "Boleta vinculada al pedido correcto")
        check(boleta["clienteId"] == cliente_id, "Boleta asignada al cliente correcto")
        check(Decimal(str(boleta["total"])) == total_esperado, "Total de boleta coincide con total del pedido")
        check(
            boleta["tipoDocumento"] == "COMPROBANTE_ACADEMICO_NO_TRIBUTARIO",
            "Documento es comprobante academico inmutable"
        )

        # Idempotencia de Boleta: reintento -> 200 OK con mismo ID
        boleta_reintento = expect(
            8083, "POST", "/api/v1/boletas", 200,
            token=jwt_cliente, payload={"pedidoId": pedido_id}
        )
        check(boleta_reintento["id"] == boleta_id, "Reintento de boleta devuelve el mismo ID")

        # 7. Generacion de Detalle Boleta
        print("\n--- PASO 7: Generacion de Detalle de Boleta ---", flush=True)
        detalles = expect(
            8084, "POST", "/api/v1/detalles-boleta", 201,
            token=jwt_cliente, payload={"boletaId": boleta_id}
        )
        check(len(detalles) == 2, f"Detalle contiene 2 lineas (obtenidas: {len(detalles)})")
        check(all(d["boletaId"] == boleta_id for d in detalles), "Todas las lineas pertenecen a la boleta")
        check(sorted(d["numeroLinea"] for d in detalles) == [1, 2], "Lineas numeradas 1 y 2")
        suma_detalles = sum(Decimal(str(d["totalVenta"])) for d in detalles)
        check(suma_detalles == total_esperado, f"Suma de detalles {suma_detalles} == {total_esperado}")

        # Idempotencia de Detalle Boleta: reintento -> 200 OK con mismas lineas
        detalles_reintento = expect(
            8084, "POST", "/api/v1/detalles-boleta", 200,
            token=jwt_cliente, payload={"boletaId": boleta_id}
        )
        check(
            [d["id"] for d in detalles_reintento] == [d["id"] for d in detalles],
            "Reintento de detalle devuelve las mismas lineas"
        )

        # 8. Verificacion de aislamiento y permisos
        print("\n--- PASO 8: Verificacion de aislamiento / permisos ---", flush=True)
        expect(8082, "GET", f"/api/v1/pedidos/{pedido_id}", 403, token=jwt_otro)
        expect(8083, "GET", f"/api/v1/boletas/{boleta_id}", 404, token=jwt_otro)
        expect(8084, "GET", f"/api/v1/detalles-boleta/boleta/{boleta_id}", 404, token=jwt_otro)

        # 9. Verificacion directa en MySQL por IDs y relaciones exactas (sin exposicion de hashes)
        print("\n--- PASO 9: Verificacion directa en MySQL por IDs y relaciones exactas ---", flush=True)

        # 9a. Esquema Usuarios: cliente
        sql_usr = f"SELECT id_usuario, email_usuario, rol_usuario FROM huertohogar_usuarios.usuario WHERE id_usuario = {cliente_id};"
        rows_usr = [line.split('\t') for line in mysql_exec(sql_usr).splitlines() if line]
        check(len(rows_usr) == 1, "Exactamente 1 registro de usuario encontrado para el cliente")
        check(
            rows_usr[0][0] == str(cliente_id) and rows_usr[0][1] == client_email and rows_usr[0][2] == "CLIENTE",
            f"Usuario {cliente_id} coincide con email {client_email} y rol CLIENTE"
        )

        # 9b. Esquema Usuarios: refresh_sesion
        sql_ses = f"SELECT usuario_id, token_hash FROM huertohogar_usuarios.refresh_sesion WHERE usuario_id = {cliente_id};"
        rows_ses = [line.split('\t') for line in mysql_exec(sql_ses).splitlines() if line]
        check(len(rows_ses) == 1, "Exactamente 1 refresh_sesion activa para el cliente en BD")
        check(rows_ses[0][0] == str(cliente_id), "ID de usuario en refresh_sesion coincide")
        check(rows_ses[0][1] == refresh_hash, "Hash de refresh token verificado en BD sin exposicion en log")

        # 9c. Esquema Pedidos: pedido exacto y extraccion de referencia_reserva
        sql_ped = f"SELECT id, cliente_id, estado, total, referencia_reserva, planeta_destino FROM huertohogar_pedidos.pedido WHERE id = {pedido_id};"
        rows_ped = [line.split('\t') for line in mysql_exec(sql_ped).splitlines() if line]
        check(len(rows_ped) == 1, "Exactamente 1 pedido encontrado con el ID generado")
        ped_row = rows_ped[0]
        check(ped_row[0] == str(pedido_id), f"ID de pedido {ped_row[0]} == {pedido_id}")
        check(ped_row[1] == str(cliente_id), f"Cliente del pedido {ped_row[1]} == {cliente_id}")
        check(ped_row[2] == "PENDIENTE", f"Estado del pedido {ped_row[2]} == PENDIENTE")
        check(Decimal(ped_row[3]) == total_esperado, f"Total en BD {ped_row[3]} == {total_esperado}")
        ref_reserva = ped_row[4]
        check(bool(ref_reserva), f"Referencia de reserva generada para vinculacion exacta: {ref_reserva}")
        check(ped_row[5] == "Bruma", "Destino del pedido coincide (Bruma)")

        # Lineas de pedido: cardinalidad y campos exactos
        sql_lp = f"SELECT producto_id, cantidad, precio_unitario, subtotal FROM huertohogar_pedidos.linea_pedido WHERE pedido_id = {pedido_id} ORDER BY producto_id;"
        rows_lp = [line.split('\t') for line in mysql_exec(sql_lp).splitlines() if line]
        check(len(rows_lp) == 2, f"Exactamente 2 lineas de pedido en BD para pedido {pedido_id}")
        check(
            rows_lp[0] == [str(prod_a["id"]), str(cant_a), str(precio_a), str(cant_a * precio_a)],
            f"Linea 1 de pedido coincide exactamente con Prod A ({cant_a}x{precio_a}={cant_a * precio_a})"
        )
        check(
            rows_lp[1] == [str(prod_b["id"]), str(cant_b), str(precio_b), str(cant_b * precio_b)],
            f"Linea 2 de pedido coincide exactamente con Prod B ({cant_b}x{precio_b}={cant_b * precio_b})"
        )

        # 9d. Esquema Catalogo: reserva_stock filtrada EXCLUSIVAMENTE por la referencia_reserva del pedido
        sql_res = f"SELECT referencia, estado, total FROM huertohogar_catalogo.reserva_stock WHERE referencia = '{ref_reserva}';"
        rows_res = [line.split('\t') for line in mysql_exec(sql_res).splitlines() if line]
        check(len(rows_res) == 1, f"Exactamente 1 reserva en Catalogo vinculada a la referencia {ref_reserva}")
        check(
            rows_res[0][0] == ref_reserva and rows_res[0][1] == "RESERVADA" and Decimal(rows_res[0][2]) == total_esperado,
            f"Reserva {ref_reserva} en estado RESERVADA con total exacto {total_esperado}"
        )

        # Lineas de reserva en Catalogo: filtradas por reserva_referencia exacta
        sql_lr = f"SELECT producto_id, cantidad, precio_unitario, subtotal FROM huertohogar_catalogo.linea_reserva WHERE reserva_referencia = '{ref_reserva}' ORDER BY producto_id;"
        rows_lr = [line.split('\t') for line in mysql_exec(sql_lr).splitlines() if line]
        check(len(rows_lr) == 2, f"Exactamente 2 lineas de reserva en Catalogo para referencia {ref_reserva}")
        check(
            rows_lr[0] == [str(prod_a["id"]), str(cant_a), str(precio_a), str(cant_a * precio_a)],
            f"Linea 1 de reserva coincide con Prod A ({cant_a}x{precio_a}={cant_a * precio_a})"
        )
        check(
            rows_lr[1] == [str(prod_b["id"]), str(cant_b), str(precio_b), str(cant_b * precio_b)],
            f"Linea 2 de reserva coincide con Prod B ({cant_b}x{precio_b}={cant_b * precio_b})"
        )

        # Stock en Catalogo
        sql_cat = f"SELECT id, stock FROM huertohogar_catalogo.producto WHERE id IN ({prod_a['id']}, {prod_b['id']}) ORDER BY id;"
        rows_cat = {int(line.split('\t')[0]): int(line.split('\t')[1]) for line in mysql_exec(sql_cat).splitlines() if line}
        check(rows_cat[prod_a["id"]] == stock_init_a - cant_a, f"Stock Prod A en BD: {rows_cat[prod_a['id']]} == {stock_init_a - cant_a}")
        check(rows_cat[prod_b["id"]] == stock_init_b - cant_b, f"Stock Prod B en BD: {rows_cat[prod_b['id']]} == {stock_init_b - cant_b}")

        # 9e. Esquema Boleta: filtrada por boleta_id exacto
        sql_bol = f"SELECT id_boleta, id_usuario_compra, pedido_id, total_venta FROM huertohogar_boleta.boleta WHERE id_boleta = {boleta_id};"
        rows_bol = [line.split('\t') for line in mysql_exec(sql_bol).splitlines() if line]
        check(len(rows_bol) == 1, "Exactamente 1 boleta encontrada con el ID emitido")
        check(
            rows_bol[0][0] == str(boleta_id) and rows_bol[0][1] == str(cliente_id) and rows_bol[0][2] == str(pedido_id) and Decimal(rows_bol[0][3]) == total_esperado,
            f"Boleta {boleta_id} vinculada exactamente a cliente {cliente_id}, pedido {pedido_id} y total {total_esperado}"
        )

        # 9f. Esquema Detalle Boleta: filtrado por id_boleta exacto
        sql_det = f"SELECT numero_linea, id_producto, cantidad_producto, precio_unitario, total_venta FROM huertohogar_detalle_boleta.detalle_boleta WHERE id_boleta = {boleta_id} ORDER BY numero_linea;"
        rows_det = [line.split('\t') for line in mysql_exec(sql_det).splitlines() if line]
        check(len(rows_det) == 2, f"Exactamente 2 lineas de detalle en BD para boleta {boleta_id}")
        check(
            rows_det[0] == ["1", str(prod_a["id"]), str(cant_a), str(precio_a), str(cant_a * precio_a)],
            f"Detalle linea 1 coincide con Prod A (subtotal={cant_a * precio_a})"
        )
        check(
            rows_det[1] == ["2", str(prod_b["id"]), str(cant_b), str(precio_b), str(cant_b * precio_b)],
            f"Detalle linea 2 coincide con Prod B (subtotal={cant_b * precio_b})"
        )
        check(sum(Decimal(r[4]) for r in rows_det) == total_esperado, f"Suma de detalles en BD coincide con total ({total_esperado})")

        # 10. REINICIO DE LOS CINCO SERVICIOS PROPIOS (sin tocar BD)
        print("\n--- PASO 10: Reinicio de los microservicios gestionados (sin tocar BD) ---", flush=True)
        pm.restart_all()

        # 11. Verificacion de persistencia POST-REINICIO
        print("\n--- PASO 11: Verificacion de Persistencia Post-Reinicio ---", flush=True)
        cat_post_restart = {p["id"]: p for p in expect(8080, "GET", "/api/v1/productos", 200)}
        check(
            cat_post_restart[prod_a["id"]]["stock"] == stock_init_a - cant_a,
            f"Persistencia Stock Prod A: {cat_post_restart[prod_a['id']]['stock']} == {stock_init_a - cant_a}"
        )
        check(
            cat_post_restart[prod_b["id"]]["stock"] == stock_init_b - cant_b,
            f"Persistencia Stock Prod B: {cat_post_restart[prod_b['id']]['stock']} == {stock_init_b - cant_b}"
        )

        ped_post = expect(8082, "GET", f"/api/v1/pedidos/{pedido_id}", 200, token=jwt_cliente)
        check(ped_post["id"] == pedido_id, "Pedido persiste tras reinicio")
        check(ped_post["estado"] == "PENDIENTE", "Estado del pedido persiste PENDIENTE")
        check(Decimal(str(ped_post["total"])) == total_esperado, "Total del pedido persiste")

        bol_post = expect(8083, "GET", f"/api/v1/boletas/{boleta_id}", 200, token=jwt_cliente)
        check(bol_post["id"] == boleta_id, "Boleta persiste tras reinicio")
        check(Decimal(str(bol_post["total"])) == total_esperado, "Total de boleta persiste")

        det_post = expect(8084, "GET", f"/api/v1/detalles-boleta/boleta/{boleta_id}", 200, token=jwt_cliente)
        check(len(det_post) == 2, "2 lineas de detalle persisten tras reinicio")
        check(
            sum(Decimal(str(d["totalVenta"])) for d in det_post) == total_esperado,
            "Total de detalles persiste tras reinicio"
        )

        # 12. Renovacion de sesion usando refresh token previo al reinicio
        print("\n--- PASO 12: Renovacion de sesion usando refresh token previo al reinicio ---", flush=True)
        refresh_resp = expect(8081, "POST", "/api/v1/auth/refresh", 200, payload={
            "refreshToken": refresh_token_pre
        })
        jwt_renovado = refresh_resp["accessToken"]
        refresh_token_nuevo = refresh_resp["refreshToken"]
        check(bool(jwt_renovado), "Nuevo JWT emitido via refresh token pre-reinicio")
        check(bool(refresh_token_nuevo), "Nuevo refresh token rotado")
        check(jwt_renovado != jwt_cliente, "Nuevo JWT es distinto al anterior")
        check(refresh_token_nuevo != refresh_token_pre, "Refresh token rotado es distinto al anterior")

        mis_pedidos = expect(8082, "GET", "/api/v1/pedidos/mios", 200, token=jwt_renovado)
        check(any(p["id"] == pedido_id for p in mis_pedidos), "Pedido previo aparece en pedidos/mios con nuevo JWT")

        # Intento de reusar el refresh token anterior (consumido) -> 401 Unauthorized
        expect(8081, "POST", "/api/v1/auth/refresh", 401, payload={
            "refreshToken": refresh_token_pre
        })
        print("[+] Reuso de refresh token anterior rechazado con 401 Unauthorized (un solo uso)", flush=True)

        # Comprobar en MySQL que el hash anterior fue eliminado y el nuevo registrado (sin exponer hashes)
        sql_ses_post = f"SELECT usuario_id, token_hash FROM huertohogar_usuarios.refresh_sesion WHERE usuario_id = {cliente_id};"
        rows_ses_post = [line.split('\t') for line in mysql_exec(sql_ses_post).splitlines() if line]
        check(len(rows_ses_post) == 1, "Exactamente 1 sesion activa tras la rotacion")
        check(rows_ses_post[0][1] != refresh_hash, "Hash anterior eliminado de la BD tras su consumo")
        check(rows_ses_post[0][1] == sha256_hex(refresh_token_nuevo), "Nuevo hash de sesion verificado en BD sin exposicion en log")

        # 13. Comprobacion de no-afectacion de productos de trabajo persistentes
        print("\n--- PASO 13: Comprobacion de integridad del catalogo de trabajo previo ---", flush=True)
        catalogo_final = expect(8080, "GET", "/api/v1/productos", 200)
        stock_final_compartido = {p["sku"]: p["stock"] for p in catalogo_final if not p["sku"].startswith("GATE3-")}
        for sku, stock_prev in stock_previo_compartido.items():
            check(stock_final_compartido.get(sku) == stock_prev,
                  f"Producto de trabajo {sku} permanece intacto con stock={stock_prev} (no consumido por la prueba)")

        print("\n======================================================================", flush=True)
        print("  RESULTADO: GATE 3 PASS (AUDITADO)", flush=True)
        print("  Cinco microservicios validados sobre MySQL 8.4 y HTTP real:", flush=True)
        print("  - Procesos gestionados estrictamente propios (sin matar aplicaciones externas)", flush=True)
        print("  - Fixtures aisladas y stock compartido preservado intacto", flush=True)
        print("  - Consultas SQL relacionales exactas (sin LIMIT 1 ni subcadenas)", flush=True)
        print("  - Cero exposicion de hashes o contrasenas en logs", flush=True)
        print("  - Idempotencia, aislamiento multi-inquilino y persistencia post-reinicio comprobada", flush=True)
        print("  - Renovacion y rotacion de refresh tokens con bloqueo anti-replay", flush=True)
        print("======================================================================", flush=True)

    finally:
        # Desactivar credencial del administrador efímero al concluir
        if admin_email:
            try:
                mysql_exec(
                    f"UPDATE huertohogar_usuarios.usuario "
                    f"SET contrasena_usuario = '$2a$10$EPHEMERAL_DEACTIVATED', rol_usuario = 'CLIENTE' "
                    f"WHERE email_usuario = '{admin_email}';"
                )
            except Exception:
                pass
        # Limpieza estricta de procesos propios al finalizar
        pm.stop_managed()


if __name__ == "__main__":
    run_full_gate3()
