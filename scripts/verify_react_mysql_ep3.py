#!/usr/bin/env python3
"""
Verificación E2E de React con Microservicios Independientes y MySQL 8.4
Ejecuta solicitudes a través del proxy de Vite (puerto 5173) hacia:
- Catálogo (:8080)
- Usuarios (:8081)
- Pedidos (:8082)
- Boleta (:8083)
- Detalle Boleta (:8084)
Valida persistencia relacional, integridad, idempotencia, stock y aislamiento en MySQL.
"""
import base64
import hashlib
import json
import os
import secrets
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid
from decimal import Decimal
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VITE_URL = "http://127.0.0.1:5173"


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
    return env


ENV_MAP = load_env()


def check(cond, desc):
    if not cond:
        print(f"[-] FALLO: {desc}", file=sys.stderr, flush=True)
        raise AssertionError(desc)
    print(f"[+] OK: {desc}", flush=True)


def mysql_exec(sql):
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


def http(method, path, *, token=None, payload=None, key=None, timeout=10):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {"Accept": "application/json"}
    if data is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    if key:
        headers["Idempotency-Key"] = key
    url = f"{VITE_URL}{path}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
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


def rut_valido(numero):
    factor, total = 2, 0
    for digito in reversed(str(numero)):
        total += int(digito) * factor
        factor = 2 if factor == 7 else factor + 1
    resto = 11 - total % 11
    dv = "0" if resto == 11 else "K" if resto == 10 else str(resto)
    return f"{numero}-{dv}"


def run_e2e():
    run_id = secrets.token_hex(4)
    print(f"=== INICIANDO VALIDACIÓN E2E VITE + MYSQL (Run ID: {run_id}) ===", flush=True)

    # 1. Catálogo inicial vía proxy Vite y snapshot de inventario compartido
    status, productos = http("GET", "/api/v1/productos")
    check(status == 200, "Vite proxy GET /api/v1/productos responde 200")
    check(isinstance(productos, list) and len(productos) > 0, "Catálogo tiene productos disponibles")
    stock_previo_compartido = {p["sku"]: p["stock"] for p in productos}
    print(f"    Snapshot de inventario compartido registrado ({len(stock_previo_compartido)} SKUs existentes)", flush=True)

    # 1.1 Aislamiento de prueba: provisionar Administrador efímero y producto dinámico REACT-E2E-{run_id}
    admin_email = f"admin.e2e.{run_id}@huertohogar.test"
    admin_password = f"Admin.{run_id}.Secure!1"
    admin_rut = rut_valido(16000000 + secrets.randbelow(999999))
    status, admin_reg = http("POST", "/usuarios-api/api/v1/usuarios", payload={
        "nombreUsuario": f"Admin E2E {run_id}",
        "emailUsuario": admin_email,
        "contrasena": admin_password,
        "rutUsuario": admin_rut,
        "direccionUsuario": "Sede Administrativa HuertoHogar"
    })
    check(status == 201, f"Registro de administrador efímero exitoso: {admin_email}")
    admin_id = admin_reg.get("idUsuario") or admin_reg.get("id")

    # Elevar rol a ADMINISTRADOR en base de datos MySQL real
    mysql_exec(f"UPDATE huertohogar_usuarios.usuario SET rol_usuario = 'ADMINISTRADOR' WHERE id_usuario = {admin_id};")

    # Autenticar como ADMINISTRADOR para obtener JWT con permisos de creación de productos
    status, admin_login = http("POST", "/usuarios-api/api/v1/auth/login", payload={
        "email": admin_email,
        "contrasena": admin_password
    })
    check(status == 200, "Login de administrador efímero exitoso (200)")
    admin_jwt = admin_login["accessToken"]
    check(admin_jwt and len(admin_jwt) > 20, "JWT de administrador obtenido")

    # Crear producto dinámico aislado para el ensayo E2E
    sku_dinamico = f"REACT-E2E-{run_id}".upper()
    stock_inicial = 10
    precio_unitario = 150
    prod_payload = {
        "sku": sku_dinamico,
        "nombre": f"Cultivo Aislado E2E {run_id}",
        "descripcion": "Producto efímero de prueba aislada para no consumir inventario preexistente.",
        "precio": precio_unitario,
        "stock": stock_inicial,
        "planetaOrigen": "Bruma"
    }
    status, prod = http("POST", "/api/v1/productos", token=admin_jwt, payload=prod_payload)
    check(status == 201, f"Producto de prueba aislado creado en Catálogo (201): SKU {sku_dinamico}, ID {prod.get('id')}")
    prod_id = prod["id"]
    print(f"    Producto aislado listo: {prod['nombre']} (ID: {prod_id}, Stock: {stock_inicial}, Precio: ${precio_unitario})", flush=True)

    # 2. Registro de Cliente 1 vía proxy Vite
    email1 = f"cliente.react.{run_id}@huertohogar.test"
    password1 = f"Clave.{run_id}.Secure!1"
    rut1 = rut_valido(17000000 + secrets.randbelow(999999))
    status, reg_body = http("POST", "/usuarios-api/api/v1/usuarios", payload={
        "nombreUsuario": f"Cliente React {run_id}",
        "emailUsuario": email1,
        "contrasena": password1,
        "rutUsuario": rut1,
        "direccionUsuario": "Planeta Bruma Sector 7"
    })
    check(status == 201, f"Registro de Cliente 1 exitoso (201): {email1}")
    cliente1_id = reg_body.get("idUsuario") or reg_body.get("id")

    # 3. Login Cliente 1 vía proxy Vite
    status, login_body = http("POST", "/usuarios-api/api/v1/auth/login", payload={
        "email": email1,
        "contrasena": password1
    })
    check(status == 200, "Login Cliente 1 exitoso (200)")
    jwt1 = login_body["accessToken"]
    refresh1 = login_body["refreshToken"]
    check(jwt1 and len(jwt1) > 20, "JWT accessToken válido emitido")
    check(refresh1 and len(refresh1) == 43, "Refresh token válido emitido")

    # 4. Crear Pedido en servicio Pedidos (:8082) vía proxy Vite
    idempotency_key = str(uuid.uuid4())
    cantidad_compra = 2
    total_esperado = precio_unitario * cantidad_compra
    pedido_payload = {
        "planetaDestino": "Bruma",
        "productos": [{"productoId": prod_id, "cantidad": cantidad_compra}]
    }

    status, pedido_body = http("POST", "/api/v1/pedidos", token=jwt1, key=idempotency_key, payload=pedido_payload)
    check(status == 201, f"Creación de pedido exitosa en Pedidos 8082 (201): Pedido #{pedido_body.get('id')}")
    pedido_id = pedido_body["id"]
    check(pedido_body["estado"] == "PENDIENTE", "Estado de pedido inicial es PENDIENTE")
    check(pedido_body["planetaDestino"] == "Bruma", "Destino coincide")
    check(Decimal(str(pedido_body["total"])) == Decimal(str(total_esperado)), f"Total calculado coincide: {total_esperado}")

    # 5. Idempotencia de Pedido: Reintento con misma clave y payload devuelve 200 sin nuevo descuento
    status, retry_body = http("POST", "/api/v1/pedidos", token=jwt1, key=idempotency_key, payload=pedido_payload)
    check(status == 200, "Reintento de pedido con misma Idempotency-Key devuelve 200 OK")
    check(retry_body["id"] == pedido_id, "Reintento devuelve el mismo ID de pedido")

    # 6. Conflicto de Idempotencia: misma clave con payload diferente devuelve 409
    payload_conflict = {
        "planetaDestino": "Árido",
        "productos": [{"productoId": prod_id, "cantidad": cantidad_compra}]
    }
    status, _ = http("POST", "/api/v1/pedidos", token=jwt1, key=idempotency_key, payload=payload_conflict)
    check(status == 409, "Misma Idempotency-Key con payload modificado devuelve 409 CONFLICT")

    # 6.1 Prueba de stock insuficiente: solicitar más unidades de las disponibles devuelve 409
    key_insuficiente = str(uuid.uuid4())
    payload_insuficiente = {
        "planetaDestino": "Bruma",
        "productos": [{"productoId": prod_id, "cantidad": 999}]
    }
    status_insuf, _ = http("POST", "/api/v1/pedidos", token=jwt1, key=key_insuficiente, payload=payload_insuficiente)
    check(status_insuf == 409, "Intento de pedido con stock insuficiente devuelve 409 CONFLICT")

    # 7. Verificación de descuento de stock en Catálogo (:8080)
    status, prod_actualizado = http("GET", f"/api/v1/productos/{prod_id}")
    check(status == 200, "Consulta de producto actualizado en Catálogo responde 200")
    check(prod_actualizado["stock"] == stock_inicial - cantidad_compra,
          f"Stock decrementado exactamente en {cantidad_compra} unidades ({stock_inicial} -> {prod_actualizado['stock']})")

    # 8. Consulta de Mis Pedidos y Detalle de Pedido vía proxy Vite
    status, mios = http("GET", "/api/v1/pedidos/mios", token=jwt1)
    check(status == 200, "Consulta /api/v1/pedidos/mios responde 200")
    check(any(p["id"] == pedido_id for p in mios), "El pedido creado aparece en la lista del cliente")

    status, detalle_pedido = http("GET", f"/api/v1/pedidos/{pedido_id}", token=jwt1)
    check(status == 200, f"Consulta /api/v1/pedidos/{pedido_id} responde 200")
    check(detalle_pedido["id"] == pedido_id, "ID del pedido coincide")

    # 9. Aislamiento multi-inquilino: Registrar Cliente 2 y verificar que no pueda acceder al pedido de Cliente 1
    email2 = f"cliente2.react.{run_id}@huertohogar.test"
    password2 = f"Clave.{run_id}.Secure!2"
    rut2 = rut_valido(17000000 + secrets.randbelow(999999))
    status, reg2 = http("POST", "/usuarios-api/api/v1/usuarios", payload={
        "nombreUsuario": f"Cliente React Dos {run_id}",
        "emailUsuario": email2,
        "contrasena": password2,
        "rutUsuario": rut2,
        "direccionUsuario": "Planeta Árido Sector 2"
    })
    check(status == 201, "Registro de Cliente 2 exitoso")
    status, login2 = http("POST", "/usuarios-api/api/v1/auth/login", payload={"email": email2, "contrasena": password2})
    check(status == 200, "Login Cliente 2 exitoso")
    jwt2 = login2["accessToken"]

    status, _ = http("GET", f"/api/v1/pedidos/{pedido_id}", token=jwt2)
    check(status in (403, 404), f"Cliente 2 bloqueado de acceder a pedido de Cliente 1 (HTTP {status})")

    # 10. Emisión de Boleta Académica en servicio Boleta (:8083) vía proxy Vite
    status, boleta_body = http("POST", "/api/v1/boletas", token=jwt1, payload={"pedidoId": pedido_id})
    check(status == 201, f"Emisión de Boleta exitosa en Boleta 8083 (201): Boleta #{boleta_body.get('id')}")
    boleta_id = boleta_body["id"]
    check(boleta_body["pedidoId"] == pedido_id, "Boleta vinculada al pedidoId correcto")
    check(boleta_body["clienteId"] == cliente1_id, "Boleta vinculada al clienteId correcto")
    check(Decimal(str(boleta_body["total"])) == Decimal(str(total_esperado)), "Total de boleta coincide")
    check(boleta_body["moneda"] == "GALATICOINS", "Moneda es GALATICOINS")
    check(boleta_body["tipoDocumento"] == "COMPROBANTE_ACADEMICO_NO_TRIBUTARIO", "Tipo de documento no tributario verificado")

    # 11. Idempotencia de Boleta: Reintento devuelve 200 OK con mismo ID
    status, boleta_retry = http("POST", "/api/v1/boletas", token=jwt1, payload={"pedidoId": pedido_id})
    check(status == 200, "Reintento de emisión de boleta devuelve 200 OK")
    check(boleta_retry["id"] == boleta_id, "Reintento devuelve la misma Boleta")

    # Cliente 2 no puede acceder a boleta de Cliente 1
    status, _ = http("GET", f"/api/v1/boletas/{boleta_id}", token=jwt2)
    check(status in (403, 404), f"Cliente 2 bloqueado de acceder a boleta de Cliente 1 (HTTP {status})")

    # 12. Generación de Líneas de Detalle en servicio Detalle Boleta (:8084) vía proxy Vite
    status, detalles_body = http("POST", "/api/v1/detalles-boleta", token=jwt1, payload={"boletaId": boleta_id})
    check(status == 201, f"Generación de Detalle Boleta exitosa en 8084 (201): {len(detalles_body)} líneas")
    check(isinstance(detalles_body, list) and len(detalles_body) == 1, "Generó exactamente 1 línea de detalle")
    linea = detalles_body[0]
    check(linea["boletaId"] == boleta_id, "Línea vinculada a boletaId correcto")
    check(linea["productoId"] == prod_id, "Línea contiene productoId correcto")
    check(linea["cantidadProducto"] == cantidad_compra, "Cantidad de producto coincide")
    check(Decimal(str(linea["totalVenta"])) == Decimal(str(total_esperado)), "Subtotal de venta coincide con total del pedido")

    # 13. Idempotencia de Detalle Boleta: Reintento devuelve 200 OK con mismas líneas
    status, detalles_retry = http("POST", "/api/v1/detalles-boleta", token=jwt1, payload={"boletaId": boleta_id})
    check(status == 200, "Reintento de generación de detalle devuelve 200 OK")
    check(len(detalles_retry) == 1 and detalles_retry[0]["id"] == linea["id"], "Mismas líneas de detalle devueltas sin duplicación")

    # Consulta de líneas por boletaId
    status, detalles_consulta = http("GET", f"/api/v1/detalles-boleta/boleta/{boleta_id}", token=jwt1)
    check(status == 200, f"Consulta GET /api/v1/detalles-boleta/boleta/{boleta_id} responde 200")
    check(len(detalles_consulta) == 1, "Retorna la línea de detalle consultada")

    # 14. Verificación de consistencia relacional directa en los 5 esquemas de MySQL 8.4
    print("[*] Verificando consistencia en los 5 esquemas MySQL 8.4...", flush=True)

    # Esquema 1: huertohogar_usuarios
    sql_user = f"SELECT id_usuario, email_usuario, rol_usuario FROM huertohogar_usuarios.usuario WHERE id_usuario = {cliente1_id};"
    user_row = mysql_exec(sql_user).split()
    check(len(user_row) >= 3 and user_row[1] == email1 and user_row[2] == "CLIENTE",
          f"Usuario {cliente1_id} verificado en huertohogar_usuarios: {user_row}")

    # Esquema 2: huertohogar_pedidos
    sql_ped = f"SELECT id, cliente_id, total, estado, planeta_destino, referencia_reserva FROM huertohogar_pedidos.pedido WHERE id = {pedido_id};"
    ped_row = mysql_exec(sql_ped).split()
    check(len(ped_row) >= 6 and int(ped_row[0]) == pedido_id and int(ped_row[1]) == cliente1_id and ped_row[3] == "PENDIENTE",
          f"Pedido {pedido_id} verificado en huertohogar_pedidos: {ped_row}")
    ref_reserva = ped_row[5]

    sql_linea = f"SELECT id, pedido_id, producto_id, cantidad, subtotal FROM huertohogar_pedidos.linea_pedido WHERE pedido_id = {pedido_id};"
    linea_row = mysql_exec(sql_linea).split()
    check(len(linea_row) >= 5 and int(linea_row[1]) == pedido_id and int(linea_row[2]) == prod_id and int(linea_row[3]) == cantidad_compra,
          f"Línea de pedido verificada en huertohogar_pedidos: {linea_row}")

    # Esquema 3: huertohogar_catalogo
    sql_reserva = f"SELECT referencia, estado, total FROM huertohogar_catalogo.reserva_stock WHERE referencia = '{ref_reserva}';"
    reserva_row = mysql_exec(sql_reserva).split()
    check(len(reserva_row) >= 3 and reserva_row[1] == "RESERVADA",
          f"Reserva atómica registrada en huertohogar_catalogo: {reserva_row}")

    # Esquema 4: huertohogar_boleta
    sql_boleta = f"SELECT id_boleta, pedido_id, id_usuario_compra, total_venta FROM huertohogar_boleta.boleta WHERE id_boleta = {boleta_id};"
    boleta_row = mysql_exec(sql_boleta).split()
    check(len(boleta_row) >= 4 and int(boleta_row[0]) == boleta_id and int(boleta_row[1]) == pedido_id and int(boleta_row[2]) == cliente1_id,
          f"Boleta {boleta_id} verificada en huertohogar_boleta: {boleta_row}")

    # Esquema 5: huertohogar_detalle_boleta
    sql_det = f"SELECT id_detalle, id_boleta, numero_linea, id_producto, cantidad_producto, total_venta FROM huertohogar_detalle_boleta.detalle_boleta WHERE id_boleta = {boleta_id};"
    det_row = mysql_exec(sql_det).split()
    check(len(det_row) >= 6 and int(det_row[1]) == boleta_id and int(det_row[3]) == prod_id and int(det_row[4]) == cantidad_compra,
          f"Detalle de boleta verificado en huertohogar_detalle_boleta: {det_row}")

    # 15. Verificación de aislamiento estricto: inventario compartido preexistente intacto
    print("[*] Verificando que ningún producto preexistente haya sufrido descuento de inventario...", flush=True)
    status, productos_finales = http("GET", "/api/v1/productos")
    check(status == 200, "Consulta de inventario post-ejecución responde 200")
    stock_final_map = {p["sku"]: p["stock"] for p in productos_finales}
    for sku_prev, stock_prev in stock_previo_compartido.items():
        stock_post = stock_final_map.get(sku_prev)
        check(stock_post == stock_prev,
              f"Stock compartido de SKU '{sku_prev}' inalterado ({stock_prev} -> {stock_post})")
    print(f"[+] Verificado: los {len(stock_previo_compartido)} SKUs preexistentes conservan su inventario exacto.", flush=True)

    # 16. Revocación de credenciales del administrador efímero en MySQL
    mysql_exec(f"UPDATE huertohogar_usuarios.usuario SET rol_usuario = 'CLIENTE', contrasena_usuario = 'REVOCADO_INACTIVO' WHERE id_usuario = {admin_id};")
    print(f"[+] Credenciales del administrador efímero {admin_email} revocadas en huertohogar_usuarios.", flush=True)

    print(f"\n[+++] VALIDACIÓN E2E DE REACT CON 5 MICROSERVICIOS Y MYSQL 8.4: EXITOSA [+++]", flush=True)


if __name__ == "__main__":
    run_e2e()
