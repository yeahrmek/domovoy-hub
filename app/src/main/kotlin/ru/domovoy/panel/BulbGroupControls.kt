package ru.domovoy.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Compact, independently operable lamps; only confirmed readings colour the power state. */
@Composable
internal fun BulbGroupControls(group: BulbGroup, state: BulbPanelState, sharedFailure: Boolean, onToggle: (String) -> Unit) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val labelStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp, lineHeight = 18.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        group.lamps.forEach { lamp ->
            val pending = lamp.id in state.pending
            val commandError = state.commandErrors[lamp.id]
            val status = when {
                pending -> "Ждём подтверждения"
                commandError != null -> "Ошибка команды: $commandError"
                sharedFailure -> "Нет обновления"
                else -> power(lamp.isOn)
            }
            val failing = commandError != null || sharedFailure
            val tint = when {
                failing -> MaterialTheme.colorScheme.error
                lamp.isOn == true -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Column(
                modifier = Modifier.width(MIN_TOUCH * fontScale).heightIn(min = MIN_TOUCH)
                    .clip(MaterialTheme.shapes.small)
                    .toggleable(
                        value = lamp.isOn == true,
                        enabled = !pending && !sharedFailure && lamp.isOn != null,
                        role = Role.Switch,
                        onValueChange = { onToggle(lamp.id) },
                    ).semantics {
                        contentDescription = "Питание лампы: ${lamp.name}, ${lamp.room ?: "Без комнаты"}"
                        stateDescription = status
                    }.alpha(if (pending) 0.5f else 1f).padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    TileDeviceArt(art(lamp), Modifier.size(36.dp))
                }
                Text(
                    when {
                        pending -> "Ждём"
                        commandError != null -> "Ошибка"
                        sharedFailure -> "Нет обновл."
                        lamp.isOn == null -> "Нет данных"
                        lamp.isOn -> "Вкл"
                        else -> "Выкл"
                    },
                    style = labelStyle,
                    color = tint,
                    textAlign = TextAlign.Center,
                )
                Text(lamp.name, style = labelStyle, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
