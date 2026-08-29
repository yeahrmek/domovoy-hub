package ru.domovoy.panel

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp

/**
 * The corner of a half-width tile. The two radii are the mosaic's whole shape vocabulary; a third
 * one is a tile that looks like a mistake rather than like a size.
 */
private val HALF_CORNER = 22.dp

/** The corner of a third-width tile: the bulbs, the launchers, the recuperator with no climate. */
private val THIRD_CORNER = 18.dp

/**
 * The corner a tile of this span wears. Derived rather than passed so that a tile's shape and its
 * width cannot disagree — the one thing a caller could get wrong when both were parameters.
 */
private fun corner(span: Int) = if (span >= HALF_SPAN) HALF_CORNER else THIRD_CORNER

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

/**
 * Every tile on the wall: one card, its corner from its size and its colour from the [TileHue] and
 * [TileMood] pair.
 *
 * Five callers, which is what earns it — the alternative is the same `when` over seven colour roles
 * written out five times, and a sixth tile getting one of them wrong. It draws and nothing else:
 * what kind of thing a tile is and what state it is in are [hue]'s and [mood]'s answers, out where
 * a test can reach them.
 */
@Composable
internal fun TileCard(
    /** What kind of device this is, which decides *which* colour an on tile takes. */
    hue: TileHue,
    mood: TileMood,
    /** How many of the grid's columns this tile occupies. Decides its corner, and nothing else. */
    span: Int,
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner(span))
    val outer = modifier.fillMaxWidth().padding(TILE_PADDING).heightIn(min = MIN_TOUCH)
    if (onClick == null) {
        Card(modifier = outer, shape = shape, colors = tileColors(hue, mood), border = border, content = content)
    } else {
        // The clickable Card rather than a `Modifier.clickable` outside it, so the ripple is
        // clipped to the corner it is drawn on and the tap lands on the tile and not on the gutter.
        Card(
            onClick = onClick,
            modifier = outer,
            shape = shape,
            colors = tileColors(hue, mood),
            border = border,
            content = content,
        )
    }
}

/**
 * How big a glyph is, everywhere it appears: 24 dp, which is the size the Material Symbols in
 * `res/drawable/` were exported at, so nothing is scaled.
 */
private val GLYPH_SIZE = 24.dp

/** Between the glyph and the name it sits beside, on the tiles wide enough to put them on one line. */
private val GLYPH_GAP = 8.dp

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
 * A tile's glyph and its name, laid out from the tile's own span so the rule lives in one place:
 * **beside the name on a half tile, above it on a third.** A 251 dp tile that spends its width on a
 * glyph has none left for the name, and the recuperator — the one tile whose span moves with its
 * content — gets whichever of the two its width earns, without asking twice.
 */
@Composable
internal fun TileHeading(
    @DrawableRes glyph: Int,
    name: String,
    /** How many of the grid's columns the tile occupies. See [span]. */
    span: Int,
    modifier: Modifier = Modifier,
) {
    if (span >= HALF_SPAN) {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            TileGlyph(glyph)
            Spacer(modifier = Modifier.width(GLYPH_GAP))
            Text(name, style = MaterialTheme.typography.titleMedium)
        }
    } else {
        Column(modifier = modifier) {
            TileGlyph(glyph)
            Text(name, style = MaterialTheme.typography.titleMedium)
        }
    }
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
 * "unknown" in words and the tile's colour still says [TileMood.Unknown]. Written here rather than
 * as a `let` at five call sites so that the size, and the choice to draw nothing, are one decision.
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
