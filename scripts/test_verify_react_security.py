#!/usr/bin/env python3
"""Regresiones de seguridad E2E sin HTTP real ni mutaciones MySQL."""
import unittest
from unittest.mock import patch

import verify_react_mysql_ep3 as e2e


class TestE2ESecurity(unittest.TestCase):
    def test_product_failure_always_revokes_admin_before_exiting(self):
        state = {"elevated": False, "admin_password": None, "logins": []}
        queries = []

        def fake_mysql(sql):
            queries.append(sql)
            if "SET rol_usuario = 'ADMINISTRADOR'" in sql:
                state["elevated"] = True
            if "SET rol_usuario = 'CLIENTE'" in sql:
                state["elevated"] = False
            if "SELECT rol_usuario, contrasena_usuario" in sql:
                return "CLIENTE\tREVOCADO_INACTIVO"
            return ""

        def fake_http(method, path, **kwargs):
            if method == "GET" and path == "/api/v1/productos":
                return 200, [{"sku": "COMPARTIDO", "stock": 9}]
            if method == "POST" and path == "/usuarios-api/api/v1/usuarios":
                state["admin_password"] = kwargs["payload"]["contrasena"]
                return 201, {"idUsuario": 42}
            if method == "POST" and path == "/usuarios-api/api/v1/auth/login":
                state["logins"].append(kwargs["payload"]["contrasena"])
                if state["elevated"]:
                    return 200, {"accessToken": "fake.valid.jwt.accessToken"}
                return 401, {"error": "Credenciales inválidas"}
            if method == "POST" and path == "/api/v1/productos":
                self.assertTrue(state["elevated"])
                return 503, {"error": "Fallo de catálogo"}
            raise AssertionError(f"Solicitud inesperada: {method} {path}")

        with patch.object(e2e, "mysql_exec", side_effect=fake_mysql), \
             patch.object(e2e, "http", side_effect=fake_http):
            with self.assertRaises(AssertionError):
                e2e.run_e2e()

        self.assertFalse(state["elevated"], "Rol privilegiado debe quedar revocado")
        self.assertEqual(len(state["logins"]), 2, "Incluye 401 después de revocar")
        self.assertEqual(state["logins"][0], state["admin_password"])
        self.assertGreaterEqual(len(state["admin_password"]), 24)
        self.assertFalse(state["admin_password"].startswith("Admin."))
        self.assertTrue(any("SET rol_usuario = 'CLIENTE'" in q for q in queries))

    def test_cleanup_retries_transient_db_failure(self):
        calls = []

        def fake_mysql(sql):
            calls.append(sql)
            if "UPDATE" in sql and len([q for q in calls if "UPDATE" in q]) == 1:
                raise RuntimeError("BD ocupada temporalmente")
            if "SELECT" in sql:
                return "CLIENTE\tREVOCADO_INACTIVO"
            return ""

        with patch.object(e2e, "mysql_exec", side_effect=fake_mysql), \
             patch.object(e2e, "http", return_value=(401, {})), \
             patch.object(e2e.time, "sleep"):
            e2e.revoke_temporary_admin(42, "admin@test.invalid", "secret")
        self.assertEqual(len([q for q in calls if "UPDATE" in q]), 2)

    def test_failed_cleanup_is_never_reported_as_success(self):
        with patch.object(e2e, "mysql_exec", side_effect=RuntimeError("BD inaccesible")), \
             patch.object(e2e.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "REVOCACIÓN NO VERIFICADA"):
                e2e.revoke_temporary_admin(42, "admin@test.invalid", "secret")

    def test_revoked_role_alone_is_not_enough_without_401(self):
        def fake_mysql(sql):
            if "SELECT" in sql:
                return "CLIENTE\tREVOCADO_INACTIVO"
            return ""

        with patch.object(e2e, "mysql_exec", side_effect=fake_mysql), \
             patch.object(e2e, "http", return_value=(200, {"accessToken": "unexpected"})), \
             patch.object(e2e.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "REVOCACIÓN NO VERIFICADA"):
                e2e.revoke_temporary_admin(42, "admin@test.invalid", "secret")


if __name__ == "__main__":
    unittest.main()
