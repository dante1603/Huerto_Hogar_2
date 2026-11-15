#!/usr/bin/env python3
"""
Pruebas unitarias de seguridad para manage_local_services.py:
- Validación de identidad de procesos antes de terminar PIDs.
- Protección frente a reutilización de PID (PID reuse).
- Comprobación de argumentos inválidos y procesos inexistentes.
"""
import os
import unittest
from unittest.mock import patch, MagicMock

import manage_local_services as mls


class TestManageServicesProcessIdentity(unittest.TestCase):
    def test_get_process_command_line_invalid_pid(self):
        self.assertEqual(mls.get_process_command_line(None), "")
        self.assertEqual(mls.get_process_command_line("abc"), "")
        self.assertEqual(mls.get_process_command_line(-1), "")
        self.assertEqual(mls.get_process_command_line(99999999), "")

    def test_get_process_command_line_current_process(self):
        cmd = mls.get_process_command_line(os.getpid())
        self.assertTrue(len(cmd) > 0, "Debe retornar la línea de comando del proceso actual")
        self.assertIn("python", cmd.lower(), "El proceso actual debe ser un intérprete python")

    def test_verify_process_identity_rejects_reused_pid_by_python(self):
        # El proceso actual es Python, por ende NO debe coincidir con un JAR de Spring Boot
        jar = "catalogo-service-0.1.0-SNAPSHOT.jar"
        is_valid = mls.verify_process_identity(os.getpid(), jar)
        self.assertFalse(is_valid, "No debe validar el proceso Python actual como un JAR de Catálogo")

    def test_verify_process_identity_empty_args(self):
        self.assertFalse(mls.verify_process_identity(None, "algo.jar"))
        self.assertFalse(mls.verify_process_identity(os.getpid(), None))
        self.assertFalse(mls.verify_process_identity(os.getpid(), ""))

    @patch("manage_local_services.get_process_command_line")
    def test_verify_process_identity_with_mocked_cmdline(self, mock_get_cmd):
        jar = "pedidos-service-0.1.0-SNAPSHOT.jar"

        # Caso válido: Java ejecutando el JAR esperado
        mock_get_cmd.return_value = r"C:\Java\bin\java.exe -Xmx256m -jar backend\pedidos\target\pedidos-service-0.1.0-SNAPSHOT.jar"
        self.assertTrue(mls.verify_process_identity(12345, jar))

        # Caso inválido: PID reutilizado por otro binario (ej. node o chrome)
        mock_get_cmd.return_value = r"node C:\Users\Admin\Documents\app\index.js"
        self.assertFalse(mls.verify_process_identity(12345, jar))

        # Caso inválido: Java ejecutando OTRO servicio (ej. usuarios en vez de pedidos)
        mock_get_cmd.return_value = r"java.exe -jar backend\usuarios\target\usuarios-service-0.1.0-SNAPSHOT.jar"
        self.assertFalse(mls.verify_process_identity(12345, jar))

        # Caso proceso inexistente (cmdline vacía)
        mock_get_cmd.return_value = ""
        self.assertFalse(mls.verify_process_identity(12345, jar))

    @patch("manage_local_services.subprocess.run")
    @patch("manage_local_services.check_process_alive", return_value=True)
    def test_stop_pids_aborts_on_mismatched_identity(self, mock_alive, mock_run):
        # Configurar mock de subprocess.run para cuando get_process_command_line consulta powershell
        mock_res = MagicMock()
        mock_res.returncode = 0
        mock_res.stdout = "python.exe scripts/test_manage_services.py"
        mock_run.return_value = mock_res

        # Simula intento de terminar PID bajo nombre de jar de catalogo
        entries = [{
            "name": "catalogo",
            "pid": 99999,
            "port": 8080,
            "jar_name": "catalogo-service-0.1.0-SNAPSHOT.jar"
        }]

        mls.stop_pids(entries)

        # Verificar que NUNCA se llamó taskkill
        taskkill_calls = [
            c for c in mock_run.call_args_list
            if any("taskkill" in str(arg).lower() for arg in c.args[0] if isinstance(arg, (str, list)))
        ]
        self.assertEqual(len(taskkill_calls), 0, "No debe ejecutar taskkill si la identidad no coincide")


if __name__ == "__main__":
    unittest.main()
