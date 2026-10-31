package ru.domovoy.panel

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ru.domovoy.R
import ru.domovoy.core.Device
import ru.domovoy.core.Reading
import java.time.Instant
import java.util.Locale

/** Every new type is visible immediately; controls require a dedicated, verified integration. */
@Composable
internal fun DiscoveredDeviceTile(
    tile: Device,
    now: Instant,
    modifier: Modifier = Modifier,
    error: String? = null,
    showRoom: Boolean = false,
    onOpen: () -> Unit,
) {
    val readings = discoveredReadings(tile)
    val primary = readings.firstOrNull()
    val age = oldest(readings.take(2).map { it.reading })?.let { sheetAge(it, now) }
    TileCard(
        anatomy = TileAnatomy(
            art = discoveredArt(tile),
            action = null,
            name = tile.name,
            promoted = primary?.value,
            status = listOfNotNull(primary?.label, age).joinToString(" · ").ifEmpty { "Нет показаний · Время неизвестно" },
            detail = readings.drop(1).firstOrNull()?.let { "${it.label}: ${it.value}" },
            room = tile.room,
        ),
        paint = paint(tile.onOff?.isOn, ownError = null, groupError = error),
        modifier = modifier,
        showRoom = showRoom,
        format = TileFormat.Reading,
        onClick = onOpen,
    )
}

/** No write actions are exposed, including for locks and intercom relays advertised as on/off. */
internal fun sheet(tile: Device, now: Instant, error: String?): TileSheet = TileSheet(
    id = tile.id,
    name = tile.name,
    room = tile.room,
    art = discoveredArt(tile),
    readings = discoveredReadings(tile).map { SheetReading(it.label, it.value, sheetAge(it.reading, now)) }.ifEmpty { listOf(SheetReading("Состояние", "Нет показаний", "Время неизвестно")) },
    actions = emptySet(),
    isOn = tile.onOff?.isOn,
    level = null,
    notUpdating = error,
)

/** Properties precede capabilities: a door contact is not the last relay command. */
private data class DiscoveredReading(val label: String, val value: String, val reading: Reading)

private fun discoveredReadings(tile: Device): List<DiscoveredReading> = buildList {
    tile.events.toSortedMap(compareBy<String> { if (it == "door" || it == "open") 0 else 1 }.thenBy { it }).forEach { (instance, event) ->
        add(DiscoveredReading(propertyLabel(instance), eventValue(event.value), event.lastUpdated))
    }
    tile.properties.toSortedMap(
        compareBy<String> {
            when (it) {
                "temperature" -> 0
                "humidity" -> 1
                "battery_level" -> 2
                else -> 3
            }
        }.thenBy { it },
    ).forEach { (instance, property) ->
        add(DiscoveredReading(propertyLabel(instance), propertyValue(property.value, property.unit), property.lastUpdated))
    }
    tile.onOff?.let { add(DiscoveredReading("Питание", power(it.isOn), it.lastUpdated)) }
    tile.ranges.forEach { (instance, range) ->
        add(DiscoveredReading(propertyLabel(instance), propertyValue(range.value, range.unit), range.lastUpdated))
    }
    tile.modes.forEach { (instance, mode) ->
        add(DiscoveredReading(capabilityLabel(instance), mode.current?.let(::capabilityLabel) ?: "Нет данных", mode.lastUpdated))
    }
    tile.toggles.forEach { (instance, toggle) ->
        add(DiscoveredReading(capabilityLabel(instance), power(toggle.isOn), toggle.lastUpdated))
    }
}

private fun propertyValue(value: Double?, unit: String?): String {
    if (value == null || !value.isFinite()) return "Нет данных"
    val number = String.format(Locale.ROOT, "%.1f", value).removeSuffix(".0")
    val suffix = when (unit) {
        "unit.temperature.celsius" -> " °C"
        "unit.percent" -> "%"
        "unit.volt" -> " В"
        "unit.watt" -> " Вт"
        "unit.ampere" -> " А"
        else -> ""
    }
    return number + suffix
}

private fun propertyLabel(instance: String): String = when (instance) {
    "temperature" -> "Температура"
    "humidity" -> "Влажность"
    "battery_level" -> "Батарея"
    "signal_level" -> "Сигнал"
    "voltage" -> "Напряжение"
    "power" -> "Мощность"
    "amperage" -> "Ток"
    "door", "open" -> "Дверь"
    "serviceability" -> "Исправность"
    "button" -> "Кнопка"
    "motion" -> "Движение"
    "noise" -> "Шум"
    "voice_activity" -> "Голос"
    else -> capabilityLabel(instance)
}

private fun eventValue(value: String?): String = when (value) {
    null -> "Нет данных"
    "opened" -> "Открыта"
    "closed" -> "Закрыта"
    "ajar" -> "Приоткрыта"
    "locked" -> "Заперта"
    "ok" -> "В порядке"
    "damaged" -> "Повреждение"
    "mechanical_interference" -> "Механическая помеха"
    "abnormal_tongue" -> "Ошибка ригеля"
    "click" -> "Нажатие"
    "double_click" -> "Двойное нажатие"
    "long_press" -> "Долгое нажатие"
    "detected" -> "Обнаружено"
    "not_detected" -> "Не обнаружено"
    else -> capabilityLabel(value)
}

/** Existing hardware art is reused where it describes the device; other types use a neutral glyph. */
private fun discoveredArt(tile: Device): Int = when {
    tile.vendorType == "devices.types.vacuum_cleaner" -> R.drawable.device_art_vacuum
    tile.vendorType == "devices.types.openable" -> R.drawable.device_art_intercom
    tile.vendorType == "devices.types.openable.door_lock" -> R.drawable.ic_device_lock
    tile.vendorType?.startsWith("devices.types.sensor") == true -> R.drawable.ic_device_sensor
    else -> R.drawable.ic_device_generic
}
