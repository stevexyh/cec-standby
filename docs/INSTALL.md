# AI installation runbook

Device work requires authorization; this repository's build and default tests are offline. Ask for a device target only when installation is requested. Never infer a household address from examples. Require a selected serial for every ADB command; bound network connection timeouts.

Check API, model, firmware, CEC settings and player availability read-only. Stop if unsupported. Record original settings and service lists in ignored local records before modifying anything. Preserve all existing service entries.

Baseline activation: install APK, grant WRITE_SECURE_SETTINGS to `io.github.stevexyh.cecstandby`, authorize its notification listener and send its protected SETUP broadcast. Accept the helper's own on-TV ADB prompt with Always allow. Speed-compile its package. This is a service-only APK, not a TV launcher.

The 0.1 baseline inherits hardcoded Xiaomi routing and lacks independent keep-ADB behavior. Do not present it as portable or independently accepted. Configuration tooling follows in 0.2.

Uninstall with `python3 uninstall.py --serial <SERIAL> --server-port <PORT>`. Only this package and its recorded worker files are removed. An old Aerial helper is a different package: never run both controllers concurrently or remove the old one without authorization. A new package requires a new local ADB authorization; do not copy its private key.
