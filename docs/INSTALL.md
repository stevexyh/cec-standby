# AI installation runbook

## Authorization and preflight

Read AGENTS, HANDOFF, the selected device note and ACCEPTANCE. Honor the user's current session limits. Build/test permission does not authorize TV connections. When installing, execute commands personally and show meaningful raw output; never imply success from a plan or an ADB disconnect.

Require an explicit ADB serial and owner-authorized connection. If not already connected, use a bounded adb connect (or the owner's existing ADB setup); the tools never discover household devices. ADB must show the selected target as device, not unauthorized/offline. For an authorization prompt ask the owner to choose Allow and Always allow. Do not install PC packages or change PC network/startup settings without authorization.

Device prerequisites: API 28, TCP ADB on 5555, compatible system-UID HDMI log/snapshot format, and a verified native power/player policy. An unknown model/firmware is diagnosis-only until an adapter/profile is reviewed. Supplying arbitrary guessed settings is not an adaptation procedure.

## Diagnosis (read-only, still device access)

```sh
mkdir -p local
python3 tools/device.py diagnose --serial '<ADB-SERIAL>' > local/diagnostic.json
```

Optional `--server-port <PORT>` selects the host adb server. Diagnostics contain device/window details; never publish raw output. Compare manufacturer/model/API/incremental firmware, actual selected input, CEC identity and player component to the profile. Package presence alone is insufficient for claiming support.

## Installation and configuration

```sh
./build.sh
python3 tools/device.py install --serial '<ADB-SERIAL>' --profile profiles/xiaomi-astp0.json --keep-adb
```

The build is local-only. Device action is explicit. Omit --keep-adb and leave the JSON field false to opt out. Installation validates the profile/device and checks the native player. It refuses an installed old helper rather than enabling two power controllers.

Before modifications, the installer saves original service lists and ADB setting in a private ignored local/install-*.json file. It installs the APK, grants WRITE_SECURE_SETTINGS, sends protected CONFIG, allows only its own notification listener, enables only its own accessibility service, and speed-compiles only this package. Partial failures leave the backup for diagnosis/rollback; do not blindly repeat or overwrite other bindings.

The helper's own RSA key requires a new on-TV Always allow authorization. Never copy another app/computer's private key. This APK has no launcher UI and is not a default desktop. Follow ACCEPTANCE after confirming the worker's active profile, current focus and input.

To update only configuration:

```sh
python3 tools/device.py configure --serial '<ADB-SERIAL>' --profile '<PROFILE.json>'
```

Configuration applies at the next bootstrap check; `Configuration saved` means persistence, not physical success. It is not an emergency immediate-stop mechanism.

## Read-only worker inspection (after authorized install)

```sh
adb -s '<ADB-SERIAL>' shell dumpsys activity service io.github.stevexyh.cecstandby/.CecService
adb -s '<ADB-SERIAL>' shell cat /data/local/tmp/cec-standby/status.txt
adb -s '<ADB-SERIAL>' shell cat /data/local/tmp/cec-standby/events.log
```

All ADB commands must include the selected serial and chosen server port when nondefault. No power keys are sent by installation or diagnostics. Do not run physical tests until the owner is ready.

## Old-helper migration

The old `app.aerial.cecstandby` is a different package. Plan and authorize a separate cutover: retain its APK/rollback instructions locally, stop/remove it using its own original uninstaller, then install the new helper. Do not assume inherited permissions or local ADB identity. This source migration session does not perform that cutover.

## Removal and original ADB restoration

```sh
python3 uninstall.py --serial '<ADB-SERIAL>' --server-port 5037
```

Revokes only this helper's permission/listener/accessibility entry, stops its validated process and removes only its listed work files/cache and package. Other services/media and system apps remain. App-private key is destroyed; its inert public authorization is not removed by rewriting the shared ADB authorization file.

Keep-ADB does not restore the pre-install toggle during removal: that could disconnect the tool unexpectedly. After uninstall, the owner can explicitly restore the original setting using the earliest applicable backup:

```sh
python3 tools/restore.py --serial '<ADB-SERIAL>' --backup 'local/<INSTALL-BACKUP>.json' --allow-adb-disconnect
```

The restore tool checks backup/device identity and helper absence, then restores only original adb_enabled. If it closes ADB before returning, mark the result unconfirmed until owner feedback. Never bulk-restore old service lists over services installed subsequently. No advertisement setting is modified by this repository.
