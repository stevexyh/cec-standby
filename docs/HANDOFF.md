# Handoff

## Delivered source

- 0.1.0/code 1: sanitized extraction from the old in-tree helper 0.6.3, independent namespace io.github.stevexyh.cecstandby, build/signing and AI docs.
- 0.2.0/code 2: profile-based input/vendor/player/launcher behavior, strict device/API/firmware guard, protected atomic configuration, independent opt-in ADB restoration, heartbeat/wake-grace process recovery, explicit device tooling and offline tests.
- GitHub repository is public; no APK binary release or production signing identity has been established. Original Aerial repository/staged files and original TV installation were not changed.

## Session restrictions and validation

This migration session is local-only: no TV connections (including read-only discovery), install, sleep/wake or reboot. These restrictions remain until the user explicitly authorizes a later device session. Offline build and test results are recorded in commit/review output. Exact new package has not been physically tested; do not claim completion of device installation or long-term reliability.

## Known limits / next device session

Only API 28 hidden-API and Xiaomi-style CEC log/snapshot adapters exist. Built-in ASTP0 profile requires exact model/incremental firmware; inspect actual manufacturer before use. Do not relax guards blindly. Runtime loopback ADB requires 5555. Keeper can restore a disabled ADB setting but does not guarantee TCP-mode recovery when the toggle already reads 1. Notification revival/adbd cgroup behavior is firmware-dependent. Health checks do not run while the CPU is suspended.

No Aerial runtime dependency remains in intended control flow, but this assertion has only local/source validation. First device task is an explicitly authorized old-helper cutover and exact-build acceptance, retaining rollback. The original helper's three-cycle result and launcher flashes are inherited evidence only.
