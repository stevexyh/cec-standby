package io.github.stevexyh.cecstandby;

import java.util.ArrayDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Real-time system-UID HDMI messages, without waiting for a dumpsys polling interval. */
final class LiveCecEvents {
    private static final Pattern STANDBY = Pattern.compile("^\\s*(\\d+\\.\\d+)\\s+1000\\s+\\d+\\s+\\d+\\s+D HDMI\\s*:\\s*\\[R\\]:<Standby 36> src: ([0-9]+), dst: (0|15)\\s*$");
    private final AppleDevices devices;
    private final long startedAt;
    private final ArrayDeque<String> seen = new ArrayDeque<>();
    LiveCecEvents(AppleDevices devices, long startedAt) { this.devices = devices; this.startedAt = startedAt; }

    CecEvents.Command accept(String line, long now) {
        Matcher match = STANDBY.matcher(line);
        if (!match.matches()) return null;
        long at = Math.round(Double.parseDouble(match.group(1)) * 1000);
        if (at < startedAt || now < at || now - at > 2000 || seen.contains(line)) return null;
        int port = devices.portFor(Integer.parseInt(match.group(2)), now);
        if (port < 0) return null;
        seen.addLast(line);
        if (seen.size() > 32) seen.removeFirst();
        return new CecEvents.Command("live " + line.trim(), port);
    }
}
