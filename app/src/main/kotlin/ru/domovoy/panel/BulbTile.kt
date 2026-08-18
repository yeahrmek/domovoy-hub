package ru.domovoy.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
    TileCard(hue = hue(tile), mood = mood(tile.isOn, error), span = THIRD_SPAN, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                TileHeading(glyph = glyph(tile), name = tile.name, span = THIRD_SPAN)
                Text(
                    text = statusLine(tile, now, error),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = tile.isOn == true,
                onCheckedChange = { onToggle(tile.id) },
                modifier = Modifier.touchable(),
            )
        }
    }
}

/**
 * The cell one lamp gets: 72 dp holding the lamp and nothing else — no disc, no card, no border.
 *
 * It clears the panel's 64 dp touch floor on its own, so there is no [touchable] inside it, and that
 * touch target is now invisible. That is the accepted cost of the choice and it is the same one the
 * handle-less slider took: cleaner to look at, less obviously pressable, and worse here than there,
 * because a slider at least prints a number beside it and an unlit lamp offers nothing. If nobody
 * works out the lamps are tappable, the answer is a quiet disc under the glyph and not a return to
 * the coloured ones.
 */
private val BULB_CELL = 72.dp

/**
 * How big the lamp is in it: 48 dp, twice the 24 dp every other glyph on the wall takes.
 *
 * It is the only glyph with nothing to share its cell with — no name beside it and no line under
 * it — so the 12 dp it leaves on each side is the margin the well used to occupy, and the lamp
 * simply inherits the footprint the well had. At 24 dp in a bare 72 dp cell the row reads as a
 * scatter of specks rather than as a wall of lamps, which is the one thing 28 of anything needs.
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
     * The bulb group's error: the one poll behind all of them failed, so every circle drops to
     * unlit — see [BulbCircle] — and the line under the row is what names the reason.
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
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = CIRCLE_GAP),
        )
    }
}

/**
 * One circle: **one shape and no text.** A [BULB_CELL] holding a [BULB_LAMP] lamp, and that is the
 * whole of it. Its name and its state are on it as a content description and nowhere else — there is
 * no room for either at 72 dp, and the row is one thing to read rather than 28.
 *
 * **Being on is the filled lamp, being off the outline** — see [bulbGlyph]. Two treatments came
 * before this one and both are worth not going back to: a filled amber disc per lamp turns the row
 * into 28 coloured dots, which is a status bar rather than a set of lights, and a white disc inset in
 * a coloured tile fixes that by making each lamp an object with a light in it, at two nested shapes
 * and a glyph shrunk to fit inside them. Neither puts the light in the lamp, which is the whole idea:
 * a lamp that is on should look lit, not look labelled.
 *
 * So the state is a shape and not only a hue, and there is no disc, no tile, no border and no halo in
 * either state. A halo was mocked and dropped, and the *implementation* note outlives the choice: it
 * would have had to be a `Brush.radialGradient` rather than `Modifier.blur`, which is API 31+ against
 * a minSdk of 26 — it would have drawn perfectly on this Android 13 tablet and silently nothing on
 * Android 8 to 11.
 *
 * The colours are the accent roles rather than the container ones the cards use, for the reason the
 * slider's fill takes them: there is no container here to hold `tertiaryContainer`, and
 * `onTertiaryContainer` is a colour for sitting *on* that container. So a lit lamp is `tertiary`, the
 * light family's own accent, and everything else is the quiet `onSurfaceVariant`.
 */
@Composable
private fun BulbCircle(
    tile: BulbTileState,
    error: String?,
    onToggle: (String) -> Unit,
) {
    // A failed poll collapses the circle to no state at all, which is exactly bulbGlyph's null: the
    // last value is still shown by the row's count, but neither the shape nor the colour may claim
    // anybody confirmed it. Failing outranks a perfectly good isOn here for the same reason it does
    // in mood, and with no container there is nothing to outline instead — the words go on the
    // group's one line, once, which is where they were always said.
    val confirmedOn = tile.isOn.takeIf { error == null }
    Box(
        modifier =
        Modifier
            .size(BULB_CELL)
            // Clipped before it is clickable so the one thing that does draw a shape here — the
            // ripple under a finger — is a circle the size of the touch target rather than a
            // rectangle the size of the cell.
            .clip(CircleShape)
            .clickable { onToggle(tile.id) }
            .semantics { contentDescription = "${tile.name} · ${power(tile.isOn)}" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(bulbGlyph(confirmedOn)),
            // The one glyph on the wall that is not decorative is not this one either: the circle
            // itself carries the lamp's name and state, and saying it twice is worse than once.
            contentDescription = null,
            tint = if (confirmedOn == true) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
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
