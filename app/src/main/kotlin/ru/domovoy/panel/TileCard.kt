package ru.domovoy.panel

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.domovoy.R

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
 * The **art and controls** line: the 80 dp hardware image on the left and the round power button on
 * the right.
 */
private val ART_ROW = 80.dp

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
 * **296 dp, and the same 296 for a bulb as for an air conditioner.** That is the point of the whole
 * file — the mosaic had four heights and ragged bottom edges because each kind laid itself out
 * around what it happened to have.
 *
 * It was 328 while the status slot reserved four lines for a string of unbounded length. Two of
 * those four were never filled by anything the flat produces, and the 48 dp they left at the foot of
 * every card was the reserve showing rather than padding. Capping the slot took it to 280; enlarging
 * the art row from 64 to 80 brought it to 296 — see [STATUS_ROW].
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
 * ramp the card sits on is [surface]'s, which marks it wears is [marks]'s,
 * whether it is outlined is [paint]'s, and how wide it is is [span]'s — all of them pure functions a
 * test can reach. This lays them out and does so identically for all six tile types, which is the
 * thing that was missing: there was a rule for how two air conditioners agreed with each other and
 * no rule at all for how an air conditioner agreed with the launcher beside it.
 *
 * **Slot order, top to bottom.** Art and the power button on the top line and the words at the bottom is
 * the reference app's anatomy; the promoted value between them is this panel's one addition to it,
 * and the refusal that addition stands for is in PLAN.md — a wall panel is read without being
 * touched, so the value is the point and dropping it to look more like a phone app would be a
 * handsome thing that had stopped being a panel.
 *
 * **An empty slot is empty, not absent.** A launcher has no power button, no slider and no value, and it
 * reserves all three anyway. That is what buys bottom edges that line up across kinds, and it is
 * the cost of it too: a relay bulb carries a 64 dp band where a dimmable bulb uses its slider. The
 * alternative — each kind collapsing what it does not have — is the four ragged heights this
 * replaces.
 */
@Composable
internal fun TileCard(
    /** What this tile puts in each of the five slots. */
    anatomy: TileAnatomy,
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
     * The round power button, on the top line beside the art. Empty on tiles [TileAnatomy.controls] says
     * have none, and the 64 dp it would have taken stays reserved.
     */
    toggle: @Composable () -> Unit = {},
    /** The slider, on its own band. Empty on the tiles that have none, and reserved all the same. */
    level: @Composable () -> Unit = {},
    /**
     * What the top-right button does, on the one kind of tile that has one — see [TileAction]. It
     * is never called on a tile whose [TileAnatomy.action] is null, because no button is drawn
     * there: an unreachable callback rather than a dead tap.
     */
    onAction: () -> Unit = {},
) {
    val shape = RoundedCornerShape(TILE_CORNER)
    val outer = modifier.fillMaxWidth().padding(TILE_PADDING).heightIn(min = TILE_HEIGHT)
    val colors = tileColors(paint.mood)
    val border = groupFailureBorder(paint)
    if (onClick == null) {
        Card(modifier = outer, shape = shape, colors = colors, border = border) {
            TileBody(anatomy, paint.mood, toggle, level, onAction)
        }
    } else {
        // The clickable Card rather than a `Modifier.clickable` outside it, so the ripple is
        // clipped to the corner it is drawn on and the tap lands on the tile and not on the gutter.
        Card(onClick = onClick, modifier = outer, shape = shape, colors = colors, border = border) {
            TileBody(anatomy, paint.mood, toggle, level, onAction)
        }
    }
}

/** The five slots in order. Split out only because [TileCard] draws two kinds of [Card]. */
@Composable
private fun TileBody(
    anatomy: TileAnatomy,
    mood: TileMood,
    toggle: @Composable () -> Unit,
    level: @Composable () -> Unit,
    onAction: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(TILE_CONTENT_PADDING)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(ART_ROW),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TileArt(anatomy.art, mood)
            Spacer(modifier = Modifier.weight(1f))
            // **What the tile's state says, then what a finger can do about it**, and that split is
            // the order of this line rather than an accident of when each was written: the art and
            // the on mark at the left end, the offline glyph closing the reading, and the controls
            // gathered after it — one region for the eye and one for the hand, instead of a control
            // between two status marks.
            // The 64 dp floor belongs to the finger, while the power disc inside it stays quiet.
            // Drawn whether or not a power button arrives, keeping the control corner aligned.
            Box(modifier = Modifier.touchable(), contentAlignment = Alignment.Center) { toggle() }
            // **Last, so that the button is in the corner** — which is where the reference puts it
            // and, on the three kinds with no power button, the only way it gets there: its box
            // is reserved on every tile, so a button drawn before it would sit 64 dp in from the
            // edge with an empty box beside it. Inboard of the action on a tile that has both, which is the
            // reference's order too: power first, then the one that is not power.
            TileActionButton(anatomy.action, onAction)
        }
        Slot(LEVEL_ROW) { level() }
        Slot(PROMOTED_ROW) { PromotedValue(anatomy.promoted) }
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
 * How big the real-device art is everywhere on the wall. The art row already reserves 64 dp, so
 * using the whole row makes the photographed hardware legible without changing tile height.
 */
private val DEVICE_ART_SIZE = 80.dp

/**
 * One tile's real-device art, at the size every device image on the wall is.
 *
 * The raster is never tinted: its material, shadows, and on/off lighting are part of the image.
 * `contentDescription` is null deliberately because the adjacent device name already identifies it.
 */
@Composable
internal fun TileDeviceArt(
    @DrawableRes art: Int,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(art),
        contentDescription = null,
        modifier = modifier.size(DEVICE_ART_SIZE),
        contentScale = ContentScale.Fit,
    )
}

/**
 * **The art slot: the real hardware with its small state mark at the top-right.**
 *
 * Device identity and state live in the untinted raster, and after the palette went to one accent
 * this is where "what kind of thing is this" is said in full: a bulb looks like a bulb and lights up
 * when it is lit. Tinting the photo would erase exactly that — the distinction between an unlit and
 * a glowing lamp is in the image, and it is the warm one on this wall.
 *
 * On gets the small accented dot. Failure gets only the struck-through red wifi glyph: the device
 * itself never turns red and never moves when connectivity changes.
 */
@Composable
private fun TileArt(
    @DrawableRes art: Int,
    mood: TileMood,
) {
    Box(modifier = Modifier.size(DEVICE_ART_SIZE)) {
        TileDeviceArt(art)
        when {
            TileMark.Lit in marks(mood) ->
                Box(
                    modifier =
                    Modifier.align(Alignment.TopEnd).size(MARK_SIZE).clip(CircleShape)
                        .background(tileAccent()),
                )

            TileMark.Offline in marks(mood) ->
                Icon(
                    painter = painterResource(R.drawable.ic_wifi_off),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.TopEnd).size(MARK_GLYPH_SIZE),
                    tint = MaterialTheme.colorScheme.error,
                )
        }
    }
}

/** How big the on dot is where it sits on the device art. */
private val MARK_SIZE = 20.dp

/**
 * How big a mark that is a *glyph* rather than a disc is: 28 dp, a third more than the dot's 20.
 *
 * A filled circle is legible at any size that is visible at all; line art is not, and the whole of
 * what this particular glyph means is the bar struck through it. At 20 dp that bar is under a
 * pixel and a half of stroke on this tablet and the mark reads as a wifi symbol — which says the
 * opposite of what it is there to say.
 *
 * Still small against the art it sits opposite, which is the reference's proportion: the tile's
 * identity is the device, and this is a note in the corner about it.
 */
private val MARK_GLYPH_SIZE = 28.dp

/**
 * How big the button's own circle is: **40 dp, drawn inside the 64 dp box a finger actually gets.**
 *
 * The reference's buttons are roughly a third the width of the art — a phone's measurement, taken
 * from something held 30 cm from the face. [MIN_TOUCH] is this
 * panel's floor for anything tappable and is not negotiable, so the two numbers are split: the
 * target is 64 and the ring drawn in the middle of it is 40, which is smaller than the art beside it
 * and larger than anything else on the line. Quiet by being outlined and neutral rather than by
 * being too small to hit.
 */
private val ACTION_BUTTON_SIZE = 40.dp

/** The glyph inside that ring, with 8 dp of ring left around it. */
private val ACTION_GLYPH_SIZE = 24.dp

/** The visible power disc inside its larger wall-sized touch target. */
private val POWER_BUTTON_SIZE = 44.dp

/** The power symbol inside that disc. */
private val POWER_GLYPH_SIZE = 28.dp

/**
 * The reference's round on/off button: accented only while a current reading says the device is on,
 * and neutral for off, unknown, or failing. The 44 dp disc is visual; the whole 64 dp box is the
 * touch target.
 */
@Composable
internal fun TilePowerButton(
    isOn: Boolean,
    mood: TileMood,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accented = TileMark.Power in marks(mood)
    val container =
        if (accented) tileAccent() else MaterialTheme.colorScheme.surfaceContainerHighest
    val icon = if (accented) tileOnAccent() else MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .size(MIN_TOUCH)
            .toggleable(
                value = isOn,
                role = Role.Switch,
                onValueChange = { onToggle() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(POWER_BUTTON_SIZE).clip(CircleShape).background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_power_settings_new),
                contentDescription = null,
                modifier = Modifier.size(POWER_GLYPH_SIZE),
                tint = icon,
            )
        }
    }
}

/**
 * **The tile's one secondary control**: a small outlined circle at the top right, or nothing at all
 * on the kinds that have no second action — which is every kind but the curtain. See [TileAction].
 *
 * **Outlined and neutral rather than filled.** The four steps of the neutral ramp are 5 L\* apart in
 * dark and 2 in light, so a filled disc in any of them is a disc that disappears on one mood; the
 * ring is `outline` against `onSurface` and reads on all four. It is also what keeps the colour
 * budget where the last commit put it — the saturated things on a tile are the on mark and accented
 * power button, and a control that is merely *available* is not news.
 *
 * **Where it sits was decided rather than discovered.** It is the last thing on the art line: the
 * status marks end the reading half of it, the power control's reserved box follows, and this closes
 * the line in the corner the reference puts its buttons in. Drawing it before that box was tried
 * and looked wrong on exactly the tile that has one — a curtain has no power control and the box is
 * reserved anyway, so the button came out 64 dp in from the edge with an empty square beside it.
 * Nothing has to move for it — the row's spacer
 * absorbs the width — and the arithmetic is in [TileAction]: on the third-width tile that is the
 * only kind carrying one, the art, this button and the reserved power box still fit its 219 dp.
 * The offline glyph overlays the art and consumes no extra width. What does not fit either way is a
 * second button, which is the whole of why [action] answers with one and not with a set.
 *
 * The `contentDescription` is the one on this wall that is not null: every other glyph here is
 * decorative and sits beside a name that says the same thing, and this one is a button whose whole
 * meaning is what pressing it does.
 */
@Composable
private fun TileActionButton(
    action: TileAction?,
    onAction: () -> Unit,
) {
    if (action == null) return
    Box(modifier = Modifier.touchable(), contentAlignment = Alignment.Center) {
        OutlinedIconButton(onClick = onAction, modifier = Modifier.size(ACTION_BUTTON_SIZE)) {
            Icon(
                painter = painterResource(glyph(action)),
                contentDescription = action.label,
                modifier = Modifier.size(ACTION_GLYPH_SIZE),
            )
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
 * **In `onSurface`, like every other word on the card**, and it took the tile's family accent
 * until now. Colouring the biggest thing on a tile was how the wall said what kind of thing that
 * tile was, and the wall has stopped saying it that way: two of the three accents were a blue and
 * an amber that the tablet's blue light filter turns into two browns, and a 44sp number is a large
 * enough area of the wrong colour to drag the whole card with it. What kind of device this is, is
 * said by the photograph of the hardware above it. What the *number* has to be is legible, and
 * `onSurface` is 11.4:1 on the worst step in light and 8.8:1 in dark against the accent's 4.0.
 *
 * The colour is still a parameter, defaulting to the inherited content colour, because the sheet
 * draws this too and a caller that needs a different one should not have to fight the default.
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
 * So the family moved to the accents and the surface was left carrying [surface], which is the
 * mood — and then the families went too. What wears [tileAccent] today is the accented power
 * button, the slider fill and the on dot, and nothing else: three small things a finger uses or
 * looks for, in one violet. **One content colour for all four steps**, because all four are neutral
 * surfaces: `onSurface` is the pair for every one of them and there is no longer a mood in which
 * the text has to change with the card.
 *
 * **[TileMood.Failing] no longer fills the card or the device art.** [TileArt] adds the one red
 * struck-through wifi mark from the reference while leaving the hardware image intact.
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
 * **The one colour on the wall that is not a grey or the red.** The violet in `PanelTheme.kt`, and
 * everything wearing it is drawn *on* a neutral surface and has to show against it.
 *
 * **It took an argument and now takes none, and that is the change.** It was three accents keyed on
 * a tile's family — blue for the things that move air, amber for the things that make light, grey
 * for the rest — spent on four things each: the glyph, the promoted value, the on mark and the
 * slider fill. A photograph of the tablet ended it. The wall runs behind Samsung's blue light
 * filter, which is a warm film over the whole screen: the blue came out beige, the amber came out
 * brown, and brown is the wrong neighbour for the one colour on this panel that has to be
 * unmistakable. Two families that are two beiges are not two families.
 *
 * So: **one accent, and only on what a finger uses** — the accented power button and the slider
 * fill, plus the small dot that says a device is lit. The promoted value and every other word on
 * the card are `onSurface` now; what kind of thing a tile is, is said by the photograph of the
 * hardware on it, which is a better answer than a hue was and was already there. A bulb looks like
 * a bulb, and it lights up when it is on — see [TileArt].
 *
 * Kept as a function rather than inlined at its three call sites: it is `MaterialTheme`-dependent,
 * so it has to be `@Composable` anyway, and the last time this file had a second copy of a colour
 * rule in it the copy had already drifted.
 */
@Composable
internal fun tileAccent(): Color = MaterialTheme.colorScheme.primary

/**
 * **The colour written on the accent.** The round power button is filled with [tileAccent], so its
 * symbol takes the `on` role of it — white in light, a deep violet in dark, both written out in
 * `PanelTheme.kt`.
 */
@Composable
private fun tileOnAccent(): Color = MaterialTheme.colorScheme.onPrimary

/**
 * The 64 dp floor for controls inside a tile. The visible control can be smaller, while the finger
 * still gets the whole box.
 */
internal fun Modifier.touchable(): Modifier = sizeIn(minWidth = MIN_TOUCH, minHeight = MIN_TOUCH)
