import copy
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from profile import load, validate
from device import main, matching

class ToolingTest(unittest.TestCase):
    def setUp(self):
        self.profile = load(ROOT / "profiles/xiaomi-astp0.json")
    def test_profiles_reject_unsafe_data(self):
        for key, value in [("port", 0), ("keepAdb", "true"), ("action", "bad; touch x"), ("sdk", 29), ("unknown", 1), ("launchers", ["bad"])]:
            d = copy.deepcopy(self.profile)
            d[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                validate(d)
    def test_exact_match(self):
        facts = {k: str(self.profile[k]) for k in ("manufacturer", "model", "sdk", "firmware")}
        self.assertTrue(matching(self.profile, facts))
        facts["firmware"] = "unverified"
        self.assertFalse(matching(self.profile, facts))
    def test_duplicate_json_keys(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bad.json"
            path.write_text('{"id":"a","id":"b"}')
            with self.assertRaises(ValueError):
                load(path)
    def test_invalid_profile_never_calls_adb(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "bad.json"
            path.write_text('{}')
            with patch("device.subprocess.run") as run, self.assertRaises(ValueError):
                main(["configure", "--serial", "synthetic", "--profile", str(path)])
            run.assert_not_called()
    def test_mismatch_never_mutates(self):
        from device import Device
        with patch.object(Device, "run", return_value="device"), patch.object(Device, "inspect", return_value={"manufacturer":"Other", "model":"Other", "sdk":"28", "firmware":"other"}), patch.object(Device, "shell") as shell:
            with self.assertRaises(RuntimeError):
                main(["configure", "--serial", "synthetic", "--profile", str(ROOT / "profiles/xiaomi-astp0.json")])
            shell.assert_not_called()
    def test_install_saves_originals_and_targets_only_self(self):
        import device
        from device import Device, PKG
        facts = {k: str(self.profile[k]) for k in ("manufacturer", "model", "sdk", "firmware")}
        originals = {"adb_enabled":"1", "accessibility_enabled":"1", "enabled_accessibility_services":"example.voice/.Service", "enabled_notification_listeners":"example.other/.Listener"}
        commands = []
        def shell(*args):
            commands.append(args)
            if args[:2] == ("pm", "path"):
                return "package:/system/example.apk"
            if args[:3] == ("pm", "list", "packages"):
                return ""
            if args[:2] == ("am", "broadcast") and PKG + ".CONFIG" in args:
                return 'Broadcast completed: result=0, data="Configuration saved; awaiting bridge validation"'
            return ""
        def run(*args):
            return "device" if args == ("get-state",) else "Success"
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            apk = root / "test.apk"
            apk.write_bytes(b"synthetic")
            with patch.object(device, "ROOT", root), patch.object(Device, "run", side_effect=run), patch.object(Device, "inspect", return_value=facts), patch.object(Device, "originals", return_value=originals), patch.object(Device, "shell", side_effect=shell):
                main(["install", "--serial", "synthetic", "--profile", str(ROOT / "profiles/xiaomi-astp0.json"), "--apk", str(apk), "--keep-adb"])
            backups = list((root / "local").glob("*.json"))
            self.assertEqual(len(backups), 1)
            self.assertEqual(json.loads(backups[0].read_text())["originals"], originals)
            self.assertEqual(backups[0].stat().st_mode & 0o777, 0o600)
            config = next(c for c in commands if PKG + ".CONFIG" in c)
            self.assertTrue(json.loads(config[-1])["keepAdb"])
            self.assertIn(("cmd", "notification", "allow_listener", PKG + "/" + PKG + ".Revival"), commands)
            self.assertFalse(any(c[0] == "input" or c[:2] == ("settings", "put") for c in commands))
    def test_old_helper_blocks_install_before_mutation(self):
        import device
        from device import Device, OLD
        facts = {k: str(self.profile[k]) for k in ("manufacturer", "model", "sdk", "firmware")}
        def shell(*args):
            return "package:/system/player.apk" if args[:2] == ("pm", "path") else "package:" + OLD
        with patch.object(Device, "run", return_value="device"), patch.object(Device, "inspect", return_value=facts), patch.object(Device, "shell", side_effect=shell), patch.object(Device, "originals") as originals:
            with self.assertRaises(RuntimeError):
                main(["configure", "--serial", "synthetic", "--profile", str(ROOT / "profiles/xiaomi-astp0.json")])
            originals.assert_not_called()
    def test_bootstrap_and_uninstall_embedded_shell_syntax(self):
        import ast
        module = ast.parse((ROOT / "uninstall.py").read_text())
        scripts = [node.value for node in ast.walk(module) if isinstance(node, ast.Constant) and isinstance(node.value, str) and "store=/data/local/tmp/cec-standby" in node.value]
        self.assertEqual(len(scripts), 1)
        for script in scripts:
            subprocess.run(["sh", "-n"], input=script, text=True, check=True)
    def health(self, directory, interactive, now):
        result = subprocess.run(["sh", "-c", '. "$1"; bridge_health "$2" "$3" "$4"; printf "%s" "$healthy"', "health-test", str(ROOT / "assets/health.sh"), directory, str(interactive), str(now)], check=True, text=True, capture_output=True)
        return result.stdout
    def test_health_actual_shell_policy(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d)
            self.assertEqual(self.health(d, 1, 10), "1")
            self.assertEqual(self.health(d, 1, 41), "0")
            (path / "heartbeat").write_text("100000")
            self.assertEqual(self.health(d, 1, 110), "1")
            self.assertEqual(self.health(d, 0, 1000), "1")
            self.assertEqual(self.health(d, 1, 1000), "1")  # wake grace
            self.assertEqual(self.health(d, 1, 1031), "0")  # stuck worker
            (path / "heartbeat").write_text("1031000")
            self.assertEqual(self.health(d, 1, 1032), "1")
            self.assertFalse((path / "wake-grace").exists())
    def test_no_device_in_build_or_tests(self):
        self.assertNotIn("adb ", (ROOT / "build.sh").read_text())
        self.assertNotIn("device.py", (ROOT / "tools/test.sh").read_text())

if __name__ == "__main__":
    unittest.main()
