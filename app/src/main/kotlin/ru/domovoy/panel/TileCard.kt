package ru.domovoy.panel

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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

/** The **name**: one line of `titleMedium`, whose line height is 28sp. A longer name wraps. */
private val NAME_ROW = 28.dp

/**
 * The **status line**: four lines of `bodyMedium`, whose line height is 24sp.
 *
 * Four rather than one, and this is the number the whole anatomy is sized around. The panel refuses
 * to hide state behind a tap, so a tile prints its on/off, both of its ages, its second reading
 * with two more ages, and the reason a poll stopped landing — a run-on of up to 90 characters, on a
 * card 251 or 188 dp wide. Four lines is what the longest of them measures on the narrower of the
 * two, and reserving it on every tile is what stops a bulb sitting 70 dp higher than the strip
 * beside it.
 *
 * _It is a reserve and not a ceiling._ A vendor error long enough to run past four lines makes that
 * one tile taller rather than being clipped or ellipsised — the panel does not swallow the reason a
 * thing is broken to keep a bottom edge straight. Nothing the flat has ever produced does that; the
 * failing recuperator, which is the longest line on the wall, is why [span] gives a tile with an
 * error the wider column.
 */
private val STATUS_ROW = 96.dp

/**
 * How tall every tile is: the five slots plus the padding, written out so the number is visible.
 *
 * **328 dp, and the same 328 for a bulb as for an air conditioner.** That is the point of the whole
 * file — the mosaic had four heights and ragged bottom edges because each kind laid itself out
 * around what it happened to have. A minimum rather than a fixed height so that nothing is ever
 * clipped; the slots below sum to exactly this, so a tile only exceeds it by wrapping past a
 * reserve, which see.
 */
private val TILE_HEIGHT =
    ART_ROW + LEVEL_ROW + PROMOTED_ROW + NAME_ROW + STATUS_ROW + TILE_CONTENT_PADDING * 2

/**
 * **Every tile on the wall, drawn: one card, one anatomy, five slots, one height.**
 *
 * It decides nothing. What goes in the slots is [TileAnatomy]'s answer, what colour the card takes
 * is [hue]'s and [mood]'s, and how wide it is is [span]'s — all of them pure functions a test can
 * reach. This lays them out and does so identically for all six tile types, which is the thing that
 * was missing: there was a rule for how two air conditioners agreed with each other and no rule at
 * all for how an air conditioner agreed with the launcher beside it.
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
    /** What kind of device this is, which decides *which* colour an on tile takes. */
    hue: TileHue,
    mood: TileMood,
    modifier: Modifier = Modifier,
    /**
     * Bad news that belongs to the tile's *group* rather than to the tile. Only the recuperators
     * have both kinds — see [RecuperatorTile] — and an outline is what keeps them apart: a filled
     * red tile is this device, an outlined one is all five.
     */
    border: BorderStroke? = null,
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
    val colors = tileColors(hue, mood)
    if (onClick == null) {
        Card(modifier = outer, shape = shape, colors = colors, border = border) {
            TileBody(anatomy, toggle, level)
        }
    } else {
        // The clickable Card rather than a `Modifier.clickable` outside it, so the ripple is
        // clipped to the corner it is drawn on and the tap lands on the tile and not on the gutter.
        Card(onClick = onClick, modifier = outer, shape = shape, colors = colors, border = border) {
            TileBody(anatomy, toggle, level)
        }
    }
}

/** The five slots in order. Split out only because [TileCard] draws two kinds of [Card]. */
@Composable
private fun TileBody(
    anatomy: TileAnatomy,
    toggle: @Composable () -> Unit,
    level: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(TILE_CONTENT_PADDING)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(ART_ROW),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TileGlyph(anatomy.art)
            Spacer(modifier = Modifier.weight(1f))
            // The 64 dp floor, for the switch inside it: a Switch is 52×32 dp, so the finger gets
            // the box rather than the graphic. Drawn whether or not a switch arrives, which is what
            // keeps the art line one height on all six kinds.
            Box(modifier = Modifier.touchable(), contentAlignment = Alignment.Center) { toggle() }
        }
        Slot(LEVEL_ROW) { level() }
        Slot(PROMOTED_ROW) { PromotedValue(anatomy.promoted) }
        Slot(NAME_ROW) {
            // Never truncated. A wall read from four metres cannot spend its one legible label on
            // "Свет в гарде…", which is the reference app's answer and the one PLAN.md refuses.
            Text(text = anatomy.name, style = MaterialTheme.typography.titleMedium)
        }
        Slot(STATUS_ROW) {
            Column {
                Text(text = anatomy.status, style = MaterialTheme.typography.bodyMedium)
                // The second line of the same slot, on the two tiles that have one. Its own line
                // rather than more dots on the first: it carries ages of its own, and six values in
                // one run is a line nobody reads at any size.
                anatomy.detail?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/**
 * One slot: at least [reserved] tall, full width, and empty when its content draws nothing.
 *
 * `heightIn` rather than `height` so that a name or a status line longer than its reserve grows the
 * tile instead of being cut off — see [STATUS_ROW].
 *
 * **Top-aligned, which only the status slot can tell the difference about**: the other three hold
 * exactly one line and fill their reserve. A status line of one or two lines centred in a four-line
 * reserve floats away from the name it belongs to, with a gap above it and a gap below; anchored to
 * the top it stays attached, and the slack falls at the foot of the card where it reads as padding.
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
 * How big a glyph is, everywhere it appears: 24 dp, which is the size the Material Symbols in
 * `res/drawable/` were exported at, so nothing is scaled.
 */
private val GLYPH_SIZE = 24.dp

/**
 * One tile's glyph. Untinted here and therefore tinted by [Icon] with `LocalContentColor`, which
 * inside a [TileCard] is that card's content colour — so the glyph and the text agree by
 * construction and cannot drift apart in one theme.
 *
 * `contentDescription` is null on every one of them, deliberately: they are decorative. The name is
 * right there, and a screen reader announcing "lightbulb Лампа в коридоре" says the noun twice.
 */
@Composable
internal fun TileGlyph(
    @DrawableRes glyph: Int,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(glyph),
        contentDescription = null,
        modifier = modifier.size(GLYPH_SIZE),
    )
}

/**
 * The one value a tile says at wall distance, or nothing at all when it has none.
 *
 * `displaySmall` — 44sp on this panel's scale, see `panelTypography` — because this is the line the
 * whole type scale exists for: the 22 °C and the 33.5 % that CLAUDE.md says is the point of hanging
 * a panel on a wall. Everything else on the tile is read standing at it.
 *
 * **Null draws nothing**, and that is [promoted]'s decision arriving intact. A tile with no value
 * has an empty slot rather than the word "unknown" set at 44sp; the status line under it still says
 * "unknown" in words and the tile's colour still says [TileMood.Unknown]. The slot it would have
 * filled stays reserved either way — see [TileCard].
 */
@Composable
internal fun PromotedValue(
    value: String?,
    modifier: Modifier = Modifier,
) {
    if (value != null) {
        Text(text = value, style = MaterialTheme.typography.displaySmall, modifier = modifier)
    }
}

/** The width of that outline: thick enough to read from across a hallway, thin enough to be one. */
private val GROUP_FAILURE_OUTLINE = 3.dp

/** The outline a tile wears when its whole group stopped updating, or null when it did not. */
@Composable
internal fun groupFailureBorder(groupError: String?): BorderStroke? = groupError?.let {
    BorderStroke(GROUP_FAILURE_OUTLINE, MaterialTheme.colorScheme.error)
}

/**
 * The colour roles, and only roles: no hex literal anywhere in `panel/`. A hardcoded colour is a
 * tile that is unreadable in one of the two themes, and the theme that breaks is the one nobody is
 * looking at when they check. The values are in `PanelTheme.kt` and nowhere else.
 *
 * **Two axes.** [TileHue] says which colour an on tile takes — climate the primary container, light
 * the tertiary, everything else the secondary — and [TileMood] says whether it takes one at all.
 * One colour for everything that is on made a wall where the air conditioner and the bedroom lamp
 * were the same object.
 *
 * [TileMood.Off] and [TileMood.Unknown] wear `surfaceContainer` whatever the hue — an unlit lamp is
 * not warm and a stopped recuperator is not cool — and the difference between the two is said in
 * words on the status line, which is where it was always said: "off" against "unknown". What must
 * not happen is either of them borrowing an *on* colour and claiming a reading nobody has taken.
 *
 * **[TileMood.Failing] is a filled `errorContainer`, on every tile**, which reverses what commit 2
 * landed on. Commit 2 painted it red for the reason [mood] still ranks `Failing` above `isOn` — a
 * failing tile shows a value nobody has confirmed — and then pulled it, because one unreachable
 * vendor made the wall read as an emergency and the paint is loudest exactly when it is least
 * useful. It comes back because the neutral treatment failed the other way round: a failing tile
 * that looks identical to a working one puts the whole weight on a status line nobody reads from
 * four metres, and a mosaic is read by colour and shape before it is read by words. This palette's
 * error container is also close to the neutral in weight, so a wall of it reads as muted rather than
 * as alarm — which is not what commit 2 tried.
 *
 * **The boot case is known and accepted**: until the first poll lands, every tile on Главная is
 * rose. If that reads as alarm rather than as "nothing has been read yet", docs/ui.md records the
 * fix — tell "never polled" from "stopped polling", both of which `Staleness.kt` already has, and
 * leave the first one neutral. That is a separate decision and not a patch to this one.
 *
 * The *group's* failure keeps its outline **as well as** the fill: five outlined recuperators is one
 * vendor rather than five broken units, and that distinction has to survive the fill — see
 * [groupFailureBorder].
 *
 * Reachable outside [TileCard] for the one thing on the wall that is a tile without being a card:
 * a bulb circle, which wears a shape of its own and these same colours — see [BulbCircles].
 */
@Composable
internal fun tileColors(
    hue: TileHue,
    mood: TileMood,
): CardColors = when (mood) {
    TileMood.On ->
        when (hue) {
            TileHue.Climate ->
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            TileHue.Light ->
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            TileHue.Neutral ->
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
        }
    TileMood.Failing ->
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    TileMood.Off, TileMood.Unknown ->
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
}

/**
 * The 64 dp floor, for the controls inside a tile. A `Switch` is 52×32 dp and a `Slider` thinner
 * still; both sit inside a box this size so the finger has the whole of it.
 */
internal fun Modifier.touchable(): Modifier = sizeIn(minWidth = MIN_TOUCH, minHeight = MIN_TOUCH)
