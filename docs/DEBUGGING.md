# Debugging

Start with offline tests and source inspection. For authorized device work, gather package version, firmware/API, settings originals, accessibility/notification bindings, worker PID/status, bounded event journal and recent system-UID CEC messages. Keep raw evidence in ignored local/; sanitize before sharing.

A process is not proof of working control. ADB closing is not proof of screen-off. Check received CEC, guarded action, actual power transition and physical wake separately. Do not replay old CEC history into real power actions. Do not repeatedly inject POWER to recover a failed test.

For unknown firmware, collect diagnostics and prepare a profile before enabling actions. On failure preserve the scene; do not disable system launchers, change factory settings or erase user data. Follow ACCEPTANCE and report the exact unverified boundary.
