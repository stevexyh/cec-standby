# Acceptance

## Offline
Clean isolated build, non-debuggable manifest, expected package/version, signature verification, parser regressions, shell/Python syntax and secret scan. Tests must be usable without ADB or any network.

## Physical (separate opt-in)
Only with authorization: validate selected input/player and keep-ADB opt-in, test real remote off -> wait ten seconds -> on three times, stop on first failure. Record screen transition durations and physical result; then inspect own journal. Test standby while another app is foreground (must not sleep TV), service revival, ADB restart recovery and separately authorized reboot. Report exact APK acceptance and soak duration independently.

New namespace and refactored builds have no physical acceptance. The original prototype passed three cycles on one firmware; that evidence is not transferable automatically.
