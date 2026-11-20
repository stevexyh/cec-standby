package io.github.stevexyh.cecstandby;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Synthetic TCL/Realtek (rtd285o) fixtures in the bridge's logcat format (-v epoch -v uid). */
public final class TclCecEventsTest {
    private static final String HEADER = "mHdmiControlEnabled: true\n";
    private static final String DEVICE = "CEC: logical_address: 0x04 device_type: 4 vendor_id: 4346 display_name: Apple TV power_status: 0 physical_address: 0x1000 port_id: 1\n";
    private static final long NOW = 1793121094000L;
    private static final String HAL = "         1793121093.948  1000   245   252 I sita.proxy: tifhal_hdmicec_callback client un8TxLogAddr=4 un8RxLogAddr=15 un8Msg=0x36 un8DataLen=2 ";
    private static final String INACTIVE = "         1793121093.700  1000   637   637 D HdmiCecController: ---yums--- handleIncomingCecCommand command:<InActive Source> src: 4, dst: 0, params: 10 00";
    private static final String REPORT = "         1793121093.800  1000   637   637 D HdmiCecController: ---yums--- handleIncomingCecCommand command:<Report Power Status> src: 4, dst: 15, params: 01";
    private static int checks;

    public static void main(String[] args) {
        AppleDevices devices = known();
        TclCecEvents parser = new TclCecEvents(devices, NOW - 1000);
        CecEvents.Command hal = parser.accept(HAL, NOW);
        check(hal != null && hal.port == 1, "HAL broadcast <Standby> maps to HDMI1");
        check(parser.accept(HAL, NOW) == null, "Same HAL line consumed once");
        check(parser.accept(HAL.replace("RxLogAddr=15", "RxLogAddr=0"), NOW) != null, "Directed HAL <Standby> accepted");
        check(fresh().accept(HAL.replace("RxLogAddr=15", "RxLogAddr=5"), NOW) == null, "HAL <Standby> to another device ignored");
        check(fresh().accept(HAL.replace("0x36", "0x90"), NOW) == null, "Other HAL opcodes ignored");
        check(fresh().accept(HAL.replace("TxLogAddr=4", "TxLogAddr=8"), NOW) == null, "HAL <Standby> from unknown source ignored");
        check(fresh().accept(HAL.replace("  1000 ", " 10043 "), NOW) == null, "Non-system UID ignored");
        check(new TclCecEvents(devices, NOW).accept(HAL, NOW) == null, "Pre-start buffered HAL line ignored");
        check(fresh().accept(HAL, NOW + 3000) == null, "Stale HAL line ignored");

        parser = fresh();
        check(parser.accept(INACTIVE, NOW) == null, "<Inactive Source> alone does not sleep");
        CecEvents.Command pair = parser.accept(REPORT, NOW);
        check(pair != null && pair.port == 1, "<Inactive Source> + standby report maps to HDMI1");
        check(parser.accept(REPORT.replace("yums", "zzhengqingcec"), NOW) == null, "Duplicate debug line of the report does not fire again");
        check(fresh().accept(REPORT, NOW) == null, "Standby report without <Inactive Source> ignored");
        parser = fresh();
        parser.accept(INACTIVE.replace("1793121093.700", "1793121090.500"), NOW);
        check(parser.accept(REPORT, NOW) == null, "Pair further apart than 3 s ignored");
        parser = fresh();
        parser.accept(INACTIVE, NOW);
        check(parser.accept(REPORT.replace("params: 01", "params: 00"), NOW) == null, "Report of power on ignored");
        parser = fresh();
        parser.accept(INACTIVE, NOW);
        check(parser.accept(REPORT.replace("src: 4", "src: 8"), NOW) == null, "Report from another source ignored");
        parser = fresh();
        parser.accept(INACTIVE.replace("D HdmiCecController: ---yums--- ", "D HdmiCecController: "), NOW);
        check(parser.accept(REPORT.replace("D HdmiCecController: ---yums--- ", "D HdmiCecController: "), NOW) != null, "Lines without vendor debug prefix accepted");
        parser = fresh();
        parser.accept(INACTIVE.replace(", dst: 0,", ", dst: 15,"), NOW);
        check(parser.accept(REPORT, NOW) == null, "<Inactive Source> not addressed to the TV ignored");

        // Snapshot fallback: dumpsys history uses local wall-clock seconds.
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date(NOW - 1000));
        String history = "    [R] time=" + time + " message=<InActive Source> src: 4, dst: 0, params: 10 00\n" +
                "    [R] time=" + time + " message=<Report Power Status> src: 4, dst: 15, params: 01\n";
        TclCecEvents snapshots = fresh();
        check(snapshots.acceptSnapshot(HEADER + DEVICE + history, NOW) == null, "History present at the first snapshot is never executed");
        snapshots = fresh();
        snapshots.acceptSnapshot(HEADER + DEVICE, NOW - 1000);
        CecEvents.Command fromHistory = snapshots.acceptSnapshot(HEADER + DEVICE + history, NOW);
        check(fromHistory != null && fromHistory.port == 1, "New history pair maps to HDMI1");
        check(snapshots.acceptSnapshot(HEADER + DEVICE + history, NOW + 500) == null, "History pair consumed once");
        snapshots = fresh();
        snapshots.acceptSnapshot(HEADER + DEVICE, NOW - 1000);
        check(snapshots.acceptSnapshot(HEADER + DEVICE + history, NOW + 10_000) == null, "Stale history pair ignored");
        snapshots = fresh();
        snapshots.acceptSnapshot(HEADER + DEVICE, NOW - 1000);
        check(snapshots.acceptSnapshot(HEADER + DEVICE + history.split("\n")[1] + "\n", NOW) == null, "History report without <Inactive Source> ignored");
        snapshots = fresh();
        snapshots.acceptSnapshot(HEADER + DEVICE, NOW - 1000);
        check(snapshots.acceptSnapshot("mHdmiControlEnabled: false\n" + DEVICE + history, NOW) == null, "Disabled CEC ignored");

        String tvInput = "  sessionStateMap: ITvInputSession -> SessionState\n" +
                "    android.os.Binder@11aa22: com.android.server.tv.TvInputManagerService$SessionState@33bb44\n" +
                "      inputId: example.input/.Other/HW2\n" +
                "    android.os.Binder@38388ff: com.android.server.tv.TvInputManagerService$SessionState@9895aff\n" +
                "      inputId: example.input/.Passthrough/HW1\n" +
                "  mainSessionToken: android.os.Binder@38388ff\n";
        check("example.input/.Passthrough/HW1".equals(TvInputs.mainSessionInput(tvInput)), "Main tv_input session selects the current input");
        check("null".equals(TvInputs.mainSessionInput(tvInput.replace("mainSessionToken: android.os.Binder@38388ff", "mainSessionToken: null"))), "No main session reports null");
        check("null".equals(TvInputs.mainSessionInput("")), "Empty dump reports null");
        System.out.println("TCL CEC parser: " + checks + " checks passed");
    }

    private static TclCecEvents fresh() { return new TclCecEvents(known(), NOW - 1000); }
    private static AppleDevices known() { AppleDevices devices = new AppleDevices(); devices.observeSnapshot(HEADER + DEVICE, NOW - 1000); return devices; }
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
}
