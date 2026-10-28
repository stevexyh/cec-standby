import io.github.stevexyh.cecstandby.CecEvents;

public final class CecEventsTest {
    private static final long NOW = 1793113771490L;
    private static final String REPORT = "1793113771.489  1000  3926 26786 D MiTv-Impl-HdmiManagerImpl: onStatusChanged, dev:CEC: logical_address: 0x04 device_type: 4 vendor_id: 4346 display_name: Apple TV power_status: 1 physical_address: 0x1000 port_id: 1, status:3";
    public static void main(String[] args) {
        CecEvents events = new CecEvents(NOW - 1000);
        check(events.accept(REPORT, NOW) == null, "No initial standby without seeing device on");
        events = primed();
        check(events.accept(REPORT, NOW) != null, "Accept Apple on to standby");
        check(events.accept(REPORT, NOW) == null, "Ignore duplicate vendor reports");
        check(primed().accept(REPORT.replace("1000", "10043"), NOW) == null, "Reject non-system UID");
        check(primed().accept(REPORT.replace("vendor_id: 4346", "vendor_id: 1234"), NOW) == null, "Reject other vendor");
        check(primed().accept(REPORT.replace("0x04", "0x08"), NOW) == null, "Keep device state separate");
        check(primed().accept(REPORT.replace("port_id: 1", "port_id: 2"), NOW) == null, "Keep input state separate");
        check(primed().accept(REPORT, NOW + 9000) == null, "Ignore stale logs");
        check(primed().accept(REPORT, NOW - 5000) == null, "Ignore future logs");
        check(new CecEvents(NOW).accept(REPORT, NOW) == null, "Ignore buffered startup reports");
        check(primed().accept(REPORT.replace("power_status: 1", "power_status: 2"), NOW) == null, "Do not sleep for a transitional power state");
        check(primed().accept(REPORT.replace("status:3", "status:1"), NOW) == null, "Ignore device added events");
        events = new CecEvents(NOW - 1000);
        String historicalOn = REPORT.replace("1793113771.489", "1793113760.000").replace("power_status: 1", "power_status: 0");
        check(events.accept(historicalOn, NOW) == null, "Historical on report seeds state without an action");
        check(events.accept(REPORT, NOW) != null, "First fresh standby works when device was already on at startup");
        events = new CecEvents(NOW);
        events.accept(REPORT, NOW);
        check(events.accept(REPORT.replace("1793113771.489", "1793113772.000"), NOW + 1000) == null, "Historical standby cannot become a fresh transition");
        if (args.length > 0) {
            CecEvents replay = new CecEvents(1793114180000L);
            int matches = 0;
            try {
                for (String line : java.nio.file.Files.readAllLines(java.nio.file.Paths.get(args[0]))) {
                    java.util.regex.Matcher timestamp = java.util.regex.Pattern.compile("^\\s*(\\d+\\.\\d+)").matcher(line);
                    if (timestamp.find()) {
                        long at = (long) (Double.parseDouble(timestamp.group(1)) * 1000);
                        if (replay.accept(line, Math.max(1793114180000L, at + 1)) != null) matches++;
                    }
                }
            } catch (java.io.IOException e) { throw new AssertionError(e); }
            check(matches == 1, "Actual TV test log must produce exactly one standby: " + matches);
            System.out.println("Actual TV log replay: one standby detected");
        }
        System.out.println("CEC log parser: 15 checks passed");
    }
    private static CecEvents primed() {
        CecEvents events = new CecEvents(NOW - 1000);
        events.accept(REPORT.replace("power_status: 1", "power_status: 0"), NOW);
        return events;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
