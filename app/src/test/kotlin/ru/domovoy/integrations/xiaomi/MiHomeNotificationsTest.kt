package ru.domovoy.integrations.xiaomi

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MiHomeNotificationsTest {
    private fun notification(
        key: String = "vacuum-alert",
        title: String = "Пылесос",
        text: String = "Проверьте бак с грязной водой",
        postedAt: Long = 100L,
        packageName: String = MI_HOME_PACKAGE,
        isGroupSummary: Boolean = false,
    ) = MiHomeNotification(key, title, text, postedAt, packageName, isGroupSummary)

    @Test
    fun `only Mi Home notifications with visible content appear`() {
        val notifications = MiHomeNotifications()

        notifications.onPosted(notification(packageName = "com.example.other"))
        notifications.onPosted(notification(isGroupSummary = true))
        notifications.onPosted(notification(title = " ", text = "\n"))
        assertEquals(emptyList(), notifications.notifications.value)

        notifications.onPosted(notification())
        assertEquals(listOf(notification()), notifications.notifications.value)
    }

    @Test
    fun `a title or body alone is enough and surrounding whitespace is removed`() {
        val notifications = MiHomeNotifications()

        notifications.onPosted(notification(key = "title", title = "  Mi Home  ", text = "", postedAt = 100L))
        notifications.onPosted(notification(key = "body", title = "", text = "  Уборка завершена\n", postedAt = 200L))

        assertEquals(
            listOf(
                notification(key = "body", title = "", text = "Уборка завершена", postedAt = 200L),
                notification(key = "title", title = "Mi Home", text = "", postedAt = 100L),
            ),
            notifications.notifications.value,
        )
    }

    @Test
    fun `reposting the same notification updates its content without duplication`() {
        val notifications = MiHomeNotifications()
        notifications.onPosted(notification())

        notifications.onPosted(notification(text = "Робот застрял", postedAt = 200L))

        assertEquals(listOf(notification(text = "Робот застрял", postedAt = 200L)), notifications.notifications.value)
    }

    @Test
    fun `an update with no visible content does not retain the previous message`() {
        val notifications = MiHomeNotifications()
        notifications.onPosted(notification())

        notifications.onPosted(notification(title = "", text = ""))

        assertEquals(emptyList(), notifications.notifications.value)
    }

    @Test
    fun `removing an Android notification removes the message without inventing vacuum state`() {
        val notifications = MiHomeNotifications()
        notifications.onPosted(notification())
        notifications.onPosted(notification(key = "humidifier", title = "Увлажнитель", text = "Нет воды"))

        notifications.onRemoved("vacuum-alert")
        notifications.onRemoved("unrelated")

        assertEquals(
            listOf(notification(key = "humidifier", title = "Увлажнитель", text = "Нет воды")),
            notifications.notifications.value,
        )
    }

    @Test
    fun `only the newest messages are retained in newest first order`() {
        val notifications = MiHomeNotifications(maxNotifications = 2)
        notifications.onPosted(notification(key = "middle", postedAt = 200L))
        notifications.onPosted(notification(key = "newest", postedAt = 300L))
        notifications.onPosted(notification(key = "oldest", postedAt = 100L))

        assertEquals(listOf("newest", "middle"), notifications.notifications.value.map { it.key })
    }

    @Test
    fun `reconnection replaces missed notifications without emitting a temporary empty list`() = runTest {
        val notifications = MiHomeNotifications()
        notifications.onPosted(notification(key = "stale"))

        notifications.notifications.test {
            assertEquals(listOf("stale"), awaitItem().map { it.key })
            notifications.onListenerConnected(
                listOf(
                    notification(key = "active"),
                    notification(key = "unrelated", packageName = "com.example.other"),
                    notification(key = "summary", isGroupSummary = true),
                ),
            )
            assertEquals(listOf("active"), awaitItem().map { it.key })
            expectNoEvents()
        }
        assertEquals(MiHomeNotificationStatus.Connected, notifications.status.value)
    }

    @Test
    fun `an empty successful snapshot clears messages that were removed while disconnected`() {
        val notifications = MiHomeNotifications()
        notifications.onPosted(notification())

        notifications.onListenerConnected(emptyList())

        assertEquals(emptyList(), notifications.notifications.value)
        assertEquals(MiHomeNotificationStatus.Connected, notifications.status.value)
    }

    @Test
    fun `disconnection keeps the last messages and exposes the loss of live access`() {
        val notifications = MiHomeNotifications()
        notifications.onListenerConnected(listOf(notification()))

        notifications.onListenerDisconnected()
        notifications.onAccessChanged(granted = true)

        assertEquals(listOf(notification()), notifications.notifications.value)
        assertEquals(MiHomeNotificationStatus.Disconnected, notifications.status.value)
    }

    @Test
    fun `revoking notification access clears private messages and requests access again`() {
        val notifications = MiHomeNotifications()
        assertEquals(MiHomeNotificationStatus.AccessRequired, notifications.status.value)
        notifications.onAccessChanged(granted = true)
        assertEquals(MiHomeNotificationStatus.Connecting, notifications.status.value)
        notifications.onListenerConnected(listOf(notification()))

        notifications.onAccessChanged(granted = false)

        assertEquals(emptyList(), notifications.notifications.value)
        assertEquals(MiHomeNotificationStatus.AccessRequired, notifications.status.value)
    }

    @Test
    fun `a repeated notification permission check keeps a connected listener connected`() {
        val notifications = MiHomeNotifications()
        notifications.onListenerConnected(emptyList())

        notifications.onAccessChanged(granted = true)

        assertEquals(MiHomeNotificationStatus.Connected, notifications.status.value)
    }
}
