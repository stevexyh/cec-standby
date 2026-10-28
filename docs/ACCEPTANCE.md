# Acceptance

## Offline (default)

Run build.sh, tools/test.sh, APK badging/signature inspection, index whitespace check and indexed secret scan. Verify package/version, non-debuggable state and protected receiver permissions. Confirm build/tests do not run ADB, device discovery, emulator startup or network operations.

Tests cover CEC UID/vendor/freshness/cache/destination handling; exact profile matching and invalid values; disabled/mismatched configuration; keep-ADB opt-out/rate limit; actual shell awake heartbeat, suspended heartbeat, wake grace and stalled progress; invalid/mismatched profiles causing no device mutation. Device orchestration tests use mocked responses only.

## Exact-build physical (separate opt-in)

- Save originals/rollback artifacts and verify no other power helper is active. Confirm input/player/focus and helper's profile active state. Verify notification/accessibility bindings retain existing services.
- With explicit timing authorization, test real remote off -> ten seconds -> on three times. Stop at first failure; preserve scene. Record physical power result and transition durations, then correlate with the journal.
- With another app foreground, an external source's standby must not switch off the TV.
- Test keepAdb opt-out, opted-in recovery after firmware disables ADB, process-loss recovery and bound restart after a stalled awake worker. These disruptions require authorization; beware losing the controlling connection.
- Test a separately authorized reboot, retained app-local authorization, renewed binding/bridge readiness and physical remote cycles after reboot.
- Test uninstall preserving unrelated settings/services/media, and only then optionally restore original ADB with explicit disconnect authorization.

## Reporting

Use statuses: locally passed, physically passed on exact build/device, inherited prototype evidence, failed, not run. Report soak duration separately. New package 0.2.0 has no physical acceptance and no established long-term reliability. Original prototype evidence does not transfer across namespace/runtime changes.
