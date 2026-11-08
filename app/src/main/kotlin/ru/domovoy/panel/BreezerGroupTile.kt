package ru.domovoy.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.domovoy.R
import java.time.Instant
import kotlin.time.Duration

/** Individual failures stay on their device; only a stopped shared poll marks the whole group. */
@Composable
internal fun BreezerGroupTile(
    tiles: List<RecuperatorTileState>,
    state: RecuperatorPanelState,
    now: Instant,
    interval: Duration,
    open: Boolean,
    onOpen: () -> Unit,
    onToggle: (String) -> Unit,
    onOpenDevice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sharedFailure = notUpdating(state.error, state.lastPolledAt, now, interval)
    val ready = tiles.filter { it.isOn != null && it.error == null && it.online != false }
    val allOn = ready.isNotEmpty() && ready.all { it.isOn == true }
    val pending = tiles.any { it.id in state.pending }
    val paint = paint(allOn, ownError = null, groupError = state.error)
    HomeDeviceTile(
        anatomy = TileAnatomy(
            art = art(tiles),
            action = null,
            name = "Бризеры",
            promoted = null,
            status = "",
            detail = state.error,
            room = tiles.mapNotNull { it.room }.distinct().singleOrNull() ?: "Все комнаты",
        ),
        paint = paint, polledAt = state.lastPolledAt, now = now, interval = interval,
        pending = pending, modifier = modifier, showValue = false,
        onOpen = onOpen, openLabel = if (open) "Свернуть" else "Показать бризеры", expanded = open,
        toggle = {
            TilePowerButton(
                "Бризеры",
                null,
                allOn,
                paint.mood,
                onToggle = {
                    ready.filter { it.isOn != !allOn && it.id !in state.pending }
                        .forEach { onToggle(it.id) }
                },
                enabled = !pending && !sharedFailure && ready.isNotEmpty(),
            )
        },
        controls = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tiles.forEach { tile ->
                    BreezerStatusIcon(tile, state, sharedFailure, onOpen = { onOpenDevice(tile.id) })
                }
            }
        },
    )
}

@Composable
private fun BreezerStatusIcon(tile: RecuperatorTileState, state: RecuperatorPanelState, sharedFailure: Boolean, onOpen: () -> Unit) {
    val status = when {
        tile.id in state.pending -> "Ждём подтверждения"
        tile.id in state.commandErrors -> "Ошибка команды"
        tile.error != null || tile.online == false -> "Нет связи"
        sharedFailure -> "Нет обновления"
        tile.isOn == null -> "Нет данных"
        tile.isOn -> "Включён"
        else -> "Выключен"
    }
    val unavailable = status in listOf("Нет связи", "Нет обновления", "Ошибка команды")
    val tint = when {
        unavailable -> MaterialTheme.colorScheme.error
        status == "Включён" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val labelStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp, lineHeight = 18.sp)
    Column(
        modifier = Modifier.width(MIN_TOUCH * fontScale).heightIn(min = MIN_TOUCH)
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics {
                contentDescription = "Показать бризер: ${tile.name}, ${tile.room ?: "Без комнаты"}"
                stateDescription = status
            }.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            TileDeviceArt(art(tile), Modifier.size(36.dp))
            if (unavailable) {
                Icon(
                    painterResource(if (status == "Ошибка команды") R.drawable.ic_power_settings_new else R.drawable.ic_wifi_off),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.align(Alignment.TopEnd).size(16.dp),
                )
            } else if (status == "Включён") {
                Box(Modifier.align(Alignment.TopEnd).size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            }
        }
        Text(
            when (status) {
                "Включён" -> "Вкл"
                "Выключен" -> "Выкл"
                "Ждём подтверждения" -> "Ждём"
                "Ошибка команды" -> "Ошибка"
                "Нет обновления" -> "Нет обновл."
                else -> status
            },
            style = labelStyle,
            color = tint,
            textAlign = TextAlign.Center,
            modifier = Modifier.heightIn(min = 36.dp * fontScale),
        )
        Text(
            when (tile.room) {
                "Маленькая детская" -> "Мал. детская"
                null -> "Без комнаты"
                else -> tile.room
            },
            style = labelStyle,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
