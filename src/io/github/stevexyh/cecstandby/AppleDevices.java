package io.github.stevexyh.cecstandby;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Retain a verified physical binding across Xiaomi's transient CEC hotplug eviction. */
final class AppleDevices {
    private static final long RETAIN_MS = 30_000;
    private static final Pattern DEVICE = Pattern.compile("logical_address: 0x([0-9a-fA-F]+) device_type: 4 vendor_id: ([0-9]+).*physical_address: 0x([0-9a-fA-F]+) port_id: ([1-9][0-9]*)");
    private static final class Device {
        final int physical, port;
        long seen;
        Device(int physical, int port, long seen) { this.physical = physical; this.port = port; this.seen = seen; }
    }
    private final Map<Integer, Device> devices = new HashMap<>();
    private boolean enabled;

    synchronized void observeSnapshot(String dump, long now) {
        enabled = dump.contains("mHdmiControlEnabled: true");
        if (!enabled) { devices.clear(); return; }
        Iterator<Device> it = devices.values().iterator();
        while (it.hasNext()) if (now - it.next().seen > RETAIN_MS) it.remove();
        Matcher match = DEVICE.matcher(dump);
        while (match.find()) {
            int logical = Integer.parseInt(match.group(1), 16);
            int vendor = Integer.parseInt(match.group(2));
            int physical = Integer.parseInt(match.group(3), 16);
            int port = Integer.parseInt(match.group(4));
            Device old = devices.get(logical);
            if (vendor == 4346) devices.put(logical, new Device(physical, port, now));
            else if (vendor != 16777215 || (old != null && (old.physical != physical || old.port != port))) devices.remove(logical);
        }
    }

    synchronized int portFor(int logical, long now) {
        Device device = devices.get(logical);
        return enabled && device != null && now >= device.seen && now - device.seen <= RETAIN_MS ? device.port : -1;
    }
}
