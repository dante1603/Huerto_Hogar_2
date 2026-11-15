#!/usr/bin/env python3
"""
Script de gestión de microservicios locales sobre MySQL 8.4 para desarrollo y pruebas E2E.
Auditoría y seguridad:
- Rastrea exclusivamente los PIDs de JVM propios.
- No termina procesos ajenos ni usa Taskkill ciego.
"""
import json
import os
import signal
import socket
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PID_FILE = ROOT / ".active_microservices_pids.json"
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


def check_port(port, timeout=0.8):
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=timeout):
            return True
    except Exception:
        return False


def start_services():
    if PID_FILE.exists():
        print(f"[!] Ya existe archivo de PIDs activos: {PID_FILE}. Ejecuta 'stop' primero si quedaron procesos activos.", file=sys.stderr)
        sys.exit(1)

    occupied = [p for p in PORTS if check_port(p)]
    if occupied:
        print(f"[-] Error: Puertos ocupados antes del inicio: {occupied}. Libera esos puertos antes de continuar.", file=sys.stderr)
        sys.exit(1)

    env_map = load_env()
    db_pass = env_map.get("DB_PASSWORD", "")
    jwt_secret = env_map.get("JWT_SECRET_BASE64", "")
    jwt_issuer = "huertohogar-usuarios"
    cors_origins = "http://127.0.0.1:5173,http://localhost:5173"

    configs = [
        ("catalogo", "backend/catalogo", "backend/catalogo/target/catalogo-service-0.1.0-SNAPSHOT.jar", {
            "PORT": "8080",
            "DB_URL": "jdbc:mysql://localhost:3306/huertohogar_catalogo",
            "DB_USER": "huerto_app",
            "DB_PASSWORD": db_pass,
            "JWT_SECRET_BASE64": jwt_secret,
            "JWT_ISSUER": jwt_issuer,
            "APP_ALLOWED_ORIGINS": cors_origins,
        }),
        ("usuarios", "backend/usuarios", "backend/usuarios/target/usuarios-service-0.1.0-SNAPSHOT.jar", {
            "SERVER_PORT": "8081",
            "DB_URL": "jdbc:mysql://localhost:3306/huertohogar_usuarios",
            "DB_USER": "huerto_app",
            "DB_PASSWORD": db_pass,
            "JWT_SECRET_BASE64": jwt_secret,
            "JWT_ISSUER": jwt_issuer,
            "APP_ALLOWED_ORIGINS": cors_origins,
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
            "APP_ALLOWED_ORIGINS": cors_origins,
        }),
        ("boleta", "backend/boleta", "backend/boleta/target/boleta-service-0.1.0-SNAPSHOT.jar", {
            "PORT": "8083",
            "DB_URL": "jdbc:mysql://localhost:3306/huertohogar_boleta",
            "DB_USER": "huerto_app",
            "DB_PASSWORD": db_pass,
            "JWT_SECRET_BASE64": jwt_secret,
            "JWT_ISSUER": jwt_issuer,
            "PEDIDOS_BASE_URL": "http://localhost:8082",
            "APP_ALLOWED_ORIGINS": cors_origins,
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
            "APP_ALLOWED_ORIGINS": cors_origins,
        }),
    ]

    managed_pids = []
    print("[*] Iniciando 5 microservicios Spring Boot con perfiles MySQL...", flush=True)
    for name, mod_dir, jar_rel, env_vars in configs:
        jar_path = ROOT / jar_rel
        if not jar_path.exists():
            print(f"[-] JAR no encontrado: {jar_path}", file=sys.stderr)
            stop_pids(managed_pids)
            sys.exit(1)
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
        managed_pids.append({"name": name, "pid": proc.pid, "port": env_vars.get("PORT") or env_vars.get("SERVER_PORT")})
        print(f"    Lanzado {name} (PID: {proc.pid})", flush=True)

    with open(PID_FILE, "w", encoding="utf-8") as f:
        json.dump(managed_pids, f, indent=2)

    print("[*] Esperando que los 5 microservicios respondan HTTP (hasta 90s)...", flush=True)
    for port in PORTS:
        start_t = time.time()
        ready = False
        while time.time() - start_t < 90:
            if check_port(port, timeout=1):
                ready = True
                break
            time.sleep(1)
        if not ready:
            print(f"[-] Microservicio en puerto {port} no respondió en 90s.", file=sys.stderr)
            stop_services()
            sys.exit(1)
        print(f"    Puerto {port} listo.", flush=True)

    print("[+] Los 5 microservicios están en línea y saludables.", flush=True)


def stop_pids(pids_data):
    for entry in pids_data:
        pid = entry["pid"] if isinstance(entry, dict) else entry
        name = entry.get("name", str(pid)) if isinstance(entry, dict) else str(pid)
        try:
            if os.name == "nt":
                subprocess.run(["taskkill", "/F", "/T", "/PID", str(pid)], capture_output=True)
            else:
                os.kill(pid, signal.SIGTERM)
            print(f"    Terminado {name} (PID: {pid})", flush=True)
        except Exception as e:
            print(f"    No se pudo terminar {name} (PID: {pid}): {e}", flush=True)


def stop_services():
    if not PID_FILE.exists():
        print("[!] No hay archivo de PIDs activos registrado.")
        return
    with open(PID_FILE, "r", encoding="utf-8") as f:
        try:
            pids_data = json.load(f)
        except Exception:
            pids_data = []
    print("[*] Terminando microservicios gestionados...", flush=True)
    stop_pids(pids_data)
    try:
        PID_FILE.unlink()
    except Exception:
        pass
    print("[+] Servicios detenidos.", flush=True)


def status_services():
    print("[*] Estado de puertos:")
    for p in PORTS:
        st = "EN LINEA" if check_port(p) else "LIBRE"
        print(f"    Puerto {p}: {st}")
    if PID_FILE.exists():
        with open(PID_FILE, "r", encoding="utf-8") as f:
            print("[*] PIDs registrados:", f.read().strip())
    else:
        print("[*] Sin archivo de PIDs activos.")


def daemon_services():
    try:
        start_services()
        print("[*] Modo daemon activo. Presiona Ctrl+C o termina la tarea para detener.", flush=True)
        while True:
            time.sleep(2)
            # Verificar si algún proceso cayó inesperadamente
            with open(PID_FILE, "r", encoding="utf-8") as f:
                pids_data = json.load(f)
            for entry in pids_data:
                pid = entry["pid"]
                name = entry["name"]
                if not check_process_alive(pid):
                    print(f"[!] ADVERTENCIA: {name} (PID: {pid}) ha finalizado inesperadamente.", flush=True)
    except (KeyboardInterrupt, SystemExit):
        pass
    finally:
        stop_services()


def check_process_alive(pid):
    try:
        if os.name == "nt":
            res = subprocess.run(["tasklist", "/FI", f"PID eq {pid}", "/FO", "CSV"], capture_output=True, text=True)
            return str(pid) in res.stdout
        else:
            os.kill(pid, 0)
            return True
    except Exception:
        return False


if __name__ == "__main__":
    action = sys.argv[1] if len(sys.argv) > 1 else "status"
    if action == "daemon":
        daemon_services()
    elif action == "start":
        start_services()
    elif action == "stop":
        stop_services()
    elif action == "status":
        status_services()
    else:
        print("Uso: manage_local_services.py [daemon|start|stop|status]")
