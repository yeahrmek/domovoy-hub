package ru.domovoy.panel

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.time.Instant

/**
 * Power and optional advertised brightness, retaining readings when a poll fails.
 * Unknown bulbs appear individually; grouped bulbs use this same tile when expanded.
 */
@Composable
fun BulbTile(
    tile: BulbTileState,
    now: Instant,
    modifier: Modifier = Modifier,
    error: String? = null,
    showRoom: Boolean = false,
    pending: Boolean = false,
    /** Open the device sheet; null leaves the card non-clickable. */
    onOpen: (() -> Unit)? = null,
    onToggle: (String) -> Unit = {},
    onSetBrightness: (String, Double) -> Unit = { _, _ -> },
) {
    val paint = paint(tile, error)
    TileCard(
        anatomy = anatomy(tile, now, error),
        paint = paint,
        modifier = modifier,
        format = if (tile.brightnessBounds != null) TileFormat.Control else TileFormat.Compact,
        showRoom = showRoom,
        pending = pending,
        hasLevel = tile.brightnessBounds != null,
        onClick = onOpen,
        toggle = {
            TilePowerButton(
                deviceName = tile.name,
                room = tile.room,
                isOn = tile.isOn == true,
                mood = paint.mood,
                onToggle = { onToggle(tile.id) },
                enabled = !pending,
            )
        },
        level = {
            val bounds = tile.brightnessBounds
            if (bounds != null) {
                LevelControl(
                    id = tile.id,
                    value = tile.brightnessPercent,
                    bounds = bounds,
                    showBounds = false,
                    label = "Яркость",
                    unit = "%",
                    enabled = !pending,
                    onSetValue = { onSetBrightness(tile.id, it) },
                )
            }
        },
    )
}

/**
 * Room summary: the lit count remains visible while collapsed.
 * Expanding reveals names and individual controls without replacing the summary.
 */
@Composable
fun BulbGroupTile(
    group: BulbGroup,
    now: Instant,
    modifier: Modifier = Modifier,
    /** A shared poll failure adds an outline and one reason for the whole group. */
    error: String? = null,
    /** Whether that poll has stopped landing at all, with or without a call having failed. */
    notUpdating: Boolean = false,
    /** Whether the lamps are open under it. The tile says which, on its second status line. */
    open: Boolean = false,
    showRoom: Boolean = false,
    pending: Boolean = false,
    onOpen: () -> Unit = {},
) {
    TileCard(
        anatomy = anatomy(group, now, error, notUpdating, open),
        paint = paint(group, error),
        modifier = modifier,
        showRoom = showRoom,
        pending = pending,
        onClick = onOpen,
    )
}

/** Unknown power stays explicit; known power is conveyed by the switch. */
internal fun statusLine(
    tile: BulbTileState,
    now: Instant,
): String = listOfNotNull(
    "Нет данных".takeIf { tile.isOn == null },
).joinToString(" · ")

/** Power words for sheets and read-only readings; an absent reading stays unknown. */
internal fun power(isOn: Boolean?): String = when (isOn) {
    true -> "Включено"
    false -> "Выключено"
    null -> "Нет данных"
}
