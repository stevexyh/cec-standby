# Architecture

## Components and flow

1. `SetupReceiver` handles protected CONFIG/SETUP, boot and package replacement. CONFIG validates the full JSON then uses AtomicFile to persist it in app-private storage. A configuration error leaves the previous file intact.
2. `CecService` owns an accessibility-bound background executor. It checks opt-in ADB recovery, sends the validated profile over loopback ADB, and bootstraps the shell bridge. It has no UI/key/accessibility-content processing.
3. `AdbKeeper` watches ADB_ENABLED and periodically rechecks. It only restores a disabled setting when keepAdb/enabled are true and the exact device matches, at most once per minute. It never repeatedly toggles ADB because a TCP connection failed. WRITE_SECURE_SETTINGS allows recovery even when shell access is down.
4. `LocalAdb` connects only to 127.0.0.1:5555 using its own app-private RSA key. It never imports a computer key; backup is disabled. The TV must separately authorize it.
5. A detached UID 2000 `ShellBridge` consumes system-UID CEC logs with snapshot fallback and a bounded identity cache. Before native POWER it verifies configured vendor/port, exact selected input and actual player activity. Other apps are left alone. Wake handoff only acts during an armed wake window on configured launcher components.
6. The bootstrap compares config/version/process identity and checks worker progress. A suspended worker may have no heartbeat; after waking it has a 30-second grace period. A stalled awake worker is restarted. There is no wake lock. Fallback snapshots can keep control working while logcat reconnects.

## Ownership and boundaries

The package owns only its private data and `/data/local/tmp/cec-standby`. Its process name is `cec-standby-bridge`. It is not an init service: detachment survives app force-stop but remains in adbd's cgroup. An adbd restart may kill it; bootstrap recovery depends on restored app bindings and available ADB.

No Aerial code/resources are linked. Recognizing Aerial as an optional launcher does not require it to be installed. No system package, launcher, ad preference or user media is changed. Installer refuses coexistence with the old helper to avoid double POWER injections.

## Protected interfaces

All exported setup/config receivers require WRITE_SECURE_SETTINGS. CONFIG action is `io.github.stevexyh.cecstandby.CONFIG`, receiver `.SetupReceiver`, string extra `profile` containing the whole JSON. Result code 0 and `Configuration saved` confirm persistence only, not worker readiness or physical acceptance. SETUP action enables only this helper's accessibility entry while retaining other entries. A protected test_sleep extra requests real standby; never use it during offline validation.

Config is applied on the next bootstrap check (normally at most 15 seconds awake plus connection timeouts). The worker holds an immutable profile and restarts on profile changes. The service may be stopped by firmware; no exact restoration-time or long-term guarantee is claimed. Hidden APIs/log formatting are currently Android 9/Xiaomi-specific.
