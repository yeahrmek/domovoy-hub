package ru.domovoy.integrations.domonap

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import ru.domovoy.integrations.xiaomi.MI_HOME_PACKAGE
import ru.domovoy.integrations.xiaomi.miHomeMessage
import ru.domovoy.integrations.xiaomi.miHomeNotifications

/**
 * Watches call notifications without modifying them or touching audio. Requires user-granted
 * notification access. Never launches a full-screen intent: doing so interrupted a physical call;
 * see docs/domonap.md.
 */
class DomonapCallListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        domonapCalls.onPosted(
            packageName = sbn.packageName,
            channelId = sbn.notification.channelId.orEmpty(),
            key = sbn.key,
        )
        if (sbn.packageName == MI_HOME_PACKAGE) miHomeNotifications.onPosted(miHomeMessage(sbn))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        domonapCalls.onRemoved(sbn.key)
        if (sbn.packageName == MI_HOME_PACKAGE) miHomeNotifications.onRemoved(sbn.key)
    }

    override fun onListenerConnected() {
        // Replay what is up right now: the binding may have been missing while a call started, and
        // removals that happened while unbound were never delivered.
        val snapshot = runCatching { checkNotNull(activeNotifications) }
        snapshot.onSuccess { notifications ->
            domonapCalls.onListenerReconnected()
            notifications.filterNot { it.packageName == MI_HOME_PACKAGE }.forEach(::onNotificationPosted)
            miHomeNotifications.onListenerConnected(notifications.filter { it.packageName == MI_HOME_PACKAGE }.map(::miHomeMessage))
        }.onFailure { miHomeNotifications.onListenerDisconnected() }
    }

    override fun onListenerDisconnected() {
        miHomeNotifications.onListenerDisconnected()
        // The tablet reboots unattended and the system drops listeners; ask for the binding back
        // rather than waiting for someone to notice the panel stopped seeing calls.
        requestRebind(ComponentName(this, DomonapCallListener::class.java))
    }

    override fun onDestroy() {
        miHomeNotifications.onListenerDisconnected()
        super.onDestroy()
    }
}
