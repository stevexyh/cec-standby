set -eu
umask 077
store=/data/local/tmp/cec-standby
mkdir -p "$store"
chmod 700 "$store"
pid="$(cat "$store/pid" 2>/dev/null || true)"
case "$pid" in
    ''|*[!0-9]*) pid= ;;
    *)
        if kill -0 "$pid" 2>/dev/null && grep -q '^cec-standby-bridge' "/proc/$pid/cmdline"; then
            if [ "$(cat "$store/version" 2>/dev/null)" = "$2" ]; then
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
chmod 644 "$store/bridge.apk.tmp"
mv "$store/bridge.apk.tmp" "$store/bridge.apk"
rm -f "$store/stop"
CLASSPATH="$store/bridge.apk" nohup setsid app_process /system/bin --nice-name=cec-standby-bridge io.github.stevexyh.cecstandby.ShellBridge < /dev/null > "$store/runtime.log" 2>&1 &
for round in 1 2 3 4 5 6 7 8 9 10; do
    if [ "$(cat "$store/version" 2>/dev/null)" = "$2" ] && [ -s "$store/status.txt" ]; then
        cat "$store/status.txt"
        exit 0
    fi
    sleep .2
done
echo 'Bridge launch pending; inspect runtime.log'
