package ru.domovoy.panel

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * One tile that only opens somebody else's app: its name, and one line saying either that it opens
 * or which package is missing.
 *
 * No switch, no slider and no age — there is nothing to drive and nothing that goes stale. The one
 * gesture is the tap, and it is *disabled* when the app is not installed rather than being accepted
 * and quietly doing nothing: a tile that looks tappable and swallows the tap is the dead tap this
 * tile exists not to be. The line under the name says which package would have to be there, so the
 * refusal comes with its reason.
 *
 * [now] is not a parameter and deliberately so — every other tile takes one to work out its age,
 * and this one has no reading to age.
 */
@Composable
fun LauncherTile(
    tile: LauncherTileState,
    modifier: Modifier = Modifier,
    onOpen: (String) -> Unit = {},
) {
    // Nothing is read here, so there is no on/off to be in a mood about: the tile sits on the
    // unread step of the ramp unless it cannot do its single job, and a missing app is the only bad
    // news it has — its own, so it takes the red offline mark rather than the group outline. The package
    // name is the reason, which is what the line under the name prints too. See [paint].
    // The one tile that fills none of the three slots a control could go in — no switch, no slider,
    // no value — and reserves all three anyway, which is what puts its bottom edge on the same line
    // as the air conditioner's. See [TileCard].
    TileCard(
        anatomy = anatomy(tile),
        paint = paint(tile),
        modifier = modifier,
        onClick = if (tile.openable) ({ onOpen(tile.packageName) }) else null,
    )
}
