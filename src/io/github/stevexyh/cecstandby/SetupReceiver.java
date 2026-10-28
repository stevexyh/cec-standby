package io.github.stevexyh.cecstandby;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;

public final class SetupReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if ("io.github.stevexyh.cecstandby.CONFIG".equals(intent.getAction())) {
            try {
                ConfigStore.save(context, intent.getStringExtra("profile"));
                setResultCode(0); setResultData("Configuration saved; awaiting bridge validation");
            } catch (Exception e) { setResultCode(1); setResultData("Rejected configuration: " + e.getMessage()); return; }
        }
        enable(context);
        if (intent.getBooleanExtra("test_sleep", false)) CecService.testSleep();
    }

    static synchronized void enable(Context context) {
        try {
            String mine = new ComponentName(context, CecService.class).flattenToString();
            String current = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            List<String> services = new ArrayList<>();
            boolean found = false;
            if (current != null) for (String item : current.split(":")) {
                if (item.isEmpty()) continue;
                services.add(item);
                ComponentName component = ComponentName.unflattenFromString(item);
                if (component != null && mine.equals(component.flattenToString())) found = true;
            }
            if (!found) {
                services.add(mine);
                Settings.Secure.putString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, android.text.TextUtils.join(":", services));
            }
            Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
        } catch (Exception e) { Log.e("CecStandby", "Cannot restore service", e); }
    }
}
