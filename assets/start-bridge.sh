set -eu
umask 077
store=/data/local/tmp/cec-standby
mkdir -p "$store"
chmod 700 "$store"
printf '%s' "$3" | base64 -d > "$store/profile.json.tmp"
changed=1
if cmp -s "$store/profile.json" "$store/profile.json.tmp"; then changed=0; fi
mv "$store/profile.json.tmp" "$store/profile.json"
bridge_health "$store" "$4" "$(cut -d . -f1 /proc/uptime)"
pid=$(cat "$store/pid" 2>/dev/null || true)
case "$pid" in
    ''|*[!0-9]*) ;;
    *)
        if kill -0 "$pid" 2>/dev/null && [ "$(tr '\000' '\n' < "/proc/$pid/cmdline" | head -n 1)" = cec-standby-bridge ]; then
            if [ "$(cat "$store/version" 2>/dev/null)" = "$2" ] && [ "$changed" = 0 ] && [ "$healthy" = 1 ]; then
                cat "$store/status.txt" 2>/dev/null || echo 'Bridge starting'
                exit 0
            fi
            kill "$pid"
            for round in 1 2 3 4 5 6 7 8 9 10; do
                kill -0 "$pid" 2>/dev/null || break
                sleep .1
            done
            if kill -0 "$pid" 2>/dev/null; then echo 'Previous bridge has not stopped'; exit 1; fi
        fi
        ;;
esac
cp "$1" "$store/bridge.apk.tmp"
chmod 600 "$store/bridge.apk.tmp"
mv "$store/bridge.apk.tmp" "$store/bridge.apk"
rm -f "$store/stop" "$store/status.txt" "$store/heartbeat"
CLASSPATH="$store/bridge.apk" nohup setsid app_process /system/bin --nice-name=cec-standby-bridge io.github.stevexyh.cecstandby.ShellBridge < /dev/null > "$store/runtime.log" 2>&1 &
for round in 1 2 3 4 5 6 7 8 9 10; do
    if [ "$(cat "$store/version" 2>/dev/null)" = "$2" ] && [ -s "$store/status.txt" ]; then
        cat "$store/status.txt"
        exit 0
    fi
    sleep .2
done
echo 'Bridge launch pending; inspect runtime.log'
