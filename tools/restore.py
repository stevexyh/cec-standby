#!/usr/bin/env python3
"""Restore the original ADB toggle AFTER uninstalling this helper. May disconnect ADB."""
import argparse
import json
from pathlib import Path
import sys
from device import Device, PKG


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--server-port", type=int, default=5037)
    parser.add_argument("--backup", type=Path, required=True)
    parser.add_argument("--allow-adb-disconnect", action="store_true", required=True)
    args = parser.parse_args()
    backup = json.loads(args.backup.read_text())
    if backup["serial"] != args.serial:
        raise ValueError("Backup serial does not match")
    device = Device(args.serial, args.server_port)
    if device.inspect() != backup["device"]:
        raise ValueError("Backup device/firmware does not match")
    if "package:" + PKG in device.shell("pm", "list", "packages", PKG).splitlines():
        raise ValueError("Uninstall helper first to prevent keep-ADB restoration")
    original = backup["originals"]["adb_enabled"]
    if original not in ("0", "1", "null"):
        raise ValueError("Invalid original setting")
    print("Restoring only original ADB toggle; this may close the connection", flush=True)
    if original == "null":
        device.shell("settings", "delete", "global", "adb_enabled")
    else:
        device.shell("settings", "put", "global", "adb_enabled", original)

if __name__ == "__main__":
    main()
