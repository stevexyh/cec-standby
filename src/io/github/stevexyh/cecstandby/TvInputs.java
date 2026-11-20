package io.github.stevexyh.cecstandby;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Current TV input for firmware that leaves Settings.System tv_input_id unset (TCL): the input of
 * TvInputManagerService's main session, as printed by {@code dumpsys tv_input}.
 */
final class TvInputs {
    private static final Pattern MAIN = Pattern.compile("(?m)^\\s*mainSessionToken: (\\S+)\\s*$");
    private TvInputs() { }

    /** Returns the main session's input id, or "null" (like an unset setting) when there is none. */
    static String mainSessionInput(String dump) {
        Matcher main = MAIN.matcher(dump);
        if (!main.find() || "null".equals(main.group(1))) return "null";
        Matcher session = Pattern.compile("(?m)^\\s*" + Pattern.quote(main.group(1)) +
                ": \\S*SessionState@\\S+\\s*\\n\\s*inputId: (\\S+)\\s*$").matcher(dump);
        return session.find() ? session.group(1) : "null";
    }
}
