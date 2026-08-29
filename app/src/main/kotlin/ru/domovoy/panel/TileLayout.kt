package ru.domovoy.panel

import androidx.annotation.DrawableRes
import ru.domovoy.R
import java.time.Instant

/**
 * The number of columns the mosaic is laid out on.
 *
 * Six, and measured rather than guessed: the wall tablet is 1600 px at 340 dpi, so the panel is
 * **753 dp** wide in the portrait it is mounted in. The brief's first draft was four columns from
 * a 10" tablet nobody had measured, and on the real wall that made a hero tile 753 dp holding a
 * name, a temperature and a slider — the switch stranded 700 dp from the value it switches. Six
 * puts the hero at 376 dp and everything else within reach of it.
 *
 * Portrait is the only width this has to hold: the tablet is mounted vertically and its
 * auto-rotate is off. If that ever changes, this is the number that has to change with it.
 */
internal const val COLUMNS = 6

/**
 * Half the panel, 376 dp: everything with a slider on it, and the recuperator that reports climate.
 *
 * Two of these fill a row exactly, which is the point. Named for the geometry rather than for the
 * tile — "hero" stopped meaning anything once the curtain and the strips joined the air conditioner
 * at this width, and they joined it because at a third of the panel their status lines wrapped and
 * two strips side by side came out different heights.
 */
internal const val HALF_SPAN = 3

/**
 * A third of the panel, 251 dp: a name, one line and a switch. The bulbs, the launchers, and a
 * recuperator with no climate to report. Three of these fill a row.
 */
internal const val THIRD_SPAN = 2

/**
 * How wide one recuperator is: the only span in the panel decided by content rather than by type.
 *
 * Half the panel when the device reports climate, because there is a second line to put there; a
 * third when it reports neither, because a half-width tile holding one line of "on · 2 min ago" is
 * a hole in the wall.
 *
 * Asked of [climateLine] rather than of `temperature != null || humidity != null` on purpose: the
 * span exists to hold that line, so the two cannot drift apart. [Instant] is irrelevant to the
 * answer — the line is present or absent whatever the ages are — so any instant does.
 */
internal fun span(tile: RecuperatorTileState): Int = if (climateLine(tile, Instant.EPOCH) == null) {
    THIRD_SPAN
} else {
    HALF_SPAN
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
