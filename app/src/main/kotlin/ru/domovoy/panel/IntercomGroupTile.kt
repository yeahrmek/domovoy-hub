package ru.domovoy.panel

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.domovoy.R
import ru.domovoy.core.Device
import java.time.Instant
import kotlin.time.Duration

@Composable
internal fun IntercomGroupTile(
    tile: Device,
    state: DiscoveredPanelState,
    now: Instant,
    interval: Duration,
    open: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeDeviceTile(
        anatomy = TileAnatomy(
            art = R.drawable.device_art_intercom,
            action = null,
            name = tile.name,
            promoted = cardText(eventValue(tile.events["open"]?.value)),
            status = "",
            detail = state.error,
            room = tile.room,
        ),
        paint = paint(null, ownError = null, groupError = state.error),
        polledAt = state.lastPolledAt,
        now = now,
        interval = interval,
        modifier = modifier,
        onOpen = onOpen,
        openLabel = if (open) "Свернуть" else "Показать домофоны",
        expanded = open,
    )
}
