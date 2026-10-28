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

## Profile schema v1

All keys are required; unknown keys are rejected. Whole profiles are replaced atomically. Host-side validation catches mistakes before mutation; Android validates independently at its permission boundary. Config must remain under 16 KiB. Keep host/runtime validation parity tests when changing the format.

| Fields | Meaning and limits |
| --- | --- |
| schemaVersion | Integer 1 |
| id | Lowercase alphanumeric/hyphen identifier, at most 64 characters |
| manufacturer, model, firmware | Exact Build manufacturer (case-insensitive), model and incremental build; no wildcards |
| sdk | Exactly 28; other API adapters are not implemented |
| vendorId, port | CEC vendor 0..16777214 and port 1..15 |
| inputId | Exact Settings.System tv_input_id value |
| player | Native activity component, package/class, short or full class form |
| action, inputExtra, inputValue | Intent action, integer-extra name and value 0..65535 |
| launchers | 1..16 exact allowed launcher activity components |
| keepAdb | Boolean, explicit owner opt-in; sample default false |
| enabled | Boolean; false disables this profile's power and keep-ADB actions |

No arbitrary shell command fields are supported. Use `profiles/xiaomi-astp0.json` as a structural template; verify every device-specific value before enabling another profile. Host tools may target another authorized adb server port; the TV-local runtime requires 5555.

## Commits and publication

0.1.0/code 1 is the sanitized renamed baseline from the old 0.6.3 prototype. 0.2.0/code 2 adds profiles and independent recovery. There are no binary releases. Keep namespace/version literals consistent and signing identity stable.

Review staged content and run the index scanner before push. It detects common secret/private-path patterns, excluded artifacts and binary files; it is not a proof that every possible secret is absent. Manually inspect the complete tracked file set. Raw diagnostics/backups and keys stay ignored. Preserve upstream MIT attribution. Use GitHub noreply commit identity to avoid publishing a personal address.
