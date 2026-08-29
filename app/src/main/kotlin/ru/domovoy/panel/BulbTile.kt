package ru.domovoy.panel

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.time.Instant

/**
 * One bulb: its name, whether it is on, and how old that reading is. When [error] is set the poll
 * behind these values failed — the tile keeps showing the last value it had, and says so, rather
 * than blanking out or spinning.
 *
 * Two kinds of bulb reach this composable, and they are the same tile: the one the panel has no
 * state for, which never joins its room's group, and the ones from inside a group that has been
 * opened. Both are a lamp with a name, a switch and an age, which is what a bulb is when there is
 * room to say so.
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
        paint = paint(tile, error),
        modifier = modifier,
        toggle = {
            Switch(checked = tile.isOn == true, onCheckedChange = { onToggle(tile.id) })
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
 * call stops landing, [favourites] pulls all 28 onto Главная. At 280 dp each that is seven rows of
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
        hue = hue(group),
        paint = paint(group, error),
        modifier = modifier,
        onClick = onOpen,
    )
}

/**
 * The line under the name: on/off, and how old that reading is once it is worth saying. The reason a
 * poll failed is the tile's second line now — see [TileAnatomy] — and on a bulb it has to be, since
 * a bulb is a quarter-width tile and this line has about sixteen characters to spend.
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
    ageLine(tile.lastUpdated.takeIf { tile.isOn != null }, now),
).joinToString(" · ")

/**
 * The three words a bulb's state comes in. "unknown" and never "off" for a bulb that reported
 * nothing — the same care the colours take, in the place the panel has always taken it.
 */
private fun power(isOn: Boolean?): String = when (isOn) {
    true -> "on"
    false -> "off"
    null -> "unknown"
}
