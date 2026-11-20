package io.github.stevexyh.cecstandby;

import java.util.*;

/** Validated data only: no executable commands or unbounded user-controlled routes. */
final class Profile {
    static final Set<String> KEYS = new HashSet<>(Arrays.asList("schemaVersion", "id", "manufacturer", "model", "sdk", "firmware", "vendorId", "port", "inputId", "player", "action", "inputExtra", "inputValue", "launchers", "keepAdb", "enabled"));
    /** Schema 2 adds firmware adapters; wake-handoff keys are present only when the handoff is enabled. */
    static final Set<String> HANDOFF_KEYS = new HashSet<>(Arrays.asList("action", "inputExtra", "inputValue", "launchers"));
    static final Set<String> V2_KEYS = new HashSet<>(Arrays.asList("events", "inputSource", "standbyKey", "wakeHandoff"));
    static final List<String> EVENTS = Arrays.asList("xiaomi", "tcl");
    static final List<String> INPUT_SOURCES = Arrays.asList("setting", "tv-input-session");
    static final List<String> STANDBY_KEYS = Arrays.asList("POWER", "SLEEP");
    final String id, manufacturer, model, firmware, inputId, player, action, inputExtra, events, inputSource, standbyKey;
    final int schemaVersion, sdk, vendorId, port, inputValue;
    final List<String> launchers;
    final boolean keepAdb, enabled, wakeHandoff;
    Profile(Map<String,Object> data) {
        schemaVersion = integer(data,"schemaVersion",1,2);
        if (schemaVersion == 1) {
            if (!KEYS.equals(data.keySet())) throw new IllegalArgumentException("Missing or unknown configuration keys");
            // Schema 1 is the original Xiaomi behavior.
            events = "xiaomi"; inputSource = "setting"; standbyKey = "POWER"; wakeHandoff = true;
        } else {
            wakeHandoff = data.get("wakeHandoff") instanceof Boolean && (Boolean) data.get("wakeHandoff");
            Set<String> expected = new HashSet<>(KEYS);
            expected.addAll(V2_KEYS);
            if (!wakeHandoff) expected.removeAll(HANDOFF_KEYS);
            if (!expected.equals(data.keySet())) throw new IllegalArgumentException("Missing or unknown configuration keys");
            bool(data,"wakeHandoff");
            events = choice(data,"events",EVENTS);
            inputSource = choice(data,"inputSource",INPUT_SOURCES);
            standbyKey = choice(data,"standbyKey",STANDBY_KEYS);
        }
        id=text(data,"id","[a-z0-9][a-z0-9-]{0,63}");
        manufacturer=text(data,"manufacturer","[A-Za-z0-9_. -]{1,80}");
        model=text(data,"model","[A-Za-z0-9_. -]{1,80}");
        firmware=text(data,"firmware","[A-Za-z0-9_. -]{1,120}");
        sdk=integer(data,"sdk",28,28); // Only the Android 9 hidden-API adapter exists.
        vendorId=integer(data,"vendorId",0,16777214);port=integer(data,"port",1,15);
        inputId=text(data,"inputId","[A-Za-z0-9_.$/]{1,240}");
        player=text(data,"player","[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)+/(?:\\.)?[a-zA-Z][a-zA-Z0-9_.$]*");
        if (wakeHandoff) {
            action=text(data,"action","[A-Za-z][A-Za-z0-9_.]{1,180}");
            inputExtra=text(data,"inputExtra","[A-Za-z][A-Za-z0-9_]{0,63}");
            inputValue=integer(data,"inputValue",0,65535);
            Object list=data.get("launchers");
            if (!(list instanceof List) || ((List<?>)list).isEmpty() || ((List<?>)list).size()>16) throw new IllegalArgumentException("Invalid launchers");
            List<String> result=new ArrayList<>();
            for(Object v:(List<?>)list) {
                if (!(v instanceof String) || !((String)v).matches("[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z][a-zA-Z0-9_]*)+/(?:\\.)?[a-zA-Z][a-zA-Z0-9_.$]*")) throw new IllegalArgumentException("Invalid launcher");
                result.add((String)v);
            }
            launchers=Collections.unmodifiableList(result);
        } else {
            action=null; inputExtra=null; inputValue=-1; launchers=Collections.emptyList();
        }
        keepAdb=bool(data,"keepAdb");enabled=bool(data,"enabled");
    }
    boolean matches(String maker,String deviceModel,int api,String build) {
        return manufacturer.equalsIgnoreCase(maker) && model.equals(deviceModel) && sdk==api && firmware.equals(build);
    }
    static String normalizeComponent(String component) {
        int slash=component.indexOf('/');
        if(slash<0) return component;
        return component.charAt(slash+1)=='.' ? component.substring(0,slash+1)+component.substring(0,slash)+component.substring(slash+1) : component;
    }
    boolean isLauncher(String component) {
        for(String allowed:launchers) if(normalizeComponent(allowed).equals(normalizeComponent(component))) return true;
        return false;
    }
    private static String text(Map<String,Object> d,String k,String pattern) {
        Object v=d.get(k);if(!(v instanceof String) || !((String)v).matches(pattern)) throw new IllegalArgumentException("Invalid "+k);return (String)v;
    }
    private static String choice(Map<String,Object> d,String k,List<String> allowed) {
        Object v=d.get(k);if(!(v instanceof String) || !allowed.contains(v)) throw new IllegalArgumentException("Invalid "+k);return (String)v;
    }
    private static int integer(Map<String,Object> d,String k,int min,int max) {
        Object v=d.get(k);if(!(v instanceof Integer) && !(v instanceof Long)) throw new IllegalArgumentException("Invalid "+k);
        long n=((Number)v).longValue();if(n<min||n>max)throw new IllegalArgumentException("Invalid "+k);return (int)n;
    }
    private static boolean bool(Map<String,Object>d,String k) {
        Object v=d.get(k);if(!(v instanceof Boolean))throw new IllegalArgumentException("Invalid "+k);return (Boolean)v;
    }
}
