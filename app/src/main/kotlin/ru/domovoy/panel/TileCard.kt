package ru.domovoy.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The corner of a half-width tile. The two radii are the mosaic's whole shape vocabulary; a third
 * one is a tile that looks like a mistake rather than like a size.
 */
private val HALF_CORNER = 22.dp

/** The corner of a third-width tile: the bulbs, the launchers, the recuperator with no climate. */
private val THIRD_CORNER = 18.dp

/**
 * The corner a tile of this span wears. Derived rather than passed so that a tile's shape and its
 * width cannot disagree — the one thing a caller could get wrong when both were parameters.
 */
private fun corner(span: Int) = if (span >= HALF_SPAN) HALF_CORNER else THIRD_CORNER

/**
 * The smallest anything tappable is allowed to be — 64 dp, not the platform's 48. This is read and
 * touched at arm's length from a wall, standing up, often with one hand full; the platform's
 * minimum is sized for a phone held 30 cm from the face.
 */
internal val MIN_TOUCH = 64.dp

/**
 * Half the grid gutter, since each tile pads itself and two neighbours make the 8 dp. Done on the
 * tile rather than as the grid's arrangement so that the outer edge is padded too — a tile flush
 * against the bezel on a wall panel reads as a tile that has been cut off.
 */
private val TILE_PADDING = 4.dp

/**
 * Every tile on the wall: one card, its corner from its size and its colour from its [TileMood].
 *
 * Five callers, which is what earns it — the alternative is the same `when` over four colour roles
 * written out five times, and a sixth tile getting one of them wrong. It draws and nothing else:
 * what mood a tile is in is [mood]'s answer, out where a test can reach it.
 */
@Composable
internal fun TileCard(
    mood: TileMood,
    /** How many of the grid's columns this tile occupies. Decides its corner, and nothing else. */
    span: Int,
    modifier: Modifier = Modifier,
    /**
     * Bad news that belongs to the tile's *group* rather than to the tile. Only the recuperators
     * have both kinds — see [RecuperatorTile] — and an outline is what keeps them apart: a filled
     * red tile is this device, an outlined one is all five.
     */
    border: BorderStroke? = null,
    /**
     * What the whole tile does when tapped, or null when it does nothing. Null rather than a
     * disabled flag: a tile that looks tappable and swallows the tap is the dead tap the launcher
     * tile exists not to be — see [LauncherTile].
     */
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner(span))
    val outer = modifier.fillMaxWidth().padding(TILE_PADDING).heightIn(min = MIN_TOUCH)
    if (onClick == null) {
        Card(modifier = outer, shape = shape, colors = tileColors(mood), border = border, content = content)
    } else {
        // The clickable Card rather than a `Modifier.clickable` outside it, so the ripple is
        // clipped to the corner it is drawn on and the tap lands on the tile and not on the gutter.
        Card(
            onClick = onClick,
            modifier = outer,
            shape = shape,
            colors = tileColors(mood),
            border = border,
            content = content,
        )
    }
}

/** The width of that outline: thick enough to read from across a hallway, thin enough to be one. */
private val GROUP_FAILURE_OUTLINE = 3.dp

/** The outline a tile wears when its whole group stopped updating, or null when it did not. */
@Composable
internal fun groupFailureBorder(groupError: String?): BorderStroke? = groupError?.let {
    BorderStroke(GROUP_FAILURE_OUTLINE, MaterialTheme.colorScheme.error)
}

/**
 * The colour roles, and only roles: no hex literal anywhere in `panel/`. A hardcoded colour is a
 * tile that is unreadable in one of the two themes, and the theme that breaks is the one nobody is
 * looking at when they check.
 *
 * Only *on* has a colour of its own. [TileMood.Off], [TileMood.Unknown] and [TileMood.Failing] all
 * wear the neutral, and the difference between the three is said in words on the status line, which
 * is where it was always said — "off", "unknown", "not updating: <reason>". What must not happen is
 * any of them borrowing the *on* colour and claiming a reading nobody has taken.
 *
 * [TileMood.Failing] was `errorContainer` until the wall had a few of them on it at once. A red tile
 * per failing device turns a flat with one unreachable vendor into a panel that reads as an
 * emergency, and it is loudest exactly when it is least useful — at boot, when nothing has been read
 * yet and every tile is failing at once. The one thing still painted rather than written is the
 * *group's* failure, which outlines its tiles rather than filling them — see [groupFailureBorder].
 *
 * Reachable outside [TileCard] for the one thing on the wall that is a tile without being a card:
 * a bulb circle, which wears a shape of its own and these same colours — see [BulbCircles].
 */
@Composable
internal fun tileColors(mood: TileMood): CardColors = when (mood) {
    TileMood.On ->
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    TileMood.Off, TileMood.Unknown, TileMood.Failing ->
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
}

/**
 * The 64 dp floor, for the controls inside a tile. A `Switch` is 52×32 dp and a `Slider` thinner
 * still; both sit inside a box this size so the finger has the whole of it.
 */
internal fun Modifier.touchable(): Modifier = sizeIn(minWidth = MIN_TOUCH, minHeight = MIN_TOUCH)
