package io.github.stevexyh.cecstandby;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Boots and checks the device-local shell bridge; no competing CEC or wake actions. */
public final class CecService extends AccessibilityService {
    private static volatile CecService instance;
    private ScheduledExecutorService worker;
    private volatile boolean connected;
    private volatile LocalAdb running;
    private volatile String status = "starting";
    private volatile long checks;
    private AdbKeeper keeper;

    @Override protected void onServiceConnected() {
        if (connected) return;
        connected = true;
        instance = this;
        keeper = new AdbKeeper(this);
        worker = Executors.newSingleThreadScheduledExecutor();
        worker.execute(this::ensureBridge);
    }

    private void ensureBridge() {
        if (!connected) return;
        boolean healthy = false;
        try {
            keeper.check();
            String config = ConfigStore.read(this);
            if (config == null) { status = "not configured; no power actions"; return; }
            String encoded = android.util.Base64.encodeToString(config.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            for (String asset : new String[]{"health.sh", "start-bridge.sh"}) try (InputStream in = getAssets().open(asset)) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = in.read(buffer)) != -1) bytes.write(buffer, 0, count);
                bytes.write(10);
            }
            status = shell("sh -c " + quote(new String(bytes.toByteArray(), StandardCharsets.UTF_8)) +
                    " cec-standby-start " + quote(getApplicationInfo().sourceDir) + " " + quote(ShellBridge.VERSION) + " " + quote(encoded) + " " + (getSystemService(PowerManager.class).isInteractive() ? "1" : "0"));
            checks++;
            healthy = status.startsWith("version=" + ShellBridge.VERSION + " ");
        } catch (Exception e) { status = e.toString(); if (connected) Log.w("CecStandby", "Bridge check failed", e); }
        finally {
            // Xiaomi can restart adbd while going to standby. Restore the bridge before wake,
            // rather than leaving it absent until an interactive-only check runs after wake.
            boolean interactive = getSystemService(PowerManager.class).isInteractive();
            if (connected) try { worker.schedule(this::ensureBridge, healthy && interactive ? 15 : 2, TimeUnit.SECONDS); }
            catch (java.util.concurrent.RejectedExecutionException stopped) { }
        }
    }

    private String shell(String command) throws Exception {
        try (LocalAdb adb = new LocalAdb(this)) {
            running = adb;
            adb.connect(command);
            StringBuilder output = new StringBuilder();
            try {
                while (connected) {
                    output.append(new String(adb.read(), StandardCharsets.UTF_8));
                    if (output.length() > 16384) throw new java.io.IOException("Bridge response too large");
                }
            } catch (java.io.EOFException expected) { }
            return output.toString().trim();
        } finally { running = null; }
    }

    private static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }

    static void testSleep() {
        CecService service = instance;
        if (service != null) service.worker.execute(() -> {
            try {
                service.shell("CLASSPATH=" + ShellBridge.DIR + "/bridge.apk app_process /system/bin io.github.stevexyh.cecstandby.ShellBridge --standby");
            } catch (Exception e) { Log.w("CecStandby", "Test standby failed", e); }
        });
    }

    @Override protected void dump(java.io.FileDescriptor fd, java.io.PrintWriter writer, String[] args) {
        writer.println("Bridge bootstrap connected=" + connected + " checks=" + checks);
        writer.println(status);
        if (keeper != null) writer.println("ADB: " + keeper.status());
    }
    private void stop() {
        connected = false;
        if (instance == this) instance = null;
        if (keeper != null) { keeper.close(); keeper = null; }
        if (worker != null) worker.shutdownNow();
        if (running != null) running.close();
    }
    @Override public boolean onUnbind(Intent intent) { stop(); return super.onUnbind(intent); }
    @Override public void onDestroy() { stop(); super.onDestroy(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
}
