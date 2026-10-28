# Handoff

## Status
0.1.0 namespace-migration baseline derived from Aerial's separate CEC standby prototype 0.6.3. No Aerial build dependency. Original device installation is untouched. Independent ADB recovery and profile tooling are planned for 0.2.0.

## Current validation boundary
Local build/tests only. No TV connections, discovery, install, sleep/wake or reboot are authorized for this migration session. Exact new package has not been physically tested. Do not claim long-term reliability.

## Known constraints
Android 9 Xiaomi log formats/native input routing. ADB and hidden framework APIs are prerequisites. Firmware can force-stop third-party apps and restart adbd. Wake may expose launcher/no-signal surfaces.
