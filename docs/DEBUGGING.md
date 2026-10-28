# Debugging

## Decision tree

1. Offline build/validation failure: fix locally; no TV command is justified by it.
2. Device offline/unauthorized: preserve the last scene, ask for power/network or authorization only when the user permits device work. ADB loss can be normal during native standby.
3. No active profile: compare exact manufacturer/model/API/firmware and player availability; do not weaken guards to force an unknown TV to work.
4. Bootstrap down: inspect service status (including ADB recovery reason), local authorization and notification/accessibility bindings. ADB_ENABLED=1 does not guarantee a listening TCP port; the keeper restores a disabled toggle but intentionally does not repeatedly toggle an enabled setting on a closed port.
5. Worker stalled: inspect PID/version/status heartbeat and bounded journal. Healthy asleep state can legitimately have old timestamps; wake gets a 30-second grace interval. Persistent lack of progress while awake should cause controlled restart.
6. Received CEC but no standby: compare vendor/port, selected input and exact activity. Another foreground app must block power actions. Do not replay old logs or issue repeated POWER as a workaround.
7. Wake flashes: compare armed wake and activity transitions. Firmware launcher surfaces/HDMI acquisition may be outside the worker's control. Never disable the stock launcher to hide them.

## Evidence and recovery

Use the explicit diagnose tool, worker status/events.log/events.previous and bounded system logs only under current authorization. Keep raw material and settings originals under ignored local/. Redact serials, addresses, tokens and unrelated content before sharing.

A running process, fresh snapshot count, ADB disconnect or successful configuration broadcast is not physical acceptance. Document actual received events, action results and owner's observed screen state independently. No multi-day guarantee follows from three cycles.

Rollback with the package-specific uninstaller; restore only intentionally changed settings through the selected backup. Preserve user data and other service bindings. Do not factory-reset, modify system apps, dump keys or change PC network settings as a troubleshooting shortcut.
