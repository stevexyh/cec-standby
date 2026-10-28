package io.github.stevexyh.cecstandby;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Device-local shell worker, independent of application force-stop but not adbd lifetime. */
public final class ShellBridge {
    static final String VERSION = "0.2.0";
    static final String DIR = "/data/local/tmp/cec-standby";
    private final Profile profile;
    private final boolean active;
    private static final Pattern FOCUS = Pattern.compile("mCurrentFocus=Window\\{[^\\n]*?u[0-9]+\\s+([^/\\s]+)/([^}\\s]+)");
    private static final Pattern WAKE = Pattern.compile("^\\s*(\\d+\\.\\d+)\\s+1000\\s+\\d+\\s+\\d+\\s+I PowerManagerService\\s*: Waking up from sleep.*");
    private static final ScheduledExecutorService TIMEOUTS = Executors.newSingleThreadScheduledExecutor();
    private final Context context;
    private final PowerManager power;
    private final AppleDevices devices;
    private final ExecutorService actions = Executors.newSingleThreadExecutor();
    private final File armed = new File(DIR, "resume-hdmi");
    private volatile Process logger;
    private volatile long frames, wakeUntil, lastFrameAt;
    private volatile String focus = "unknown", status = "starting", lastAction = "none";
    private long lastStandbyAt = Long.MIN_VALUE / 2;

    private ShellBridge(Context context) throws Exception {
        this.context = context; power = context.getSystemService(PowerManager.class);
        profile = ConfigStore.parse(new String(Files.readAllBytes(new File(DIR,"profile.json").toPath()), StandardCharsets.UTF_8));
        devices = new AppleDevices(profile.vendorId);
        boolean route = false;
        android.content.ComponentName component = android.content.ComponentName.unflattenFromString(profile.player);
        try { route = context.getPackageManager().getActivityInfo(component,0).enabled; } catch(android.content.pm.PackageManager.NameNotFoundException absent) { }
        active = profile.enabled && route && profile.matches(android.os.Build.MANUFACTURER,android.os.Build.MODEL,android.os.Build.VERSION.SDK_INT,android.os.Build.VERSION.INCREMENTAL);
    }

    public static void main(String[] args) throws Exception {
        if (android.os.Process.myUid() != 2000) throw new SecurityException("Shell UID required");
        Looper.prepareMainLooper();
        Class<?> threadClass = Class.forName("android.app.ActivityThread");
        Object thread = threadClass.getMethod("systemMain").invoke(null);
        Context system = (Context) threadClass.getMethod("getSystemContext").invoke(thread);
        Context context = system.createPackageContext("com.android.shell", Context.CONTEXT_IGNORE_SECURITY);
        ShellBridge bridge = new ShellBridge(context);
        if (args.length > 0) {
            try {
                System.out.println("uid=" + android.os.Process.myUid() + " package=" + context.getPackageName() + " interactive=" + bridge.power.isInteractive());
                if ("--open-input".equals(args[0])) { bridge.openInput(); System.out.println("HDMI activity requested"); }
                if ("--check".equals(args[0])) {
                    long started = SystemClock.elapsedRealtime();
                    System.out.println("input=" + currentInput());
                    System.out.println("activity=" + bridge.focusedActivity());
                    System.out.println("profile=" + bridge.profile.id + " active=" + bridge.active);
                    System.out.println("checksMs=" + (SystemClock.elapsedRealtime() - started));
                }
                if ("--standby".equals(args[0])) {
                    bridge.standby(new CecEvents.Command("owner-requested diagnostic standby", bridge.profile.port));
                    bridge.actions.shutdown();
                    if (!bridge.actions.awaitTermination(10, TimeUnit.SECONDS)) throw new java.io.IOException("Diagnostic standby timed out");
                    System.out.println("interactiveAfterStandby=" + bridge.power.isInteractive());
                }
            } finally { bridge.actions.shutdownNow(); TIMEOUTS.shutdownNow(); }
            return;
        }
        try (FileOutputStream lock = new FileOutputStream(new File(DIR, "bridge.lock"));
             FileLock held = lock.getChannel().tryLock()) {
            if (held == null) return;
            write("pid", Integer.toString(android.os.Process.myPid()));
            write("version", VERSION);
            bridge.record("Profile=" + bridge.profile.id + " active=" + bridge.active);
            bridge.record("Started " + VERSION + " uid=2000 pid=" + android.os.Process.myPid());
            if (bridge.power.isInteractive()) bridge.beginWake("bridge-start");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> { if (bridge.logger != null) bridge.logger.destroy(); }));
            new Thread(bridge::snapshots, "cec-snapshots").start();
            new Thread(bridge::logs, "cec-live-events").start();
            Looper.loop();
        }
    }

    private void snapshots() {
        RawCecEvents parser = new RawCecEvents(devices);
        long writtenAt = 0;
        boolean wasInteractive = power.isInteractive();
        while (!new File(DIR, "stop").exists()) {
            try {
                boolean interactive = power.isInteractive();
                if (interactive && !wasInteractive) beginWake("power-edge");
                wasInteractive = interactive;
                if (interactive) {
                    String dump = command("dumpsys", "hdmi_control");
                    long now = System.currentTimeMillis();
                    CecEvents.Command event = parser.accept(dump, now);
                    focus = focusedPackage();
                    frames++;
                    lastFrameAt = SystemClock.elapsedRealtime();
                    if (event != null) standby(event);
                    checkWake();
                }
                if (SystemClock.elapsedRealtime() - writtenAt > 3000) {
                    try { context.getPackageManager().getPackageInfo("io.github.stevexyh.cecstandby", 0); }
                    catch (android.content.pm.PackageManager.NameNotFoundException removed) { break; }
                    write("status.txt", "version=" + VERSION + " pid=" + android.os.Process.myPid() + " uid=2000\n" +
                            "interactive=" + power.isInteractive() + " frames=" + frames + " focus=" + focus + "\n" +
                            "logs=" + status + " snapshotAgeMs=" + (SystemClock.elapsedRealtime() - lastFrameAt) + "\n" +
                            "profile=" + profile.id + " active=" + active + " elapsedMs=" + SystemClock.elapsedRealtime() + "\n" +
                            "lastAction=" + lastAction + "\n");
                    write("heartbeat", Long.toString(SystemClock.elapsedRealtime()));
                    writtenAt = SystemClock.elapsedRealtime();
                }
                Thread.sleep(SystemClock.elapsedRealtime() < wakeUntil ? 200 : 1000);
            } catch (Exception e) { Log.w("CecBridge", "Snapshot failed", e); pause(); }
        }
        if (logger != null) logger.destroy();
        System.exit(0);
    }

    private void logs() {
        while (!new File(DIR, "stop").exists()) {
            long startedAt = System.currentTimeMillis();
            LiveCecEvents parser = new LiveCecEvents(devices, startedAt);
            try {
                Process process = new ProcessBuilder("/system/bin/logcat", "-T", "1", "-v", "epoch", "-v", "uid", "-s", "HDMI:D", "PowerManagerService:I").redirectErrorStream(true).start();
                logger = process;
                status = "listening";
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        long now = System.currentTimeMillis();
                        CecEvents.Command event = parser.accept(line, now);
                        if (event != null) standby(event);
                        Matcher wake = WAKE.matcher(line);
                        if (wake.matches()) {
                            long at = Math.round(Double.parseDouble(wake.group(1)) * 1000);
                            if (at >= startedAt && now >= at && now - at < 2000) beginWake("system-log");
                        }
                    }
                } finally { process.destroy(); logger = null; }
                status = "log stream ended";
            } catch (Exception e) { status = e.toString(); Log.w("CecBridge", "Log stream failed", e); }
            pause();
        }
    }

    private synchronized void standby(CecEvents.Command event) {
        long now = SystemClock.elapsedRealtime();
        if (!active || event.port != profile.port || new File(DIR, "stop").exists() || !power.isInteractive() || now - lastStandbyAt < 4000) return;
        lastStandbyAt = now;
        actions.execute(() -> {
            try {
                String front = focusedPackage();
                for (int i = 0; i < 4 && "unknown".equals(front); i++) { Thread.sleep(50); front = focusedPackage(); }
                String selected = currentInput();
                if (new File(DIR, "stop").exists() || !profile.inputId.equals(selected)) return;
                if (!power.isInteractive() || !Profile.normalizeComponent(profile.player).equals(Profile.normalizeComponent(focusedActivity()))) { record("Ignored standby outside HDMI player: " + front); return; }
                if (profile.inputId.equals(selected)) write("resume-hdmi", Long.toString(System.currentTimeMillis()));
                wakeUntil = 0;
                record("Native standby: " + event.identity);
                command("input", "keyevent", "KEYCODE_POWER");
                for (int i = 0; i < 30 && power.isInteractive(); i++) Thread.sleep(100);
                record("Standby result: interactive=" + power.isInteractive());
            } catch (Exception e) { Log.e("CecBridge", "Native standby failed", e); }
        });
    }

    private synchronized void checkWake() {
        if (wakeUntil == 0) return;
        if (SystemClock.elapsedRealtime() < wakeUntil) handoff();
        else finishWake();
    }

    private synchronized void beginWake(String source) {
        long now = SystemClock.elapsedRealtime();
        record("Wake check source=" + source + " armed=" + armed.exists() + " interactive=" + power.isInteractive());
        if (!active || new File(DIR, "stop").exists() || !armed.exists() || now < wakeUntil) return;
        wakeUntil = now + 5000;
        actions.execute(() -> {
            for (int i = 0; i < 20 && !power.isInteractive(); i++) try { Thread.sleep(25); } catch (InterruptedException ignored) { return; }
            handoff();
        });
    }

    private synchronized void handoff() {
        if (!active || new File(DIR, "stop").exists() || !power.isInteractive() || SystemClock.elapsedRealtime() >= wakeUntil) return;
        try {
            String activity = focusedActivity();
            String front = packageOf(activity);
            if (Profile.normalizeComponent(profile.player).equals(Profile.normalizeComponent(activity))) {
                if (armed.exists()) record("Wake: native HDMI player already focused");
                armed.delete(); return;
            }
            if ("unknown".equals(front)) return;
            if (!profile.isLauncher(activity)) { record("Wake: leaving unlisted foreground"); finishWake(); return; }
            if (!profile.inputId.equals(currentInput())) { finishWake(); return; }
            openInput();
            record("Early wake handoff: " + front + " -> configured input");
            armed.delete();
        } catch (Exception e) { Log.w("CecBridge", "Wake handoff failed", e); finishWake(); }
    }

    private void openInput() throws Exception {
        if (!active) throw new IllegalStateException("Inactive/mismatched profile");
        Intent intent = new Intent(profile.action)
                .setComponent(ComponentName.unflattenFromString(profile.player))
                .putExtra(profile.inputExtra, profile.inputValue).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // A standalone shell process has no registered application thread. Use the same null
        // caller and shell package as `am start`, without starting another Java VM at every wake.
        Object manager = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        for (java.lang.reflect.Method method : Class.forName("android.app.IActivityManager").getMethods()) {
            if (!method.getName().equals("startActivity") || method.getParameterTypes().length != 10) continue;
            int result = (Integer) method.invoke(manager, null, "com.android.shell", intent, null, null, null, 0, 0, null, null);
            if (result < 0) throw new java.io.IOException("Activity start rejected: " + result);
            return;
        }
        throw new java.io.IOException("Unsupported activity-manager signature");
    }

    private void finishWake() { wakeUntil = 0; armed.delete(); }
    private static String currentInput() throws Exception {
        // Like `am start`, `settings` obtains its provider through the shell's external-provider
        // API. Context.getContentResolver() requires a registered application thread and fails
        // in app_process, even when creating a com.android.shell package context succeeds.
        return command("settings", "get", "system", "tv_input_id").trim();
    }
    private String focusedPackage() throws Exception { return packageOf(focusedActivity()); }
    private static String packageOf(String activity) { int slash = activity.indexOf('/'); return slash < 0 ? activity : activity.substring(0, slash); }
    private String focusedActivity() throws Exception {
        Matcher match = FOCUS.matcher(command("dumpsys", "window", "windows"));
        return match.find() ? match.group(1) + "/" + match.group(2) : "unknown";
    }
    private void record(String value) {
        lastAction = value;
        Log.i("CecBridge", value);
        // Keep an on-device chronology across logd pruning, wall-clock correction and adbd
        // restarts. It contains only this helper's actions, not application/window content.
        synchronized (ShellBridge.class) {
            File journal = new File(DIR, "events.log");
            if (journal.length() > 65536) {
                File previous = new File(DIR, "events.previous");
                previous.delete(); journal.renameTo(previous);
            }
            try (FileOutputStream out = new FileOutputStream(journal, true)) {
                String entry = "wall=" + System.currentTimeMillis() + " elapsed=" + SystemClock.elapsedRealtime() +
                        " pid=" + android.os.Process.myPid() + " " + value + "\n";
                out.write(entry.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) { Log.w("CecBridge", "Cannot write action journal", e); }
        }
    }
    private static void pause() { try { Thread.sleep(1000); } catch (InterruptedException ignored) { } }
    private static void write(String name, String value) throws Exception {
        File temp = new File(DIR, name + ".tmp"), target = new File(DIR, name);
        Files.write(temp.toPath(), value.getBytes(StandardCharsets.UTF_8));
        if (!temp.renameTo(target)) throw new java.io.IOException("Cannot replace " + name);
    }
    private static String command(String... args) throws Exception {
        Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
        ScheduledFuture<?> timeout = TIMEOUTS.schedule(process::destroy, 5, TimeUnit.SECONDS);
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
                if (output.length() > 1024 * 1024) throw new java.io.IOException("Command output too large");
            }
            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) throw new java.io.IOException("Command failed: " + args[0]);
            return output.toString();
        } finally { timeout.cancel(false); process.destroy(); }
    }
}
