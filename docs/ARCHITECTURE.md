# Architecture

A service-only Android APK bootstraps a detached UID 2000 Java worker over its own loopback ADB connection. The worker listens to system-UID CEC logs and uses HDMI snapshots as fallback. It checks selected input and player focus before native power-key standby; wake routing is limited to a previously armed wake window.

The accessibility service bootstraps and monitors; notification binding and boot receiver restore it. The independent app-private RSA key never leaves app storage. No Aerial code or resources are linked. Optional Aerial package recognition only handles launcher transitions.

The worker is not an init service: it survives app force-stop but remains in adbd's cgroup. ADB restart may kill it. Snapshot and log formats, hidden APIs and native player routes are firmware-specific.
