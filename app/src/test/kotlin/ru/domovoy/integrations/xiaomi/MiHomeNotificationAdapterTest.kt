package ru.domovoy.integrations.xiaomi

import android.app.Notification
import android.os.Bundle
import android.os.Process
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import ru.domovoy.integrations.domonap.CallState
import ru.domovoy.integrations.domonap.DomonapCallListener
import ru.domovoy.integrations.domonap.domonapCalls
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MiHomeNotificationAdapterTest {
    @Test
    @Config(shadows = [UnavailableNotificationSnapshot::class])
    fun `an unavailable reconnect snapshot preserves messages and reports lost connection`() {
        miHomeNotifications.onListenerConnected(listOf(MiHomeNotification("old", "Пылесос", "Застрял", 1000)))
        DomonapCallListener().onListenerConnected()
        assertEquals("Застрял", miHomeNotifications.notifications.value.single().text)
        assertEquals(MiHomeNotificationStatus.Disconnected, miHomeNotifications.status.value)
    }

    @Implements(NotificationListenerService::class)
    class UnavailableNotificationSnapshot {
        @Implementation
        fun getActiveNotifications(): Array<StatusBarNotification>? = null
    }

    @Test
    fun `Mi Home delivery never interrupts the panel as an intercom call`() {
        domonapCalls.onListenerReconnected()
        miHomeNotifications.onListenerConnected(emptyList())
        val notification = Notification().apply {
            extras = Bundle().apply { putCharSequence(Notification.EXTRA_TEXT, "Застрял") }
        }
        val sbn = StatusBarNotification(MI_HOME_PACKAGE, MI_HOME_PACKAGE, 2, "test", 10001, 0, 0, notification, Process.myUserHandle(), 1234)
        val listener = DomonapCallListener()
        listener.onNotificationPosted(sbn)
        assertEquals("Застрял", miHomeNotifications.notifications.value.single().text)
        assertEquals(CallState.Idle, domonapCalls.state.value)
        listener.onNotificationRemoved(sbn)
        assertEquals(emptyList(), miHomeNotifications.notifications.value)
    }

    @Test
    fun `expanded vendor text and timestamp reach the panel`() {
        val notification = Notification().apply {
            extras = Bundle().apply {
                putCharSequence(Notification.EXTRA_TITLE, "Робот-пылесос")
                putCharSequence(Notification.EXTRA_TEXT, "Короткий текст")
                putCharSequence(Notification.EXTRA_BIG_TEXT, "Проверьте бак для грязной воды")
            }
        }
        val sbn = StatusBarNotification(MI_HOME_PACKAGE, MI_HOME_PACKAGE, 1, "test", 10001, 0, 0, notification, Process.myUserHandle(), 1234)
        val message = miHomeMessage(sbn)
        assertEquals("Робот-пылесос", message.title)
        assertEquals("Проверьте бак для грязной воды", message.text)
        assertEquals(1234, message.postedAt)
        assertEquals(MI_HOME_PACKAGE, message.packageName)
    }

    @Test
    fun `inbox lines are preserved when no expanded text exists`() {
        val notification = Notification().apply {
            extras = Bundle().apply { putCharSequenceArray(Notification.EXTRA_TEXT_LINES, arrayOf("Застрял", "Проверьте колёса")) }
            flags = Notification.FLAG_GROUP_SUMMARY
        }
        val sbn = StatusBarNotification(MI_HOME_PACKAGE, MI_HOME_PACKAGE, 1, "test", 10001, 0, 0, notification, Process.myUserHandle(), 1234)
        assertEquals("Застрял\nПроверьте колёса", miHomeMessage(sbn).text)
        assertEquals(true, miHomeMessage(sbn).isGroupSummary)
    }
}
