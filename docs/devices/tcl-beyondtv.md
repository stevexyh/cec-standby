# TCL BeyondTV compatibility evidence

TCL Android TV on Realtek rtd285o, Android 9/API 28. Public hardware compatibility information only; no household addresses, serials, raw logs or credentials belong here.

## Firmware behavior

Apple TV (CEC logical address 4, vendor 4346) sends `<Standby>` (0x36) twice on sleep, broadcast and directed, followed by `<Inactive Source>` and a broadcast `<Report Power Status>` = standby. The TCL CEC HAL receives `<Standby>` but never forwards it to HdmiControlService, so the TV shows "No signal" until the firmware's own no-signal timer. `<Inactive Source>` and the power report do reach HdmiControlService. Settings.System `tv_input_id` is unset; the current input is the main session in `dumpsys tv_input`.

| Build | ro.build.version.incremental | Signals in system-UID logcat |
| --- | --- | --- |
| BeyondTV, `.../AP03` | 23 | `sita.proxy` HAL receive line with `un8Msg=0x36`, and the `HdmiCecController` pair |
| BeyondTV2, `.../AP04` | AP04 | `HdmiCecController` pair only; the HAL no longer logs raw messages |

The `tcl` events adapter accepts either signal; the bridge's 4-second standby debounce collapses them. `KEYCODE_SLEEP` is used instead of POWER; CEC wake from Apple TV keeps working afterwards and the native player (`com.tcl.tv/.TVActivity`) stays on the HDMI input, so no wake handoff is configured.

HDMI1 is `com.tcl.tvpassthrough/.TvPassThroughService/HW1413744128` on both builds. This mapping is not universal.

## Acceptance (0.3.0)

Common to both builds: installed with `tools/device.py install`, the helper's own ADB key accepted on the TV, bridge active with the exact profile; `--check` resolves the configured HDMI1 input; CEC wake from Apple TV returns to the HDMI player and the bridge process survives standby; after `adb reboot` the helper restarts the bridge without a computer (about 40 s after boot) and Apple TV sleep again puts the TV to standby immediately (owner-observed; the post-reboot journal entry was not read back).

| Profile | Signal that fired | CEC message to TV asleep |
| --- | --- | --- |
| `tcl-beyondtv-ap03` | live HAL `<Standby>` (`sita.proxy`, 0x36) | about 0.4 s |
| `tcl-beyondtv2-ap04` | live `<Inactive Source>` + broadcast standby report | about 0.5 s |

Not yet run: the dumpsys-history fallback on a device, the `<Inactive Source>` pair as the firing signal on AP03, power-cut recovery, multi-day soak.
