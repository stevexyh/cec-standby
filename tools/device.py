#!/usr/bin/env python3
"""Explicit device operations only. Build/tests never import or execute this entrypoint."""
import argparse
import json
import shlex
import subprocess
import sys
from pathlib import Path
from datetime import datetime, timezone
from profile import load

ROOT = Path(__file__).resolve().parents[1]
PKG = "io.github.stevexyh.cecstandby"
OLD = "app.aerial.cecstandby"

class Device:
    def __init__(self, serial, port, adb="adb"):
        self.command = [adb, "-P", str(port), "-s", serial]
        self.serial = serial
    def run(self, *args):
        result = subprocess.run(self.command + list(args), capture_output=True, text=True, timeout=30)
        if result.returncode:
            raise RuntimeError(result.stderr.strip() or result.stdout.strip() or "ADB failed")
        return result.stdout.strip()
    def shell(self, *args):
        return self.run("shell", shlex.join(args))
    def inspect(self):
        return {key: self.shell("getprop", prop) for key, prop in {
            "manufacturer": "ro.product.manufacturer", "model": "ro.product.model", "sdk": "ro.build.version.sdk", "firmware": "ro.build.version.incremental"}.items()}
    def originals(self):
        return {"adb_enabled": self.shell("settings", "get", "global", "adb_enabled"),
                "accessibility_enabled": self.shell("settings", "get", "secure", "accessibility_enabled"),
                "enabled_accessibility_services": self.shell("settings", "get", "secure", "enabled_accessibility_services"),
                "enabled_notification_listeners": self.shell("settings", "get", "secure", "enabled_notification_listeners")}

def matching(profile, facts):
    return (profile["manufacturer"].lower() == facts["manufacturer"].lower() and
            all(str(profile[k]) == facts[k] for k in ("model", "sdk", "firmware")))

def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["install", "configure", "diagnose"])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--server-port", type=int, default=5037)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--profile", type=Path)
    parser.add_argument("--apk", type=Path, default=ROOT / "build/cec-standby.apk")
    parser.add_argument("--keep-adb", action="store_true", default=None)
    args = parser.parse_args(argv)
    if not 1 <= args.server_port <= 65535:
        parser.error("Invalid server port")
    profile = load(args.profile) if args.profile else None
    if args.action != "diagnose" and profile is None:
        parser.error("--profile is required; unknown devices are diagnosis-only")
    if profile and args.keep_adb is not None:
        profile["keepAdb"] = args.keep_adb
    if args.action == "install" and not args.apk.is_file():
        parser.error("Build an APK first")
    device = Device(args.serial, args.server_port, args.adb)
    if device.run("get-state") != "device":
        raise RuntimeError("Target must already be connected and authorized")
    facts = device.inspect()
    if args.action == "diagnose":
        report = {"device": facts, "profileMatches": matching(profile, facts) if profile else None,
                  "settings": device.originals(), "focus": device.shell("dumpsys", "window", "windows"),
                  "hdmi": device.shell("dumpsys", "hdmi_control"),
                  "helper": device.shell("dumpsys", "activity", "service", PKG + "/.CecService")}
        # Output can include device-specific details; keep it in ignored local/.
        print(json.dumps(report, indent=2))
        return
    if not matching(profile, facts):
        raise RuntimeError("Profile does not exactly match this device; use diagnose")
    if not device.shell("pm", "path", profile["player"].split("/")[0]).startswith("package:"):
        raise RuntimeError("Configured native player is missing")
    # Refuse to enable a second power controller, including during its temporary revival gap.
    if device.shell("pm", "list", "packages", OLD).splitlines().count("package:" + OLD):
        raise RuntimeError("Old helper is installed; stop and plan an explicit migration first")
    originals = device.originals()
    directory = ROOT / "local"
    directory.mkdir(mode=0o700, exist_ok=True)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    backup = directory / ("install-" + stamp + ".json")
    import os
    with os.fdopen(os.open(backup, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w") as file:
        json.dump({"serial": args.serial, "device": facts, "originals": originals,
                   "ownedDirectory": "/data/local/tmp/cec-standby", "profile": profile}, file, indent=2)
    print("Originals saved to ignored local/ before modification")
    if args.action == "install":
        output = device.run("install", "-r", str(args.apk.resolve()))
        if "Success" not in output:
            raise RuntimeError("APK installation was not confirmed: " + output)
    device.shell("pm", "grant", PKG, "android.permission.WRITE_SECURE_SETTINGS")
    payload = json.dumps(profile, separators=(",", ":"))
    output = device.shell("am", "broadcast", "-a", PKG + ".CONFIG", "-n", PKG + "/.SetupReceiver", "--es", "profile", payload)
    if "result=0" not in output or "Configuration saved" not in output:
        raise RuntimeError("Configuration was not confirmed: " + output)
    device.shell("cmd", "notification", "allow_listener", PKG + "/" + PKG + ".Revival")
    device.shell("am", "broadcast", "-a", PKG + ".SETUP", "-n", PKG + "/.SetupReceiver")
    device.shell("cmd", "package", "compile", "-m", "speed", "-f", PKG)
    print("Setup submitted. Accept the helper's own TV ADB prompt with Always allow if shown.")
    print("No power keys were injected. Physical acceptance is a separate authorized step.")

if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError, subprocess.TimeoutExpired) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
