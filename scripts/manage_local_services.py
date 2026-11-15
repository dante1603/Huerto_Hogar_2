#!/usr/bin/env python3
"""
Script de gestión de microservicios locales sobre MySQL 8.4 para desarrollo y pruebas E2E.
Auditoría y seguridad:
- Rastrea exclusivamente los PIDs de JVM propios.
- No termina procesos ajenos ni usa Taskkill ciego.
"""
import csv
import json
import ntpath
import os
import re
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
        try:
            proc = subprocess.Popen(
                cmd,
                env=svc_env,
                cwd=str(ROOT),
                stdout=out_f,
                stderr=out_f,
                creationflags=flags
            )
        finally:
            out_f.close()

        # Guardar ruta ABSOLUTA y momento de creación; un PID solo no es identidad.
        # Si no se puede inspeccionar la JVM recién lanzada, detenerla por su
        # handle Popen y abortar. Nunca guardar una identidad incompleta.
        jar_abs = str(jar_path.resolve())
        snapshot = None
        for _ in range(10):
            snapshot = get_process_snapshot(proc.pid)
            if snapshot or proc.poll() is not None:
                break
            time.sleep(0.2)
        if not snapshot or not verify_process_identity(
            proc.pid, jar_abs, snapshot.get("created_at") if snapshot else None,
            snapshot=snapshot
        ):
            print(f"[-] No se pudo identificar de forma segura la JVM {name}.", file=sys.stderr)
            if proc.poll() is None:
                proc.terminate()  # handle propio de Popen: no usa un PID reciclado.
            stop_pids(managed_pids)
            sys.exit(1)
        managed_pids.append({
            "name": name,
            "pid": proc.pid,
            "port": env_vars.get("PORT") or env_vars.get("SERVER_PORT"),
            "jar_path": jar_abs,
            "created_at": snapshot["created_at"]
        })
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
    return True


def get_process_snapshot(pid):
    """Lee línea de comando y creación de la misma instancia del proceso.

    En Linux relee /proc/<pid>/stat para detectar reciclaje durante la captura.
    Si no se puede demostrar identidad, se devuelve None (fail closed).
    """
    try:
        pid = int(pid)
    except (TypeError, ValueError):
        return None
    if pid <= 0:
        return None

    if os.name == "nt":
        script = (
            f"$p=Get-CimInstance Win32_Process -Filter 'ProcessId = {pid}'; "
            "if ($null -ne $p) { [PSCustomObject]@{ "
            "CommandLine=$p.CommandLine; "
            "Created=$p.CreationDate.ToUniversalTime().ToString('o') "
            "} | ConvertTo-Json -Compress }"
        )
        try:
            res = subprocess.run(
                ["powershell", "-NoProfile", "-NonInteractive", "-Command", script],
                capture_output=True, text=True, timeout=25
            )
            debug = os.environ.get("HH_DEBUG_PROCESS_SNAPSHOT") == "1"
            if debug:
                print(f"[snapshot Windows] exit={res.returncode}, "
                      f"stdout_len={len(res.stdout)}, stderr_len={len(res.stderr)}",
                      file=sys.stderr)
            if res.returncode != 0 or not res.stdout.strip():
                return None
            data = json.loads(res.stdout)
            command = data.get("CommandLine")
            created = data.get("Created")
            if debug:
                print(f"[snapshot Windows] command_type={type(command).__name__}, "
                      f"creation_type={type(created).__name__}", file=sys.stderr)
            if not isinstance(command, str) or not isinstance(created, str):
                return None
            return {"command_line": command, "created_at": created}
        except (OSError, ValueError, subprocess.TimeoutExpired) as exc:
            if os.environ.get("HH_DEBUG_PROCESS_SNAPSHOT") == "1":
                print(f"[snapshot Windows] error={type(exc).__name__}", file=sys.stderr)
            return None

    stat_path = Path(f"/proc/{pid}/stat")
    cmd_path = Path(f"/proc/{pid}/cmdline")

    def read_start_time():
        data = stat_path.read_text(encoding="utf-8", errors="replace")
        fields = data[data.rfind(")") + 1:].split()
        if len(fields) < 20:
            raise ValueError("stat incompleto")
        return fields[19]  # starttime: campo 22 del /proc/<pid>/stat

    try:
        started_before = read_start_time()
        command = cmd_path.read_bytes().replace(b"\x00", b" ").decode(
            "utf-8", errors="replace"
        ).strip()
        started_after = read_start_time()
        if not command or started_before != started_after:
            return None
        return {"command_line": command, "created_at": started_before}
    except (OSError, ValueError):
        return None


def get_process_command_line(pid):
    """Compatibilidad de diagnóstico: la decisión de parar usa snapshot completo."""
    snapshot = get_process_snapshot(pid)
    return snapshot["command_line"] if snapshot else ""


def _normalized_absolute_jar(path):
    if not isinstance(path, str) or not path:
        return None
    if os.name == "nt":
        return ntpath.normcase(ntpath.normpath(path)) if ntpath.isabs(path) else None
    return os.path.normcase(os.path.normpath(path)) if os.path.isabs(path) else None


def _java_jar_from_command(command):
    # Aceptar únicamente el ejecutable Java como proceso principal, con -jar.
    exe = re.match(r'^\s*(?:"([^"]+)"|(\S+))', command)
    if not exe:
        return None
    exe_name = ntpath.basename(exe.group(1) or exe.group(2)).lower()
    if exe_name not in {"java", "java.exe", "javaw.exe"}:
        return None
    jar = re.search(r'(?:^|\s)-jar\s+(?:"([^"]+)"|(\S+))(?=\s|$)', command, re.I)
    return (jar.group(1) or jar.group(2)) if jar else None


def verify_process_identity(pid, expected_jar, expected_created_at=None, *, snapshot=None):
    """Impide taskkill si PID, ruta absoluta del JAR o creación no coinciden."""
    expected = _normalized_absolute_jar(expected_jar)
    if not expected or not expected_created_at:
        return False
    observed = snapshot if snapshot is not None else get_process_snapshot(pid)
    if not observed or str(observed.get("created_at")) != str(expected_created_at):
        return False
    command = observed.get("command_line")
    if not isinstance(command, str):
        return False
    launched_jar = _java_jar_from_command(command)
    return bool(launched_jar and _normalized_absolute_jar(launched_jar) == expected)


def stop_pids(pids_data):
    """No termina jamás procesos sin identidad; informa entradas no resueltas."""
    unresolved = []
    for entry in pids_data:
        if not isinstance(entry, dict):
            print("[!] PID legado sin metadatos: terminación rechazada.", file=sys.stderr)
            unresolved.append(entry)
            continue
        pid = entry.get("pid")
        name = entry.get("name", str(pid))
        if not pid:
            unresolved.append(entry)
            continue
        if not check_process_alive(pid):
            print(f"    Proceso {name} (PID: {pid}) ya no está activo.", flush=True)
            continue

        if not verify_process_identity(
            pid, entry.get("jar_path"), entry.get("created_at")
        ):
            print(
                f"[!] Identidad incompleta o distinta de {name} (PID {pid}). "
                "No se terminará este proceso; requiere revisión.",
                file=sys.stderr, flush=True
            )
            unresolved.append(entry)
            continue

        try:
            if os.name == "nt":
                result = subprocess.run(
                    ["taskkill", "/F", "/T", "/PID", str(pid)],
                    capture_output=True, text=True, timeout=12
                )
                if result.returncode != 0:
                    raise RuntimeError(f"taskkill devolvió {result.returncode}")
            else:
                os.kill(int(pid), signal.SIGTERM)
            print(f"    Terminado {name} (PID: {pid})", flush=True)
        except Exception as exc:
            print(f"[!] No se pudo detener {name} (PID: {pid}): {exc}", file=sys.stderr)
            unresolved.append(entry)
    return unresolved


def stop_services():
    if not PID_FILE.exists():
        print("[!] No hay archivo de PIDs activos registrado.")
        return
    try:
        with open(PID_FILE, "r", encoding="utf-8") as f:
            pids_data = json.load(f)
        if not isinstance(pids_data, list):
            raise ValueError("registro de PIDs no es una lista")
    except (OSError, ValueError) as exc:
        print(f"[!] Registro de PIDs ilegible: {exc}. No se elimina.", file=sys.stderr)
        return
    print("[*] Terminando microservicios gestionados...", flush=True)
    unresolved = stop_pids(pids_data)
    if unresolved:
        # Mantener la evidencia para revisión/reintento: nunca fingir que se detuvo todo.
        PID_FILE.write_text(json.dumps(unresolved, indent=2), encoding="utf-8")
        print(f"[!] {len(unresolved)} proceso(s) sin cierre verificado; revisar {PID_FILE}.",
              file=sys.stderr)
        return
    PID_FILE.unlink()
    print("[+] Servicios gestionados detenidos.", flush=True)


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
    owns_stack = False
    try:
        # Si start_services rechaza iniciar, NO detener una pila preexistente.
        owns_stack = start_services()
        print("[*] Modo daemon activo. Presiona Ctrl+C o termina la tarea para detener.", flush=True)
        while True:
            time.sleep(2)
            if not PID_FILE.exists():
                break
            try:
                with open(PID_FILE, "r", encoding="utf-8") as f:
                    pids_data = json.load(f)
            except Exception:
                break
            for entry in pids_data:
                pid = entry["pid"]
                name = entry["name"]
                if not check_process_alive(pid):
                    print(f"[!] ADVERTENCIA: {name} (PID: {pid}) ha finalizado inesperadamente.", flush=True)
    except KeyboardInterrupt:
        pass
    finally:
        if owns_stack:
            stop_services()


def check_process_alive(pid):
    try:
        pid = int(pid)
        if pid <= 0:
            return False
        if os.name == "nt":
            res = subprocess.run(
                ["tasklist", "/FI", f"PID eq {pid}", "/FO", "CSV"],
                capture_output=True, text=True, timeout=8
            )
            if res.returncode != 0:
                return False
            # No buscar por subcadena: comparar columna PID numérica exacta.
            return any(
                len(row) >= 2 and row[1].strip() == str(pid)
                for row in csv.reader(res.stdout.splitlines())
            )
        os.kill(pid, 0)
        return True
    except (OSError, ValueError, subprocess.TimeoutExpired):
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
