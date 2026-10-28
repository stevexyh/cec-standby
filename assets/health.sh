# Shared by bootstrap and offline tests. Sets healthy; never sends device commands.
bridge_health() {
store=$1
interactive=$2
now=$3
healthy=0
if [ -f "$store/heartbeat" ]; then
    heartbeat=$(cat "$store/heartbeat")
    case "$heartbeat" in ''|*[!0-9]*) ;; *)
        age=$((now - heartbeat / 1000))
        if [ "$age" -ge 0 ] && [ "$age" -lt 30 ]; then healthy=1; fi
    esac
fi
# A suspended worker has no advancing heartbeat. Allow one wake grace interval.
if [ "$interactive" = 0 ]; then
    healthy=1
    rm -f "$store/wake-grace"
elif [ "$healthy" = 0 ]; then
    grace=$(cat "$store/wake-grace" 2>/dev/null || true)
    case "$grace" in ''|*[!0-9]*) grace=$now; printf '%s' "$grace" > "$store/wake-grace" ;; esac
    if [ $((now - grace)) -lt 30 ]; then healthy=1; fi
else
    rm -f "$store/wake-grace"
fi
}
