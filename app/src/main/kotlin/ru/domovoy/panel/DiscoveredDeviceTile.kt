package ru.domovoy.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import ru.domovoy.R
import ru.domovoy.core.Device
import ru.domovoy.core.Reading
import java.time.Instant
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Discovered devices show readings and the controls verified for their type. */
@Composable
internal fun DiscoveredDeviceTile(
    tile: Device,
    now: Instant,
    modifier: Modifier = Modifier,
    error: String? = null,
    commandError: String? = null,
    pending: Boolean = false,
    showRoom: Boolean = false,
    controlledLight: BulbTileState? = null,
    onToggle: () -> Unit = {},
    onSetMode: (String, String) -> Unit = { _, _ -> },
    onSetHumidity: (Double) -> Unit = {},
    polledAt: Instant? = null,
    interval: Duration = 1.minutes,
    onOpen: () -> Unit,
) {
    if (tile.vendorType == "devices.types.sensor.climate") {
        ClimateSensorRow(tile, modifier, showRoom, commandError ?: error, onOpen)
        return
    }
    if (tile.vendorType == "devices.types.humidifier") {
        HumidifierTile(tile, now, modifier, commandError ?: error, pending, polledAt, interval, onToggle, onSetMode, onSetHumidity, onOpen)
        return
    }
    val readings = discoveredReadings(tile)
    val primary = readings.firstOrNull()
    val door = tile.vendorType == "devices.types.openable" || tile.vendorType == "devices.types.openable.door_lock"
    val pairedSwitch = "upper_button" in tile.events && "lower_button" in tile.events
    val light = switchLight(tile, listOfNotNull(controlledLight))
    val isOn = if (light != null) light.isOn else tile.onOff?.isOn
    val paint = paint(isOn, ownError = commandError, groupError = error)
    TileCard(
        anatomy = TileAnatomy(
            art = discoveredArt(tile),
            action = null,
            name = tile.name,
            promoted = if (light != null) "Нет данных".takeIf { light.isOn == null } else primary?.value.takeUnless { pairedSwitch },
            status = when {
                light != null -> ""
                pairedSwitch -> primary?.let { "${it.label}: ${it.value}" }.orEmpty()
                (door || canTogglePower(tile)) && primary != null -> ""
                else -> primary?.label ?: "Нет показаний"
            },
            detail = commandError ?: error ?: if (door || light != null) null else readings.drop(1).firstOrNull()?.let { "${it.label}: ${it.value}" },
            room = tile.room,
        ),
        paint = paint,
        modifier = modifier,
        pending = pending,
        showRoom = showRoom,
        format = if (pairedSwitch) TileFormat.Compact else TileFormat.Reading,
        onClick = onOpen,
        toggle = {
            if (canTogglePower(tile) || light != null) {
                TilePowerButton(tile.name, tile.room, isOn == true, paint.mood, onToggle, enabled = !pending)
            }
        },
    )
}

@Composable
private fun HumidifierTile(
    tile: Device,
    now: Instant,
    modifier: Modifier,
    error: String?,
    pending: Boolean,
    polledAt: Instant?,
    interval: Duration,
    onToggle: () -> Unit,
    onSetMode: (String, String) -> Unit,
    onSetHumidity: (Double) -> Unit,
    onOpen: () -> Unit,
) {
    var expanded by rememberSaveable(tile.id) { mutableStateOf(false) }
    val modes = humidifierModes(tile)
    val paint = paint(tile.onOff?.isOn, ownError = error, groupError = null)
    HomeDeviceTile(
        anatomy = TileAnatomy(discoveredArt(tile), null, tile.name, null, "", error, tile.room),
        paint = paint,
        polledAt = polledAt,
        now = now,
        interval = interval,
        modifier = modifier,
        pending = pending,
        commandError = error,
        onOpen = onOpen,
        showValue = false,
        toggle = {
            if (canTogglePower(tile)) {
                TilePowerButton(tile.name, tile.room, tile.onOff?.isOn == true, paint.mood, onToggle, enabled = !pending)
            }
        },
        controls = {
            ClimateReadings(tile, Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                if (modes.isNotEmpty()) {
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(MIN_TOUCH).semantics {
                            contentDescription = "${if (expanded) "Скрыть" else "Показать"} уровни: ${tile.name}, ${tile.room ?: "Без комнаты"}"
                            stateDescription = if (expanded) "Развёрнуто" else "Свёрнуто"
                        },
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp).rotate(if (expanded) 270f else 90f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            val target = tile.ranges["humidity"]
            target?.bounds?.let { bounds ->
                LevelControl(
                    id = tile.id,
                    value = target.value,
                    bounds = bounds,
                    onSetValue = onSetHumidity,
                    enabled = !pending,
                    label = "Целевая влажность",
                    unit = "%",
                    showBounds = false,
                    prominentValue = true,
                )
            }
            if (expanded) HumidifierModeControls(modes, onSetMode, enabled = !pending)
        },
    )
}

/** Units identify the visible readings; accessibility retains the full measurement names. */
@Composable
private fun ClimateReadings(
    tile: Device,
    modifier: Modifier = Modifier,
    arrangement: Arrangement.Horizontal = Arrangement.spacedBy(24.dp),
    between: @Composable () -> Unit = {},
) {
    Row(modifier, horizontalArrangement = arrangement, verticalAlignment = Alignment.CenterVertically) {
        listOf("temperature", "humidity").forEach { instance ->
            if (instance == "humidity") between()
            val property = tile.properties[instance]
            val value = property?.let { propertyValue(it.value, it.unit) }?.takeUnless { it == "Нет данных" } ?: "—"
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { contentDescription = "${propertyLabel(instance)}: $value" },
            )
        }
    }
}

/** Read-only climate readings share one slim row; smaller containers wrap below the identity. */
@Composable
private fun ClimateSensorRow(
    tile: Device,
    modifier: Modifier,
    showRoom: Boolean,
    error: String?,
    onOpen: () -> Unit,
) {
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().padding(6.dp).semantics {
            if (error != null) stateDescription = error
        },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
    ) {
        BoxWithConstraints(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            val inline = maxWidth / LocalDensity.current.fontScale >= 600.dp
            val identity: @Composable () -> Unit = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TileDeviceArt(discoveredArt(tile), Modifier.size(48.dp))
                    Column {
                        Text(tile.name, style = MaterialTheme.typography.titleMedium)
                        if (showRoom) {
                            Text(tile.room ?: "Без комнаты", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            val readings: @Composable () -> Unit = {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    ClimateReadings(tile)
                    if (error != null) {
                        Text("—", color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
            }
            if (inline) {
                Row(Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) { identity() }
                    readings()
                }
            } else {
                Column(Modifier.fillMaxWidth().heightIn(min = MIN_TOUCH), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    identity()
                    readings()
                }
            }
        }
    }
}

/** Power and modes follow the device capabilities; entry devices remain read-only. */
internal fun sheet(tile: Device, now: Instant, error: String?, controlledLight: BulbTileState? = null): TileSheet {
    val light = switchLight(tile, listOfNotNull(controlledLight))
    val humidity = tile.ranges["humidity"].takeIf { tile.vendorType == "devices.types.humidifier" }
    val level = humidity?.bounds?.let { SheetLevel(humidity.value ?: it.min, it) }
    val deviceReadings = discoveredReadings(tile).map { SheetReading(it.label, it.value, sheetAge(it.reading, now)) }
    val readings = listOfNotNull(light?.let { SheetReading("Свет гардероба", power(it.isOn), sheetAge(it.lastUpdated, now)) }) +
        deviceReadings
    return TileSheet(
        id = tile.id,
        name = tile.name,
        room = tile.room,
        art = discoveredArt(tile),
        readings = readings.ifEmpty { listOf(SheetReading("Состояние", "Нет показаний", "Время неизвестно")) },
        actions = buildSet {
            if (canTogglePower(tile) || light != null) add(SheetAction.Power)
            if (humidifierModes(tile).isNotEmpty()) add(SheetAction.Mode)
            if (level != null) add(SheetAction.Level)
        },
        isOn = if (light != null) light.isOn else tile.onOff?.isOn,
        level = level,
        levelLabel = "Целевая влажность",
        levelUnit = "%",
        modes = humidifierModes(tile),
        notUpdating = error,
    )
}

/** Properties precede capabilities: a door contact is not the last relay command. */
private data class DiscoveredReading(val label: String, val value: String, val reading: Reading)

private fun discoveredReadings(tile: Device): List<DiscoveredReading> = buildList {
    if (canTogglePower(tile)) tile.onOff?.let { add(DiscoveredReading("Питание", power(it.isOn), it.lastUpdated)) }
    tile.events.toSortedMap(compareBy<String> { if (it == "door" || it == "open" || it == "upper_button") 0 else 1 }.thenBy { it }).forEach { (instance, event) ->
        add(DiscoveredReading(propertyLabel(instance), eventValue(event.value), event.lastUpdated))
    }
    tile.properties.toSortedMap(
        compareBy<String> {
            when (it) {
                "power" -> if (canTogglePower(tile)) -1 else 3
                "temperature" -> 0
                "humidity" -> 1
                "battery_level" -> 2
                else -> 3
            }
        }.thenBy { it },
    ).forEach { (instance, property) ->
        add(DiscoveredReading(propertyLabel(instance), propertyValue(property.value, property.unit), property.lastUpdated))
    }
    if (!canTogglePower(tile)) tile.onOff?.let { add(DiscoveredReading("Питание", power(it.isOn), it.lastUpdated)) }
    tile.ranges.forEach { (instance, range) ->
        val label = if (instance == "humidity" && tile.vendorType == "devices.types.humidifier") "Целевая влажность" else propertyLabel(instance)
        add(DiscoveredReading(label, propertyValue(range.value, range.unit), range.lastUpdated))
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
    "upper_button" -> "Верхняя клавиша"
    "lower_button" -> "Нижняя клавиша"
    "motion" -> "Движение"
    "noise" -> "Шум"
    "voice_activity" -> "Голос"
    else -> capabilityLabel(instance)
}

internal fun eventValue(value: String?): String = when (value) {
    null -> "Нет данных"
    "opened" -> "Открыта"
    "closed" -> "Закрыта"
    "all_closed" -> "Все закрыты"
    "some_open" -> "Есть открытая дверь"
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

/** Cards and details share hardware art; unfamiliar models retain a neutral device illustration. */
private fun discoveredArt(tile: Device): Int = when (tile.vendorType) {
    "devices.types.vacuum_cleaner" -> R.drawable.device_art_vacuum
    "devices.types.openable" -> R.drawable.device_art_intercom
    "devices.types.openable.door_lock" -> R.drawable.device_art_door_lock
    "devices.types.sensor.climate" -> R.drawable.device_art_climate_sensor
    "devices.types.smart_speaker.yandex.station.cucumber" -> R.drawable.device_art_station_midi_green
    "devices.types.media_device.tv", "devices.types.media_device.tv.yandex.magritte" -> R.drawable.device_art_television
    "devices.types.switch" -> R.drawable.device_art_switch
    "devices.types.socket" -> R.drawable.device_art_socket
    "devices.types.humidifier" -> R.drawable.device_art_humidifier
    else -> R.drawable.device_art_generic
}
