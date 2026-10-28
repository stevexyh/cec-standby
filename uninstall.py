#!/usr/bin/env python3
"""Remove only this helper and its bindings; preserve every other TV service."""
import argparse
import shlex
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("--serial", required=True)
parser.add_argument("--server-port", default="5037")
args = parser.parse_args()
adb = ["adb", "-P", args.server_port, "-s", args.serial]
pkg = "io.github.stevexyh.cecstandby"


def shell(*command, check=True):
    return subprocess.run(adb + ["shell", shlex.join(command)], check=check, text=True, capture_output=True, timeout=30).stdout.strip()


# Prevent either binding from restoring the helper while removing it.
shell("pm", "revoke", pkg, "android.permission.WRITE_SECURE_SETTINGS", check=False)
shell("cmd", "notification", "disallow_listener", pkg + "/" + pkg + ".Revival", check=False)
current = shell("settings", "get", "secure", "enabled_accessibility_services")
services = [item for item in current.split(":") if item not in ("", "null", pkg + "/" + pkg + ".CecService", pkg + "/.CecService")]
if services:
    shell("settings", "put", "secure", "enabled_accessibility_services", ":".join(services))
else:
    shell("settings", "delete", "secure", "enabled_accessibility_services")
# UID 2000 outlives the application. Stop its own validated process and remove only
# the files this tool creates; never kill unrelated shell processes or ADB sessions.
shell("sh", "-c", r'''
store=/data/local/tmp/cec-standby
if [ -d "$store" ]; then
    touch "$store/stop"
    pid="$(cat "$store/pid" 2>/dev/null)"
    case "$pid" in
        ''|*[!0-9]*) ;;
        *)
            if [ "$(tr '\000' '\n' < "/proc/$pid/cmdline" | head -n 1)" = cec-standby-bridge ] 2>/dev/null; then
                for round in 1 2 3 4 5 6; do
                    kill -0 "$pid" 2>/dev/null || break
                    sleep 1
                done
                if kill -0 "$pid" 2>/dev/null && [ "$(tr '\000' '\n' < "/proc/$pid/cmdline" | head -n 1)" = cec-standby-bridge ]; then kill "$pid"; fi
            fi
            ;;
    esac
    for name in pid pid.tmp version version.tmp status.txt status.txt.tmp resume-hdmi resume-hdmi.tmp stop bridge.lock bridge.apk bridge.apk.tmp runtime.log events.log events.previous profile.json profile.json.tmp heartbeat heartbeat.tmp wake-grace; do
        rm -f "$store/$name"
    done
    # ART creates this cache only inside our newly created bridge directory.
    if [ -d "$store/oat" ]; then rm -r "$store/oat"; fi
    rmdir "$store" 2>/dev/null || true
fi
''')
subprocess.run(adb + ["uninstall", pkg], check=True, timeout=30)
