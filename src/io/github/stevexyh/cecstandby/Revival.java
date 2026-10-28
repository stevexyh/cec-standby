package io.github.stevexyh.cecstandby;

import android.service.notification.NotificationListenerService;

/** Xiaomi force-stops third-party apps at standby; the system notification binding revives us. */
public final class Revival extends NotificationListenerService {
    @Override public void onCreate() { super.onCreate(); SetupReceiver.enable(this); }
    @Override public void onListenerConnected() { SetupReceiver.enable(this); }
}
