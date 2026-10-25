package ru.domovoy.panel

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.domovoy.core.Reading
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

/** Power, target, and the oldest displayed reading age. Failures use the separate detail line. */
internal fun statusLine(
    tile: AcTileState,
    now: Instant,
): String = listOfNotNull(
    power(tile.isOn),
    "Температура неизвестна".takeIf { tile.targetTemperature == null },
    ageLine(oldest(tile.readings()), now),
).joinToString(" · ")

/** Only readings with values contribute to age; absent values are already shown as unknown. */
private fun AcTileState.readings(): List<Reading> = listOfNotNull(
    powerLastUpdated.takeIf { isOn != null },
    temperatureLastUpdated.takeIf { targetTemperature != null },
)
