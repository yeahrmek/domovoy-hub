package ru.domovoy.panel

import androidx.annotation.DrawableRes
import ru.domovoy.core.Bounds
import ru.domovoy.core.ColorSetting
import ru.domovoy.core.Mode
import ru.domovoy.core.Reading
import ru.domovoy.core.Toggle
import java.time.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/** Device types with detail sheets. Aqara is not integrated and must remain read-only. */
enum class SheetSubject {
    AirConditioner,
    Curtain,
    LightStrip,
    Bulb,
    Recuperator,
}

/** Verified sheet actions, narrowed further by each device’s advertised capabilities. */
enum class SheetAction(
    /** What the control announces itself as — the sheet's buttons are words, not glyphs. */
    internal val label: String,
) {
    /** The device’s own power capability. */
    Power("Питание"),

    /** A numeric control with vendor-reported bounds. */
    Level("Уровень"),

    /** The top of that range in one press — the curtain, fully open. */
    Open("Открыть"),

    /** The bottom of it — the curtain, fully shut. */
    Close("Закрыть"),

    /** One of the values a device advertised for a mode instance. */
    Mode("Режим"),

    /** A secondary boolean capability, such as ionization. */
    Toggle("Переключить"),

    /** RGB swatches and scene presets on the one bulb that advertises them. */
    Color("Цвет"),

    /** One of the three verified Tuya recuperator speed datapoints. */
    Speed("Скорость"),
}

/** Actions supported by a device type; instance capabilities narrow this set below. */
internal fun sheetActions(subject: SheetSubject): Set<SheetAction> = when (subject) {
    SheetSubject.AirConditioner ->
        setOf(SheetAction.Power, SheetAction.Level, SheetAction.Mode, SheetAction.Toggle)
    SheetSubject.LightStrip -> setOf(SheetAction.Power, SheetAction.Level)

    SheetSubject.Curtain -> setOf(SheetAction.Level, SheetAction.Open, SheetAction.Close)
    SheetSubject.Bulb -> setOf(SheetAction.Power, SheetAction.Level, SheetAction.Color)
    SheetSubject.Recuperator -> setOf(SheetAction.Power, SheetAction.Speed)
}

/** The detail-sheet type for a controllable tile. */
internal fun subject(tile: AcTileState): SheetSubject = SheetSubject.AirConditioner

internal fun subject(tile: CurtainTileState): SheetSubject = SheetSubject.Curtain

internal fun subject(tile: LightStripTileState): SheetSubject = SheetSubject.LightStrip

internal fun subject(tile: BulbTileState): SheetSubject = SheetSubject.Bulb

internal fun subject(tile: RecuperatorTileState): SheetSubject = SheetSubject.Recuperator

/** A reading with its own age; tiles instead summarize their oldest displayed reading. */
internal data class SheetReading(
    /** What this reading is: `power`, `target`, `position`, `brightness`, `colour`, `humidity`. */
    val label: String,
    /** What it says — or the word for a value the vendor has never sent, never a stand-in number. */
    val value: String,
    /** How old it is, in full and always said. See [sheetAge]. */
    val age: String,
)

/** The range a sheet's level control drives, and where in it the last reading sat. */
internal data class SheetLevel(
    /** Start at the last reading or the vendor range minimum when unknown. */
    val value: Double,
    /** What the device said it accepts. Never a constant: an action off the grid is rejected. */
    val bounds: Bounds,
)

/** Detail-sheet content and actions. Tile readings remain visible on both surfaces. */
internal data class TileSheet(
    val id: String,
    /** The device's own name, exactly as the tile prints it. */
    val name: String,
    /** Which room it is in, or null for the ones no vendor placed. */
    val room: String?,
    /** The same art the tile wears, so the sheet is recognisably the thing that was tapped. */
    @DrawableRes val art: Int,
    /** Every reading behind this device, each with its own age. */
    val readings: List<SheetReading>,
    /** What the sheet offers a finger. Empty is an answer — see [sheetActions]. */
    val actions: Set<SheetAction>,
    /** The last power reading; null stays visibly unknown even though the control is unchecked. */
    val isOn: Boolean?,
    /**
     * The range to drive, or null when the vendor named none. Non-null exactly when [actions] has
     * [SheetAction.Level] in it.
     */
    val level: SheetLevel?,
    val levelLabel: String = "Яркость",
    val levelUnit: String = "%",
    /** Enumerated and boolean controls, exactly as this device advertised them. */
    val modes: Map<String, Mode> = emptyMap(),
    val toggles: Map<String, Toggle> = emptyMap(),
    /** Color metadata for an RGB/scene surface. Kelvin remains read-only until a live write reflects. */
    val color: ColorSetting? = null,
    /** Verified Tuya speeds; empty on every non-recuperator and on an offline recuperator. */
    val fanSpeeds: List<FanSpeed> = emptyList(),
    val selectedFanSpeeds: List<FanSpeed> = emptyList(),
    /** The same short failure reason shown on the tile. */
    val notUpdating: String?,
)

/** The word every tile on this wall uses for a value the vendor has never sent. */
private const val UNKNOWN = "Нет данных"

/** Show each capability’s reading and age separately. */
internal fun sheet(
    tile: AcTileState,
    now: Instant,
    error: String?,
): TileSheet = TileSheet(
    id = tile.id,
    name = tile.name,
    room = tile.room,
    art = art(tile),
    readings = listOfNotNull(
        SheetReading("Питание", power(tile.isOn), sheetAge(tile.powerLastUpdated, now)),
        SheetReading("Задано", promoted(tile) ?: UNKNOWN, sheetAge(tile.temperatureLastUpdated, now)),
        tile.measuredTemperature?.let {
            SheetReading("В комнате", measured(it, DEGREES), sheetAge(tile.measuredTemperatureLastUpdated, now))
        },
    ),
    actions =
    sheetActions(subject(tile)).driving(tile.bounds)
        .availableModes(tile.modes)
        .availableToggles(tile.toggles),
    isOn = tile.isOn,
    level = level(tile.targetTemperature, tile.bounds),
    levelLabel = "Температура",
    levelUnit = if (tile.unit == "unit.temperature.celsius") "°C" else "",
    modes = tile.modes,
    toggles = tile.toggles,
    notUpdating = error,
)

internal fun sheet(
    tile: CurtainTileState,
    now: Instant,
    error: String?,
): TileSheet = TileSheet(
    id = tile.id,
    name = tile.name,
    room = tile.room,
    art = art(tile),
    readings = listOf(
        SheetReading("Открытие", promoted(tile) ?: UNKNOWN, sheetAge(position(tile)?.reading ?: tile.lastUpdated, now)),
    ),
    actions = sheetActions(subject(tile)).driving(tile.bounds),
    isOn = null,
    level = level(tile.openPercent, tile.bounds),
    levelLabel = "Открытие",
    notUpdating = error,
)

/** Strip colour is displayed with its age; this sheet offers no unverified colour action. */
internal fun sheet(
    tile: LightStripTileState,
    now: Instant,
    error: String?,
): TileSheet = TileSheet(
    id = tile.id,
    name = tile.name,
    room = tile.room,
    art = art(tile),
    readings = listOfNotNull(
        SheetReading("Питание", power(tile.isOn), sheetAge(tile.powerLastUpdated, now)),
        SheetReading("Яркость", promoted(tile) ?: UNKNOWN, sheetAge(tile.brightnessLastUpdated, now)),
        tile.color?.let { colour ->
            SheetReading("Цвет", colorLine(tile) ?: UNKNOWN, sheetAge(colour.lastUpdated, now))
        },
    ),
    actions = sheetActions(subject(tile)).driving(tile.bounds),
    isOn = tile.isOn,
    level = level(tile.brightnessPercent, tile.bounds),
    levelUnit = if (tile.unit == "unit.percent") "%" else "",
    color = tile.color,
    notUpdating = error,
)

internal fun sheet(
    tile: BulbTileState,
    now: Instant,
    error: String?,
): TileSheet = TileSheet(
    id = tile.id,
    name = tile.name,
    room = tile.room,
    art = art(tile),
    readings = listOfNotNull(
        SheetReading("Питание", power(tile.isOn), sheetAge(tile.lastUpdated, now)),
        tile.brightnessBounds?.let {
            SheetReading(
                "Яркость",
                tile.brightnessPercent?.let { value -> "${value.toInt()} %" } ?: UNKNOWN,
                sheetAge(tile.brightnessLastUpdated, now),
            )
        },
        tile.color?.let { color ->
            SheetReading("Цвет", colorDescription(color), sheetAge(color.lastUpdated, now))
        },
    ),
    actions =
    sheetActions(subject(tile)).driving(tile.brightnessBounds)
        .availableColor(tile.color),
    isOn = tile.isOn,
    level = level(tile.brightnessPercent, tile.brightnessBounds),
    color = tile.color,
    notUpdating = error,
)

/** Show separate ages for each datapoint. Device errors take precedence over group errors. */
internal fun sheet(
    tile: RecuperatorTileState,
    now: Instant,
    groupError: String?,
): TileSheet {
    val offline = tile.online == false
    return TileSheet(
        id = tile.id,
        name = tile.name,
        room = tile.room,
        art = art(tile),
        readings = listOfNotNull(
            SheetReading(
                "Питание",
                if (offline) "Нет связи" else power(tile.isOn),
                sheetAge(tile.powerLastUpdated, now),
            ),
            if (offline) {
                null
            } else {
                SheetReading("Скорость", speedLabel(tile), sheetAge(tile.speedLastUpdated, now))
            },
            SheetReading(
                "Температура",
                measured(tile.temperature, DEGREES),
                sheetAge(tile.temperatureLastUpdated, now),
            ),
            SheetReading(
                "Влажность",
                measured(tile.humidity, PERCENT_SIGN),
                sheetAge(tile.humidityLastUpdated, now),
            ),
        ),
        actions =
        if (offline) {
            sheetActions(subject(tile)) - SheetAction.Speed
        } else {
            sheetActions(subject(tile))
        },
        isOn = tile.isOn,
        level = null,
        fanSpeeds = if (offline) emptyList() else FanSpeed.entries,
        selectedFanSpeeds = if (offline) emptyList() else tile.speeds,
        notUpdating = tile.error ?: groupError,
    )
}

/** Without vendor bounds, remove the level control and both range-end actions. */
private fun Set<SheetAction>.driving(bounds: Bounds?): Set<SheetAction> = if (bounds != null) {
    this
} else {
    this - setOf(SheetAction.Level, SheetAction.Open, SheetAction.Close)
}

private fun Set<SheetAction>.availableModes(modes: Map<String, Mode>): Set<SheetAction> = if (modes.isEmpty()) this - SheetAction.Mode else this

private fun Set<SheetAction>.availableToggles(toggles: Map<String, Toggle>): Set<SheetAction> = if (toggles.isEmpty()) this - SheetAction.Toggle else this

private fun Set<SheetAction>.availableColor(color: ColorSetting?): Set<SheetAction> = if (color?.instance == "rgb" || color?.scenes?.isNotEmpty() == true) this else this - SheetAction.Color

internal fun colorDescription(color: ColorSetting): String = when (color.instance) {
    "rgb" -> color.value?.toInt()?.let(::rgbLabel) ?: UNKNOWN
    "temperature_k" -> color.value?.toInt()?.let { "$it K" } ?: UNKNOWN
    "scene" -> color.scene?.let(::capabilityLabel) ?: UNKNOWN
    else -> UNKNOWN
}

/** The range to drive and where in it to start, or null when the vendor named no range. */
private fun level(
    value: Double?,
    bounds: Bounds?,
): SheetLevel? = bounds?.let { SheetLevel(value = value ?: it.min, bounds = it) }

/**
 * Sheets always show reading age, including minutes. Future timestamps are treated as fresh;
 * [Reading.Never] remains “never read”.
 */
internal fun sheetAge(
    reading: Reading,
    now: Instant,
): String = when (reading) {
    Reading.Never -> "Время неизвестно"
    is Reading.At -> {
        val age = (now.toEpochMilli() - reading.instant.toEpochMilli()).milliseconds
        when {
            age < 1.minutes -> "Только что"
            age < 1.hours -> "${age.inWholeMinutes} мин назад"
            age < 1.days -> "${age.inWholeHours} ч назад"
            else -> "${age.inWholeDays} дн. назад"
        }
    }
}

/** Readable colour names accompany the actual swatch; arbitrary RGB stays visually exact. */
private fun rgbLabel(rgb: Int): String = when (rgb) {
    0xFF0000 -> "Красный"
    0x00FF00 -> "Зелёный"
    0x0000FF -> "Синий"
    0xFFFFFF -> "Белый"
    0x000000 -> "Чёрный"
    else -> "Выбранный цвет"
}
