package ru.domovoy.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.domovoy.R
import ru.domovoy.core.Bounds

/** Retains a local draft while dragging; applies new readings when the gesture is over. */
@Composable
internal fun LevelControl(
    id: String,
    value: Double?,
    bounds: Bounds,
    onSetValue: (Double) -> Unit,
    modifier: Modifier = Modifier,
    stepButtons: Boolean = false,
    enabled: Boolean = true,
    label: String = "Уровень",
    unit: String = "%",
) {
    val reported = (value ?: bounds.min).toFloat()
    var dragged by remember(id, bounds) { mutableFloatStateOf(reported) }
    var lastReported by remember(id, bounds) { mutableFloatStateOf(reported) }
    var dragging by remember(id, bounds) { mutableStateOf(false) }
    var wasEnabled by remember(id, bounds) { mutableStateOf(enabled) }
    SideEffect {
        if (!dragging && (reported != lastReported || (!wasEnabled && enabled))) {
            dragged = reported
            lastReported = reported
        }
        wasEnabled = enabled
    }
    val slider: @Composable (Modifier) -> Unit = { sliderModifier ->
        SlimSlider(
            value = dragged,
            onValueChange = {
                dragging = true
                dragged = it
            },
            valueRange = bounds.min.toFloat()..bounds.max.toFloat(),
            onValueChangeFinished = {
                dragging = false
                onSetValue(dragged.toDouble())
            },
            modifier = sliderModifier.semantics { contentDescription = label },
            enabled = enabled,
        )
    }
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val valueText = if (dragging) {
                "${levelNumber(bounds.snap(dragged.toDouble()))} $unit"
            } else {
                "${levelNumber(bounds.min)}–${levelNumber(bounds.max)} $unit"
            }
            Text(valueText, style = MaterialTheme.typography.labelSmall)
        }
        if (stepButtons) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton(R.drawable.ic_remove, "Уменьшить", enabled) {
                    dragged = bounds.snap(dragged - bounds.precision).toFloat()
                    onSetValue(dragged.toDouble())
                }
                slider(Modifier.weight(1f))
                StepButton(R.drawable.ic_add, "Увеличить", enabled) {
                    dragged = bounds.snap(dragged + bounds.precision).toFloat()
                    onSetValue(dragged.toDouble())
                }
            }
        } else {
            slider(Modifier.fillMaxWidth())
        }
    }
}

private fun levelNumber(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

@Composable
private fun StepButton(
    @androidx.annotation.DrawableRes glyph: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.touchable()) {
        Icon(painter = painterResource(glyph), contentDescription = label)
    }
}

/** The visible track is 8 dp; its touch area is 64 dp. */
private val TRACK_HEIGHT = 8.dp

/** The unfilled track is a low-emphasis neutral composited over the tile’s current surface. */
private const val REST_OF_TRACK_ALPHA = 0.24f

/**
 * A slim track and Material thumb inside a 64 dp touch area.
 * Material [Slider] retains native drag behaviour, value semantics, and accessibility.
 */
// The slot API and coerced fraction require a local experimental opt-in.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SlimSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val filled = tileAccent()
    val rest = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = REST_OF_TRACK_ALPHA)
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        modifier = modifier.height(MIN_TOUCH),
        enabled = enabled,
        track = { state ->
            Box(
                modifier = Modifier.fillMaxWidth().height(MIN_TOUCH),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier =
                    Modifier.fillMaxWidth().height(TRACK_HEIGHT).clip(CircleShape).background(rest),
                ) {
                    // Material clamps the displayed fraction even when a vendor reading exceeds its bounds.
                    Box(
                        modifier =
                        Modifier.fillMaxWidth(state.coercedValueAsFraction).fillMaxHeight()
                            .background(filled),
                    )
                }
            }
        },
    )
}
