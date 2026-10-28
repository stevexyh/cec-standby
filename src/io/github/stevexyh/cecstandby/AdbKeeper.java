package io.github.stevexyh.cecstandby;

import android.content.Context;
import android.database.ContentObserver;
import android.os.*;
import android.provider.Settings;
import android.util.Log;

/** Restores only the explicitly opted-in global toggle, without depending on shell access. */
final class AdbKeeper {
    private final Context context;
    private final RecoveryPolicy policy=new RecoveryPolicy();
    private final ContentObserver observer;
    private volatile String status="not configured";
    AdbKeeper(Context context) {
        this.context=context;
        observer=new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean self){check();}
        };
        context.getContentResolver().registerContentObserver(Settings.Global.getUriFor(Settings.Global.ADB_ENABLED),false,observer);
    }
    synchronized void check() {
        try {
            String raw=ConfigStore.read(context);
            if(raw==null){status="not configured";return;}
            Profile p=ConfigStore.parse(raw);
            boolean match=p.matches(Build.MANUFACTURER,Build.MODEL,Build.VERSION.SDK_INT,Build.VERSION.INCREMENTAL);
            boolean disabled=Settings.Global.getInt(context.getContentResolver(),Settings.Global.ADB_ENABLED,0)==0;
            if(policy.shouldRestore(p.keepAdb&&p.enabled&&match,disabled,SystemClock.elapsedRealtime())) {
                if(!Settings.Global.putInt(context.getContentResolver(),Settings.Global.ADB_ENABLED,1))throw new IllegalStateException("ADB setting rejected");
                status="restored disabled ADB";
                Log.i("CecStandby",status);
            } else status=!p.keepAdb?"opted out":!match||!p.enabled?"inactive profile":disabled?"restore rate limited":"ADB enabled";
        }catch(Exception e){status=e.toString();Log.w("CecStandby","ADB keep check failed",e);}
    }
    String status(){return status;}
    void close(){context.getContentResolver().unregisterContentObserver(observer);}
}
