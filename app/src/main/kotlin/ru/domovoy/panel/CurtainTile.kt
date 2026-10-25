package ru.domovoy.panel

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.time.Instant

/** Curtain position and reading age. A failed poll retains the last position visibly. */
@Composable
fun CurtainTile(
    tile: CurtainTileState,
    now: Instant,
    modifier: Modifier = Modifier,
    error: String? = null,
    showRoom: Boolean = false,
    pending: Boolean = false,
    /** Open the device sheet; null leaves the card non-clickable. */
    onOpen: (() -> Unit)? = null,
    onSetOpen: (String, Double) -> Unit = { _, _ -> },
) {
    // Curtain position supplies its mood; shared poll failures add an outline.
    TileCard(
        anatomy = anatomy(tile, now, error),
        paint = paint(tile, now, error),
        modifier = modifier,
        format = TileFormat.Control,
        showRoom = showRoom,
        pending = pending,
        hasLevel = tile.bounds != null,
        onClick = onOpen,
        // The secondary action sends the same vendor range capability as the slider, at an endpoint.
        onAction = {
            val target = action(tile, now)?.let { actionTarget(tile, it) }
            if (target != null) onSetOpen(tile.id, target)
        },
        level = {
            val bounds = tile.bounds
            if (bounds != null) {
                LevelControl(
                    id = tile.id,
                    value = sliderStart(tile, bounds.min).toDouble(),
                    bounds = bounds,
                    label = "Открытие",
                    unit = "%",
                    enabled = !pending,
                    onSetValue = { onSetOpen(tile.id, it) },
                )
            }
        },
    )
}

/**
 * Start at the latest range or open/close command, matching the displayed position.
 * Unknown positions start at the vendor range minimum.
 */
internal fun sliderStart(
    tile: CurtainTileState,
    min: Double,
): Float = (position(tile)?.percent ?: min).toFloat()

/** Position and age come from the same latest command. Unknown positions have no age. */
internal fun statusLine(
    tile: CurtainTileState,
    now: Instant,
): String {
    val position = position(tile)
    if (confirmedPosition(tile, now) != null && position != null) return sheetAge(position.reading, now)
    return listOfNotNull(
        promoted(tile) ?: "Нет данных",
        ageLine(position?.reading, now),
    ).joinToString(" · ")
}
