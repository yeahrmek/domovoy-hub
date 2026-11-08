package ru.domovoy.panel

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.time.Instant

/** Power, brightness, and read-only colour. A failed poll visibly retains the last readings. */
@Composable
fun LightStripTile(
    tile: LightStripTileState,
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
        format = TileFormat.Control,
        showRoom = showRoom,
        pending = pending,
        hasLevel = tile.bounds != null,
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
            val bounds = tile.bounds
            if (bounds != null) {
                LevelControl(
                    id = tile.id,
                    value = tile.brightnessPercent,
                    bounds = bounds,
                    showBounds = false,
                    label = "Яркость",
                    unit = if (tile.unit == "unit.percent") "%" else "",
                    enabled = !pending,
                    onSetValue = { onSetBrightness(tile.id, it) },
                )
            }
        },
    )
}

/** Missing values remain explicit; power is conveyed by the switch. */
internal fun statusLine(
    tile: LightStripTileState,
    now: Instant,
): String = listOfNotNull(
    "Нет данных".takeIf { tile.isOn == null },
    "Яркость неизвестна".takeIf { tile.brightnessPercent == null },
).joinToString(" · ")

/**
 * Label colour as read-only. Missing capability omits the line; an unread capability says unknown.
 */
internal fun colorLine(tile: LightStripTileState): String? {
    val color = tile.color ?: return null
    return "${colorDescription(color)} · только просмотр"
}
