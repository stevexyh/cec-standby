package io.github.stevexyh.cecstandby;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TCL/Realtek (rtd285o) firmware: the CEC HAL receives &lt;Standby&gt; but never forwards it to
 * HdmiControlService. Two observable signals remain, both written by system-UID processes:
 * <ul>
 *   <li>the HAL's own receive log ({@code sita.proxy}, opcode 0x36) on firmware that logs it;</li>
 *   <li>the source's &lt;Inactive Source&gt; followed within {@link #PAIR_MS} by its broadcast
 *   &lt;Report Power Status&gt; = standby, which Apple TV sends when it goes to sleep. This is the
 *   only signal on firmware whose HAL does not log raw messages.</li>
 * </ul>
 * The pair is also read from the dumpsys history as a fallback for a stalled log stream.
 */
final class TclCecEvents {
    static final long PAIR_MS = 3000;
    private static final String PREFIX = "^\\s*(\\d+\\.\\d+)\\s+1000\\s+\\d+\\s+\\d+\\s+";
    private static final Pattern HAL_STANDBY = Pattern.compile(PREFIX + "I sita\\.proxy\\s*: tifhal_hdmicec_callback client un8TxLogAddr=([0-9]+) un8RxLogAddr=(0|15) un8Msg=0x36(?:\\s.*)?$");
    private static final String RECEIVED = "D HdmiCecController\\s*: (?:---\\w+---\\s+)?handleIncomingCecCommand command:";
    private static final Pattern INACTIVE = Pattern.compile(PREFIX + RECEIVED + "<InActive Source> src: ([0-9]+), dst: 0(?:,.*)?$");
    private static final Pattern REPORT_STANDBY = Pattern.compile(PREFIX + RECEIVED + "<Report Power Status> src: ([0-9]+), dst: 15, params: 01\\s*$");
    private static final Pattern HISTORY = Pattern.compile("^\\[R\\] time=(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}) message=<(InActive Source|Report Power Status)> src: ([0-9]+), dst: ([0-9]+)(?:, params: (.*))?$");

    private final AppleDevices devices;
    private final long startedAt;
    private final ArrayDeque<String> seenLines = new ArrayDeque<>();
    private final Map<Integer, Long> inactiveAt = new HashMap<>();
    private final Set<String> seenHistory = new HashSet<>();
    private boolean primed;

    TclCecEvents(AppleDevices devices, long startedAt) { this.devices = devices; this.startedAt = startedAt; }

    /** One live logcat line ({@code -v epoch -v uid}). */
    synchronized CecEvents.Command accept(String line, long now) {
        Matcher hal = HAL_STANDBY.matcher(line);
        if (hal.matches()) return fresh(hal.group(1), now) ? command(line, Integer.parseInt(hal.group(2)), now, "HAL <Standby>") : null;
        Matcher inactive = INACTIVE.matcher(line);
        if (inactive.matches()) {
            if (fresh(inactive.group(1), now)) inactiveAt.put(Integer.parseInt(inactive.group(2)), millis(inactive.group(1)));
            return null;
        }
        Matcher report = REPORT_STANDBY.matcher(line);
        if (!report.matches() || !fresh(report.group(1), now)) return null;
        int source = Integer.parseInt(report.group(2));
        Long at = inactiveAt.remove(source);
        long reportedAt = millis(report.group(1));
        if (at == null || reportedAt < at || reportedAt - at > PAIR_MS) return null;
        return command(line, source, now, "<Inactive Source> + standby");
    }

    /** A {@code dumpsys hdmi_control} snapshot; never acts on history present before the first snapshot. */
    synchronized CecEvents.Command acceptSnapshot(String dump, long now) {
        devices.observeSnapshot(dump, now);
        if (!dump.contains("mHdmiControlEnabled: true")) return null;
        Map<Integer, Long> pending = new HashMap<>();
        Set<String> history = new HashSet<>();
        CecEvents.Command result = null;
        for (String raw : dump.split("\n")) {
            Matcher event = HISTORY.matcher(raw.trim());
            if (!event.matches()) continue;
            Long at = parseLocal(event.group(1));
            if (at == null) continue;
            int source = Integer.parseInt(event.group(3));
            if ("InActive Source".equals(event.group(2))) {
                if ("0".equals(event.group(4))) pending.put(source, at);
                continue;
            }
            if (!"15".equals(event.group(4)) || !"01".equals(event.group(5) == null ? null : event.group(5).trim())) continue;
            Long inactive = pending.remove(source);
            if (inactive == null || at < inactive || at - inactive > PAIR_MS) continue;
            String id = event.group(0);
            history.add(id);
            if (!primed || seenHistory.contains(id) || now < at || now - at > 8000) continue;
            int port = devices.portFor(source, now);
            if (port > 0) { result = new CecEvents.Command("history " + id, port); seenHistory.add(id); }
        }
        if (!primed) seenHistory.addAll(history);
        seenHistory.retainAll(history);
        primed = true;
        return result;
    }

    private CecEvents.Command command(String line, int source, long now, String kind) {
        if (seenLines.contains(line)) return null;
        int port = devices.portFor(source, now);
        if (port < 0) return null;
        seenLines.addLast(line);
        if (seenLines.size() > 32) seenLines.removeFirst();
        return new CecEvents.Command("live " + kind + ": " + line.trim(), port);
    }

    private boolean fresh(String epoch, long now) {
        long at = millis(epoch);
        return at >= startedAt && now >= at && now - at <= 2000;
    }
    private static long millis(String epoch) { return Math.round(Double.parseDouble(epoch) * 1000); }
    private static Long parseLocal(String value) {
        java.util.Date at = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).parse(value, new ParsePosition(0));
        return at == null ? null : at.getTime();
    }
}
