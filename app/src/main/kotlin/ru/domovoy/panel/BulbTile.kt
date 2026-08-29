package ru.domovoy.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.domovoy.R
import java.time.Instant

/**
 * One bulb: its name, whether it is on, and how old that reading is. When [error] is set the poll
 * behind these values failed — the tile keeps showing the last value it had, and says so, rather
 * than blanking out or spinning.
 */
@Composable
fun BulbTile(
    tile: BulbTileState,
    now: Instant,
    modifier: Modifier = Modifier,
    error: String? = null,
    onToggle: (String) -> Unit = {},
) {
    TileCard(
        anatomy = anatomy(tile, now, error),
        hue = hue(tile),
        mood = mood(tile.isOn, error),
        modifier = modifier,
        toggle = {
            Switch(checked = tile.isOn == true, onCheckedChange = { onToggle(tile.id) })
        },
    )
}

/**
 * The disc one lamp gets: 72 dp of the tile colour its mood earns, with the lamp on it.
 *
 * It clears the panel's 64 dp touch floor on its own, so there is no [touchable] inside it — and
 * unlike the bare lamp this replaces, that target is something a finger can see. The lamps were the
 * one control on the wall whose only drawn shape was the ripple it took to press, which is the cost
 * the handle-less slider still pays and the one thing a disc costs nothing to fix.
 */
private val BULB_CELL = 72.dp

/**
 * How big the lamp is on it: 48 dp, twice the 24 dp every other glyph on the wall takes.
 *
 * It is the only glyph with nothing to share its cell with — no name beside it and no line under
 * it — so the 12 dp it leaves on each side is the disc's own margin rather than wasted cell. At
 * 24 dp on a 72 dp disc the lamp reads as a speck on a dot, and the row becomes a status bar of
 * coloured circles rather than a wall of lamps, which is the one thing 28 of anything needs.
 */
private val BULB_LAMP = 48.dp

/** The gutter between circles, and the grid's own 8 dp. */
private val CIRCLE_GAP = 8.dp

/**
 * The lights group: one wrapping row of circles and one line under it.
 *
 * This is what 28 third-width tiles became. The bulbs are on/off only and they are the many, so a
 * card each is what made Главная fourteen rows of lamps; who is in this row is [bulbGroup]'s answer
 * and the line is [bulbGroupLine]'s, out where a test reaches both.
 *
 * The row says "not updating" once for all of them rather than 28 times, because one
 * `/v1.0/user/info` call is behind every circle in it — see [notUpdating].
 */
@Composable
fun BulbCircles(
    group: BulbGroup,
    now: Instant,
    modifier: Modifier = Modifier,
    /**
     * The bulb group's error: the one poll behind all of them failed, so every circle in the row
     * goes rose — see [BulbCircle] — and the line under it is what names the reason. The rose says
     * which lamps, the line says why; neither is enough on its own from four metres.
     */
    error: String? = null,
    /** Whether that poll has stopped landing at all — said once, on the line under the row. */
    notUpdating: Boolean = false,
    onToggle: (String) -> Unit = {},
) {
    Column(modifier = modifier.padding(CIRCLE_GAP / 2)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CIRCLE_GAP),
            verticalArrangement = Arrangement.spacedBy(CIRCLE_GAP),
        ) {
            group.circles.forEach { tile -> BulbCircle(tile = tile, error = error, onToggle = onToggle) }
        }
        Text(
            text = bulbGroupLine(group, now, notUpdating, error),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = CIRCLE_GAP),
        )
    }
}

/**
 * One circle: **one disc, one lamp and no text.** A [BULB_CELL] filled with the colour its [mood]
 * earns, carrying the same outlined lamp whatever that mood is. Its name and its state are on it as
 * a content description and nowhere else — there is no room for either at 72 dp, and the row is one
 * thing to read rather than 28.
 *
 * **The disc says the state and the lamp says what kind of thing it is**, which is the split every
 * other tile on the wall already makes: a card's colour is its mood and its glyph is its type. A
 * bulb that had to say both with one glyph was the odd one out, and it was the odd one out for a
 * reason that has gone — there was no container for a failure to colour, so a failed group had to
 * drop every lamp to its unlit shape and leave the reason entirely to the words underneath.
 *
 * So the moods are the card roles rather than the accent ones, because there is a container here to
 * hold them now: `tertiaryContainer` for a lit lamp, the light family's own, `surfaceContainer` for
 * an unlit one, and `errorContainer` for a failing group — the rose every other tile on this wall
 * already wears, see `tileColors`. The lamp takes the on-colour of whichever it is sitting on.
 *
 * **A circle is only ever [TileMood.On], [TileMood.Off] or [TileMood.Failing].** A bulb with no
 * state at all breaks out of the row and becomes a named tile — that is `bulbGroup`'s whole split —
 * so [TileMood.Unknown] never reaches a disc, and the branch it shares below is the `when` being
 * exhaustive rather than a case anybody has seen.
 *
 * A halo was mocked and dropped, and the *implementation* note outlives the choice: it would have
 * had to be a `Brush.radialGradient` rather than `Modifier.blur`, which is API 31+ against a minSdk
 * of 26 — it would have drawn perfectly on this Android 13 tablet and silently nothing on Android 8
 * to 11.
 */
@Composable
private fun BulbCircle(
    tile: BulbTileState,
    error: String?,
    onToggle: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // The same function every other tile asks, with the same ranking: Failing outranks a perfectly
    // good isOn, because a circle showing a value nobody has confirmed must not claim it is lit.
    // What it does with that answer is now a colour, like everywhere else.
    val (disc, lamp) = when (mood(tile.isOn, error)) {
        TileMood.On -> colors.tertiaryContainer to colors.onTertiaryContainer
        TileMood.Failing -> colors.errorContainer to colors.onErrorContainer
        TileMood.Off, TileMood.Unknown -> colors.surfaceContainer to colors.onSurfaceVariant
    }
    Box(
        modifier =
        Modifier
            .size(BULB_CELL)
            // Clipped before it is filled and before it is clickable, so the disc, the ripple under
            // a finger and the touch target are one circle rather than a circle inside a square.
            .clip(CircleShape)
            .background(disc)
            .clickable { onToggle(tile.id) }
            .semantics { contentDescription = "${tile.name} · ${power(tile.isOn)}" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bulb),
            // The one glyph on the wall that is not decorative is not this one either: the circle
            // itself carries the lamp's name and state, and saying it twice is worse than once.
            contentDescription = null,
            tint = lamp,
            modifier = Modifier.size(BULB_LAMP),
        )
    }
}

/** The line under the name: on/off, how old the reading is, and the error if the poll failed. */
internal fun statusLine(
    tile: BulbTileState,
    now: Instant,
    error: String?,
): String {
    val power = power(tile.isOn)
    val age = ageLabel(tile.lastUpdated, now)
    return if (error == null) "$power · $age" else "$power · $age · not updating: $error"
}

/**
 * The three words a bulb's state comes in. Shared with the circle's content description so that the
 * one place a circle can be read out says the same thing the tile's line has always said —
 * "unknown" and never "off" for a bulb that reported nothing.
 */
private fun power(isOn: Boolean?): String = when (isOn) {
    true -> "on"
    false -> "off"
    null -> "unknown"
}
