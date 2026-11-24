#!/usr/bin/env python3
"""Pruebas de seguridad del ciclo de vida de cinco JVMs locales."""
import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import manage_local_services as mls


class TestIdentity(unittest.TestCase):
    @staticmethod
    def path(name="pedidos-service-0.1.0-SNAPSHOT.jar", directory="pedidos"):
        return str((mls.ROOT / "backend" / directory / "target" / name).resolve())

    @staticmethod
    def snapshot(jar, started="987654"):
        return {
            "command_line": f'"{mls.JAVA_BIN}" -Xmx256m -jar "{jar}"',
            "created_at": started
        }

    def test_invalid_and_missing_pid(self):
        for value in [None, "abc", 0, -1, 99999999]:
            self.assertIsNone(mls.get_process_snapshot(value))

    def test_current_process_is_not_java_jar(self):
        snap = mls.get_process_snapshot(os.getpid())
        self.assertIsNotNone(snap)
        self.assertFalse(mls.verify_process_identity(
            os.getpid(), self.path(), snap["created_at"], snapshot=snap
        ))

    def test_exact_absolute_jar_and_start_time_are_required(self):
        jar = self.path()
        snap = self.snapshot(jar)
        self.assertTrue(mls.verify_process_identity(42, jar, "987654", snapshot=snap))
        self.assertFalse(mls.verify_process_identity(42, jar, "987655", snapshot=snap))
        self.assertFalse(mls.verify_process_identity(42, jar, None, snapshot=snap))
        self.assertFalse(mls.verify_process_identity(42, Path(jar).name, "987654", snapshot=snap))

    def test_same_basename_from_another_directory_rejected(self):
        expected = self.path()
        wrong_path = self.path(directory="otro_directorio")
        self.assertFalse(mls.verify_process_identity(
            42, expected, "987654", snapshot=self.snapshot(wrong_path)
        ))

    def test_no_match_by_substring_or_injected_args(self):
        jar = self.path()
        self.assertFalse(mls.verify_process_identity(
            42, jar, "987654",
            snapshot={"command_line": f'python script.py --notes "java -jar {jar}"',
                      "created_at": "987654"}
        ))
        self.assertFalse(mls.verify_process_identity(
            42, jar, "987654",
            snapshot={"command_line": f'"{mls.JAVA_BIN}" -classpath "{jar}" Main',
                      "created_at": "987654"}
        ))

    @patch("manage_local_services.check_process_alive", return_value=True)
    @patch("manage_local_services.subprocess.run")
    @patch("manage_local_services.os.kill")
    def test_legacy_pid_fail_closed(self, kill, run, alive):
        legacy = [{"name": "catalogo", "pid": 1234, "jar_name": "catalogo.jar"}, 3456]
        self.assertEqual(mls.stop_pids(legacy), legacy)
        run.assert_not_called()
        kill.assert_not_called()

    @patch("manage_local_services.check_process_alive", return_value=True)
    @patch("manage_local_services.verify_process_identity", return_value=False)
    @patch("manage_local_services.subprocess.run")
    @patch("manage_local_services.os.kill")
    def test_reused_pid_not_killed(self, kill, run, identity, alive):
        entry = {"pid": 12345, "name": "catalogo",
                 "jar_path": self.path(), "created_at": "123"}
        self.assertEqual(mls.stop_pids([entry]), [entry])
        run.assert_not_called()
        kill.assert_not_called()

    @patch("manage_local_services.stop_pids")
    def test_unresolved_records_are_preserved(self, stop):
        with tempfile.TemporaryDirectory() as d:
            f = Path(d) / "pids.json"
            entry = {"pid": 1234, "jar_path": self.path(), "created_at": "3"}
            f.write_text(json.dumps([entry]), encoding="utf-8")
            stop.return_value = [entry]
            with patch.object(mls, "PID_FILE", f):
                mls.stop_services()
            self.assertEqual(json.loads(f.read_text(encoding="utf-8")), [entry])

    @patch("manage_local_services.stop_pids")
    def test_corrupt_pid_file_is_not_deleted(self, stop):
        with tempfile.TemporaryDirectory() as d:
            f = Path(d) / "pids.json"
            f.write_text("not json", encoding="utf-8")
            with patch.object(mls, "PID_FILE", f):
                mls.stop_services()
            self.assertTrue(f.exists())
            stop.assert_not_called()

    @patch("manage_local_services.stop_services")
    @patch("manage_local_services.start_services", side_effect=SystemExit(1))
    def test_second_daemon_must_not_stop_existing_stack(self, start, stop):
        with self.assertRaises(SystemExit):
            mls.daemon_services()
        stop.assert_not_called()

    @patch("manage_local_services.stop_services")
    @patch("manage_local_services.time.sleep", side_effect=KeyboardInterrupt)
    @patch("manage_local_services.start_services", return_value=True)
    def test_owned_daemon_cleanup_on_interrupt(self, start, sleep, stop):
        mls.daemon_services()
        stop.assert_called_once()


if __name__ == "__main__":
    unittest.main()
