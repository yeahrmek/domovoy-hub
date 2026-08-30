package ru.domovoy.panel

import androidx.annotation.DrawableRes
import ru.domovoy.core.Bounds
import ru.domovoy.core.Reading
import java.time.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * **What kind of device a tap opens a sheet on.** One value per kind, including the one kind that
 * has no tile yet, because the whole point of the table below is that a kind cannot be given an
 * action by being forgotten.
 *
 * Not every tile is here and that is the other half of the answer — see [subject].
 */
enum class SheetSubject {
    AirConditioner,
    Curtain,
    LightStrip,
    Bulb,
    Recuperator,

    /**
     * **The Aqara door lock, which has no tile in `panel/` and has this row anyway.**
     *
     * Aqara's developer project is still in review (docs/aqara.md) and no key exists, so nothing
     * constructs this subject today. It is written down before the tile that would have to obey it,
     * because the rule it carries is the one an agent building "a detail sheet per device" breaks
     * first: the lock **reports and does not act**. Its row in [sheetActions] is empty and is
     * asserted as its own case rather than left to be inferred from an absence.
     */
    Lock,
}

/**
 * **The whole vocabulary of things a sheet may do**, and every one of them is a request this panel
 * has actually sent to a real device.
 *
 * The reference app's sheet has a good deal more on it — a `Color` section with five swatches, a
 * `Modes` list, `reset`, `add` — and none of that is here. Not as an omission: each rests on a
 * capability nobody has verified. `docs/yandex.md` still lists *what a `devices.capabilities.mode`
 * action body looks like* and *what a `color_setting` action body looks like* as open questions, and
 * `reset` is not a capability any vendor in this flat reported at all. `CLAUDE.md` refuses code
 * against an unverified endpoint outright and asks for the gap to be *said*; this is where it is
 * said. The recuperators' three fan speeds are the same refusal twice over — the command path is
 * unverified (docs/tuya.md) and Tuya is a metered monthly allowance, so a control somebody can
 * fidget with costs money as well as being a guess.
 */
enum class SheetAction(
    /** What the control announces itself as — the sheet's buttons are words, not glyphs. */
    internal val label: String,
) {
    /**
     * The device's own on/off. `devices.capabilities.on_off` on Yandex, verified end to end from
     * this tablet on a real bulb and a real air conditioner; `switch` on Tuya, which the recuperator
     * tile has always driven.
     *
     * It is the same switch the tile carries, drawn again at sheet size, and that repetition is
     * deliberate: [TileAction] refuses a *second* power control on one card because a tile with two
     * things on it that both switch the device off is a tile nobody trusts, and a sheet is not that
     * card — it is a second surface, opened on purpose, with the tile behind a scrim.
     */
    Power("power"),

    /**
     * The one number the vendor reported a range for: the air conditioner's target, the strip's
     * brightness, how far open the curtain is. `devices.capabilities.range`, verified on the tablet
     * against `ac-03`'s setpoint.
     */
    Level("level"),

    /** The top of that range in one press — the curtain, fully open. */
    Open("open"),

    /** The bottom of it — the curtain, fully shut. */
    Close("close"),
}

/**
 * **What a sheet offers for a kind of device, and the lock's row is empty.**
 *
 * A pure function of the type and of nothing else, out here beside [controls], [action] and [span]
 * for the reason all of them are: a decision that only exists inside a `@Composable` is a decision
 * no test can reach, and "did the lock quietly acquire an unlock button" is exactly the question a
 * screenshot answers worst.
 *
 * **[SheetSubject.Lock] is `emptySet()` and that is the load-bearing line in this file.** `CLAUDE.md`
 * and `docs/aqara.md` agree: the lock tile reports and does not act — no unlock, no open, no door
 * release, and no power switch that could be mistaken for one. A door lock openable from a panel in
 * the hallway is a door lock openable by whoever is standing in the hallway. The row exists with no
 * tile behind it so that the rule is written before the tile is, rather than after.
 *
 * What each of the other five may offer is narrowed by what the device itself reported — see the
 * overloads below, which drop the level on a vendor that named no bounds.
 */
internal fun sheetActions(subject: SheetSubject): Set<SheetAction> = when (subject) {
    SheetSubject.AirConditioner -> setOf(SheetAction.Power, SheetAction.Level)
    SheetSubject.LightStrip -> setOf(SheetAction.Power, SheetAction.Level)
    // The two ends as well as the slider: the curtain is the one device in the flat whose useful
    // positions are its extremes, and both of them are the same verified `range` action the slider
    // sends. The tile has room for one of the two — see [TileAction] — and the sheet has room for
    // both.
    SheetSubject.Curtain -> setOf(SheetAction.Level, SheetAction.Open, SheetAction.Close)
    SheetSubject.Bulb -> setOf(SheetAction.Power)
    // Power and nothing else. The three fan speeds are independent Tuya booleans whose command path
    // docs/tuya.md records as unverified, and Tuya is metered by the month — see [SheetAction].
    SheetSubject.Recuperator -> setOf(SheetAction.Power)
    SheetSubject.Lock -> emptySet()
}

/**
 * **Which kinds of tile a tap opens a sheet on — and which open none, which is a rule and not a gap.**
 *
 * Overloads per tile state for the reason [hue], [glyph] and [controls] have them: the tile states
 * are unrelated data classes and there is no sealed type over them. The two that answer null are
 * written out rather than left absent, because "this tile has nothing behind it" is a claim this
 * file is making:
 *
 * - **The launcher.** Xiaomi will issue no credentials, so the vacuum and the humidifier are a
 *   hosted widget and a launcher tile with no readable state at all, and Domonap has no API the
 *   panel calls. A sheet over either would be a scrim with nothing under it; the tap opens the
 *   vendor's own app, which is the whole of what those tiles are for.
 * - **The lights group.** Its tap is already spoken for — it opens the room's lamps in the grid, and
 *   each of those is a bulb with a sheet of its own one tap further in.
 */
internal fun subject(tile: AcTileState): SheetSubject = SheetSubject.AirConditioner

internal fun subject(tile: CurtainTileState): SheetSubject = SheetSubject.Curtain

internal fun subject(tile: LightStripTileState): SheetSubject = SheetSubject.LightStrip

internal fun subject(tile: BulbTileState): SheetSubject = SheetSubject.Bulb

internal fun subject(tile: RecuperatorTileState): SheetSubject = SheetSubject.Recuperator

internal fun subject(tile: LauncherTileState): SheetSubject? = null

internal fun subject(group: BulbGroup): SheetSubject? = null

/**
 * **One line of a sheet: a reading, and how old that one reading is.**
 *
 * The tile prints one age for the whole card — the oldest of what it shows — and that is right for a
 * wall read from four metres, where four timestamps in one paragraph was the thing that made it
 * unreadable. It is not right for somebody who has walked up and tapped: on `ac-01` the on/off and
 * the target were read 81 days apart, and which of the two the tile is under-claiming for is a fact
 * the panel holds and had nowhere to put. This is that nowhere.
 */
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
    /**
     * Where the control starts. The last value the device reported, or — when it has reported
     * none — the bottom of its own range, which is the one value certainly on the grid and is where
     * every slider on this panel already starts. The reading beside it still says "unknown".
     */
    val value: Double,
    /** What the device said it accepts. Never a constant: an action off the grid is rejected. */
    val bounds: Bounds,
)

/**
 * **Everything one sheet shows and offers**, produced by one pure function per tile type — the same
 * shape [TileAnatomy] has, and for the same reason: the composable draws it and decides nothing.
 *
 * **Nothing here is anywhere else's only home.** A number that moved off a tile into a sheet would
 * turn a glance into a walk, which is the rule this whole change is built to. Every promoted value a
 * tile prints is printed here too, by the same formatter; what is new is the per-reading ages, the
 * readings a 251 dp card had no width for, and the actions it had no room for.
 */
internal data class TileSheet(
    /** The device's own name, exactly as the tile prints it. */
    val name: String,
    /** Which room it is in, or null for the ones no vendor placed. */
    val room: String?,
    /** Its family, which is what the sheet's accents are coloured with — see [tileAccent]. */
    val hue: TileHue,
    /** The same art the tile wears, so the sheet is recognisably the thing that was tapped. */
    @DrawableRes val art: Int,
    /** Every reading behind this device, each with its own age. */
    val readings: List<SheetReading>,
    /** What the sheet offers a finger. Empty is an answer — see [sheetActions]. */
    val actions: Set<SheetAction>,
    /**
     * Whether the device is on, for the power control. Null when it has never said — the switch is
     * drawn unchecked, on the tile's rule, and the reading above it says "unknown" rather than
     * "off".
     */
    val isOn: Boolean?,
    /** The range to drive, or null when the vendor named none. Non-null exactly when [actions] has
     * [SheetAction.Level] in it. */
    val level: SheetLevel?,
    /**
     * Why the panel is not updating this device, or null when it is. A sheet covers the tile it was
     * opened from, so the tile's bad news has to survive the tap — and it is the same four-word
     * reason the wall prints, never a vendor sentence.
     */
    val notUpdating: String?,
)

/** The word every tile on this wall uses for a value the vendor has never sent. */
private const val UNKNOWN = "unknown"

/**
 * The air conditioner's sheet: **the two readings the tile prints one age for**, each with its own.
 *
 * `ac-01` was read 81 days apart across its two capabilities and the tile says "81 d ago" for the
 * pair, under-claiming its freshness rather than over-claiming it. Which value is the old one is the
 * thing this answers.
 */
internal fun sheet(
    tile: AcTileState,
    now: Instant,
    error: String?,
): TileSheet = TileSheet(
    name = tile.name,
    room = tile.room,
    hue = hue(tile),
    art = glyph(tile),
    readings = listOf(
        SheetReading("power", power(tile.isOn), sheetAge(tile.powerLastUpdated, now)),
        // The same formatter the tile promotes with, so the number at the top of the card and the
        // number in the sheet cannot come out rounded differently.
        SheetReading("target", promoted(tile) ?: UNKNOWN, sheetAge(tile.temperatureLastUpdated, now)),
    ),
    actions = sheetActions(subject(tile)).driving(tile.bounds),
    isOn = tile.isOn,
    level = level(tile.targetTemperature, tile.bounds),
    notUpdating = error,
)

internal fun sheet(
    tile: CurtainTileState,
    now: Instant,
    error: String?,
): TileSheet = TileSheet(
    name = tile.name,
    room = tile.room,
    hue = hue(tile),
    art = glyph(tile),
    readings = listOf(
        SheetReading("position", promoted(tile) ?: UNKNOWN, sheetAge(tile.lastUpdated, now)),
    ),
    actions = sheetActions(subject(tile)).driving(tile.bounds),
    // No power: a curtain is a position, and "shut" is a position rather than a power state — the
    // same answer [controls] gives it on the tile.
    isOn = null,
    level = level(tile.openPercent, tile.bounds),
    notUpdating = error,
)

/**
 * The strip's, whose third reading is the colour — **shown, aged, and still not controllable**. The
 * reference app puts a row of swatches here; this panel has no colour action to send and says so, in
 * the words the tile has always used. See docs/yandex.md's open question about a `color_setting`
 * action body.
 */
internal fun sheet(
    tile: LightStripTileState,
    now: Instant,
    error: String?,
): TileSheet = TileSheet(
    name = tile.name,
    room = tile.room,
    hue = hue(tile),
    art = glyph(tile),
    readings = listOfNotNull(
        SheetReading("power", power(tile.isOn), sheetAge(tile.powerLastUpdated, now)),
        SheetReading("brightness", promoted(tile) ?: UNKNOWN, sheetAge(tile.brightnessLastUpdated, now)),
        // A strip with no `color_setting` at all gets no row: an absent capability is not a
        // capability that reported nothing, and the flat has one of each.
        tile.color?.let { colour ->
            SheetReading("colour", colorLine(tile) ?: UNKNOWN, sheetAge(colour.lastUpdated, now))
        },
    ),
    actions = sheetActions(subject(tile)).driving(tile.bounds),
    isOn = tile.isOn,
    level = level(tile.brightnessPercent, tile.bounds),
    notUpdating = error,
)

internal fun sheet(
    tile: BulbTileState,
    now: Instant,
    error: String?,
): TileSheet = TileSheet(
    name = tile.name,
    room = tile.room,
    hue = hue(tile),
    art = glyph(tile),
    readings = listOf(SheetReading("power", power(tile.isOn), sheetAge(tile.lastUpdated, now))),
    actions = sheetActions(subject(tile)),
    isOn = tile.isOn,
    level = null,
    notUpdating = error,
)

/**
 * **The recuperator's, and the sheet this whole change is easiest to justify by.** Four datapoints
 * are timestamped separately on the wire and the tile prints the oldest of them once; here they are
 * four rows with four ages, which is what the tile used to try to do on two lines and could not.
 *
 * [groupError] rather than `error`: this is the one tile with two kinds of bad news and its own is
 * already on [RecuperatorTileState]. Its own comes first, because Tuya charges a call per device — a
 * unit that timed out is *this* unit while the four beside it may be current.
 */
internal fun sheet(
    tile: RecuperatorTileState,
    now: Instant,
    groupError: String?,
): TileSheet {
    // Tuya's own reachability flag, and the tile's refusal kept intact: a device Tuya says is
    // offline is not confirming its switch or its speeds, so the sheet does not claim either. The
    // climate it measured is still printed — the tile keeps that too — with the age that says how
    // long ago it was measured.
    val offline = tile.online == false
    return TileSheet(
        name = tile.name,
        room = tile.room,
        hue = hue(tile),
        art = glyph(tile),
        readings = listOfNotNull(
            SheetReading(
                "power",
                if (offline) "offline" else power(tile.isOn),
                sheetAge(tile.powerLastUpdated, now),
            ),
            if (offline) {
                null
            } else {
                SheetReading("fan", speedLabel(tile), sheetAge(tile.speedLastUpdated, now))
            },
            SheetReading(
                "temperature",
                measured(tile.temperature, DEGREES),
                sheetAge(tile.temperatureLastUpdated, now),
            ),
            SheetReading(
                "humidity",
                measured(tile.humidity, PERCENT_SIGN),
                sheetAge(tile.humidityLastUpdated, now),
            ),
        ),
        actions = sheetActions(subject(tile)),
        isOn = tile.isOn,
        level = null,
        notUpdating = tile.error ?: groupError,
    )
}

/**
 * Drops the level and its two ends when the vendor named no bounds — the same refusal [controls] and
 * [action] make on the tile, in one place for all three kinds.
 */
private fun Set<SheetAction>.driving(bounds: Bounds?): Set<SheetAction> = if (bounds != null) {
    this
} else {
    this - setOf(SheetAction.Level, SheetAction.Open, SheetAction.Close)
}

/** The range to drive and where in it to start, or null when the vendor named no range. */
private fun level(
    value: Double?,
    bounds: Bounds?,
): SheetLevel? = bounds?.let { SheetLevel(value = value ?: it.min, bounds = it) }

/**
 * **How old a reading is, said in full — and a sheet always says it.**
 *
 * Deliberately not [ageLine], which is the wall's wording and says *nothing at all* under an hour:
 * "3 min ago" repeated across twelve tiles is the noise the wall was cleared of, and a reading
 * confirmed by dozens of polls is not news to somebody walking past. A sheet is the other case
 * entirely — it is opened on purpose by somebody standing at the panel, and "how old is this,
 * exactly" is the question they opened it with. So this has a minutes case and no threshold to hold
 * its tongue behind.
 *
 * Milliseconds and not `Duration.between`, on the rule [ageLine] and [isStale] already follow: a
 * stamp dated in the future — a tablet whose clock jumped back — reads as fresh rather than absurd.
 *
 * `Reading.Never` is "never read" whatever the clock says: 33 of the 116 recorded capabilities have
 * never reported, and formatting their `0.0` as a date would put *1 Jan 1970* on the wall.
 */
internal fun sheetAge(
    reading: Reading,
    now: Instant,
): String = when (reading) {
    Reading.Never -> "never read"
    is Reading.At -> {
        val age = (now.toEpochMilli() - reading.instant.toEpochMilli()).milliseconds
        when {
            age < 1.minutes -> "just now"
            age < 1.hours -> "${age.inWholeMinutes} min ago"
            age < 1.days -> "${age.inWholeHours} h ago"
            else -> "${age.inWholeDays} d ago"
        }
    }
}
