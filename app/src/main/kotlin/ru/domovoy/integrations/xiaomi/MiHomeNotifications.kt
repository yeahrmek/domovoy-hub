package ru.domovoy.integrations.xiaomi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val MI_HOME_PACKAGE = "com.xiaomi.smarthome"

/** Mi Home's own text, not a claim about the current state of a particular device. */
data class MiHomeNotification(
    val key: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val packageName: String = MI_HOME_PACKAGE,
    val isGroupSummary: Boolean = false,
)

enum class MiHomeNotificationStatus {
    AccessRequired,
    Connecting,
    Connected,
    Disconnected,
}

/** Tracks active messages from Mi Home without changing or dismissing the original notifications. */
class MiHomeNotifications(private val maxNotifications: Int = 5) {
    private val mutableNotifications = MutableStateFlow<List<MiHomeNotification>>(emptyList())
    val notifications: StateFlow<List<MiHomeNotification>> = mutableNotifications.asStateFlow()

    private val mutableStatus = MutableStateFlow(MiHomeNotificationStatus.AccessRequired)
    val status: StateFlow<MiHomeNotificationStatus> = mutableStatus.asStateFlow()

    init {
        require(maxNotifications > 0)
    }

    @Synchronized
    fun onPosted(notification: MiHomeNotification) {
        if (notification.packageName != MI_HOME_PACKAGE) return
        val visible = notification.visibleMessage()
        if (visible == null) {
            onRemoved(notification.key)
            return
        }
        mutableNotifications.value = newestMessages(mutableNotifications.value.filterNot { it.key == visible.key } + visible)
    }

    /** Removal means that Android no longer shows this message, not that a vacuum fault is fixed. */
    @Synchronized
    fun onRemoved(key: String) {
        mutableNotifications.value = mutableNotifications.value.filterNot { it.key == key }
    }

    @Synchronized
    fun onAccessChanged(granted: Boolean) {
        if (!granted) {
            mutableNotifications.value = emptyList()
            mutableStatus.value = MiHomeNotificationStatus.AccessRequired
        } else if (mutableStatus.value == MiHomeNotificationStatus.AccessRequired) {
            mutableStatus.value = MiHomeNotificationStatus.Connecting
        }
    }

    /** A complete successful snapshot replaces messages missed while the listener was disconnected. */
    @Synchronized
    fun onListenerConnected(activeSnapshot: List<MiHomeNotification>) {
        mutableNotifications.value = newestMessages(activeSnapshot.mapNotNull { it.visibleMessage() }.distinctBy { it.key })
        mutableStatus.value = MiHomeNotificationStatus.Connected
    }

    /** Retain the last messages, but let the interface identify them as no longer live. */
    @Synchronized
    fun onListenerDisconnected() {
        if (mutableStatus.value != MiHomeNotificationStatus.AccessRequired) {
            mutableStatus.value = MiHomeNotificationStatus.Disconnected
        }
    }

    private fun newestMessages(messages: List<MiHomeNotification>): List<MiHomeNotification> = messages.sortedByDescending { it.postedAt }.take(maxNotifications)

    private fun MiHomeNotification.visibleMessage(): MiHomeNotification? {
        if (packageName != MI_HOME_PACKAGE || isGroupSummary || (title.isBlank() && text.isBlank())) return null
        return copy(title = title.trim(), text = text.trim())
    }
}

/** Shared by the existing notification listener and panel activity. */
val miHomeNotifications = MiHomeNotifications()
