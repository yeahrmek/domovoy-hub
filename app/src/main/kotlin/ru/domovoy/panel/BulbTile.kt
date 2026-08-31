package ru.domovoy.panel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.domovoy.core.Reading
import java.time.Instant

/**
 * One bulb: its name, whether it is on, optional brightness, and how old those readings are. When
 * [error] is set the poll behind these values failed — the tile keeps showing the last value it had,
 * and says so, rather than blanking out or spinning.
 *
 * Two kinds of bulb reach this composable, and they are the same tile: the one the panel has no
 * state for, which never joins its room's group, and the ones from inside a group that has been
 * opened. Relay-backed lights remain power-only; a bulb that advertised brightness uses the same
 * slider band as the strips and promotes its percentage.
 */
@Composable
fun BulbTile(
    tile: BulbTileState,
    now: Instant,
    modifier: Modifier = Modifier,
    error: String? = null,
    /** What a tap on the card does: open this device's sheet. Null when there is none — see [AcTile]. */
    onOpen: (() -> Unit)? = null,
    onToggle: (String) -> Unit = {},
    onSetBrightness: (String, Double) -> Unit = { _, _ -> },
) {
    // The paint is worked out once and read twice: the card takes it, and so does the power button,
    // whose colour is the tile's second on-mark.
    val paint = paint(tile, error)
    TileCard(
        anatomy = anatomy(tile, now, error),
        paint = paint,
        modifier = modifier,
        onClick = onOpen,
        toggle = {
            TilePowerButton(
                isOn = tile.isOn == true,
                mood = paint.mood,
                onToggle = { onToggle(tile.id) },
            )
        },
        level = {
            val bounds = tile.brightnessBounds
            if (bounds != null) {
                var dragged by
                    remember(tile.id) {
                        mutableFloatStateOf((tile.brightnessPercent ?: bounds.min).toFloat())
                    }
                SlimSlider(
                    value = dragged,
                    onValueChange = { dragged = it },
                    valueRange = bounds.min.toFloat()..bounds.max.toFloat(),
                    onValueChangeFinished = { onSetBrightness(tile.id, dragged.toDouble()) },
                )
            }
        },
    )
}

/**
 * **A room's lamps, as one tile**: `7 lamps`, how many of them are lit, one age, and a tap that
 * opens the seven.
 *
 * This is what a wrapping row of 72 dp discs became. The discs were the most saturated thing on the
 * wall and its biggest touch targets, seven of them identical and unlabelled under one shared line —
 * so the eye landed there first and learned nothing, and which lamp was which was not recoverable
 * from the wall at all. They were also the one thing on this panel that was a tile without being a
 * card: their own shape, their own colour rules, their own touch target, sitting outside the anatomy
 * every other kind agreed on.
 *
 * **The other option was seven ordinary tiles, and the count is why it was not taken.** The flat has
 * 28 bulbs against 7 of everything else, and one Yandex call feeds all of them — so the moment that
 * call stops landing, [favourites] pulls all 28 onto Главная. At 296 dp each that is seven rows of
 * lamp before the wall says anything about the air conditioner, which is precisely the "fourteen
 * rows of lamps" the group was invented to prevent, four times taller. What the panel refuses to
 * hide behind a tap is a *reading* — see PLAN.md — and no reading is hidden here: how many lamps,
 * how many on, and how old the oldest of them is are all on the closed tile. What the tap opens is
 * which lamp is which, which the row of discs never showed at all.
 *
 * Everything it says comes from [bulbGroup] and [anatomy]; this draws it, like every other tile, and
 * decides nothing.
 */
@Composable
fun BulbGroupTile(
    group: BulbGroup,
    now: Instant,
    modifier: Modifier = Modifier,
    /**
     * The bulb group's error: the one poll behind all of them failed. The tile takes the group
     * outline and its status line names the reason — said once for the whole group rather than once
     * per lamp, because one `/v1.0/user/info` call is behind every one of them. It is *only* the
     * group's: a lights group has no failure of its own to fill with.
     */
    error: String? = null,
    /** Whether that poll has stopped landing at all, with or without a call having failed. */
    notUpdating: Boolean = false,
    /** Whether the lamps are open under it. The tile says which, on its second status line. */
    open: Boolean = false,
    onOpen: () -> Unit = {},
) {
    TileCard(
        anatomy = anatomy(group, now, error, notUpdating, open),
        paint = paint(group, error),
        modifier = modifier,
        onClick = onOpen,
    )
}

/**
 * The line under the name: on/off, optional brightness, and one age for everything the tile shows.
 * The reason a poll failed is the tile's second line — see [TileAnatomy] — and on a bulb it has to
 * be, since a bulb is a quarter-width tile and this line has about sixteen characters to spend.
 *
 * A lamp switched on twenty days ago still says "20 d ago"; one read this morning says nothing but
 * "on" — see [ageLine]. A lamp the panel has no value for says "unknown" and no age at all, because
 * it has no reading for an age to be about: "unknown · never read" was the same fact twice.
 */
internal fun statusLine(
    tile: BulbTileState,
    now: Instant,
): String = listOfNotNull(
    power(tile.isOn),
    promoted(tile),
    ageLine(oldest(tile.readings()), now),
).joinToString(" · ")

private fun BulbTileState.readings(): List<Reading> = listOfNotNull(
    lastUpdated.takeIf { isOn != null },
    brightnessLastUpdated.takeIf { brightnessPercent != null },
    color?.takeIf { it.value != null }?.lastUpdated,
)

/**
 * The three words a bulb's state comes in. "unknown" and never "off" for a bulb that reported
 * nothing — the same care the colours take, in the place the panel has always taken it.
 *
 * Shared with [TileSheet] rather than private now: every sheet with a power reading on it says the
 * same three words, and a second copy of this `when` would be a second place for "unknown" to
 * quietly become "off".
 */
internal fun power(isOn: Boolean?): String = when (isOn) {
    true -> "on"
    false -> "off"
    null -> "unknown"
}
