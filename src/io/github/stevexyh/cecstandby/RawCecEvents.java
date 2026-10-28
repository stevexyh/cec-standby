package io.github.stevexyh.cecstandby;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** New explicit Apple standby messages from a local shell snapshot, never buffered history. */
final class RawCecEvents {
    private final AppleDevices devices;
    private static final Pattern STANDBY = Pattern.compile("\\[R\\] time=(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}) message=<Standby 36> src: ([0-9]+), dst: (0|15)(?:$|\\s)");
    private final Set<String> seen = new HashSet<>();
    private boolean primed;
    RawCecEvents() { this(new AppleDevices()); }
    RawCecEvents(AppleDevices devices) { this.devices = devices; }

    CecEvents.Command accept(String dump, long now) {
        devices.observeSnapshot(dump, now);
        if (!dump.contains("mHdmiControlEnabled: true")) return null;
        Set<String> history = new HashSet<>();
        Set<String> consumed = new HashSet<>();
        CecEvents.Command result = null;
        for (String line : dump.split("\n")) {
            Matcher event = STANDBY.matcher(line.trim());
            if (!event.find()) continue;
            String id = event.group(0);
            history.add(id);
            if (!primed || seen.contains(id)) { consumed.add(id); continue; }
            java.util.Date at = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).parse(event.group(1), new ParsePosition(0));
            if (at == null || now - at.getTime() < 0 || now - at.getTime() > 8000) { consumed.add(id); continue; }
            int port = devices.portFor(Integer.parseInt(event.group(2)), now);
            if (port > 0) {
                result = new CecEvents.Command(id, port);
                consumed.add(id);
            }
        }
        // Device discovery after STR temporarily reports vendor_id=16777215. Keep a new
        // command pending within its freshness window instead of losing it before Apple is known.
        seen.retainAll(history); seen.addAll(consumed); primed = true;
        return result;
    }
}
