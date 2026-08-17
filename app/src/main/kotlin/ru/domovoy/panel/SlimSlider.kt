package ru.domovoy.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * How thick the bar is: 6 dp, fully rounded. Material's own is 16 dp with a tall handle beside it,
 * and on a 376 dp tile that control is the loudest thing on the tile — louder than the value it sets.
 */
private val TRACK_HEIGHT = 6.dp

/**
 * How present the unfilled part is. Low emphasis on purpose: the filled portion is the reading and
 * the rest is only the range it sits in.
 *
 * An alpha over the tile's own container rather than a role of its own, and that is the one thing
 * here that had to be worked out rather than picked. `outlineVariant` was the obvious candidate and
 * it is *the same value as* `secondaryContainer` in the dark scheme — so the track would have
 * vanished on exactly one tile, an open curtain at night, which is the tile nobody has looked at
 * (see docs/ui.md). Compositing a neutral over whatever the tile is wearing cannot do that.
 *
 * _The number is a guess and is this one constant._
 */
private const val REST_OF_TRACK_ALPHA = 0.24f

/**
 * The panel's slider: a 6 dp rounded track, no visible handle, and the filled portion is the value.
 *
 * It reads as a reading with a range behind it rather than as a control demanding to be operated,
 * which is what a wall panel wants — the number above it is the point and this is how it is changed,
 * not the other way round.
 *
 * Material 3's [Slider] with its `track` and `thumb` slots overridden, **not** a `Box` with a
 * `pointerInput`: the slot version keeps the drag behaviour, the value semantics and the
 * accessibility that a hand-rolled draggable would silently drop. The thumb draws nothing at all,
 * which is safe rather than clever — the slider wraps each slot in a `Box` of its own and measures
 * that, so an empty one is a zero-size box and not a missing child.
 *
 * **The touch area is 64 dp tall whatever the track looks like.** A 6 dp visual is not a 6 dp
 * target, and this is the wall panel's rule overriding the aesthetic one. It is the track slot that
 * is 64 dp — the bar is drawn centred inside it — because the slider's own height is the taller of
 * its two slots and its drag handling covers exactly that. A `heightIn` on the outside would have
 * left the gesture where the 6 dp was.
 *
 * _Accepted with its cost:_ a slider with no handle does not announce that it can be dragged, and
 * nobody standing at a wall gets a tooltip. All three tiles that have one print a number and a unit
 * on the line above, which is the hint there is.
 */
// The slot overload and the fraction the track slot reads are both annotated experimental on this
// BOM. Opted in here rather than in `app/build.gradle.kts` — the module-wide opt-in there is for
// Expressive, which every tile is built on, and this is one function.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SlimSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    /** Which colour the filled portion takes, so a climate slider and a light one do not match. */
    hue: TileHue,
    modifier: Modifier = Modifier,
) {
    val filled = filledTrackColor(hue)
    val rest = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = REST_OF_TRACK_ALPHA)
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        modifier = modifier,
        thumb = {},
        track = { state ->
            Box(
                modifier = Modifier.fillMaxWidth().height(MIN_TOUCH),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier =
                    Modifier.fillMaxWidth().height(TRACK_HEIGHT).clip(CircleShape).background(rest),
                ) {
                    // The fraction is the slider's own, coerced into the range for us: a tile whose
                    // vendor reported a value outside its own bounds still draws a bar that fits.
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

/**
 * The filled portion's colour: the tile's domain, on the same two axes as everything else on the
 * wall — see [tileColors], which is the same question asked of the card behind this.
 *
 * The *accent* of each family rather than its container, because that container is what the tile is
 * already painted with when it is on: a climate slider on a climate tile has to be the blue that
 * shows against pale blue, not that same pale blue again.
 */
@Composable
private fun filledTrackColor(hue: TileHue): Color = when (hue) {
    TileHue.Climate -> MaterialTheme.colorScheme.primary
    TileHue.Light -> MaterialTheme.colorScheme.tertiary
    TileHue.Neutral -> MaterialTheme.colorScheme.secondary
}
