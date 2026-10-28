package io.github.stevexyh.cecstandby;

public final class RawCecEventsTest {
    public static void main(String[] args) throws Exception {
        String header = "mHdmiControlEnabled: true\nCEC: logical_address: 0x04 device_type: 4 vendor_id: 4346 display_name: Apple TV power_status: 0 physical_address: 0x1000 port_id: 1\n";
        String event = "[R] time=2026-10-27 23:27:39 message=<Standby 36> src: 4, dst: 0\n";
        long now = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT).parse("2026-10-27 23:27:40").getTime();
        RawCecEvents events = new RawCecEvents();
        check(events.accept(header + event, now) == null, "Old history at connection is not replayed");
        check(events.accept(header + event, now) == null, "Existing command is not repeated");
        events = new RawCecEvents(); events.accept(header, now);
        CecEvents.Command command = events.accept(header + event, now);
        check(command != null && command.port == 1, "Fresh original standby targets HDMI1");
        check(events.accept(header + event, now) == null, "Fresh command consumed once");
        events = new RawCecEvents(); events.accept(header, now);
        check(events.accept(header + event.replace("src: 4", "src: 8"), now) == null, "Other devices ignored");
        check(events.accept(header + event.replace("dst: 0", "dst: 5"), now) == null, "Other destinations ignored");
        check(events.accept(header + event, now + 10000) == null, "Stale command ignored");
        events = new RawCecEvents(); events.accept(header, now);
        check(events.accept(header + event.replace("dst: 0", "dst: 15"), now) != null, "Broadcast standby accepted");
        events = new RawCecEvents(); events.accept("mHdmiControlEnabled: true\n", now);
        String unknown = header.replace("vendor_id: 4346", "vendor_id: 16777215");
        check(events.accept(unknown + event, now) == null, "Unknown vendor does not authorize standby");
        check(events.accept(header + event, now + 1000) != null, "Fresh command survives delayed Apple identification");
        check(events.accept(header + event, now + 2000) == null, "Delayed command consumed only once");
        events = new RawCecEvents(); events.accept(header, now);
        events.accept(unknown + event, now);
        check(events.accept(header + event, now + 10000) == null, "Delayed identification cannot revive stale commands");
        events = new RawCecEvents(); events.accept(unknown + event, now);
        check(events.accept(header + event, now + 1000) == null, "Discovery cannot replay initial baseline history");
        System.out.println("Raw CEC parser: 13 checks passed");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
