package ru.domovoy.panel

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toOffset
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest

private class CardLanding(val origin: Offset) {
    val progress = Animatable(0f)
}

/** Velocity grows gently through the edge zone and is bounded outside the viewport. */
internal fun cardDragScrollVelocity(pointerY: Float, viewport: Rect, edge: Float): Float {
    if (edge <= 0f || viewport.height <= 0f) return 0f
    val proximity = when {
        pointerY < viewport.top + edge -> -((viewport.top + edge - pointerY) / edge).coerceIn(0f, 1f)
        pointerY > viewport.bottom - edge -> ((pointerY - viewport.bottom + edge) / edge).coerceIn(0f, 1f)
        else -> 0f
    }
    return proximity * kotlin.math.abs(proximity) * edge * 8f
}

/** Hold any card to move it; device taps and controls retain their own gestures. */
@Composable
internal fun EditablePanelCard(
    cardKey: String,
    label: String,
    favourite: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onFavourite: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDrop: () -> Unit,
    dragBounds: MutableMap<String, Rect>,
    modifier: Modifier = Modifier,
    onDragPosition: (Offset?) -> Unit = {},
    onDragCancel: () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    var grabOffset by remember(cardKey) { mutableStateOf(Offset.Zero) }
    var dragging by remember(cardKey) { mutableStateOf(false) }
    var cardOrigin by remember(cardKey) { mutableStateOf(Offset.Zero) }
    var pointer by remember(cardKey) { mutableStateOf(Offset.Zero) }
    var placement by remember(cardKey) { mutableStateOf<IntOffset?>(null) }
    var landing by remember(cardKey) { mutableStateOf<CardLanding?>(null) }
    val animatedPlacement = placement?.let {
        animateIntOffsetAsState(it, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f), label = "card placement")
    }
    LaunchedEffect(cardKey) {
        snapshotFlow { landing }.collectLatest { motion ->
            if (motion == null) return@collectLatest
            motion.progress.animateTo(1f, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f))
            if (landing === motion) landing = null
        }
    }
    val drop by rememberUpdatedState(onDrop)
    val cancel by rememberUpdatedState(onDragCancel)
    val dragPosition by rememberUpdatedState(onDragPosition)
    val haptics = LocalHapticFeedback.current
    val pinnable = LocalPinnableContainer.current
    val slotColor = MaterialTheme.colorScheme.primary
    val slotFill = MaterialTheme.colorScheme.primaryContainer
    // Scrolling must not dispose the source item and its active pointer handler.
    LaunchedEffect(cardKey, pinnable) {
        snapshotFlow { dragging || landing != null }.collectLatest { active ->
            if (!active) return@collectLatest
            val pin = pinnable?.pin()
            try {
                awaitCancellation()
            } finally {
                pin?.release()
            }
        }
    }
    fun finishDrag(cancelled: Boolean = false) {
        if (!dragging) return
        landing = CardLanding(pointer - grabOffset)
        dragging = false
        if (cancelled) cancel() else drop()
        dragPosition(null)
    }
    DisposableEffect(cardKey, dragBounds) {
        onDispose {
            dragBounds.remove(cardKey)
            if (dragging) {
                cancel()
                dragPosition(null)
            }
        }
    }
    val actions = Modifier.semantics {
        customActions = buildList {
            add(
                CustomAccessibilityAction("${if (favourite) "Убрать из избранного" else "Добавить в избранное"}: $label") {
                    onFavourite()
                    true
                },
            )
            if (canMoveUp) {
                add(
                    CustomAccessibilityAction("Выше: $label") {
                        onMoveUp()
                        true
                    },
                )
            }
            if (canMoveDown) {
                add(
                    CustomAccessibilityAction("Ниже: $label") {
                        onMoveDown()
                        true
                    },
                )
            }
        }
    }
    Column(
        modifier.fillMaxWidth()
            .onPlaced {
                placement = it.positionInParent().round()
                cardOrigin = it.localToRoot(Offset.Zero)
            }
            .onGloballyPositioned {
                cardOrigin = it.localToRoot(Offset.Zero)
                dragBounds[cardKey] = it.boundsInRoot()
            }
            .semantics { testTag = "${if (dragging) "drop-slot" else "card-slot"}:$cardKey" }
            .drawBehind {
                if (dragging) {
                    val inset = 6.dp.toPx()
                    val slotSize = Size((size.width - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f))
                    val radius = CornerRadius(12.dp.toPx())
                    drawRoundRect(slotFill, Offset(inset, inset), slotSize, radius)
                    drawRoundRect(slotColor, Offset(inset, inset), slotSize, radius, style = Stroke(2.dp.toPx()))
                }
            }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(0, 0, zIndex = if (dragging || landing != null) 1f else 0f)
                }
            }
            .pointerInput(cardKey) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { start ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val motion = landing
                        val visualShift = motion?.let { (it.origin - cardOrigin) * (1f - it.progress.value) }
                            ?: animatedPlacement?.let { (it.value - (placement ?: IntOffset.Zero)).toOffset() } ?: Offset.Zero
                        grabOffset = start - visualShift
                        landing = null
                        dragging = true
                        pointer = cardOrigin + start
                        dragPosition(pointer)
                    },
                    onDragCancel = { finishDrag(cancelled = true) },
                    onDragEnd = { finishDrag() },
                ) { change, _ ->
                    change.consume()
                    pointer = cardOrigin + change.position
                    dragPosition(pointer)
                }
            },
    ) {
        Column(
            Modifier.graphicsLayer {
                val motion = landing
                val shift = when {
                    dragging -> pointer - grabOffset - cardOrigin
                    motion != null -> (motion.origin - cardOrigin) * (1f - motion.progress.value)
                    else -> animatedPlacement?.let { (it.value - (placement ?: IntOffset.Zero)).toOffset() } ?: Offset.Zero
                }
                translationX = shift.x
                translationY = shift.y
                alpha = if (dragging) 0.9f else 1f
            },
        ) {
            content(actions)
        }
    }
}
