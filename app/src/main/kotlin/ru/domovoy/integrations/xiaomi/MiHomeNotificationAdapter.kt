package ru.domovoy.integrations.xiaomi

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification

/** Uses vendor-supplied notification text; no device state is inferred from its wording. */
internal fun miHomeMessage(sbn: StatusBarNotification): MiHomeNotification {
    val notification = sbn.notification
    val extras = notification.extras ?: Bundle.EMPTY
    val expanded = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }
    val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")?.takeIf { it.isNotBlank() }
    return MiHomeNotification(
        key = sbn.key,
        title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
        text = expanded ?: lines ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
        postedAt = sbn.postTime,
        packageName = sbn.packageName,
        isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
    )
}
