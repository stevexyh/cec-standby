# Xiaomi ASTP0 compatibility evidence

Original prototype was tested on MiTV-ASTP0, Android 9/API 28, firmware 22.3.11.890. Public hardware compatibility information only; no household addresses, serials, raw logs or credentials belong here.

Native CEC standby handling did not sleep this firmware. Accessibility screen lock broke subsequent CEC wake; native short POWER policy preserved wake. Native STR launches stock home and kills third-party apps. Hotplug polling can temporarily evict a known Apple CEC device; bounded identity caching handles that race.

HDMI1 was represented by HW5 and native player input 23. This mapping is not universal. Renamed/refactored packages are untested on physical hardware; current evidence applies only to the old prototype.
