package ru.domovoy.panel

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.domovoy.R

/** One corner radius for every tile width. */
private val TILE_CORNER = 22.dp

/** Wall controls use a 64 dp minimum for standing, arm’s-length interaction. */
internal val MIN_TOUCH = 64.dp

/** Each tile supplies half the gutter, including at the outer grid edge. */
private val TILE_PADDING = 4.dp

/** Inside the card, on all four sides. One padding, on every tile, whatever its span. */
private val TILE_CONTENT_PADDING = 12.dp

/** Main controls remain easy to reach while simple cards omit absent content. */
private val ART_ROW = MIN_TOUCH

/** Common controls and typography; absent content takes no space. */
@Composable
internal fun TileCard(
    /** The device identity, values and status. */
    anatomy: TileAnatomy,
    /** Device mood and group failure determine the surface, marks, and border. */
    paint: TilePaint,
    modifier: Modifier = Modifier,
    showRoom: Boolean = false,
    pending: Boolean = false,
    hasLevel: Boolean = false,
    format: TileFormat = tileFormat(anatomy, hasLevel),
    /** Optional action on the whole card. */
    onClick: (() -> Unit)? = null,
    /** Optional power control. */
    toggle: @Composable () -> Unit = {},
    /** Optional range control, displayed only when the device advertises a range. */
    level: @Composable () -> Unit = {},
    /** Called only when [TileAnatomy.action] supplies a secondary button. */
    onAction: () -> Unit = {},
) {
    val shape = RoundedCornerShape(TILE_CORNER)
    val outer = modifier.fillMaxWidth().padding(TILE_PADDING).heightIn(min = format.minimumHeight.dp)
    val colors = tileColors(paint.mood)
    val border = groupFailureBorder(paint)
    if (onClick == null) {
        Card(modifier = outer, shape = shape, colors = colors, border = border) {
            TileBody(anatomy, paint, showRoom, pending, hasLevel, format, toggle, level, onAction)
        }
    } else {
        Card(onClick = onClick, modifier = outer, shape = shape, colors = colors, border = border) {
            TileBody(anatomy, paint, showRoom, pending, hasLevel, format, toggle, level, onAction)
        }
    }
}

/** The body is shared by actionable and informational cards. */
@Composable
private fun TileBody(
    anatomy: TileAnatomy,
    paint: TilePaint,
    showRoom: Boolean,
    pending: Boolean,
    hasLevel: Boolean,
    format: TileFormat,
    toggle: @Composable () -> Unit,
    level: @Composable () -> Unit,
    onAction: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(TILE_CONTENT_PADDING)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(ART_ROW),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TileArt(anatomy.art, paint.mood)
            Spacer(modifier = Modifier.weight(1f))

            Box(modifier = Modifier.touchable(), contentAlignment = Alignment.Center) { toggle() }

            TileActionButton(anatomy.action, onAction, enabled = !pending)
        }
        Text(text = anatomy.name, style = MaterialTheme.typography.titleMedium)
        if (showRoom) {
            Text(
                text = anatomy.room ?: "Без комнаты",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PromotedValue(anatomy.promoted ?: "—".takeIf { format == TileFormat.Reading || format == TileFormat.Control })
        if (hasLevel) level()
        StatusText(if (pending) "Ждём подтверждения…" else anatomy.status, announce = pending)
        anatomy.detail?.let { StatusText(it, announce = paint.groupFailing || paint.mood == TileMood.Failing) }
    }
}

/** Status and age can wrap together; technical details stay in the sheet. */
@Composable
private fun StatusText(text: String, announce: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = if (announce) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
    )
}

/** The device art fills the shared art row. */
private val DEVICE_ART_SIZE = 80.dp

/** Untinted hardware art. Its adjacent device name supplies the accessible description. */
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

/** Device art keeps its appearance; state adds an accent dot or error-coloured offline glyph. */
@Composable
private fun TileArt(
    @DrawableRes art: Int,
    mood: TileMood,
) {
    Box(modifier = Modifier.size(ART_ROW)) {
        TileDeviceArt(art, Modifier.size(ART_ROW))
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
private val MARK_SIZE = 12.dp

/** The offline glyph needs more space than the dot for its strike-through to remain legible. */
private val MARK_GLYPH_SIZE = 28.dp

/** The secondary button’s visible disc sits inside the reserved control box. */
private val ACTION_BUTTON_SIZE = 40.dp

/** The glyph inside that ring, with 8 dp of ring left around it. */
private val ACTION_GLYPH_SIZE = 24.dp

/** The visible power disc inside its larger wall-sized touch target. */
private val POWER_BUTTON_SIZE = 44.dp

/** The power symbol inside that disc. */
private val POWER_GLYPH_SIZE = 28.dp

/** Power is accented only when confirmed on; the full 64 dp box is the touch target. */
@Composable
internal fun TilePowerButton(
    deviceName: String,
    room: String?,
    isOn: Boolean,
    mood: TileMood,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val label = stringResource(R.string.device_power, listOfNotNull(deviceName, room).joinToString(", "))
    val accented = TileMark.Power in marks(mood)
    val container =
        if (accented) tileAccent() else MaterialTheme.colorScheme.surfaceContainerHighest
    val icon = if (accented) tileOnAccent() else MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .size(MIN_TOUCH)
            .semantics { contentDescription = label }
            .toggleable(
                value = isOn,
                enabled = enabled,
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

/** An outlined secondary action at the end of the art row; wide tiles have room for one. */
@Composable
private fun TileActionButton(
    action: TileAction?,
    onAction: () -> Unit,
    enabled: Boolean,
) {
    if (action == null) return
    Box(
        modifier = Modifier.size(MIN_TOUCH)
            .semantics { contentDescription = action.label }
            .clickable(enabled = enabled, role = Role.Button, onClick = onAction),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(ACTION_BUTTON_SIZE).clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(glyph(action)),
                contentDescription = null,
                modifier = Modifier.size(ACTION_GLYPH_SIZE),
            )
        }
    }
}

/** A value in displaySmall for wall-distance reading; null leaves its slot empty. */
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

/** Group failures add a border while retaining the tile’s device state. */
@Composable
private fun groupFailureBorder(paint: TilePaint): BorderStroke? = if (paint.groupFailing) {
    BorderStroke(GROUP_FAILURE_OUTLINE, MaterialTheme.colorScheme.error)
} else {
    null
}

/** Mood selects a neutral surface role; all tile text uses onSurface in both themes. */
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

/** One accent for on marks, power controls, and sliders on neutral surfaces. */
@Composable
internal fun tileAccent(): Color = MaterialTheme.colorScheme.primary

/** The theme’s matching content colour for controls filled with [tileAccent]. */
@Composable
private fun tileOnAccent(): Color = MaterialTheme.colorScheme.onPrimary

/** Reserve at least 64 dp for tile controls. */
internal fun Modifier.touchable(): Modifier = sizeIn(minWidth = MIN_TOUCH, minHeight = MIN_TOUCH)
