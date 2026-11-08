package ru.domovoy.panel

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.time.Instant

/** Air conditioner state and target temperature. A failed poll retains the last readings visibly. */
@Composable
fun AcTile(
    tile: AcTileState,
    now: Instant,
    modifier: Modifier = Modifier,
    error: String? = null,
    showRoom: Boolean = false,
    pending: Boolean = false,
    /** Open the device sheet; null leaves the card non-clickable. */
    onOpen: (() -> Unit)? = null,
    onToggle: (String) -> Unit = {},
    onSetTemperature: (String, Double) -> Unit = { _, _ -> },
    onSetMode: (String, String, String) -> Unit = { _, _, _ -> },
) {
    // The shared Yandex poll failure adds an outline; device mood also colours the power control.
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
        onAction = {
            nextFanMode(tile)?.let { next -> onSetMode(tile.id, "fan_speed", next) }
        },
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
                    value = tile.targetTemperature,
                    bounds = bounds,
                    label = "Температура",
                    unit = if (tile.unit == "unit.temperature.celsius") "°C" else "",
                    enabled = !pending,
                    onSetValue = { onSetTemperature(tile.id, it) },
                )
            }
        },
    )
}

/** Missing values remain explicit; known power is conveyed by the switch. */
internal fun statusLine(
    tile: AcTileState,
    now: Instant,
): String = listOfNotNull(
    "Нет данных".takeIf { tile.isOn == null },
    "Температура неизвестна".takeIf { tile.targetTemperature == null },
).joinToString(" · ")
