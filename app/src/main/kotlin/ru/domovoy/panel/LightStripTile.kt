package ru.domovoy.panel

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.domovoy.core.Reading
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
                    label = "Яркость",
                    unit = if (tile.unit == "unit.percent") "%" else "",
                    enabled = !pending,
                    onSetValue = { onSetBrightness(tile.id, it) },
                )
            }
        },
    )
}

/**
 * Power, brightness, and the oldest displayed reading age, including colour.
 * A poll failure temporarily replaces the colour detail.
 */
internal fun statusLine(
    tile: LightStripTileState,
    now: Instant,
): String = listOfNotNull(
    power(tile.isOn),
    "Яркость неизвестна".takeIf { tile.brightnessPercent == null },
    ageLine(oldest(tile.readings()), now),
).joinToString(" · ")

/** Only readings with values contribute to age; missing values are shown as unknown. */
internal fun LightStripTileState.readings(): List<Reading> = listOfNotNull(
    powerLastUpdated.takeIf { isOn != null },
    brightnessLastUpdated.takeIf { brightnessPercent != null },
    color?.takeIf { it.value != null }?.lastUpdated,
)

/**
 * Label colour as read-only. Missing capability omits the line; an unread capability says unknown.
 * Its age contributes to the tile’s single status age.
 */
internal fun colorLine(tile: LightStripTileState): String? {
    val color = tile.color ?: return null
    return "${colorDescription(color)} · только просмотр"
}
