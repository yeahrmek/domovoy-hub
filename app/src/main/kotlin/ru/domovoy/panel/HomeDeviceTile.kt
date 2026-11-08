package ru.domovoy.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import ru.domovoy.R
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
    commandError: String? = null,
    openLabel: String = "Настроить",
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    horizontalValue: Boolean = false,
    showValue: Boolean = true,
    expanded: Boolean? = null,
    toggle: @Composable () -> Unit = {},
    primaryControl: (@Composable () -> Unit)? = null,
    controls: @Composable () -> Unit = {},
) {
    val stale = notUpdating(null, polledAt, now, interval)
    val failing = commandError != null || paint.groupFailing || stale || paint.mood == TileMood.Failing
    val feedback = commandError ?: anatomy.detail
    val failureReason = if (failing) feedback ?: "Обновления не поступают" else null
    // A summary with individual device actions expands from its header.
    val headerOpensOnly = expanded != null && !showValue
    val openAction = Modifier.clickable(role = Role.Button, onClick = onOpen).semantics {
        contentDescription = "$openLabel: ${anatomy.name}, ${anatomy.room ?: "Без комнаты"}"
        stateDescription = listOfNotNull(
            expanded?.let { if (it) "Развёрнуто" else "Свёрнуто" },
            failureReason ?: anatomy.status.takeIf { cardText(it) != it },
        ).joinToString(". ")
    }
    Card(
        modifier = modifier.fillMaxWidth().padding(6.dp).then(if (headerOpensOnly) Modifier else openAction),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (paint.mood == TileMood.Failing) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, if (failing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
    ) {
        BoxWithConstraints(Modifier.padding(16.dp)) {
            val effectiveWidth = maxWidth / LocalDensity.current.fontScale
            val inlineValue = horizontalValue && effectiveWidth >= 500.dp
            val heading: @Composable () -> Unit = {
                Row(Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH).then(if (headerOpensOnly) openAction else Modifier), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TileDeviceArt(anatomy.art, Modifier.size(48.dp).testTag("device-art"))
                    Column(Modifier.weight(1f)) {
                        Text(anatomy.name, style = MaterialTheme.typography.titleMedium)
                        Text(anatomy.room ?: "Без комнаты", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    toggle()
                    if (inlineValue && showValue) PromotedValue(anatomy.promoted, color = valueColor)
                    if (expanded != null && (!showValue || anatomy.promoted == null || inlineValue || primaryControl != null)) {
                        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(24.dp).rotate(if (expanded) 270f else 90f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (primaryControl != null && effectiveWidth >= 500.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(1f)) { heading() }
                        primaryControl()
                    }
                } else {
                    heading()
                    if (primaryControl != null) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { primaryControl() }
                    } else if (showValue && !inlineValue && anatomy.promoted != null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { PromotedValue(anatomy.promoted, color = valueColor) }
                            if (expanded != null) {
                                Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, modifier = Modifier.size(24.dp).rotate(if (expanded) 270f else 90f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                controls()
                val message = if (pending) {
                    "Ждём подтверждения…"
                } else if (failing) {
                    ""
                } else {
                    cardText(anatomy.status)
                }
                if (message.isNotBlank()) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = if (pending) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
                    )
                }
                if ((failing || feedback != null) && !(failing && showValue && anatomy.promoted == "—")) {
                    Text(
                        if (failing) "—" else cardText(feedback.orEmpty()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (commandError != null || failing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}
