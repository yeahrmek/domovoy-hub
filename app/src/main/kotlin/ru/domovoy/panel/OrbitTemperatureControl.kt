package ru.domovoy.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import ru.domovoy.core.Bounds
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DIAL_START = 135f
private const val DIAL_SWEEP = 270f

/** A vendor-bounded target; dragging previews locally and sends one command on release. */
@Composable
internal fun OrbitTemperatureControl(
    id: String,
    value: Double?,
    bounds: Bounds,
    enabled: Boolean,
    onSetValue: (Double) -> Unit,
    modifier: Modifier = Modifier,
    unit: String = "°C",
) {
    val reported = value?.let { dialSnap(it, bounds) } ?: bounds.min
    var draft by remember(id, bounds) { mutableDoubleStateOf(reported) }
    var lastReported by remember(id, bounds) { mutableStateOf(value) }
    var dragging by remember(id, bounds) { mutableStateOf(false) }
    var adjusted by remember(id, bounds) { mutableStateOf(false) }
    var wasEnabled by remember(id, bounds) { mutableStateOf(enabled) }
    val latestValue by rememberUpdatedState(value)
    val send by rememberUpdatedState(onSetValue)
    val validRange = bounds.min.isFinite() && bounds.max.isFinite() && bounds.max > bounds.min
    val adjustable = enabled && validRange
    val step = bounds.precision.takeIf { it.isFinite() && it > 0.0 }
    SideEffect {
        if (!dragging && (value != lastReported || (!wasEnabled && enabled))) {
            draft = reported
            lastReported = value
            adjusted = false
        }
        wasEnabled = enabled
    }
    val shown = if (value == null && !adjusted) null else draft
    val cold = MaterialTheme.colorScheme.tertiaryFixed
    val warm = MaterialTheme.colorScheme.tertiaryFixedDim
    val outline = MaterialTheme.colorScheme.outlineVariant
    val thumb = MaterialTheme.colorScheme.surface
    val thumbOutline = MaterialTheme.colorScheme.onSurface
    val fraction = if (validRange) ((draft - bounds.min) / (bounds.max - bounds.min)).toFloat().coerceIn(0f, 1f) else 0f
    val steps = if (validRange && step != null) ((bounds.max - bounds.min) / step).roundToInt().minus(1).coerceAtLeast(0) else 0
    val apply = { next: Double ->
        draft = dialSnap(next, bounds)
        adjusted = true
        send(draft)
    }
    Box(
        modifier = modifier.size(210.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Температура"
                stateDescription = shown?.let { "${dialNumber(it)} $unit" } ?: "Нет данных"
                if (validRange) {
                    progressBarRangeInfo = ProgressBarRangeInfo(draft.toFloat(), bounds.min.toFloat()..bounds.max.toFloat(), steps)
                }
                if (adjustable) {
                    setProgress { next ->
                        if (next.isFinite()) {
                            apply(next.toDouble())
                            true
                        } else {
                            false
                        }
                    }
                } else {
                    disabled()
                }
            }
            .pointerInput(id, bounds, adjustable) {
                if (!adjustable) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val diameter = size.width.toFloat()
                    // The centre remains available to its parent card; the wide arc accepts touch.
                    if (!onDialArc(down.position, diameter)) return@awaitEachGesture
                    down.consume()
                    dragging = true
                    adjusted = true
                    draft = dialValue(down.position, diameter, bounds)
                    var committed = false
                    try {
                        val released = drag(down.id) { change ->
                            draft = dialValue(change.position, diameter, bounds)
                            change.consume()
                        }
                        if (released) {
                            dragging = false
                            send(draft)
                            committed = true
                        }
                    } finally {
                        dragging = false
                        if (!committed) {
                            draft = latestValue?.let { dialSnap(it, bounds) } ?: bounds.min
                            adjusted = false
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val stroke = 4.dp.toPx()
            val diameter = size.minDimension - stroke
            val inset = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(diameter, diameter)
            drawArc(outline, DIAL_START, DIAL_SWEEP, false, inset, arcSize, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
            rotate(DIAL_START) {
                drawArc(
                    brush = Brush.sweepGradient(0f to cold, 0.75f to warm, 0.80f to warm, 0.97f to cold, 1f to cold, center = center),
                    startAngle = 0f,
                    sweepAngle = DIAL_SWEEP,
                    useCenter = false,
                    topLeft = inset,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            if (shown != null) {
                val angle = (DIAL_START + DIAL_SWEEP * fraction) * PI / 180
                val radius = diameter / 2
                val position = center + Offset(cos(angle).toFloat() * radius, sin(angle).toFloat() * radius)
                drawCircle(thumb, radius = 8.dp.toPx(), center = position)
                drawCircle(thumbOutline, radius = 8.dp.toPx(), center = position, style = Stroke(2.dp.toPx()))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Задано", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                shown?.let { "${dialNumber(it)} $unit" } ?: "Нет данных",
                style = if (shown == null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 30.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(dialNumber(bounds.min), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(dialNumber(bounds.max), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun dialSnap(value: Double, bounds: Bounds): Double = bounds.snap(value).coerceIn(bounds.min, bounds.max)

private fun dialNumber(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

private fun onDialArc(position: Offset, diameter: Float): Boolean {
    val distance = hypot(position.x - diameter / 2, position.y - diameter / 2)
    return distance >= diameter * 0.18f && distance <= diameter * 0.52f
}

/** The bottom gap picks its closest end, so crossing it cannot invent an out-of-range target. */
private fun dialValue(position: Offset, diameter: Float, bounds: Bounds): Double {
    val angle = atan2((position.y - diameter / 2).toDouble(), (position.x - diameter / 2).toDouble()) * 180 / PI
    val relative = (angle - DIAL_START + 360) % 360
    val swept = if (relative <= DIAL_SWEEP) {
        relative
    } else if (relative < 315) {
        DIAL_SWEEP.toDouble()
    } else {
        0.0
    }
    return dialSnap(bounds.min + (bounds.max - bounds.min) * swept / DIAL_SWEEP, bounds)
}
