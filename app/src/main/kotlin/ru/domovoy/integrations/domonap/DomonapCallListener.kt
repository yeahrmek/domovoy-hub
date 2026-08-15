package ru.domovoy.integrations.domonap

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Watches Domonap's notifications so the panel knows when the intercom is mid-call.
 *
 * It only ever reads. Nothing here cancels, snoozes or otherwise touches a notification — the call
 * screen is Domonap's own activity and the panel must not get in front of it.
 *
 * Needs notification access, which is a user-granted toggle rather than a manifest permission:
 * Settings → Notifications → Special app access, or for development
 * `adb shell cmd notification allow_listener ru.domovoy/.integrations.domonap.DomonapCallListener`.
 */
class DomonapCallListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        domonapCalls.onPosted(sbn.packageName, sbn.notification.channelId, sbn.key)
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
