package ru.domovoy.integrations.domonap

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Watches call notifications without modifying them or touching audio. Requires user-granted
 * notification access. Never launches a full-screen intent: doing so interrupted a physical call;
 * see docs/domonap.md.
 */
class DomonapCallListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        domonapCalls.onPosted(
            packageName = sbn.packageName,
            channelId = sbn.notification.channelId,
            key = sbn.key,
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        domonapCalls.onRemoved(sbn.key)
    }

    override fun onListenerConnected() {
        domonapCalls.onListenerReconnected()
        // Replay what is up right now: the binding may have been missing while a call started, and
        // removals that happened while unbound were never delivered.
        runCatching { activeNotifications.orEmpty() }
            .getOrDefault(emptyArray())
            .forEach(::onNotificationPosted)
    }

    override fun onListenerDisconnected() {
        // The tablet reboots unattended and the system drops listeners; ask for the binding back
        // rather than waiting for someone to notice the panel stopped seeing calls.
        requestRebind(ComponentName(this, DomonapCallListener::class.java))
    }
}
