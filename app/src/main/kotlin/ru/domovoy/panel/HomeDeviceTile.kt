package ru.domovoy.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.domovoy.R
import ru.domovoy.core.Reading
import java.time.Instant
import kotlin.time.Duration

/** Home presents readings and real controls together; the card still opens all device details. */
@Composable
internal fun HomeDeviceTile(
    anatomy: TileAnatomy,
    paint: TilePaint,
    polledAt: Instant?,
    now: Instant,
    interval: Duration,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    pending: Boolean = false,
    reportedAt: Reading? = null,
    commandError: String? = null,
    openLabel: String = "Настроить",
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    horizontalValue: Boolean = false,
    toggle: @Composable () -> Unit = {},
    primaryControl: (@Composable () -> Unit)? = null,
    controls: @Composable () -> Unit = {},
) {
    val stale = notUpdating(null, polledAt, now, interval)
    val failing = paint.groupFailing || stale || paint.mood == TileMood.Failing
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().padding(6.dp).heightIn(min = 216.dp)
            .semantics { contentDescription = "$openLabel: ${anatomy.name}, ${anatomy.room ?: "Без комнаты"}" },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (paint.mood == TileMood.Failing) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, if (failing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
    ) {
        val heading: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(anatomy.name, style = MaterialTheme.typography.titleMedium)
                    Text(anatomy.room ?: "Без комнаты", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                toggle()
            }
        }
        val status: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (pending) "Ждём подтверждения…" else anatomy.status,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).then(if (pending) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
                )
                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            val feedback = commandError ?: anatomy.detail
            if (feedback != null) {
                Text(
                    feedback,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (commandError != null || failing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            reportedAt?.takeIf { isHistory(it, now) }?.let { reading ->
                Text("Значение: ${sheetAge(reading, now)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (stale) "Обновления не поступают" else "Связь: ${sheetAge(Reading.At(checkNotNull(polledAt)), now).lowercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = if (stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (primaryControl == null) {
                if (horizontalValue) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(1f)) { heading() }
                        PromotedValue(anatomy.promoted, color = valueColor)
                    }
                } else {
                    heading()
                    PromotedValue(anatomy.promoted, color = valueColor)
                }
                controls()
                status()
            } else {
                if (LocalWindowInfo.current.containerSize.width / LocalDensity.current.density >= 560) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            heading()
                            status()
                        }
                        primaryControl()
                    }
                } else {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        heading()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { primaryControl() }
                        status()
                    }
                }
                controls()
            }
        }
    }
}
