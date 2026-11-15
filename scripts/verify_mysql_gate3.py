#!/usr/bin/env python3
"""
Verificacion de Gate 3: Persistencia transaccional real de Full Stack con MySQL 8.4.
Cinco microservicios independientes sobre HTTP real, esquemas MySQL reales y reinicio con persistencia.
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

PORTS = {
    "catalogo": 8080,
    "usuarios": 8081,
    "pedidos": 8082,
    "boleta": 8083,
    "detalle": 8084,
}

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
    """Ejecuta SQL en MySQL a traves de docker compose exec sin exponer secretos."""
    cmd = [
        "docker", "compose", "exec", "-T", "mysql",
        "sh", "-c", 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot'
    ]
    proc = subprocess.run(
        cmd,
        input=sql,
        text=True,
        capture_output=True,
        cwd=str(ROOT)
    )
    if proc.returncode != 0:
        raise RuntimeError(f"Error MySQL: {proc.stderr}")
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


def find_pids_on_ports():
    ps_cmd = (
        "Get-NetTCPConnection -LocalPort 8080,8081,8082,8083,8084 -State Listen "
        "-ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique"
    )
    res = subprocess.run(["powershell", "-Command", ps_cmd], capture_output=True, text=True)
    pids = [int(p.strip()) for p in res.stdout.strip().splitlines() if p.strip().isdigit()]
    return pids


def kill_services():
    pids = find_pids_on_ports()
    print(f"[*] Deteniendo servicios en puertos 8080-8084 (PIDs: {pids})...", flush=True)
    for pid in pids:
        try:
            subprocess.run(["taskkill", "/F", "/T", "/PID", str(pid)], capture_output=True)
        except Exception as e:
            print(f"    Error terminando PID {pid}: {e}", flush=True)
    # Esperar liberacion de puertos
    for _ in range(25):
        remaining = find_pids_on_ports()
        if not remaining:
            print("[+] Todos los puertos 8080-8084 liberados.", flush=True)
            return
        time.sleep(1)
    raise RuntimeError("No se liberaron los puertos 8080-8084 tras taskkill")


def check_port_ready(port, timeout=90):
    start = time.time()
    while time.time() - start < timeout:
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=1):
                code, _ = http(port, "GET", "/swagger-ui.html", timeout=2)
                if code in (200, 302, 401, 404):
                    return True
        except Exception:
            pass
        time.sleep(1)
    return False


def start_services():
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
        subprocess.Popen(
            cmd,
            env=svc_env,
            cwd=str(ROOT),
            stdout=out_f,
            stderr=out_f,
            creationflags=flags
        )
        print(f"    Lanzado {name} ({jar_rel}) -> {log_file}", flush=True)

    # Esperar a que los 5 puertos respondan
    print("[*] Esperando que los 5 microservicios respondan HTTP (hasta 90s)...", flush=True)
    for port in [8080, 8081, 8082, 8083, 8084]:
        if not check_port_ready(port, timeout=90):
            raise TimeoutError(f"Servicio en puerto {port} no respondio en 90s")
        print(f"    Puerto {port} listo.", flush=True)


def ensure_services_running():
    pids = find_pids_on_ports()
    if len(pids) < 5:
        print("[*] Servicios no encontrados en todos los puertos, iniciando...", flush=True)
        if pids:
            kill_services()
        start_services()
    else:
        print(f"[+] 5 servicios ya activos en puertos 8080-8084 (PIDs: {pids})", flush=True)


def run_full_gate3():
    print("======================================================================", flush=True)
    print("  GATE 3: VALIDACION TRANSACCIONAL REAL FULL STACK CON MYSQL 8.4", flush=True)
    print("======================================================================", flush=True)

    ensure_services_running()

    # 1. Comprobacion de esquemas en MySQL
    print("\n--- PASO 1: Comprobacion de esquemas en MySQL ---", flush=True)
    dbs = mysql_exec("SHOW DATABASES LIKE 'huertohogar_%';").splitlines()
    print("Esquemas encontrados:", dbs, flush=True)
    for expected_db in [
        "huertohogar_catalogo", "huertohogar_usuarios", "huertohogar_pedidos",
        "huertohogar_boleta", "huertohogar_detalle_boleta"
    ]:
        check(expected_db in dbs, f"Esquema {expected_db} presente en MySQL")

    # 2. Comprobacion de catalogo previo y productos de prueba
    print("\n--- PASO 2: Consulta de catalogo y productos de prueba ---", flush=True)
    catalogo = expect(8080, "GET", "/api/v1/productos", 200)
    check(len(catalogo) >= 2, f"Catalogo tiene productos ({len(catalogo)} encontrados)")
    por_sku = {p["sku"]: p for p in catalogo}
    check("BRU-TUB-001" in por_sku, "Producto BRU-TUB-001 existe en catalogo")
    check("ARI-FRU-002" in por_sku, "Producto ARI-FRU-002 existe en catalogo")
    prod_a = por_sku["BRU-TUB-001"]
    prod_b = por_sku["ARI-FRU-002"]
    stock_inicial_a = prod_a["stock"]
    stock_inicial_b = prod_b["stock"]
    print(f"    Producto A: ID {prod_a['id']}, SKU {prod_a['sku']}, Stock={stock_inicial_a}, Precio={prod_a['precio']}", flush=True)
    print(f"    Producto B: ID {prod_b['id']}, SKU {prod_b['sku']}, Stock={stock_inicial_b}, Precio={prod_b['precio']}", flush=True)
    check(stock_inicial_a >= 2 and stock_inicial_b >= 1, "Stock suficiente para compra de prueba")

    # 3. Flujo completo: registro -> login -> pedido -> reserva -> boleta -> detalle
    print("\n--- PASO 3: Registro y Login de Cliente de prueba ---", flush=True)
    sufijo = uuid.uuid4().hex[:8]
    client_rut = rut_valido(25000000 + secrets.randbelow(5000000))
    client_email = f"cliente.gate3.{sufijo}@huertohogar.test"
    client_pass = "TestPassGate3!2026"

    usuario_reg = expect(8081, "POST", "/api/v1/usuarios", 201, payload={
        "nombreUsuario": f"Cliente Gate3 {sufijo}",
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
    check(bool(jwt_cliente), "JWT de cliente emitido")
    check(bool(refresh_token_pre), "Refresh token emitido")
    check(login_data["idUsuario"] == cliente_id, "ID de usuario coincide")

    # Preparar segundo cliente para verificar aislamiento
    sufijo_otro = uuid.uuid4().hex[:8]
    otro_rut = rut_valido(30000000 + secrets.randbelow(5000000))
    otro_email = f"otro.gate3.{sufijo_otro}@huertohogar.test"
    otro_reg = expect(8081, "POST", "/api/v1/usuarios", 201, payload={
        "nombreUsuario": f"Otro Cliente {sufijo_otro}",
        "rutUsuario": otro_rut,
        "direccionUsuario": "Colonia Lunar Sur",
        "emailUsuario": otro_email,
        "contrasena": client_pass,
    })
    otro_login = expect(8081, "POST", "/api/v1/auth/login", 200, payload={
        "email": otro_email,
        "contrasena": client_pass,
    })
    jwt_otro = otro_login["accessToken"]

    print("\n--- PASO 4: Creacion de Pedido y Reserva de Stock ---", flush=True)
    cant_a = 2
    cant_b = 1
    total_esperado = Decimal(str(cant_a * prod_a["precio"] + cant_b * prod_b["precio"]))
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
        cat_post[prod_a["id"]]["stock"] == stock_inicial_a - cant_a,
        f"Stock Prod A: {stock_inicial_a} - {cant_a} = {stock_inicial_a - cant_a} (real: {cat_post[prod_a['id']]['stock']})"
    )
    check(
        cat_post[prod_b["id"]]["stock"] == stock_inicial_b - cant_b,
        f"Stock Prod B: {stock_inicial_b} - {cant_b} = {stock_inicial_b - cant_b} (real: {cat_post[prod_b['id']]['stock']})"
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

    # 8. Verificacion de aislamiento y permisos (seguridad multi-inquilino)
    print("\n--- PASO 8: Verificacion de aislamiento / permisos ---", flush=True)
    # Otro cliente no puede ver pedido ajeno
    expect(8082, "GET", f"/api/v1/pedidos/{pedido_id}", 403, token=jwt_otro)
    # Otro cliente no puede ver boleta ajena
    expect(8083, "GET", f"/api/v1/boletas/{boleta_id}", 404, token=jwt_otro)
    # Otro cliente no puede ver detalles ajenos
    expect(8084, "GET", f"/api/v1/detalles-boleta/boleta/{boleta_id}", 404, token=jwt_otro)

    # 9. Verificacion directa en los esquemas MySQL
    print("\n--- PASO 9: Verificacion directa en cada esquema MySQL ---", flush=True)
    # 9a. Esquema Usuarios
    sql_usr = f"SELECT id_usuario, email_usuario, rol_usuario FROM huertohogar_usuarios.usuario WHERE id_usuario = {cliente_id};"
    out_usr = mysql_exec(sql_usr)
    print("MySQL huertohogar_usuarios.usuario:\n" + out_usr, flush=True)
    check(client_email in out_usr and "CLIENTE" in out_usr, "Usuario registrado en MySQL huertohogar_usuarios")

    refresh_hash = sha256_hex(refresh_token_pre)
    sql_ses = f"SELECT usuario_id, token_hash FROM huertohogar_usuarios.refresh_sesion WHERE usuario_id = {cliente_id};"
    out_ses = mysql_exec(sql_ses)
    print("MySQL huertohogar_usuarios.refresh_sesion:\n" + out_ses, flush=True)
    check(refresh_hash in out_ses, "Hash de refresh token encontrado en MySQL refresh_sesion")

    # 9b. Esquema Catalogo
    sql_cat = f"SELECT id, sku, stock FROM huertohogar_catalogo.producto WHERE id IN ({prod_a['id']}, {prod_b['id']});"
    out_cat = mysql_exec(sql_cat)
    print("MySQL huertohogar_catalogo.producto:\n" + out_cat, flush=True)

    sql_res = "SELECT referencia, estado, total FROM huertohogar_catalogo.reserva_stock ORDER BY creada_en DESC LIMIT 1;"
    out_res = mysql_exec(sql_res)
    print("MySQL huertohogar_catalogo.reserva_stock:\n" + out_res, flush=True)
    check("RESERVADA" in out_res and str(total_esperado) in out_res, "Reserva RESERVADA con total correcto")

    # 9c. Esquema Pedidos
    sql_ped = f"SELECT id, cliente_id, estado, total, referencia_reserva FROM huertohogar_pedidos.pedido WHERE id = {pedido_id};"
    out_ped = mysql_exec(sql_ped)
    print("MySQL huertohogar_pedidos.pedido:\n" + out_ped, flush=True)
    check(f"{pedido_id}" in out_ped and "PENDIENTE" in out_ped and str(total_esperado) in out_ped,
          "Pedido PENDIENTE con total correcto en MySQL huertohogar_pedidos")

    sql_lp = f"SELECT id, producto_id, cantidad, precio_unitario, subtotal FROM huertohogar_pedidos.linea_pedido WHERE pedido_id = {pedido_id};"
    out_lp = mysql_exec(sql_lp)
    print("MySQL huertohogar_pedidos.linea_pedido:\n" + out_lp, flush=True)

    # 9d. Esquema Boleta
    sql_bol = f"SELECT id_boleta, id_usuario_compra, pedido_id, total_venta FROM huertohogar_boleta.boleta WHERE id_boleta = {boleta_id};"
    out_bol = mysql_exec(sql_bol)
    print("MySQL huertohogar_boleta.boleta:\n" + out_bol, flush=True)
    check(f"{boleta_id}" in out_bol and f"{pedido_id}" in out_bol and str(total_esperado) in out_bol,
          "Boleta correcta en MySQL huertohogar_boleta")

    # 9e. Esquema Detalle Boleta
    sql_det = f"SELECT id_detalle, id_boleta, id_producto, numero_linea, cantidad_producto, precio_unitario, total_venta FROM huertohogar_detalle_boleta.detalle_boleta WHERE id_boleta = {boleta_id};"
    out_det = mysql_exec(sql_det)
    print("MySQL huertohogar_detalle_boleta.detalle_boleta:\n" + out_det, flush=True)
    check(f"{boleta_id}" in out_det, "Detalles vinculados a boleta en MySQL")

    # 10. REINICIO DE LOS CINCO SERVICIOS SIN BORRAR BASES
    print("\n--- PASO 10: Reinicio de los cinco microservicios (sin tocar BD) ---", flush=True)
    kill_services()
    start_services()

    # 11. Verificacion de persistencia POST-REINICIO
    print("\n--- PASO 11: Verificacion de Persistencia Post-Reinicio ---", flush=True)
    # Consulta de productos
    cat_post_restart = {p["id"]: p for p in expect(8080, "GET", "/api/v1/productos", 200)}
    check(
        cat_post_restart[prod_a["id"]]["stock"] == stock_inicial_a - cant_a,
        f"Persistencia Stock Prod A: {cat_post_restart[prod_a['id']]['stock']} == {stock_inicial_a - cant_a}"
    )
    check(
        cat_post_restart[prod_b["id"]]["stock"] == stock_inicial_b - cant_b,
        f"Persistencia Stock Prod B: {cat_post_restart[prod_b['id']]['stock']} == {stock_inicial_b - cant_b}"
    )

    # Consulta de pedido
    ped_post = expect(8082, "GET", f"/api/v1/pedidos/{pedido_id}", 200, token=jwt_cliente)
    check(ped_post["id"] == pedido_id, "Pedido persiste tras reinicio")
    check(ped_post["estado"] == "PENDIENTE", "Estado del pedido persiste PENDIENTE")
    check(Decimal(str(ped_post["total"])) == total_esperado, "Total del pedido persiste")

    # Consulta de boleta
    bol_post = expect(8083, "GET", f"/api/v1/boletas/{boleta_id}", 200, token=jwt_cliente)
    check(bol_post["id"] == boleta_id, "Boleta persiste tras reinicio")
    check(Decimal(str(bol_post["total"])) == total_esperado, "Total de boleta persiste")

    # Consulta de detalles
    det_post = expect(8084, "GET", f"/api/v1/detalles-boleta/boleta/{boleta_id}", 200, token=jwt_cliente)
    check(len(det_post) == 2, "2 lineas de detalle persisten tras reinicio")
    check(
        sum(Decimal(str(d["totalVenta"])) for d in det_post) == total_esperado,
        "Total de detalles persiste tras reinicio"
    )

    # 12. Renovacion de sesion usando refresh token pre-reinicio
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

    # Usar nuevo JWT para consultar mis pedidos
    mis_pedidos = expect(8082, "GET", "/api/v1/pedidos/mios", 200, token=jwt_renovado)
    check(len(mis_pedidos) >= 1, "Nuevo JWT permite acceder a pedidos propios")
    check(any(p["id"] == pedido_id for p in mis_pedidos), "Pedido previo aparece en pedidos/mios con nuevo JWT")

    # Intento de reusar el refresh token anterior (consumido) -> 401 Unauthorized
    expect(8081, "POST", "/api/v1/auth/refresh", 401, payload={
        "refreshToken": refresh_token_pre
    })
    print("[+] Reuso de refresh token anterior rechazado con 401 Unauthorized (un solo uso)", flush=True)

    # Comprobar en MySQL que el hash anterior fue eliminado y el nuevo registrado
    out_ses_post = mysql_exec(f"SELECT usuario_id, token_hash FROM huertohogar_usuarios.refresh_sesion WHERE usuario_id = {cliente_id};")
    print("MySQL huertohogar_usuarios.refresh_sesion post-refresh:\n" + out_ses_post, flush=True)
    check(refresh_hash not in out_ses_post, "Hash anterior borrado de MySQL refresh_sesion")
    check(sha256_hex(refresh_token_nuevo) in out_ses_post, "Nuevo hash registrado en MySQL refresh_sesion")

    print("\n======================================================================", flush=True)
    print("  RESULTADO: GATE 3 PASS", flush=True)
    print("  Cinco microservicios validados sobre MySQL 8.4 y HTTP real:", flush=True)
    print("  - Registro, login y refresh token rotativo", flush=True)
    print("  - Creacion de pedido y reserva transaccional con descuento de stock", flush=True)
    print("  - Idempotencia en pedidos, boletas y detalles", flush=True)
    print("  - Emision de comprobante academico inmutable y lineas de detalle", flush=True)
    print("  - Aislamiento multi-inquilino estricto", flush=True)
    print("  - Persistencia comprobada tras reinicio en frio de las cinco JVM", flush=True)
    print("  - Renovacion de sesion exitosa post-reinicio con token previo", flush=True)
    print("======================================================================", flush=True)


if __name__ == "__main__":
    run_full_gate3()
