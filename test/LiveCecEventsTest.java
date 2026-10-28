package io.github.stevexyh.cecstandby;

import java.nio.file.Files;
import java.nio.file.Paths;

public final class LiveCecEventsTest {
    private static final String HEADER = "mHdmiControlEnabled: true\n";
    private static final String DEVICE = "CEC: logical_address: 0x04 device_type: 4 vendor_id: 4346 display_name: Apple TV power_status: 0 physical_address: 0x1000 port_id: 1\n";
    private static final long NOW = 1793121094000L;
    private static final String DIRECT = "1793121093.948 1000 1234 1234 D HDMI    : [R]:<Standby 36> src: 4, dst: 0";
    private static int checks;

    public static void main(String[] args) throws Exception {
        AppleDevices devices = known();
        LiveCecEvents parser = new LiveCecEvents(devices, NOW - 1000);
        check(parser.accept(DIRECT, NOW).port == 1, "Synthetic live standby maps to HDMI1");
        check(parser.accept(DIRECT, NOW) == null, "Same log event consumed once");
        check(parser.accept(DIRECT.replace("dst: 0", "dst: 15"), NOW) != null, "Broadcast accepted");
        check(parser.accept(DIRECT.replace("[R]", "[S]"), NOW) == null, "TV transmissions ignored");
        check(parser.accept(DIRECT.replace("1000 3540", "10043 3540"), NOW) == null, "Non-system UID ignored");
        check(parser.accept(DIRECT.replace("src: 4", "src: 8"), NOW) == null, "Other source ignored");
        check(parser.accept(DIRECT.replace("dst: 0", "dst: 5"), NOW) == null, "Other destination ignored");
        check(new LiveCecEvents(devices, NOW).accept(DIRECT, NOW) == null, "Pre-start buffered command ignored");
        check(new LiveCecEvents(devices, NOW - 1000).accept(DIRECT, NOW + 3000) == null, "Stale event ignored");
        check(new LiveCecEvents(devices, NOW - 1000).accept(DIRECT, NOW - 100) == null, "Future event ignored");
        devices.observeSnapshot(HEADER, NOW);
        check(new LiveCecEvents(devices, NOW - 1000).accept(DIRECT, NOW) != null, "Hotplug eviction retains verified Apple binding");
        devices.observeSnapshot(HEADER + DEVICE.replace("vendor_id: 4346", "vendor_id: 16777215"), NOW);
        check(devices.portFor(4, NOW) == 1, "Unknown same binding retains Apple identity");
        devices.observeSnapshot(HEADER + DEVICE.replace("vendor_id: 4346", "vendor_id: 1234"), NOW);
        check(devices.portFor(4, NOW) == -1, "Known different vendor revokes cached identity");
        devices = known();
        devices.observeSnapshot(HEADER + DEVICE.replace("vendor_id: 4346", "vendor_id: 16777215").replace("0x1000", "0x2000"), NOW);
        check(devices.portFor(4, NOW) == -1, "Different physical address revokes cached identity");
        devices = known();
        devices.observeSnapshot(HEADER + DEVICE.replace("vendor_id: 4346", "vendor_id: 16777215").replace("port_id: 1", "port_id: 2"), NOW);
        check(devices.portFor(4, NOW) == -1, "Different port revokes cached identity");
        devices = known();
        check(devices.portFor(4, NOW + 30_001) == -1, "Missing-device cache expires");
        devices.observeSnapshot("mHdmiControlEnabled: false\n" + DEVICE, NOW);
        check(devices.portFor(4, NOW) == -1, "Disabled CEC revokes identity");
        devices = new AppleDevices();
        devices.observeSnapshot(HEADER + DEVICE.replace("vendor_id: 4346", "vendor_id: 16777215"), NOW);
        check(devices.portFor(4, NOW) == -1, "Unknown vendor cannot create identity");
        RawCecEvents raw = new RawCecEvents();
        raw.accept(HEADER + DEVICE, NOW);
        check(raw.accept(HEADER + "[R] time=2026-10-28 01:11:33 message=<Standby 36> src: 4, dst: 0\n", NOW) != null,
                "Snapshot fallback also survives the actual hotplug race");
        if (args.length > 0) {
            devices = known();
            devices.observeSnapshot(HEADER, NOW);
            parser = new LiveCecEvents(devices, NOW - 1000);
            int received = 0;
            for (String line : Files.readAllLines(Paths.get(args[0]))) {
                if (line.contains("1793121093.") && parser.accept(line, NOW) != null) received++;
            }
            check(received == 2, "Synthetic hotplug race: both received standby messages recovered after device eviction");
            System.out.println("Synthetic race replay: both original standby messages recovered");
        }
        System.out.println("Live CEC and binding cache: " + checks + " checks passed");
    }

    private static AppleDevices known() { AppleDevices devices = new AppleDevices(); devices.observeSnapshot(HEADER + DEVICE, NOW - 1000); return devices; }
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
}
