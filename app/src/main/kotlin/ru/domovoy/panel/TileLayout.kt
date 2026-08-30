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
 * holding one line of "on · no speed" is a hole in the wall.
 *
 * **Two things count as a second line**, and both are asked of the function that produces them
 * rather than re-derived here, so that the width and what goes in it cannot drift apart:
 *
 * - [climateLine], the temperature and humidity it measures. It takes no clock at all now that the
 *   tile prints one age on the line above, so this asks it outright rather than with the
 *   `Instant.EPOCH` it used to have to invent.
 * - **Its own error.** A recuperator that is not updating says why on that second line, and the
 *   reason takes the climate's place there while it lasts — see [TileAnatomy]. It is one of four
 *   words now rather than a vendor string of unbounded length, so this is no longer the line that
 *   could run past what a tile reserves for it; what it still is is a second line, and the tile
 *   with something to say on one gets the width to say it, which is this function's whole rule.
 *
 * The group's error is deliberately not here. It fails all five at once, so letting it move spans
 * would re-lay the whole room out every time Tuya blinked.
 */
internal fun span(tile: RecuperatorTileState): Int {
    val hasSecondLine = climateLine(tile) != null || tile.error != null
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
 * A room's lamps together: **on when any of them is lit**, off when none is.
 *
 * A group tile is one card standing for seven devices, and the thing somebody walking past a
 * hallway is reading off it is whether there is light in that room — not whether the majority of
 * its lamps agree. How many of the seven are on is said exactly, at wall size, by [promoted]; the
 * colour answers the coarser question the colour is good at.
 *
 * It takes no error, and that is [paint]'s doing rather than an omission: every error a lights
 * group has is its *group's* — one `/v1.0/user/info` is behind all 28 bulbs in the flat — so there
 * is no per-device failure here for a mood to carry.
 *
 * [TileMood.Unknown] is unreachable here by construction and that is [bulbGroup]'s doing: a bulb
 * with no state at all is not in the group, so `on` is a count of lamps that all reported. The
 * unknown ones are named tiles, where the word "unknown" is printed rather than implied.
 */
internal fun mood(group: BulbGroup): TileMood = mood(group.on > 0, error = null)

/**
 * **Which step of the panel's neutral ramp a tile's card sits on**, in ramp order.
 *
 * The five steps are `surfaceContainerLowest` up to `surfaceContainerHighest`, written out in
 * `PanelTheme.kt` and mapped to these by `tileColors`. Named for the position rather than for the
 * mood so that the ordering below is a property of the type and not of a comment.
 */
internal enum class TileSurface {
    Lowest,
    Container,
    High,
    Highest,
}

/**
 * **What a tile's surface says, which after this commit is its mood and nothing else.**
 *
 * The card used to be the tile's *family*: climate filled with `primaryContainer`, light with
 * `tertiaryContainer`, everything else with `secondaryContainer`, and anything failing with a full
 * `errorContainer`. On the wall that came out as a patchwork of coloured blocks — a deep blue air
 * conditioner beside a dark amber strip beside two saturated red rectangles — where the thing being
 * aimed at is one neutral dark grey for every tile and colour spent only on small marks. So the
 * hue moved to the accents ([TileMark], the glyph, the promoted value, the slider fill) and the
 * surface was left free to carry one thing.
 *
 * **It carries the mood, and the mood is an ordering: how much the tile is asserting.** A lit device
 * is the exception worth seeing on a wall of off ones and sits highest; a tile whose own poll failed
 * wants the eye more than a quiet one; an off tile is the resting step; a tile nobody has ever read
 * asserts the least and sinks below the wall's own surface. `PanelTheme.kt` already argued that the
 * neutral family is told apart by lightness rather than by hue — this is that answer applied to all
 * three families instead of left as one family's compromise.
 *
 * **This settles `Off` against `Unknown`**, which `docs/ui.md` filed under "Open" and
 * `docs/design/panel-redesign.md` deferred to whichever change decided what the neutral ramp
 * carries. They shared `surfaceContainer`, so a lamp the panel knew nothing about was the same
 * colour as one it knew was off and only the status line told them apart — the paint undoing what
 * the strings were careful about, which is the one thing the mosaic's colour rules keep refusing to
 * do everywhere else.
 *
 * _The steps are close together and that is what a neutral ramp has to give._ Measured on the four
 * roles this maps to: **light 90.1, 91.9, 94.0, 100.0 L\*** and **dark 22.1, 17.0, 12.2, 3.9** —
 * gaps of 2, 2 and 6 in light and 5, 5 and 8 in dark. So the ramp is a reinforcement and the mark,
 * the switch and the words are the signal; a wall that had to read four moods off lightness alone
 * would want a spread neither scheme can hold. The end step is the one that carries: [Lowest] sits
 * 2 L\* *past* the wall's own background in both schemes, so a tile nobody has read reads as a hole
 * rather than as a card, which is the intended answer and is the thing to look at in the hallway.
 */
internal fun surface(mood: TileMood): TileSurface = when (mood) {
    TileMood.On -> TileSurface.Highest
    TileMood.Failing -> TileSurface.High
    TileMood.Off -> TileSurface.Container
    TileMood.Unknown -> TileSurface.Lowest
}

/**
 * **The small saturated things a tile is allowed to draw**, now that its surface is neutral — the
 * whole state vocabulary of the wall, and there is nothing else.
 *
 * It was one mark per mood and it is a set per mood, which is the change: **a state is allowed more
 * than one way of saying itself.** The reference app says "on" three times over — a dot at the
 * corner of the art, a power button in the accent colour, and the art itself lighting up — and that
 * redundancy is deliberate rather than sloppy. This wall is read behind Samsung's blue light filter,
 * which erodes a saturated colour against a neutral (the reason a room heading's mark is a `•` as
 * well as a colour), so a mark carrying a state on its own is a state that can be lost.
 */
internal enum class TileMark {
    /** **On**: a filled dot in the tile's family accent, beside the art — see [TileHue]. */
    Family,

    /**
     * **On, again**: the tile's power control takes the family accent instead of neutral grey.
     *
     * It is the same fact as [Family] said in the place a finger is already going, and it costs
     * nothing that was not already drawn — the switch is on every tile that has a power state.
     */
    Power,

    /** **This device's own poll failed**: the glyph on a filled error chip. */
    Failure,

    /**
     * **The same, said as what it is**: a small struck-through wifi glyph in the error colour, at
     * the right of the tile's top line.
     *
     * [Failure] says *which* tile has bad news by taking over its art; this says *what* the bad news
     * is — the panel cannot get to this device. It is the reference's one offline mark and the only
     * glyph on the wall that is not a device.
     *
     * **A tile's own failure only.** A group's failure is an outline and stays one: one
     * `/v1.0/user/info` feeds 34 of the 35 tiles here, so a mark keyed on the group would draw this
     * glyph on nearly every tile in the flat at once — see [TilePaint].
     */
    Offline,
}

/**
 * The marks one tile wears, from its mood.
 *
 * This is where the colour budget went. A filled card said "on" with the whole surface and said
 * "failing" with the loudest thing available; a 20 dp dot, an accented switch, a 48 dp chip and a
 * 28 dp glyph say both things between them and cost an eighth of the tile.
 *
 * **The third of the reference's three on-marks is missing and is not forgotten: the art itself
 * lighting up.** That one needs a lit and an unlit image per device kind — photographs of the actual
 * hardware — and no agent can produce them. It is its own task, blocked on those assets, and this
 * enum is where its mark goes when they exist.
 *
 * The marks are deliberately *not* the only place either state is said: the status line says on, off
 * and why in words, and [TileSurface] moves under both.
 */
internal fun marks(mood: TileMood): Set<TileMark> = when (mood) {
    TileMood.On -> setOf(TileMark.Family, TileMark.Power)
    TileMood.Failing -> setOf(TileMark.Failure, TileMark.Offline)
    TileMood.Off, TileMood.Unknown -> emptySet()
}

/**
 * **The two kinds of bad news a tile can have, kept apart** — `docs/design/panel-redesign.md`
 * item 4.
 *
 * A tile's *own* failure fills: it is this device, and the fill is carrying something no outline
 * could. Its *group's* failure outlines: the poll behind a whole family of tiles stopped landing,
 * every one of them is equally uninformed, and the tile's own colour still has a job to do.
 *
 * **The arithmetic is why.** One `/v1.0/user/info` feeds every air conditioner, curtain, strip and
 * bulb in the flat, so one failed call used to turn about 34 of the 35 tiles into filled red
 * rectangles in a single frame — the mosaic's whole family coding erased at exactly the moment
 * somebody needs to work out what broke, with the loudest signal on the wall spent saying the thing
 * the wall changing colour had already said. The recuperator had this split right from the start and
 * was the only tile that did; this is the same rule for every kind.
 */
internal data class TilePaint(
    /** This tile's own state and its own bad news. Decides the surface and the mark. */
    val mood: TileMood,
    /** Whether the poll behind this tile's whole group stopped landing. Decides the outline. */
    val groupFailing: Boolean,
)

/**
 * How one tile is painted, from the two errors it can have. Out here rather than inside seven
 * composables for the reason [hue], [mood], [span] and [anatomy] are: a decision no test can reach
 * is a decision nobody checks, and "does a group failure still leave this kind of tile its colour"
 * is exactly the question a screenshot answers slowest.
 */
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

/**
 * The curtain has no switch to read a mood off, so its position is the mood: open at all is on,
 * fully shut is off, and never reported is unknown — the same three answers its status line gives,
 * in the same order.
 */
internal fun paint(
    tile: CurtainTileState,
    groupError: String?,
): TilePaint = paint(tile.openPercent?.let { it > 0 }, ownError = null, groupError = groupError)

internal fun paint(
    tile: LightStripTileState,
    groupError: String?,
): TilePaint = paint(tile.isOn, ownError = null, groupError = groupError)

internal fun paint(
    tile: BulbTileState,
    groupError: String?,
): TilePaint = paint(tile.isOn, ownError = null, groupError = groupError)

/**
 * The one tile with both kinds at once: Tuya charges a call per device for state, so a recuperator
 * that stopped answering is *this* recuperator and the four beside it may be perfectly current.
 */
internal fun paint(
    tile: RecuperatorTileState,
    groupError: String?,
): TilePaint = paint(tile.isOn, ownError = tile.error, groupError = groupError)

/** A room's lamps: one call behind all of them, so the only failure they have is the group's. */
internal fun paint(
    group: BulbGroup,
    groupError: String?,
): TilePaint = TilePaint(mood(group), groupFailing = groupError != null)

/**
 * The launcher's, and the only one taking no group: nothing polls it, so it has no group to fail.
 * The app being gone is its own failure and the only bad news it has — it fills, like any other
 * tile's own, and the package it is missing is on the status line.
 */
internal fun paint(tile: LauncherTileState): TilePaint = paint(
    isOn = null,
    ownError = tile.packageName.takeUnless { tile.openable },
    groupError = null,
)

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

/** A room's lamps are lamps. The group tile is in the same family as the seven it stands for. */
internal fun hue(group: BulbGroup): TileHue = TileHue.Light

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
 * [climateLine] beside it.
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
 * How many of the room's lamps are lit — `5 on` — which is the one thing the group tile has that is
 * worth the wall's largest type.
 *
 * It is the value the seven discs were incapable of saying: a row of circles made you count them,
 * and counting seven of anything from four metres is not reading. The count of lamps is the tile's
 * *name* rather than its value, because how many lamps a room has does not change and how many are
 * on does.
 *
 * Never null, unlike every other tile's: a group tile only exists when there are lamps behind it,
 * and `0 on` is a reading rather than an absence — every lamp in it reported, and they reported
 * off.
 */
internal fun promoted(group: BulbGroup): String = "${group.on} on"

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
 * the group tile wears, so a lamp on its own and a room's lamps together cannot come out as
 * different lamps.
 */
@DrawableRes
internal fun glyph(tile: BulbTileState): Int = R.drawable.ic_bulb

/**
 * The same lamp again, and one of it. A group tile drawing seven small lamps would be the row of
 * discs redrawn inside a card — the thing it replaces — and the count is already on the tile in
 * words, where it can say "7" without anybody counting.
 */
@DrawableRes
internal fun glyph(group: BulbGroup): Int = R.drawable.ic_bulb

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
 * Nothing, on the launcher's rule and for the launcher's reason: the tap is the whole card, and
 * what it does is open the lamps behind it.
 *
 * **Deliberately not a switch over all seven.** Yandex has no group action — one lamp is one call —
 * so a master switch here would be seven HTTP requests behind one finger, each able to fail
 * separately, with one status line to report the mixture. The lamps keep their own switches, on
 * their own tiles, one tap further in.
 */
internal fun controls(group: BulbGroup): TileControls = TileControls.None

/**
 * **The one small round button a tile may wear at its top right**, which is the second control the
 * reference app has on nearly every tile and this wall has never had.
 *
 * There is far less of it here than there, and the two reasons are both refusals this panel already
 * makes somewhere else.
 *
 * **A second action is a vendor write, and most of the ones the reference's buttons stand for have
 * never been sent.** Its air conditioner offers power and a fan mode; docs/yandex.md's open
 * questions still include *what a `devices.capabilities.mode` action body looks like for this AC and
 * whether it is accepted at all*, so a fan mode button here would be code against an endpoint nobody
 * has verified — which CLAUDE.md refuses outright, and asks to be *said* rather than guessed at. The
 * recuperators' three speeds are independent Tuya booleans whose command path is unverified in the
 * same way, and they carry a second refusal of their own: Tuya is a metered monthly allowance, so a
 * button that reads spends allowance every time somebody walks past and fidgets with it. The strip's
 * colour is reported and not controllable and says so, in words, on its own second line.
 *
 * **The lock is the rule here with no subject yet.** There is no lock tile in `panel/` — Aqara is
 * not wired to one — so there is no overload below to leave empty, and this is where the rule waits.
 * When that tile arrives it gets no action, no switch and no slider: CLAUDE.md and docs/aqara.md say
 * it reports and does not act, and that is a rule about *every* control on the tile rather than
 * about which button it is given. The launcher is the same answer for an unrelated reason and can be
 * asserted today — it opens somebody else's app and has no state to act on.
 *
 * **One button and never two, and that is width rather than taste.** A third-width tile is 251 dp,
 * 219 of it content once the tile's own padding and the card's are off. The art takes 48, the on
 * mark 28 with its gap, and the switch's touch box 64 whether a switch arrives or not — so 79 dp is
 * left for a control that has to be 64 dp square, since [MIN_TOUCH] is this panel's floor for
 * anything a finger lands on and the reference's "roughly a third the width of the art" is a phone's
 * measurement. One fits with 15 dp to spare and a second does not fit at all. On a quarter tile —
 * 156 dp of content — even the first does not, which is the other half of why the bulbs, the
 * launchers and the lights group have none.
 */
enum class TileAction(
    /**
     * What the button announces itself as, and the one `contentDescription` on this wall that is
     * not null: every other glyph here is decorative and sits next to a name that says the same
     * thing, and a button's whole meaning is what pressing it does.
     */
    internal val label: String,
) {
    /** Drive it to the top of the range its vendor reported: the curtain, fully open. */
    Open("open"),

    /** Drive it to the bottom of that range: the curtain, fully shut. */
    Close("close"),
}

/**
 * **What a tile's top-right button would do, or null when it has none** — a pure function of the
 * type and, on the one kind that has a button, of the position it is in.
 *
 * Out here beside [controls], [promoted] and [span] for the reason all of them are: which control a
 * kind of tile offers is a decision with a right and a wrong answer, and one that only exists inside
 * a `@Composable` is one no test can reach. The switch is deliberately not repeated here — power is
 * [TileControls.Toggle] and lives on the top line already, and a wall panel with two things on one
 * tile that both switch it off is a wall panel nobody trusts.
 *
 * Seven overloads for the reason [hue], [glyph] and [controls] have them: the tile states are
 * unrelated data classes and there is no sealed type over them. Six of them are the constant `null`
 * and are written out rather than defaulted, because "this kind has no second action" is an answer
 * this file is asserting and not a gap in it — see [TileAction] for what each of them refuses.
 */
internal fun action(tile: AcTileState): TileAction? = null

internal fun action(tile: LightStripTileState): TileAction? = null

internal fun action(tile: BulbTileState): TileAction? = null

internal fun action(tile: RecuperatorTileState): TileAction? = null

internal fun action(tile: LauncherTileState): TileAction? = null

internal fun action(group: BulbGroup): TileAction? = null

/**
 * **The curtain's, and the only button on the wall**: the end of travel it is not already at.
 *
 * It is the one second action this panel can honestly draw, because it is not a new capability at
 * all — it is the `range` action the slider under it already sends, at one of the two values that
 * are always on the grid. docs/yandex.md records that action working end to end.
 *
 * **Which end is the state half of this function.** A shut curtain can only be opened and anything
 * else can be shut; a button whose press changes nothing is the dead tap [LauncherTile] exists not
 * to be. Between the ends both would do something, and the one offered is Close — a wall panel's
 * one-tap action at the end of the day is shutting the curtain, and opening it part-way is what the
 * slider is for.
 *
 * **A position nobody has read takes the same branch [curtainGlyph] takes for it**: null is drawn
 * open, so the button offered is the one that shuts it. That is an action rather than a claim —
 * nothing this tile prints says the curtain is open, its status line says "unknown" — and the two
 * rules agreeing is the point, since a glyph saying open above a button offering to open would be
 * the paint and the control disagreeing on one tile.
 *
 * Null when the vendor named no bounds, which is [controls]'s refusal in the same place: with no
 * reported range there is no "fully open" to drive to, and a button that picks one is the panel
 * inventing a position.
 */
internal fun action(tile: CurtainTileState): TileAction? {
    val bounds = tile.bounds ?: return null
    val shut = tile.openPercent != null && tile.openPercent <= bounds.min
    return if (shut) TileAction.Open else TileAction.Close
}

/**
 * Where that button sends the curtain: the end of the range **the vendor reported**, not 0 and 100.
 *
 * 0 and 100 are this flat's numbers rather than the panel's, and Yandex can only reject what is off
 * the grid — a rejected action reaches the wall as "not updating" for a reason that was ours. Null
 * when the curtain named no bounds, which is the same state in which it is offered no button.
 */
internal fun actionTarget(
    tile: CurtainTileState,
    action: TileAction,
): Double? = tile.bounds?.let {
    when (action) {
        TileAction.Open -> it.max
        TileAction.Close -> it.min
    }
}

/**
 * **A button is drawn as the state it produces**: the same two shades glyphs the curtain's art is
 * already told apart by.
 *
 * So the tile says where the curtain is with a 48 dp glyph in its family accent and says where a
 * finger can send it with a 28 dp one in a quiet outlined circle — one picture, at two sizes, in two
 * weights. Nothing new was drawn for this, which is worth saying outright: the alternative was a
 * pair of arrows, and an arrow on a wall panel is a direction without a subject.
 */
@DrawableRes
internal fun glyph(action: TileAction): Int = when (action) {
    TileAction.Open -> R.drawable.ic_vertical_shades
    TileAction.Close -> R.drawable.ic_vertical_shades_closed
}

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
    /**
     * **The controls slot's second half**: the one small round button at the top right, or null on
     * the kinds that have none — which is most of them. See [TileAction] for what each of them
     * refuses and why there is never more than one.
     *
     * It is part of the controls slot rather than a sixth one: it is drawn on the art line, in the
     * 64 dp the line is already tall, so a tile that gains or loses a button does not move.
     */
    val action: TileAction?,
    /** **Name.** What the device is called on the wall — never truncated, wrapped if it must be. */
    val name: String,
    /**
     * **Promoted value.** The one number this tile says at wall distance, or null when it has
     * none — see [promoted]. Null leaves the slot empty and does not collapse it.
     */
    val promoted: String?,
    /**
     * **Status line.** Everything CLAUDE.md requires a tile to be able to say and nobody reads from
     * four metres: the on/off in words, **one age** — the oldest of the readings the tile is
     * showing, and nothing at all when they are all fresh — and the reason when a poll stopped
     * landing. It carried an age per value until this commit, which on the recuperator was four
     * timestamps on one tile and three of them the same number.
     */
    val status: String,
    /**
     * **The status slot's second line, and where every tile's bad news lives.**
     *
     * One rule, in this order: *why the panel is not updating this tile*, if it is not; otherwise
     * the tile's second reading — the strip's colour, the recuperator's climate — or, on the lights
     * group and the launcher, the one thing they have to say that is not a reading at all.
     *
     * **The reason moved here from the status line and it moved because of width.** A quarter tile
     * is 188 dp and its status line holds about sixteen characters of `bodyMedium`, so
     * `on · 20 d ago · not updating: unreachable` was never one line of anything: it wrapped, and a
     * wrapping status line is the one thing left that could still make two tiles of the same kind
     * come out different heights. It takes the second line outright rather than queueing behind
     * what is already there, because a second reading is stale by definition once the poll behind it
     * stopped landing — and it gives the line straight back when the poll comes back.
     *
     * **It carries no age.** The age of what is on this line is folded into the one the status line
     * prints — see [ageLine] — and none of the functions behind it takes a clock.
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
    action = action(tile),
    name = tile.name,
    promoted = promoted(tile),
    status = statusLine(tile, now),
    detail = error,
)

internal fun anatomy(
    tile: CurtainTileState,
    now: Instant,
    error: String?,
): TileAnatomy = TileAnatomy(
    art = glyph(tile),
    controls = controls(tile),
    action = action(tile),
    name = tile.name,
    promoted = promoted(tile),
    status = statusLine(tile, now),
    detail = error,
)

/**
 * The strip's, whose second line is the colour it reports and cannot be driven — see [colorLine] —
 * until the poll behind it stops landing, and then it is the reason. A colour last seen four polls
 * ago is not a reading worth a line over the news that the panel has stopped reading.
 */
internal fun anatomy(
    tile: LightStripTileState,
    now: Instant,
    error: String?,
): TileAnatomy = TileAnatomy(
    art = glyph(tile),
    controls = controls(tile),
    action = action(tile),
    name = tile.name,
    promoted = promoted(tile),
    status = statusLine(tile, now),
    detail = error ?: colorLine(tile),
)

/**
 * The recuperator's, whose second line is the climate it measures — the same line [span] asks about
 * to decide how wide the tile is — or, when it is not being read, why.
 *
 * [groupError] rather than `error`: this is the one tile with two kinds of bad news, and its own is
 * already on [RecuperatorTileState]. **Its own comes first**, because Tuya charges a call per device
 * and so a tile that timed out is *this* recuperator while the four beside it may be current. Both
 * reach the second line; only the group's draws an outline.
 */
internal fun anatomy(
    tile: RecuperatorTileState,
    now: Instant,
    groupError: String?,
): TileAnatomy = TileAnatomy(
    art = glyph(tile),
    controls = controls(tile),
    action = action(tile),
    name = tile.name,
    promoted = promoted(tile),
    status = statusLine(tile, now),
    detail = tile.error ?: groupError ?: climateLine(tile),
)

internal fun anatomy(
    tile: BulbTileState,
    now: Instant,
    error: String?,
): TileAnatomy = TileAnatomy(
    art = glyph(tile),
    controls = controls(tile),
    action = action(tile),
    name = tile.name,
    promoted = promoted(tile),
    status = statusLine(tile, now),
    detail = error,
)

/**
 * **The lights group's five slots**, which is one card standing for a room's whole set of lamps.
 *
 * The name is the count — `7 lamps` — the promoted value is how many of them are lit, and the
 * status line is how many are on and how old the oldest of their readings is. It opened with the
 * count as well until this commit, which put the tile's own name back on a line seven characters
 * shorter than the name it repeated; see [bulbGroupLine].
 *
 * [notUpdating] rather than an error alone, because a group can stop being read without any call
 * having failed — see [notUpdating] — and this tile is where the whole group's bad news is said
 * once instead of seven times. It is two different facts and gets two different words: a named
 * [error] is a call that came back wrong, and a bare "not updating" is a poll that simply stopped
 * landing with nothing to name.
 *
 * **The detail line is the one on this wall that describes a gesture rather than a reading**, and it
 * is here because this is the one tile whose tap has something behind it that the card cannot show:
 * the seven names. A wall panel that hides seven devices behind an unmarked card is a wall panel
 * that has hidden them — so the gesture keeps the line whenever there is no bad news to displace it,
 * which is nearly always, and gives it up when there is. The tap still works either way; the reason
 * the panel stopped reading is the one thing on this tile that cannot be found out by tapping.
 */
internal fun anatomy(
    group: BulbGroup,
    now: Instant,
    error: String?,
    notUpdating: Boolean,
    /** Whether the lamps are showing under it, which is the only thing the tile says about itself. */
    open: Boolean,
): TileAnatomy = TileAnatomy(
    art = glyph(group),
    controls = controls(group),
    action = action(group),
    name = lampCount(group),
    promoted = promoted(group),
    status = bulbGroupLine(group, now),
    detail =
    error
        ?: "not updating".takeIf { notUpdating }
        ?: if (open) "tap to close" else "tap to see them",
)

/**
 * The launcher's, and the only one taking no `now`: nothing polls it, so it has no reading to age.
 * It still fills all five slots — an empty promoted value, and two short lines rather than one long
 * one: what the tile is, and then either the honest version of the age it does not have or the
 * package it cannot open. See [detailLine], which is where the wall's one truncation lives.
 */
internal fun anatomy(tile: LauncherTileState): TileAnatomy = TileAnatomy(
    art = glyph(tile),
    controls = controls(tile),
    action = action(tile),
    name = tile.name,
    promoted = promoted(tile),
    status = statusLine(tile),
    detail = detailLine(tile),
)
