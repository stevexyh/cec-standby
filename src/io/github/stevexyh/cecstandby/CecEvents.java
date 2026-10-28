package io.github.stevexyh.cecstandby;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Accept only current system-UID CEC reports of an observed Apple on -> standby transition. */
public final class CecEvents {
    private static final Pattern REPORT = Pattern.compile("^\\s*(\\d+\\.\\d+)\\s+1000\\s+\\d+\\s+\\d+\\s+[VDIWE] MiTv-Impl-HdmiManagerImpl: onStatusChanged, dev:CEC: logical_address: 0x([0-9a-fA-F]+) device_type: 4 vendor_id: 4346 .*power_status: ([0-3]) physical_address: 0x[0-9a-fA-F]+ port_id: ([1-9][0-9]*), status:3\\s*$");
    private final Map<String, Integer> states = new HashMap<>();
    private final long startedAt;
    public CecEvents(long startedAt) { this.startedAt = startedAt; }

    public static final class Command {
        public final String identity;
        public final int port;
        Command(String identity, int port) { this.identity = identity; this.port = port; }
    }

    public Command accept(String line, long now) {
        Matcher report = REPORT.matcher(line);
        if (!report.matches()) return null;
        long at = (long) (Double.parseDouble(report.group(1)) * 1000);
        if (now - at < 0) return null;
        int state = Integer.parseInt(report.group(3));
        String key = report.group(2) + ":" + report.group(4);
        Integer previous = states.put(key, state);
        // Seed from history without executing any buffered transition. Xiaomi reports only
        // changes, so a device already on at service startup may not report on again.
        if (at < startedAt || now - at > 8000) return null;
        if (state != 1 || previous == null || (previous != 0 && previous != 3)) return null;
        return new Command("Apple TV " + key + " on -> standby at " + report.group(1), Integer.parseInt(report.group(4)));
    }
}
