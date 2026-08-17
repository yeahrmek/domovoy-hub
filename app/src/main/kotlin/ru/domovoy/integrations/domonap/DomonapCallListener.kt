package ru.domovoy.integrations.domonap

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Watches Domonap's notifications so the panel knows when the intercom is mid-call.
 *
 * It only ever reads. Nothing here cancels, snoozes or otherwise touches a notification, and
 * nothing here answers, declines or opens the door.
 *
 * **It does not put the call screen up, and that is a retreat, not an oversight.** On this tablet
 * the platform does not launch `IncomingDomofonCallActivity` at all — two calls captured on
 * 2026-08-16, one awake and one over the keyguard, and it started neither time — so the panel was
 * made to send the record's own `fullScreenIntent` instead. Watched on the tablet at 22:41 the same
 * evening, that worked and then killed the call: the screen came up over the keyguard, and 416 ms
 * later Telecom logged `SET_DISCONNECTED` with cause `LOCAL`, which neither baseline call did.
 * Nobody had touched the tablet. The likely reason is timing — the notification runs 175–886 ms
 * ahead of the Telecom call, so the screen went up 44 ms *before* the call was registered — but
 * that is untested, and hanging up on whoever is at the door is not a thing to leave running on a
 * wall while it is being tested. [DomonapCalls] still works out when a call screen *would* be
 * shown; nothing acts on it. See docs/domonap.md.
 *
 * Needs notification access, which is a user-granted toggle rather than a manifest permission:
 * Settings → Notifications → Special app access, or for development
 * `adb shell cmd notification allow_listener ru.domovoy/.integrations.domonap.DomonapCallListener`.
 */
class DomonapCallListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        domonapCalls.onPosted(
            packageName = sbn.packageName,
            channelId = sbn.notification.channelId,
            key = sbn.key,
            hasCallScreen = sbn.notification.fullScreenIntent != null,
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
