package ru.domovoy.panel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.domovoy.core.ColorSetting

/** Dim the background while keeping the panel visible underneath. */
private const val SCRIM_ALPHA = 0.6f

/** Rounded top corners distinguish the sheet from tiles. */
private val SHEET_CORNER = 20.dp

/** Sheet content padding. */
private val SHEET_PADDING = 24.dp

/** Spacing between readings and control groups. */
private val SHEET_GAP = 16.dp

/** Fixed label width keeps reading values aligned. */
private val LABEL_WIDTH = 220.dp

/** Device details and advertised controls. Stays in the panel composition and closes on calls. */
@Composable
internal fun DeviceSheet(
    sheet: TileSheet,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** The same power command as the tile. */
    onToggle: () -> Unit = {},
    /** The same range command as the tile. */
    onSetLevel: (Double) -> Unit = {},
    onSetMode: (String, String) -> Unit = { _, _ -> },
    onSetToggle: (String, Boolean) -> Unit = { _, _ -> },
    onSetScene: (String) -> Unit = {},
    onSetRgb: (Int) -> Unit = {},
    onSetSpeed: (FanSpeed) -> Unit = {},
    pending: Boolean = false,
    commandError: String? = null,
) {
    BackHandler(onBack = onDismiss)
    val focus = remember { FocusRequester() }
    LaunchedEffect(sheet.id) { focus.requestFocus() }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight * 0.94f)
                .semantics {
                    dialog()
                    paneTitle = sheet.name
                    isTraversalGroup = true
                }
                .focusProperties { onExit = { cancelFocusChange() } }
                .focusGroup()
                .focusRequester(focus)
                .focusable(),
            shape = RoundedCornerShape(topStart = SHEET_CORNER, topEnd = SHEET_CORNER),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier =
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(SHEET_PADDING),
                verticalArrangement = Arrangement.spacedBy(SHEET_GAP),
            ) {
                PanelBrand(compact = true)
                SheetHeading(sheet, onDismiss)
                sheet.notUpdating?.let { reason ->
                    Text(
                        text = "Нет обновления: $reason",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                commandError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                sheet.readings.forEach { reading ->
                    SheetReadingRow(reading, sheet.color.takeIf { reading.label == "Цвет" })
                }
                if (pending) {
                    Text(
                        "Ждём подтверждения…",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                SheetControls(
                    sheet = sheet,
                    onToggle = onToggle,
                    onSetLevel = onSetLevel,
                    onSetMode = onSetMode,
                    onSetToggle = onSetToggle,
                    onSetScene = onSetScene,
                    onSetRgb = onSetRgb,
                    onSetSpeed = onSetSpeed,
                    enabled = !pending,
                )
            }
        }
    }
}

/** Device identity and the explicit dismiss action. */
@Composable
private fun SheetHeading(
    sheet: TileSheet,
    onDismiss: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TileDeviceArt(sheet.art)
        Spacer(modifier = Modifier.width(SHEET_GAP))
        Column(modifier = Modifier.weight(1f)) {
            sheet.room?.let { room ->
                Text(
                    text = room,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(text = sheet.name, style = MaterialTheme.typography.headlineMedium)
        }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.touchable()) {
            Text(text = "Готово", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** One reading with its label, value and age. */
@Composable
private fun SheetReadingRow(reading: SheetReading, color: ColorSetting? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = reading.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(LABEL_WIDTH),
        )
        if (color?.instance == "rgb" && color.value != null) {
            Box(
                Modifier.size(28.dp).background(Color(0xFF000000 or color.value.toLong()), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .semantics { contentDescription = "Образец цвета" },
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(text = reading.value, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = reading.age,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Render only the actions advertised by this device. */
@Composable
private fun SheetControls(
    sheet: TileSheet,
    onToggle: () -> Unit,
    onSetLevel: (Double) -> Unit,
    onSetMode: (String, String) -> Unit,
    onSetToggle: (String, Boolean) -> Unit,
    onSetScene: (String) -> Unit,
    onSetRgb: (Int) -> Unit,
    onSetSpeed: (FanSpeed) -> Unit,
    enabled: Boolean,
) {
    if (sheet.actions.isEmpty()) return
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    val level = sheet.level
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (SheetAction.Power in sheet.actions) {
            Box(modifier = Modifier.touchable(), contentAlignment = Alignment.CenterStart) {
                TilePowerButton(
                    deviceName = sheet.name,
                    room = sheet.room,
                    isOn = sheet.isOn == true,
                    mood = mood(sheet.isOn, sheet.notUpdating),
                    onToggle = onToggle,
                    enabled = enabled,
                )
            }
            Spacer(modifier = Modifier.width(SHEET_GAP))
        }
        if (level != null) {
            LevelControl(
                id = sheet.id,
                value = level.value,
                bounds = level.bounds,
                onSetValue = onSetLevel,
                modifier = Modifier.weight(1f),
                stepButtons = true,
                enabled = enabled,
                label = sheet.levelLabel,
                unit = sheet.levelUnit,
            )
        }
    }
    // Curtain end actions use vendor bounds, never an assumed 0..100 range.
    val ends = listOf(SheetAction.Open, SheetAction.Close).filter { it in sheet.actions }
    if (ends.isNotEmpty() && level != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SHEET_GAP),
        ) {
            ends.forEach { end ->
                Button(
                    onClick = {
                        onSetLevel(if (end == SheetAction.Open) level.bounds.max else level.bounds.min)
                    },
                    modifier = Modifier.weight(1f).heightIn(min = MIN_TOUCH),
                    enabled = enabled,
                ) {
                    Text(text = end.label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
    if (SheetAction.Mode in sheet.actions) ModeControls(sheet.modes, onSetMode, enabled)
    if (SheetAction.Toggle in sheet.actions) ToggleControls(sheet.toggles, onSetToggle, enabled)
    if (SheetAction.Color in sheet.actions) {
        ColorControls(sheet.color?.scenes.orEmpty(), onSetRgb = onSetRgb, onSetScene = onSetScene, enabled = enabled, color = sheet.color)
    }
    if (SheetAction.Speed in sheet.actions) {
        FanSpeedControls(
            speeds = sheet.fanSpeeds,
            selected = sheet.selectedFanSpeeds,
            enabled = enabled && sheet.isOn == true,
            onSetSpeed = onSetSpeed,
        )
    }
}
