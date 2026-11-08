package ru.domovoy.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.domovoy.R
import ru.domovoy.integrations.xiaomi.MiHomeNotification
import ru.domovoy.integrations.xiaomi.MiHomeNotificationStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Active Android notifications, labelled by their source rather than attributed to the vacuum. */
@Composable
fun MiHomeMessages(
    messages: List<MiHomeNotification>,
    status: MiHomeNotificationStatus,
) {
    if (messages.isEmpty() && status == MiHomeNotificationStatus.Connected) return
    var expanded by remember { mutableStateOf(false) }
    val connectionMessage = when (status) {
        MiHomeNotificationStatus.AccessRequired -> "Mi Home: разрешите панели доступ к уведомлениям в настройках Android."
        MiHomeNotificationStatus.Connecting -> "Mi Home: подключаем уведомления…"
        MiHomeNotificationStatus.Disconnected -> "Связь с уведомлениями потеряна. Сообщения могут быть неактуальны."
        MiHomeNotificationStatus.Connected -> null
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = if (expanded) 360.dp else 240.dp)
                .verticalScroll(rememberScrollState())
                .sizeIn(minHeight = 64.dp)
                .clickable(role = Role.Button, onClickLabel = if (expanded) "Свернуть уведомления" else "Развернуть уведомления") { expanded = !expanded }
                .padding(16.dp)
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    stateDescription = if (expanded) "Уведомления развёрнуты" else "Уведомления свёрнуты"
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isNotEmpty()) {
                val count = messages.size
                val label = when (count) {
                    1 -> "1 сообщение"
                    in 2..4 -> "$count сообщения"
                    else -> "$count сообщений"
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mi Home · $label", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Icon(painterResource(if (expanded) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down), contentDescription = null, modifier = Modifier.size(24.dp))
                }
            }
            connectionMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            (if (expanded) messages else messages.take(1)).forEachIndexed { index, message ->
                if (index > 0) HorizontalDivider()
                if (message.title.isNotBlank()) Text(message.title, style = MaterialTheme.typography.titleSmall)
                if (message.text.isNotBlank()) {
                    Text(message.text, style = MaterialTheme.typography.bodyMedium, maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    Instant.ofEpochMilli(message.postedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM HH:mm")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
