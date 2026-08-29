package ru.domovoy.panel

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The corner every tile on the wall wears. **One radius, not two.**
 *
 * It was 22 dp on a half tile and 18 dp on a third, derived from the span so that a tile's shape
 * and its width could not disagree. That was a rule about widths at a time when the mosaic had four
 * heights and no anatomy; with one anatomy the shape vocabulary is one shape, and a second radius
 * is a tile that looks like a mistake rather than like a size. docs/ui.md already recorded that on
 * the wall the two were "a real but subtle difference" nobody standing back from it could name.
 */
private val TILE_CORNER = 22.dp

/**
 * The smallest anything tappable is allowed to be — 64 dp, not the platform's 48. This is read and
 * touched at arm's length from a wall, standing up, often with one hand full; the platform's
 * minimum is sized for a phone held 30 cm from the face.
 */
internal val MIN_TOUCH = 64.dp

/**
 * Half the grid gutter, since each tile pads itself and two neighbours make the 8 dp. Done on the
 * tile rather than as the grid's arrangement so that the outer edge is padded too — a tile flush
 * against the bezel on a wall panel reads as a tile that has been cut off.
 */
private val TILE_PADDING = 4.dp

/** Inside the card, on all four sides. One padding, on every tile, whatever its span. */
private val TILE_CONTENT_PADDING = 12.dp

/**
 * The **art and controls** line: the glyph on the left, the switch on the right, and 64 dp of
 * height because that is what the switch's touch target needs whether or not there is a switch.
 */
private val ART_ROW = MIN_TOUCH

/**
 * The **level** band: the slider, drawn centred in it. [MIN_TOUCH] again, and for the same reason —
 * it is what [SlimSlider]'s own track slot measures, so a tile with a slider and a tile without one
 * reserve exactly the same strip of card.
 */
private val LEVEL_ROW = MIN_TOUCH

/** The **promoted value**: one line of `displaySmall`, whose line height is 52sp. */
private val PROMOTED_ROW = 52.dp

/**
 * The **name**: one line of `titleMedium`, whose line height is 28sp. A longer name wraps, and that
 * is the one place on the card where something still can.
 *
 * It is left that way on purpose while the status slot is capped. A device name is a string the
 * vendor supplies, so it could in principle be the thing that makes one tile taller than another —
 * but PLAN.md's reference table refuses truncated device names outright ("fine at 30 cm, useless at
 * four metres"), and a wrapped name is legible where a cut one is not. Nothing in this flat's 35
 * devices comes close: the longest, "Кондиционер", is 145 dp of the 156 a quarter tile gives it, and
 * every longer name in the catalogue is on a tile a third of the wall wide.
 */
private val NAME_ROW = 28.dp

/** One line of `bodyMedium`, whose line height is 24sp. The unit the status slot is built from. */
private val STATUS_LINE = 24.dp

/**
 * The **status line**: two lines of `bodyMedium`, **and this one is a ceiling rather than a
 * reserve.**
 *
 * It was four lines and a reserve, on the argument that a vendor error long enough to run past them
 * should make that tile taller rather than be swallowed. That argument had the priority backwards.
 * The status line was the last unbounded thing on the wall, so a vendor's error text — Java's
 * `Unable to resolve host "openapi.tuyaeu.com"`, arriving at whatever length it happened to be —
 * decided how tall a tile came out, and docs/ui.md calls *two tiles of the same kind coming out the
 * same height* the mosaic's proudest property. A string nobody in this flat controls could break it
 * at any moment.
 *
 * So the two lines are all there is, and each of them is one line: [StatusText] sets `maxLines = 1`
 * and ellipsises. **Nothing on this wall wraps.** What made that affordable rather than lossy is
 * everything else in this commit — the reason a poll failed is four words on the second line
 * instead of a vendor sentence on the first, the lights group stopped repeating its own name, an
 * offline recuperator stopped echoing what it can no longer confirm, and the launcher's package
 * moved to a line where it may be cut short. The one string still long enough to meet the ellipsis
 * is a package name, and `docs/design/panel-redesign.md` item 7 says outright that an identifier
 * that cannot be shortened is truncated rather than wrapped.
 *
 * The tile's *name* is deliberately not capped this way — see [NAME_ROW]. A device name is the one
 * thing PLAN.md refuses to truncate at any width.
 */
private val STATUS_ROW = STATUS_LINE * 2

/**
 * How tall every tile is: the five slots plus the padding, written out so the number is visible.
 *
 * **280 dp, and the same 280 for a bulb as for an air conditioner.** That is the point of the whole
 * file — the mosaic had four heights and ragged bottom edges because each kind laid itself out
 * around what it happened to have.
 *
 * It was 328 while the status slot reserved four lines for a string of unbounded length. Two of
 * those four were never filled by anything the flat produces, and the 48 dp they left at the foot of
 * every card was the reserve showing rather than padding. Capping the slot is what let it go — see
 * [STATUS_ROW].
 *
 * Still a minimum rather than a fixed height, so that nothing is ever clipped: the slots below sum
 * to exactly this, and the only one that can now exceed its share is the name — see [NAME_ROW].
 */
private val TILE_HEIGHT =
    ART_ROW + LEVEL_ROW + PROMOTED_ROW + NAME_ROW + STATUS_ROW + TILE_CONTENT_PADDING * 2

/**
 * **Every tile on the wall, drawn: one card, one anatomy, five slots, one height.**
 *
 * It decides nothing. What goes in the slots is [TileAnatomy]'s answer, which step of the neutral
 * ramp the card sits on is [surface]'s, what its accents and its marks are is [hue]'s and [mark]'s,
 * whether it is outlined is [paint]'s, and how wide it is is [span]'s — all of them pure functions a
 * test can reach. This lays them out and does so identically for all six tile types, which is the
 * thing that was missing: there was a rule for how two air conditioners agreed with each other and
 * no rule at all for how an air conditioner agreed with the launcher beside it.
 *
 * **Slot order, top to bottom.** Art and the switch on the top line and the words at the bottom is
 * the reference app's anatomy; the promoted value between them is this panel's one addition to it,
 * and the refusal that addition stands for is in PLAN.md — a wall panel is read without being
 * touched, so the value is the point and dropping it to look more like a phone app would be a
 * handsome thing that had stopped being a panel.
 *
 * **An empty slot is empty, not absent.** A launcher has no switch, no slider and no value, and it
 * reserves all three anyway. That is what buys bottom edges that line up across kinds, and it is
 * the cost of it too: a bulb tile carries a 64 dp band where a slider would go. The alternative —
 * each kind collapsing what it does not have — is the four ragged heights this replaces.
 */
@Composable
internal fun TileCard(
    /** What this tile puts in each of the five slots. */
    anatomy: TileAnatomy,
    /**
     * What kind of device this is, which is what the accents on it are coloured with: the glyph,
     * the promoted value, the on mark and the slider fill. **Not the card** — see [tileColors].
     */
    hue: TileHue,
    /**
     * This tile's state and both kinds of bad news it can have. One answer rather than a mood and a
     * border passed separately, so a caller cannot outline a tile whose mood disagrees — see
     * [TilePaint].
     */
    paint: TilePaint,
    modifier: Modifier = Modifier,
    /**
     * What the whole tile does when tapped, or null when it does nothing. Null rather than a
     * disabled flag: a tile that looks tappable and swallows the tap is the dead tap the launcher
     * tile exists not to be — see [LauncherTile].
     */
    onClick: (() -> Unit)? = null,
    /**
     * The switch, on the top line beside the art. Empty on the tiles [TileAnatomy.controls] says
     * have none, and the 64 dp it would have taken stays reserved.
     */
    toggle: @Composable () -> Unit = {},
    /** The slider, on its own band. Empty on the tiles that have none, and reserved all the same. */
    level: @Composable () -> Unit = {},
) {
    val shape = RoundedCornerShape(TILE_CORNER)
    val outer = modifier.fillMaxWidth().padding(TILE_PADDING).heightIn(min = TILE_HEIGHT)
    val colors = tileColors(paint.mood)
    val border = groupFailureBorder(paint)
    if (onClick == null) {
        Card(modifier = outer, shape = shape, colors = colors, border = border) {
            TileBody(anatomy, hue, paint.mood, toggle, level)
        }
    } else {
        // The clickable Card rather than a `Modifier.clickable` outside it, so the ripple is
        // clipped to the corner it is drawn on and the tap lands on the tile and not on the gutter.
        Card(onClick = onClick, modifier = outer, shape = shape, colors = colors, border = border) {
            TileBody(anatomy, hue, paint.mood, toggle, level)
        }
    }
}

/** The five slots in order. Split out only because [TileCard] draws two kinds of [Card]. */
@Composable
private fun TileBody(
    anatomy: TileAnatomy,
    hue: TileHue,
    mood: TileMood,
    toggle: @Composable () -> Unit,
    level: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(TILE_CONTENT_PADDING)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(ART_ROW),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TileArt(anatomy.art, hue, mood)
            TileStatusMark(hue, mood)
            Spacer(modifier = Modifier.weight(1f))
            // The 64 dp floor, for the switch inside it: a Switch is 52×32 dp, so the finger gets
            // the box rather than the graphic. Drawn whether or not a switch arrives, which is what
            // keeps the art line one height on all six kinds.
            Box(modifier = Modifier.touchable(), contentAlignment = Alignment.Center) { toggle() }
        }
        Slot(LEVEL_ROW) { level() }
        Slot(PROMOTED_ROW) { PromotedValue(anatomy.promoted, tileAccent(hue)) }
        Slot(NAME_ROW) {
            // Never truncated. A wall read from four metres cannot spend its one legible label on
            // "Свет в гарде…", which is the reference app's answer and the one PLAN.md refuses.
            Text(text = anatomy.name, style = MaterialTheme.typography.titleMedium)
        }
        // **The one slot that is a fixed height and not a floor.** Two lines, always exactly two
        // lines tall, and nothing a vendor sends can make it a third — which is what stops a
        // vendor's error text from deciding how tall a tile comes out. See [STATUS_ROW].
        Column(modifier = Modifier.fillMaxWidth().height(STATUS_ROW)) {
            StatusText(anatomy.status)
            // The second line of the same slot. Its own line rather than more dots on the first:
            // it carries the tile's second reading or the reason its poll stopped landing, and
            // either of them run onto the first is a line nobody reads at any size.
            anatomy.detail?.let { StatusText(it) }
        }
    }
}

/**
 * **One line of the status slot: exactly one line, whatever it was handed.**
 *
 * `maxLines = 1` and an ellipsis, which between them are the cap this whole commit is about. The
 * panel's own strings are all written to fit — the widest of them, "no state to read", is 124 dp of
 * the 156 a quarter tile gives it — so the ellipsis is not the normal case and is not meant to be.
 * What it is for is the string nobody here writes: a package name, and whatever a vendor invents
 * next. Truncating those is `docs/design/panel-redesign.md` item 7's own answer; wrapping them was
 * how one tile ended up 70 dp taller than the tile beside it.
 */
@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * One slot: at least [reserved] tall, full width, and empty when its content draws nothing.
 *
 * `heightIn` rather than `height` so that a name longer than its reserve grows the tile instead of
 * being cut off — see [NAME_ROW]. The status slot no longer uses this: it is the one slot with a
 * ceiling, and it draws itself in [TileBody].
 *
 * **Top-aligned**, which is what keeps a slot's content attached to what it belongs to rather than
 * floating in the middle of its reserve, with a gap above and a gap below.
 */
@Composable
private fun Slot(
    reserved: Dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = reserved),
        contentAlignment = Alignment.TopStart,
    ) {
        content()
    }
}

/**
 * How big a glyph is, **everywhere on the wall**: 48 dp, twice the 24 dp the drawables in
 * `res/drawable/` were exported at.
 *
 * 24 dp is a phone's icon size, measured for something held 30 cm from the face, and every glyph
 * here was at it — against a lamp on a bulb disc that had already been given 48 dp for exactly this
 * reason. The disc is gone and its lamp with it, but the number it was given survives it: the whole
 * set is at the size the one piece of art sized for this wall was already at.
 *
 * Scaling costs nothing: these are vector drawables, so 48 dp is redrawn rather than resampled, and
 * a stroke drawn at 2 of a 24 grid comes out at 4 dp — the whole glyph twice the size, which is what
 * legibility at four metres wants and not a hairline stretched over more pixels.
 *
 * It is *only* the art that grew. Nothing here changes what a tile says, which colour it says it in,
 * or how wide it is, and the anatomy's art slot already reserves 64 dp — so 48 fits where 24 sat and
 * no tile changes height.
 */
private val GLYPH_SIZE = 48.dp

/**
 * One tile's glyph, at the size every glyph on the wall is.
 *
 * [tint] defaults to `LocalContentColor`, which inside a [TileCard] is that card's content colour.
 * The card passes the tile's family accent instead — see [TileArt] — because the surface stopped
 * carrying the family and something had to keep carrying it.
 *
 * `contentDescription` is null on every one of them, deliberately: they are decorative. The name is
 * right there, and a screen reader announcing "lightbulb Лампа в коридоре" says the noun twice.
 */
@Composable
internal fun TileGlyph(
    @DrawableRes glyph: Int,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(glyph),
        contentDescription = null,
        modifier = modifier.size(GLYPH_SIZE),
        tint = tint,
    )
}

/** The corner on the chip a failing tile's glyph sits on. Softer than a tile's own 22 dp. */
private val CHIP_CORNER = 14.dp

/**
 * **The art slot: the glyph, in its family's colour — or on a filled error chip when this device's
 * own poll failed.**
 *
 * This is the larger half of what replaced the coloured card. A 48 dp glyph in `primary`,
 * `tertiary` or `secondary` says which family a tile belongs to from across a hallway without the
 * tile having to *be* that colour, and it says it in every mood: an unlit lamp is still a lamp, and
 * a tile whose group stopped answering still has to say what kind of thing it is — see [TilePaint].
 *
 * **[TileMark.Failure] fills**, and the fill is exactly the size of the glyph so that no tile's art
 * moves when it starts or stops failing. It was the whole card until now, which on the wall was two
 * of the twelve tiles on Главная being full saturated red rectangles — by a wide margin the loudest
 * thing on the panel, spending the strongest signal available on "this one is offline". A chip is
 * an eighth of the tile and says it once.
 */
@Composable
private fun TileArt(
    @DrawableRes glyph: Int,
    hue: TileHue,
    mood: TileMood,
) {
    if (mark(mood) == TileMark.Failure) {
        Box(
            modifier =
            Modifier.size(GLYPH_SIZE).clip(RoundedCornerShape(CHIP_CORNER))
                .background(MaterialTheme.colorScheme.error),
            contentAlignment = Alignment.Center,
        ) {
            TileGlyph(glyph, tint = MaterialTheme.colorScheme.onError)
        }
    } else {
        TileGlyph(glyph, tint = tileAccent(hue))
    }
}

/** How big the on mark is, and how far it sits from the glyph it follows. */
private val MARK_SIZE = 20.dp

private val MARK_GAP = 8.dp

/**
 * **The on mark**: a filled dot in the tile's family accent, beside the glyph, and nothing at all in
 * any other mood.
 *
 * The reference app's one green dot, in the panel's own colours rather than in a green it does not
 * have — so the dot says *on* and *which family* at once, which is what the filled card used to say
 * with the whole surface.
 *
 * It is drawn beside the switch that says the same thing, on the five kinds that have a switch, and
 * that is the point rather than a duplication: the curtain, the lights group and the launcher have
 * no switch at all, and a mark that appeared on some kinds and not others would be a wall with two
 * rules on it. Nothing moves when it appears — the row's spacer absorbs it.
 */
@Composable
private fun TileStatusMark(
    hue: TileHue,
    mood: TileMood,
) {
    if (mark(mood) != TileMark.Family) return
    Spacer(modifier = Modifier.width(MARK_GAP))
    Box(modifier = Modifier.size(MARK_SIZE).clip(CircleShape).background(tileAccent(hue)))
}

/**
 * The one value a tile says at wall distance, or nothing at all when it has none.
 *
 * `displaySmall` — 44sp on this panel's scale, see `panelTypography` — because this is the line the
 * whole type scale exists for: the 22 °C and the 33.5 % that CLAUDE.md says is the point of hanging
 * a panel on a wall. Everything else on the tile is read standing at it.
 *
 * **In the tile's family accent**, which is the other half of what replaced the coloured card: the
 * biggest thing on a tile is also the second-biggest carrier of what kind of thing that tile is. It
 * is the accent and not the container — `primary` rather than `primaryContainer` — because it is
 * written on a neutral surface now and has to show against it; every one of the three is at least
 * 5:1 there in both schemes, on 44sp type that needs 3.
 *
 * **Null draws nothing**, and that is [promoted]'s decision arriving intact. A tile with no value
 * has an empty slot rather than the word "unknown" set at 44sp; the status line under it still says
 * "unknown" in words and the tile's surface still says [TileMood.Unknown]. The slot it would have
 * filled stays reserved either way — see [TileCard].
 */
@Composable
internal fun PromotedValue(
    value: String?,
    color: Color = LocalContentColor.current,
    modifier: Modifier = Modifier,
) {
    if (value != null) {
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            color = color,
            modifier = modifier,
        )
    }
}

/** The width of that outline: thick enough to read from across a hallway, thin enough to be one. */
private val GROUP_FAILURE_OUTLINE = 3.dp

/**
 * The outline a tile wears when the poll behind its whole group stopped landing, or null when it did
 * not. It is the *only* thing that changes on such a tile: the surface, the glyph, the promoted
 * value and the mark all go on saying what they said — see [TilePaint].
 */
@Composable
private fun groupFailureBorder(paint: TilePaint): BorderStroke? = if (paint.groupFailing) {
    BorderStroke(GROUP_FAILURE_OUTLINE, MaterialTheme.colorScheme.error)
} else {
    null
}

/**
 * **Every tile on the wall is a step of the neutral ramp and nothing else.** The colour roles, and
 * only roles: no hex literal anywhere in `panel/`. A hardcoded colour is a tile that is unreadable
 * in one of the two themes, and the theme that breaks is the one nobody is looking at when they
 * check. The values are in `PanelTheme.kt` and nowhere else.
 *
 * **It used to take two axes and now takes one.** [TileHue] filled the card — climate the primary
 * container, light the tertiary, everything else the secondary, anything failing the error
 * container — and on the wall that came out as a patchwork of colour blocks rather than as a set of
 * tiles: a deep blue air conditioner, a dark amber strip, a mid-grey curtain, and two full saturated
 * red rectangles among twelve. The thing being aimed at spends its whole colour budget on three
 * small marks and paints every tile the same neutral dark grey.
 *
 * So the family moved to the accents — the glyph, the promoted value, the on mark, the slider fill,
 * all of them [tileAccent] — and the surface carries [surface], which is the mood. **One content
 * colour for all four steps**, because all four are neutral surfaces: `onSurface` is the pair for
 * every one of them and there is no longer a mood in which the text has to change with the card.
 *
 * **[TileMood.Failing] no longer fills the card**, which reverses commit 2's reversal and keeps what
 * both of them were right about. Commit 2 painted it neutral and that lost the signal; the commit
 * after it filled the card red and that cost the whole surface, at the moment the surface was most
 * needed. The mark is the third answer: [TileArt] puts the glyph on a filled error chip, which is
 * loud, local, and leaves the tile still saying what kind of thing it is.
 *
 * **The boot case goes with it.** Until the first poll lands every tile on Главная is `Unknown`
 * rather than rose — a wall of sunk, unmarked tiles, which is what "nothing has been read yet"
 * looks like. docs/ui.md held a `lastPolledAt == null` special case in reserve for this; it is not
 * needed.
 *
 * The *group's* failure is an outline and only an outline — see [groupFailureBorder] and
 * [TilePaint].
 */
@Composable
internal fun tileColors(mood: TileMood): CardColors = CardDefaults.cardColors(
    containerColor = when (surface(mood)) {
        TileSurface.Highest -> MaterialTheme.colorScheme.surfaceContainerHighest
        TileSurface.High -> MaterialTheme.colorScheme.surfaceContainerHigh
        TileSurface.Container -> MaterialTheme.colorScheme.surfaceContainer
        TileSurface.Lowest -> MaterialTheme.colorScheme.surfaceContainerLowest
    },
    contentColor = MaterialTheme.colorScheme.onSurface,
)

/**
 * **The colour a tile's family survives as**, now that no tile is painted with one: the accent of
 * each family rather than its container, because everything wearing it — the glyph, the promoted
 * value, the on mark, the slider fill — is drawn *on* a neutral surface and has to show against it.
 *
 * One table, read by [TileCard] and by [SlimSlider]. It was two until now, and the two agreed; the
 * last time this file had a second copy of a colour rule in it the copy had already drifted.
 */
@Composable
internal fun tileAccent(hue: TileHue): Color = when (hue) {
    TileHue.Climate -> MaterialTheme.colorScheme.primary
    TileHue.Light -> MaterialTheme.colorScheme.tertiary
    TileHue.Neutral -> MaterialTheme.colorScheme.secondary
}

/**
 * The 64 dp floor, for the controls inside a tile. A `Switch` is 52×32 dp and a `Slider` thinner
 * still; both sit inside a box this size so the finger has the whole of it.
 */
internal fun Modifier.touchable(): Modifier = sizeIn(minWidth = MIN_TOUCH, minHeight = MIN_TOUCH)
