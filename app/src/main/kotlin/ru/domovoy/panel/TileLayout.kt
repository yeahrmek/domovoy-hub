package ru.domovoy.panel

import androidx.annotation.DrawableRes
import ru.domovoy.R
import java.time.Instant
import kotlin.math.roundToInt

/**
 * The number of columns the mosaic is laid out on.
 *
 * Twelve, and it is a divisor rather than a width: the wall tablet is 1600 px at 340 dpi, so the
 * panel is **753 dp** wide in the portrait it is mounted in, and twelve columns is what lets both
 * tile widths below divide it exactly. No tile is ever one column.
 *
 * **It was six, and six meant the widest tile was half the wall.** Two columns of anything is a
 * phone proportion — the reference smart-home app is two columns of a 411 dp phone, and copying the
 * count instead of the size gave this panel 376 dp tiles: a name, a value and a slider spread over
 * half a wall, with a great deal of nothing between them. Three across and four across is the
 * proportion a 753 dp panel actually has room for.
 *
 * Portrait is the only width this has to hold: the tablet is mounted vertically and its
 * auto-rotate is off. If that ever changes, this is the number that has to change with it.
 */
internal const val COLUMNS = 12

/**
 * A third of the panel, 251 dp: everything with a slider on it, and the recuperator that reports
 * climate. **Three of these fill a row exactly**, which is the point.
 *
 * Named for being the wider of the two rather than for a fraction, because the fraction has moved
 * once already — this was `HALF_SPAN` at 376 dp — and the tiles that take it are the tiles with
 * more to say, whatever fraction of the wall that turns out to be.
 */
internal const val WIDE_SPAN = 4

/**
 * A quarter of the panel, 188 dp: a name, a status line and a switch. The bulbs, the launchers, and
 * a recuperator with nothing beyond its speeds to report. **Four of these fill a row exactly.**
 *
 * 188 dp was measured once before and rejected — the launcher's one line wrapped onto two at that
 * width (docs/ui.md). It is not a rejection any more: under one anatomy every tile reserves the
 * same block of status lines whether it fills them or not, so a line that wraps costs nothing that
 * was not already spent. What that rejection was really about was two tiles of the same kind coming
 * out different heights, and that cannot happen now.
 */
internal const val NARROW_SPAN = 3

/**
 * How wide one recuperator is: the only span in the panel decided by content rather than by type.
 *
 * Wide when the device has a second line to put there, narrow when it does not — a wide tile
 * holding one line of "on · 2 min ago" is a hole in the wall.
 *
 * **Two things count as a second line**, and both are asked of the function that produces them
 * rather than re-derived here, so that the width and what goes in it cannot drift apart:
 *
 * - [climateLine], the temperature and humidity it measures. [Instant] is irrelevant to whether
 *   that line exists — it is present or absent whatever the ages are — so any instant does.
 * - **Its own error.** A recuperator that is not updating says why, and the reason is a vendor
 *   string of unbounded length; at 188 dp it is the one status line on the wall long enough to run
 *   past what a tile reserves for it. The tile with the most to say gets the width to say it, which
 *   is this function's whole rule.
 *
 * The group's error is deliberately not here. It fails all five at once, so letting it move spans
 * would re-lay the whole room out every time Tuya blinked.
 */
internal fun span(tile: RecuperatorTileState): Int {
    val hasSecondLine = climateLine(tile, Instant.EPOCH) != null || tile.error != null
    return if (hasSecondLine) WIDE_SPAN else NARROW_SPAN
}

/**
 * What a tile's colour says about it. The composable maps one of these to a Material colour role
 * and does no thinking of its own — the thinking is [mood], which is out here where a test reaches
 * it.
 */
enum class TileMood {
    On,
    Off,

    /** Reported nothing at all. Not [Off]: nobody has said this device is off. */
    Unknown,

    /** The poll behind the value failed, so the value is whatever it last was. */
    Failing,
}

/**
 * The mood of one tile, from the two things every tile has: whether it is on, and whether what is
 * behind it is failing.
 *
 * [Failing] outranks everything, including a perfectly good `isOn`: a tile whose poll failed is
 * showing a value nobody has confirmed, and painting it as merely on is the panel asserting
 * something it does not know.
 *
 * A null [isOn] with nothing failing is [Unknown] and never [Off] — 33 of the 116 recorded
 * capabilities have never reported, and the status lines have always said "unknown" rather than
 * "off" for them. The colours must not undo in paint what the strings were careful about.
 */
internal fun mood(
    isOn: Boolean?,
    error: String?,
): TileMood = when {
    error != null -> TileMood.Failing
    isOn == null -> TileMood.Unknown
    isOn -> TileMood.On
    else -> TileMood.Off
}

/**
 * What kind of thing a tile is, which is the other half of its colour. One colour for everything
 * that is on makes a wall where the air conditioner and the bedroom lamp are the same object.
 *
 * Three families and no more. A fourth hue on a panel read from four metres is decoration rather
 * than information, so everything that neither moves air nor makes light shares the quiet one.
 */
enum class TileHue {
    /** Air conditioners and recuperators: the two things in the flat that move air. */
    Climate,

    /** Bulbs and light strips. */
    Light,

    /** Curtains and launchers — what is left, and deliberately the family without a colour. */
    Neutral,
}

/**
 * The hue of one tile, from its type and from nothing else.
 *
 * Six overloads rather than one function over a sealed type, because the tile states are six
 * unrelated data classes and this is the whole of what they have in common. [isOn] is deliberately
 * not consulted by any of them: a lamp that is off is still a lamp, and whether the hue is used at
 * all is [mood]'s answer, not this one's. The composable maps the pair — see `tileColors`.
 */
internal fun hue(tile: AcTileState): TileHue = TileHue.Climate

internal fun hue(tile: RecuperatorTileState): TileHue = TileHue.Climate

internal fun hue(tile: BulbTileState): TileHue = TileHue.Light

internal fun hue(tile: LightStripTileState): TileHue = TileHue.Light

internal fun hue(tile: CurtainTileState): TileHue = TileHue.Neutral

internal fun hue(tile: LauncherTileState): TileHue = TileHue.Neutral

/**
 * The unit strings Yandex names, which is the only reason the panel is willing to print a degree
 * sign or a percent sign. A number whose unit the vendor did not report is printed bare — hanging a
 * unit on it would be the panel inventing one.
 */
private const val CELSIUS_UNIT = "unit.temperature.celsius"

private const val PERCENT_UNIT = "unit.percent"

/**
 * The one value a tile says at the size the wall is read at, or null when it has none.
 *
 * **One per tile, and one only.** Before this the air conditioner promoted its target and nothing
 * else on the wall promoted anything, so the curtain's position, the strip's brightness and the
 * recuperator's temperature — the numbers somebody walking past is actually deciding something
 * about — sat at 12sp inside a dot-separated run-on line with four ages. Which one of a tile's
 * values that is is a decision with a right and a wrong answer, so it is out here beside [hue] and
 * [span] where a test reaches it rather than inside six composables.
 *
 * **Null is an answer and not a gap.** A tile with no value to promote leaves the slot empty rather
 * than setting the word "unknown" at display size: the loudest type on the wall spent on the least
 * the panel knows. Nothing is dropped by that — the status line still prints "unknown", which is
 * where that word has always been said, and the tile's colour still says [TileMood.Unknown]. This
 * is the same shape [climateLine] and `colorLine` already have.
 *
 * **What is promoted does not move with [mood].** A failing poll leaves the last value on the wall —
 * that is the whole reason the tile keeps showing it — so the promoted value must not empty out
 * underneath a tile that has gone rose. Two axes again: this says *which* value, [mood] says how
 * much to trust it.
 *
 * Six overloads for the same reason [hue] and [glyph] have them: the tile states are six unrelated
 * data classes and there is no sealed type over them. Each is also the *only* formatter for that
 * value — the status lines call these and add the word for absent — so a tile cannot print one
 * number at the top and a differently-rounded one underneath.
 */
internal fun promoted(tile: AcTileState): String? {
    val target = tile.targetTemperature?.roundToInt() ?: return null
    return if (tile.unit == CELSIUS_UNIT) "$target °C" else "$target"
}

/**
 * "40% open" rather than "40%": the curtain is the one tile whose number is meaningless without the
 * word, and it is the same string its status line has always led with.
 */
internal fun promoted(tile: CurtainTileState): String? = tile.openPercent?.let { "${it.roundToInt()}% open" }

internal fun promoted(tile: LightStripTileState): String? {
    val percent = tile.brightnessPercent?.roundToInt() ?: return null
    return if (tile.unit == PERCENT_UNIT) "$percent%" else "$percent"
}

/**
 * The temperature and not the humidity, of the two the recuperator measures: it is the one somebody
 * standing in the hallway is deciding something about, and the humidity keeps its place on
 * [climateLine] with both ages.
 *
 * Formatted by [measured] rather than here, so the promoted value and the climate line cannot come
 * out rounded differently — the same reason [span] asks [climateLine] instead of re-deriving it.
 */
internal fun promoted(tile: RecuperatorTileState): String? = tile.temperature?.let { measured(it, DEGREES) }

/**
 * Nothing, in every state a bulb has — and a *named* bulb tile is by construction the bulb the panel
 * has no state for at all, since [bulbGroup] breaks out exactly the null ones. A lamp is on or off
 * and carries no number; the wall's largest type spent on inventing one would be the row of discs'
 * old problem in a new place.
 */
internal fun promoted(tile: BulbTileState): String? = null

/** Nothing. It reads nothing about the flat — the same reason it is the one tile taking no `now`. */
internal fun promoted(tile: LauncherTileState): String? = null

/**
 * The glyph one tile wears, as a drawable in `res/drawable/`: eight of them exported to vector XML
 * rather than `material-icons-extended`, which is a large artifact for eight of them and a
 * dependency, which is an "ask first". Seven are Material Symbols and the bulb's is Tabler's, which
 * is a decision rather than an accident. See docs/ui.md, "Icons".
 *
 * Overloads per tile state for the same reason [hue] has them, and — apart from the curtain's —
 * every one of them is a constant. A tile is recognised across a hallway by its shape and its glyph
 * long before its name is legible, which on a panel looked at on the way past is most of the
 * looking.
 */
@DrawableRes
internal fun glyph(tile: AcTileState): Int = R.drawable.ic_ac_unit

@DrawableRes
internal fun glyph(tile: RecuperatorTileState): Int = R.drawable.ic_mode_fan

/**
 * The outlined lamp, whatever the tile says — a constant like the other six, because the bulb's
 * state is carried by the colour it is drawn on and not by which lamp is drawn. It is the same lamp
 * a circle wears, so a named tile and a circle cannot come out as different lamps.
 */
@DrawableRes
internal fun glyph(tile: BulbTileState): Int = R.drawable.ic_bulb

/**
 * `wb_iridescent` rather than `horizontal_rule`, which was the other candidate: the plain rule is a
 * minus sign — correct as a shape, carrying no light, and indistinguishable from a divider on a
 * panel that has dividers.
 */
@DrawableRes
internal fun glyph(tile: LightStripTileState): Int = R.drawable.ic_wb_iridescent

@DrawableRes
internal fun glyph(tile: CurtainTileState): Int = curtainGlyph(tile.openPercent)

/**
 * The two launcher tiles, told apart by the app they open — the only thing a launcher tile carries
 * that says which device it is.
 *
 * The intercom is the named branch because it is the one that has to be right: it is why somebody
 * walks up to this panel at all. Mi Home is the other of the two the catalogue holds, and a third
 * launcher tile would need its own branch here — this `when` is where it goes.
 */
@DrawableRes
internal fun glyph(tile: LauncherTileState): Int = when (tile.packageName) {
    DOMONAP_PACKAGE -> R.drawable.ic_video_camera_front
    else -> R.drawable.ic_vacuum
}

/**
 * Named here rather than imported: `launcherTiles` knows it as a catalogue row and `DomonapCalls` as
 * the sender of a notification, which are different facts about the same string.
 */
private const val DOMONAP_PACKAGE = "com.domonap.app"

/**
 * The curtain's glyph, which is the one on the wall that carries **state** rather than labelling a
 * type — Material Symbols ships the covering icons as an open/closed pair, so the flat's one curtain
 * can say what it is doing from across the room instead of only in its status line.
 *
 * Closed at 0 and open above it. A curtain 40 % open is open; only a shut one is shut, and _the
 * threshold is a guess and is this one comparison_ — if a curtain that has crept to 2 % reads as
 * open on the wall and should not, this is the number.
 *
 * **A null position takes the open glyph, not the closed one.** The closed glyph is a positive claim
 * that the curtain is shut, and the panel does not know. Same rule the strings have always
 * followed — unknown is not off — and the paint must not undo what the words were careful about.
 */
@DrawableRes
internal fun curtainGlyph(openPercent: Double?): Int = if (openPercent != null && openPercent <= 0.0) {
    R.drawable.ic_vertical_shades_closed
} else {
    R.drawable.ic_vertical_shades
}

/**
 * What a tile puts in the **controls** slot: nothing, a switch, a slider, or both.
 *
 * The two are separate axes of one answer rather than four unrelated cases, because they sit in
 * two different places on the card — the switch on the top line beside the art, the slider on its
 * own band under it (see `TileCard`). A curtain has a slider and no switch, which is the pairing a
 * single "has controls" boolean could not have said.
 *
 * It changes no layout on its own: **both bands are reserved on every tile whether they are filled
 * or not**, which is what makes a bulb the same height as an air conditioner. What this is for is
 * being *asserted* — a tile type that quietly stopped offering its slider is a tile that used to be
 * drivable and now is not, and that is a change nobody would see in a screenshot of a wall where
 * the geometry did not move.
 */
enum class TileControls {
    /** Nothing to drive. The launcher, which only opens somebody else's app. */
    None,

    /** A switch and nothing else: the bulbs, the recuperators. */
    Toggle,

    /** A slider and nothing else: the curtain, which is a position and not a power state. */
    Level,

    /** Both: the air conditioner and the light strips. */
    ToggleAndLevel,
}

/**
 * What one tile offers to a finger, from its type and — for the three that have a slider — from
 * whether the vendor reported the bounds that slider needs.
 *
 * A tile whose vendor never sent bounds gets no slider rather than one over an invented range: the
 * same refusal [promoted] makes when it declines to set the word "unknown" at display size.
 *
 * Six overloads for the same reason [hue] and [glyph] have them — the tile states are six unrelated
 * data classes and there is no sealed type over them.
 */
internal fun controls(tile: AcTileState): TileControls = if (tile.bounds == null) TileControls.Toggle else TileControls.ToggleAndLevel

/** No switch: a curtain is a position, and "shut" is a position rather than a power state. */
internal fun controls(tile: CurtainTileState): TileControls = if (tile.bounds == null) TileControls.None else TileControls.Level

internal fun controls(tile: LightStripTileState): TileControls = if (tile.bounds == null) TileControls.Toggle else TileControls.ToggleAndLevel

/**
 * A switch and no slider. The fan speeds are three booleans on Tuya rather than a range, so there
 * is nothing here a slider would be the honest control for — see [RecuperatorTileState.speeds].
 */
internal fun controls(tile: RecuperatorTileState): TileControls = TileControls.Toggle

internal fun controls(tile: BulbTileState): TileControls = TileControls.Toggle

/**
 * Nothing. The tap is the whole tile — see [LauncherTile] — and it is the card that takes it, so
 * there is no control inside the tile to put in this slot.
 */
internal fun controls(tile: LauncherTileState): TileControls = TileControls.None

/**
 * **The five slots every tile on the wall fills, and the only five.**
 *
 * Before this there were five tile types with five internal rhythms: the air conditioner 169 dp
 * tall with a dead area under its slider, the strip beside it shorter, the recuperator shorter
 * again, the launchers shorter still. Ragged bottom edges across a mosaic, because there was a rule
 * for how two tiles *of one kind* were laid out and no rule at all across kinds.
 *
 * So the anatomy is one data class, produced by one pure function per tile type, and `TileCard`
 * draws it and decides nothing. Every slot is reserved on the card whether this answers with
 * something or with null, which is what makes the heights agree: **an empty slot is empty, not
 * absent**, and a kind cannot quietly re-flow into the space another kind is using.
 *
 * Out here rather than inside the composables for the reason [hue], [mood], [span] and [promoted]
 * are: a decision no test can reach is a decision nobody checks, and "does this tile type still
 * fill all five slots" is exactly the question a screenshot answers slowest.
 */
internal data class TileAnatomy(
    /**
     * **Art.** Top-left, the size every glyph on the wall is. A drawable today and photographs of
     * the hardware one day — that is its own task and this slot is where it lands.
     */
    @DrawableRes val art: Int,
    /** **Controls.** Which of the two bands the card reserves are filled. See [TileControls]. */
    val controls: TileControls,
    /** **Name.** What the device is called on the wall — never truncated, wrapped if it must be. */
    val name: String,
    /**
     * **Promoted value.** The one number this tile says at wall distance, or null when it has
     * none — see [promoted]. Null leaves the slot empty and does not collapse it.
     */
    val promoted: String?,
    /**
     * **Status line.** Everything CLAUDE.md requires a tile to be able to say and nobody reads from
     * four metres: the on/off in words, every age, and the reason when a poll stopped landing.
     */
    val status: String,
    /**
     * The status slot's second line, for the two tiles that have a reading with an age of its own —
     * the strip's colour and the recuperator's climate — and null for the four that do not. Part of
     * the status slot rather than a sixth one: it is the same words at the same size, and the slot
     * reserves room for it on every tile whether or not it arrives.
     */
    val detail: String?,
)

/**
 * The air conditioner's five slots. [error] is its group's — one Yandex call feeds every tile in
 * it — and reaches the status line, not the values: a failed poll leaves the last reading on the
 * wall, which is the whole reason the tile keeps showing it.
 */
internal fun anatomy(
    tile: AcTileState,
    now: Instant,
    error: String?,
): TileAnatomy = TileAnatomy(
    art = glyph(tile),
    controls = controls(tile),
    name = tile.name,
    promoted = promoted(tile),
    status = statusLine(tile, now, error),
    detail = null,
)

internal fun anatomy(
    tile: CurtainTileState,
    now: Instant,
    error: String?,
): TileAnatomy = TileAnatomy(
    art = glyph(tile),
    controls = controls(tile),
    name = tile.name,
    promoted = promoted(tile),
    status = statusLine(tile, now, error),
    detail = null,
)

/** The strip's, whose second line is the colour it reports and cannot be driven — see [colorLine]. */
internal fun anatomy(
    tile: LightStripTileState,
    now: Instant,
    error: String?,
): TileAnatomy = TileAnatomy(
    art = glyph(tile),
    controls = controls(tile),
    name = tile.name,
    promoted = promoted(tile),
    status = statusLine(tile, now, error),
    detail = colorLine(tile, now),
)

/**
 * The recuperator's, whose second line is the climate it measures — the same line [span] asks about
 * to decide how wide the tile is.
 *
 * [groupError] rather than `error`: this is the one tile with two kinds of bad news, and its own is
 * already on [RecuperatorTileState]. Both reach the status line; only the group's draws an outline.
 */
internal fun anatomy(
    tile: RecuperatorTileState,
    now: Instant,
    groupError: String?,
): TileAnatomy = TileAnatomy(
    art = glyph(tile),
    controls = controls(tile),
    name = tile.name,
    promoted = promoted(tile),
    status = statusLine(tile, now, groupError),
    detail = climateLine(tile, now),
)

internal fun anatomy(
    tile: BulbTileState,
    now: Instant,
    error: String?,
): TileAnatomy = TileAnatomy(
    art = glyph(tile),
    controls = controls(tile),
    name = tile.name,
    promoted = promoted(tile),
    status = statusLine(tile, now, error),
    detail = null,
)

/**
 * The launcher's, and the only one taking no `now`: nothing polls it, so it has no reading to age.
 * It still fills all five slots — an empty promoted value and a status line that says outright that
 * there is no state to read, which is the honest version of the age it does not have.
 */
internal fun anatomy(tile: LauncherTileState): TileAnatomy = TileAnatomy(
    art = glyph(tile),
    controls = controls(tile),
    name = tile.name,
    promoted = promoted(tile),
    status = statusLine(tile),
    detail = null,
)
