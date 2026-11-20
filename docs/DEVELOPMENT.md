# Development

## Local-only commands

Requires JDK 17, Python 3.9+, Android SDK platform 36 and build-tools 36.0.0. Export JAVA_HOME and put its bin on PATH; ANDROID_HOME overrides the SDK path.

```sh
./build.sh
./tools/test.sh
python3 tools/device.py --help
python3 tools/check-secrets.py
```

Only device.py with an explicit action/serial, uninstall.py, restore.py and manually authorized device commands can contact a TV. The test runner uses synthetic fixtures, pure Java policies, mocked device calls and a temporary-directory invocation of the actual shell health function. No emulator or network is required.

The build cleans class/dex intermediates and signs a non-debuggable APK. First build creates `.keys/development.jks`; preserve it for development upgrades. For stable production signing set CEC_KEYSTORE, CEC_KEY_ALIAS, CEC_STORE_PASS and CEC_KEY_PASS outside Git. Tools consume passwords through environment references, not command-line values. Use a trusted APK signed with a stable key; no official binary release/signing identity is established by this source push.

## Profile schema v1 and v2

All keys of the selected schema are required; unknown keys are rejected. Whole profiles are replaced atomically. Host-side validation catches mistakes before mutation; Android validates independently at its permission boundary. Config must remain under 16 KiB. Keep host/runtime validation parity tests when changing the format.

| Fields | Meaning and limits |
| --- | --- |
| schemaVersion | Integer 1 (original Xiaomi behavior) or 2 |
| id | Lowercase alphanumeric/hyphen identifier, at most 64 characters |
| manufacturer, model, firmware | Exact Build manufacturer (case-insensitive), model and incremental build; no wildcards |
| sdk | Exactly 28; other API adapters are not implemented |
| vendorId, port | CEC vendor 0..16777214 and port 1..15 |
| inputId | Exact current-input id, read as selected by inputSource (v1: Settings.System tv_input_id) |
| player | Native activity component, package/class, short or full class form |
| action, inputExtra, inputValue | Wake handoff only: intent action, integer-extra name and value 0..65535 |
| launchers | Wake handoff only: 1..16 exact allowed launcher activity components |
| keepAdb | Boolean, explicit owner opt-in; sample default false |
| enabled | Boolean; false disables this profile's power and keep-ADB actions |

Schema 2 adds four required keys. Schema 1 profiles keep working and mean `xiaomi`, `setting`, `POWER`, `true`.

| Fields | Meaning and limits |
| --- | --- |
| events | `xiaomi`: AOSP-style `HDMI` log and dumpsys `<Standby 36>`. `tcl`: TCL/Realtek HAL `sita.proxy` 0x36 receive log, or `<Inactive Source>` followed within 3 s by the same source's broadcast standby `<Report Power Status>` (live system-UID log and dumpsys history) |
| inputSource | `setting`: Settings.System tv_input_id. `tv-input-session`: input of the main session in `dumpsys tv_input`, for firmware that leaves the setting unset |
| standbyKey | `POWER` or `SLEEP`; SLEEP never toggles a TV that is already asleep |
| wakeHandoff | Boolean. When false, action/inputExtra/inputValue/launchers must be omitted and the bridge never opens an input after wake |

No arbitrary shell command fields are supported. Use `profiles/xiaomi-astp0.json` (schema 1) or `profiles/tcl-beyondtv-ap03.json` (schema 2) as a structural template; verify every device-specific value before enabling another profile. Host tools may target another authorized adb server port; the TV-local runtime requires 5555.

## Commits and publication

0.1.0/code 1 is the sanitized renamed baseline from the old 0.6.3 prototype. 0.2.0/code 2 adds profiles and independent recovery. 0.3.0/code 3 adds schema 2 and the TCL/Realtek adapters. There are no binary releases. Keep namespace/version literals consistent and signing identity stable.

Review staged content and run the index scanner before push. It detects common secret/private-path patterns, excluded artifacts and binary files; it is not a proof that every possible secret is absent. Manually inspect the complete tracked file set. Raw diagnostics/backups and keys stay ignored. Preserve upstream MIT attribution. Use GitHub noreply commit identity to avoid publishing a personal address.
