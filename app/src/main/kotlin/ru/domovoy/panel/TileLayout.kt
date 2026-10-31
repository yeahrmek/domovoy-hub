package ru.domovoy.panel

import androidx.annotation.DrawableRes
import ru.domovoy.R
import ru.domovoy.core.Reading
import java.time.Instant
import kotlin.math.roundToInt

/** Twelve columns let the portrait panel fit either three wide or four narrow tiles. */
internal const val COLUMNS = 12

/** A third of the panel: tiles with a slider or a second reading. */
internal const val WIDE_SPAN = 4

/** A quarter of the panel: bulbs, launchers, and recuperators without climate readings. */
internal const val NARROW_SPAN = 3

/** Formats form separate rows, from detailed controls down to simple launch actions. */
internal enum class TileFormat(val minimumHeight: Int, val columns: Int) {
    Featured(304, 1),
    Wide(216, 1),
    Control(304, 2),
    Reading(240, 3),
    Compact(216, 4),
    Launcher(144, 2),
}

internal fun tileFormat(anatomy: TileAnatomy, hasLevel: Boolean): TileFormat = when {
    hasLevel -> TileFormat.Control
    anatomy.promoted != null || anatomy.action != null -> TileFormat.Reading
    else -> TileFormat.Compact
}

/** Keep device positions stable when climate readings arrive or a request fails. */
internal fun span(tile: RecuperatorTileState): Int = WIDE_SPAN

/** The state conveyed by a tile’s surface and marks. */
enum class TileMood {
    On,
    Off,

    /** Reported nothing at all. Not [Off]: nobody has said this device is off. */
    Unknown,

    /** The poll behind the value failed, so the value is whatever it last was. */
    Failing,
}

/** A device failure outranks its last power state; an absent power reading is unknown. */
internal fun mood(
    isOn: Boolean?,
    error: String?,
): TileMood = when {
    error != null -> TileMood.Failing
    isOn == null -> TileMood.Unknown
    isOn -> TileMood.On
    else -> TileMood.Off
}

/** A room is lit when any grouped lamp is on. Unknown lamps have separate tiles. */
internal fun mood(group: BulbGroup): TileMood = mood(group.on > 0, error = null)

/** The tile’s neutral surface roles, ordered from lowest to highest. */
internal enum class TileSurface {
    Lowest,
    Container,
    High,
    Highest,
}

/** Surface lightness reinforces mood; words and marks carry the same state. */
internal fun surface(mood: TileMood): TileSurface = when (mood) {
    TileMood.On -> TileSurface.Highest
    TileMood.Failing -> TileSurface.High
    TileMood.Off -> TileSurface.Container
    TileMood.Unknown -> TileSurface.Lowest
}

/** Accent and error marks reinforce the state expressed by the surface and text. */
internal enum class TileMark {
    /** A filled accent dot beside the device art. */
    Lit,

    /** The power control takes the accent when the device is confirmed on. */
    Power,

    /** A device failure adds an error-coloured offline glyph; group failures use an outline. */
    Offline,
}

/** Only on and failing states add marks. */
internal fun marks(mood: TileMood): Set<TileMark> = when (mood) {
    TileMood.On -> setOf(TileMark.Lit, TileMark.Power)
    TileMood.Failing -> setOf(TileMark.Offline)
    TileMood.Off, TileMood.Unknown -> emptySet()
}

/** Device failures affect mood; a shared poll failure adds an outline and preserves device state. */
internal data class TilePaint(
    /** This tile's own state and its own bad news. Decides the surface and the mark. */
    val mood: TileMood,
    /** Whether the poll behind this tile's whole group stopped landing. Decides the outline. */
    val groupFailing: Boolean,
)

/** Combine device state with independent device and group failures. */
internal fun paint(
    isOn: Boolean?,
    ownError: String?,
    groupError: String?,
): TilePaint = TilePaint(mood(isOn, ownError), groupFailing = groupError != null)

/** Yandex feeds every one of these from one call, so their only error is the group's. */
internal fun paint(
    tile: AcTileState,
    groupError: String?,
): TilePaint = paint(tile.isOn, ownError = null, groupError = groupError)

/** A fresh curtain position supplies its mood: open, shut, or unknown after it expires. */
internal fun paint(
    tile: CurtainTileState,
    now: Instant,
    groupError: String?,
): TilePaint = paint(confirmedPosition(tile, now)?.let { it > 0 }, ownError = null, groupError = groupError)

internal fun paint(
    tile: LightStripTileState,
    groupError: String?,
): TilePaint = paint(tile.isOn, ownError = null, groupError = groupError)

internal fun paint(
    tile: BulbTileState,
    groupError: String?,
): TilePaint = paint(tile.isOn, ownError = null, groupError = groupError)

/** Tuya polls each device separately, so a recuperator can have its own and a group error. */
internal fun paint(
    tile: RecuperatorTileState,
    groupError: String?,
): TilePaint = paint(tile.isOn, ownError = tile.error, groupError = groupError)

/** A room's lamps: one call behind all of them, so the only failure they have is the group's. */
internal fun paint(
    group: BulbGroup,
    groupError: String?,
): TilePaint = TilePaint(mood(group), groupFailing = groupError != null)

/** A launcher is not polled; an unavailable app is its only failure. */
internal fun paint(tile: LauncherTileState): TilePaint = paint(
    isOn = null,
    ownError = tile.packageName.takeUnless { tile.openable },
    groupError = null,
)

/** Print units only when the vendor reports them. */
private const val CELSIUS_UNIT = "unit.temperature.celsius"

private const val PERCENT_UNIT = "unit.percent"

/** The numeric reading shown at wall distance. Null reserves an empty slot; failures retain values. */
internal fun promoted(tile: AcTileState): String? {
    val target = tile.targetTemperature?.roundToInt() ?: return null
    return if (tile.unit == CELSIUS_UNIT) "$target °C" else "$target"
}

/** The last known position, with an age on the status line and sheet. See [confirmedPosition]. */
internal fun promoted(tile: CurtainTileState): String? = position(tile)?.let { percentOpen(it.percent) }

private fun percentOpen(position: Double): String = "${position.roundToInt()}%"

/** A curtain position and the reading used to determine its age. */
internal data class CurtainPosition(
    val percent: Double,
    val reading: Reading,
)

/**
 * Use the newer range or open/close command: neither capability is a position sensor.
 * Open/close requires vendor bounds; without them, the newer command invalidates the older percentage.
 */
internal fun position(tile: CurtainTileState): CurtainPosition? = when {
    isNewer(tile.openCloseLastUpdated, tile.lastUpdated) ->
        tile.bounds?.let { bounds ->
            tile.openClose?.let { open ->
                CurtainPosition(if (open) bounds.max else bounds.min, tile.openCloseLastUpdated)
            }
        }
    tile.openPercent != null -> CurtainPosition(tile.openPercent, tile.lastUpdated)
    else -> null
}

/**
 * Curtains do not report external movement, so only a recent command supports a promoted position.
 * Older commands remain visible with their age on the status line and sheet. See docs/yandex.md.
 */
internal fun confirmedPosition(
    tile: CurtainTileState,
    now: Instant,
): Double? = position(tile)?.takeUnless { isHistory(it.reading, now) }?.percent

internal fun promoted(tile: LightStripTileState): String? {
    val percent = tile.brightnessPercent?.roundToInt() ?: return null
    return if (tile.unit == PERCENT_UNIT) "$percent%" else "$percent"
}

/** Promote temperature; [climateLine] retains humidity and uses the same formatter. */
internal fun promoted(tile: RecuperatorTileState): String? = tile.temperature?.let { measured(it, DEGREES) }

/** Brightness is the bulb's useful numeric reading; a relay-backed light has no value to promote. */
internal fun promoted(tile: BulbTileState): String? = tile.brightnessPercent?.let { "${it.roundToInt()}%" }

/** Nothing. It reads nothing about the flat — the same reason it is the one tile taking no `now`. */
internal fun promoted(tile: LauncherTileState): String? = null

/** The lit count is the group’s promoted value; zero is a valid reading. */
internal fun promoted(group: BulbGroup): String = "${group.on} из ${group.lamps.size}"

/** Untinted hardware art. Bulbs and strips light up only for a positive on reading. */
@DrawableRes
internal fun art(tile: AcTileState): Int = R.drawable.device_art_air_conditioner

@DrawableRes
internal fun art(tile: RecuperatorTileState): Int = R.drawable.device_art_recuperator

@DrawableRes
internal fun art(tile: BulbTileState): Int = if (tile.isOn == true) {
    R.drawable.device_art_bulb_on
} else {
    R.drawable.device_art_bulb_off
}

/** A room's art is lit when at least one lamp in it is lit, which is the same rule [mood] uses. */
@DrawableRes
internal fun art(group: BulbGroup): Int = if (group.on > 0) {
    R.drawable.device_art_bulb_on
} else {
    R.drawable.device_art_bulb_off
}

@DrawableRes
internal fun art(tile: LightStripTileState): Int = if (tile.isOn == true) {
    R.drawable.device_art_light_strip_on
} else {
    R.drawable.device_art_light_strip_off
}

@DrawableRes
internal fun art(tile: CurtainTileState): Int = R.drawable.device_art_curtain

/** The two launchers are told apart by the hardware belonging to the app they open. */
@DrawableRes
internal fun art(tile: LauncherTileState): Int = when (tile.packageName) {
    DOMONAP_PACKAGE -> R.drawable.device_art_intercom
    else -> R.drawable.device_art_vacuum
}

private const val DOMONAP_PACKAGE = "com.domonap.app"

/**
 * One secondary action fits beside the art and reserved power target on wide tiles.
 * Narrow tiles have no room for it. Only verified vendor actions belong here; Aqara stays read-only.
 */
enum class TileAction(
    /** The accessible action label. */
    internal val label: String,
) {
    /** Drive it to the top of the range its vendor reported: the curtain, fully open. */
    Open("Открыть"),

    /** Drive it to the bottom of that range: the curtain, fully shut. */
    Close("Закрыть"),

    /** Advance to the next fan speed the device advertised. */
    Fan("Скорость вентилятора"),
}

/** A tile’s secondary action, or null when its capabilities or width allow none. */
internal fun action(tile: AcTileState): TileAction? = TileAction.Fan.takeIf { tile.modes["fan_speed"]?.available?.isNotEmpty() == true }

internal fun action(tile: LightStripTileState): TileAction? = null

internal fun action(tile: BulbTileState): TileAction? = null

internal fun action(tile: RecuperatorTileState): TileAction? = TileAction.Fan.takeIf {
    tile.isOn == true && tile.online != false && span(tile) == WIDE_SPAN
}

internal fun action(tile: LauncherTileState): TileAction? = null

internal fun action(group: BulbGroup): TileAction? = null

/**
 * Offer Open only for a freshly confirmed shut position; otherwise offer Close.
 * Bounds must come from the vendor, including when the position is unknown or expired.
 */
internal fun action(
    tile: CurtainTileState,
    now: Instant,
): TileAction? {
    val bounds = tile.bounds ?: return null
    val shut = confirmedPosition(tile, now)?.let { it <= bounds.min } == true
    return if (shut) TileAction.Open else TileAction.Close
}

/** Use the vendor’s range ends; no bounds means no action target. */
internal fun actionTarget(
    tile: CurtainTileState,
    action: TileAction,
): Double? = tile.bounds?.let {
    when (action) {
        TileAction.Open -> it.max
        TileAction.Close -> it.min
        TileAction.Fan -> return null
    }
}

/** The next AC fan value in the order this unit advertised, wrapping at the end. */
internal fun nextFanMode(tile: AcTileState): String? {
    val mode = tile.modes["fan_speed"] ?: return null
    if (mode.available.isEmpty()) return null
    val current = mode.available.indexOf(mode.current)
    return mode.available[(current + 1).mod(mode.available.size)]
}

/** The next verified Tuya speed, wrapping low → medium → high → low. */
internal fun nextFanSpeed(tile: RecuperatorTileState): FanSpeed {
    val current = tile.speeds.singleOrNull()
    return when (current) {
        FanSpeed.Low -> FanSpeed.Medium
        FanSpeed.Medium -> FanSpeed.High
        FanSpeed.High, null -> FanSpeed.Low
    }
}

/** The action icon depicts the target state; the device art identifies the hardware. */
@DrawableRes
internal fun glyph(action: TileAction): Int = when (action) {
    TileAction.Open -> R.drawable.ic_vertical_shades
    TileAction.Close -> R.drawable.ic_vertical_shades_closed
    TileAction.Fan -> R.drawable.ic_mode_fan
}

/** Content for the shared tile layout. Empty slots stay reserved to align tiles of different kinds. */
internal data class TileAnatomy(
    @DrawableRes val art: Int,
    /** Optional secondary action on the art row. */
    val action: TileAction?,
    /** **Name.** What the device is called on the wall — never truncated, wrapped if it must be. */
    val name: String,
    /** The wall-distance value; null keeps the slot empty. */
    val promoted: String?,
    /** Power or position and the oldest displayed reading age; fresh readings omit their age. */
    val status: String,
    /**
     * A poll failure takes precedence over the secondary reading or gesture hint.
     * Its reading age is included in [status].
     */
    val detail: String?,
    /** Room context shown when a tile is presented outside its own room section. */
    val room: String? = null,
)

private val ROOM_NAME_SUFFIXES = mapOf(
    "зал" to "в зале",
    "коридор" to "в коридоре",
    "гостиная" to "в гостиной",
    "спальня" to "в спальне",
    "спальня взрослая" to "во взрослой спальне",
    "детская" to "в детской",
    "маленькая детская" to "в маленькой детской",
    "кабинет" to "в кабинете",
    "гардероб" to "в гардеробе",
    "гардеробная" to "в гардеробной",
    "ванная" to "в ванной",
    "детская ванная" to "в детской ванной",
    "балкон" to "на балконе",
)

/** Remove only a known trailing room phrase; state and sheets retain the full vendor name. */
private fun tileName(name: String, room: String?): String {
    if (room.isNullOrBlank()) return name
    val suffixes = listOfNotNull(ROOM_NAME_SUFFIXES[room.trim().lowercase()], room.trim())
    val suffix = suffixes.firstOrNull { name.endsWith(" $it", ignoreCase = true) } ?: return name
    return name.dropLast(suffix.length).trimEnd().ifBlank { name }
}

/** A shared Yandex poll failure changes the detail line while retaining the last readings. */
internal fun anatomy(
    tile: AcTileState,
    now: Instant,
    error: String?,
): TileAnatomy = TileAnatomy(
    art = art(tile),
    action = action(tile),
    name = tileName(tile.name, tile.room),
    room = tile.room,
    promoted = promoted(tile),
    status = statusLine(tile, now),
    detail = error,
)

/** Promote only a fresh position; keep older positions with their age on the status line. */
internal fun anatomy(
    tile: CurtainTileState,
    now: Instant,
    error: String?,
): TileAnatomy = TileAnatomy(
    art = art(tile),
    action = action(tile, now),
    name = tileName(tile.name, tile.room),
    room = tile.room,
    promoted = confirmedPosition(tile, now)?.let(::percentOpen),
    status = statusLine(tile, now),
    detail = error,
)

/** Ordinary strip tiles carry errors; read-only colour metadata stays in the sheet. */
internal fun anatomy(
    tile: LightStripTileState,
    now: Instant,
    error: String?,
): TileAnatomy = TileAnatomy(
    art = art(tile),
    action = action(tile),
    name = tileName(tile.name, tile.room),
    room = tile.room,
    promoted = promoted(tile),
    status = statusLine(tile, now),
    detail = error,
)

/** Device failures outrank group failures, which outrank the climate detail. */
internal fun anatomy(
    tile: RecuperatorTileState,
    now: Instant,
    groupError: String?,
): TileAnatomy = TileAnatomy(
    art = art(tile),
    action = action(tile),
    name = tileName(tile.name, tile.room),
    room = tile.room,
    promoted = promoted(tile),
    status = statusLine(tile, now),
    detail = tile.error ?: groupError ?: climateLine(tile),
)

internal fun anatomy(
    tile: BulbTileState,
    now: Instant,
    error: String?,
): TileAnatomy = TileAnatomy(
    art = art(tile),
    action = action(tile),
    name = tileName(tile.name, tile.room),
    room = tile.room,
    promoted = promoted(tile),
    status = statusLine(tile, now),
    detail = error,
)

/** The group detail explains its tap, unless an error or stopped polling takes precedence. */
internal fun anatomy(
    group: BulbGroup,
    now: Instant,
    error: String?,
    notUpdating: Boolean,
    /** Whether the lamps are showing under it, which is the only thing the tile says about itself. */
    open: Boolean,
): TileAnatomy = TileAnatomy(
    art = art(group),
    action = action(group),
    name = "Свет",
    room = group.lamps.map { it.room }.distinct().singleOrNull(),
    promoted = promoted(group),
    status = bulbGroupLine(group, now),
    detail =
    error
        ?: "Нет обновлений".takeIf { notUpdating }
        ?: if (open) "Свернуть" else "Показать",
)

/** Launchers have no readings to age. Their detail identifies an unavailable app or absent state. */
internal fun anatomy(tile: LauncherTileState): TileAnatomy = TileAnatomy(
    art = art(tile),
    action = action(tile),
    name = tileName(tile.name, tile.room),
    room = tile.room,
    promoted = promoted(tile),
    status = statusLine(tile),
    detail = detailLine(tile),
)
