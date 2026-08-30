package ru.domovoy.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * How dark the wall goes behind an open sheet. Enough that the sheet is unmistakably in front and
 * little enough that the tiles behind it are still legible — a panel whose readings vanish because
 * somebody tapped one of them is a panel that stopped answering the question it was tapped with.
 */
private const val SCRIM_ALPHA = 0.6f

/** The sheet's own two corners. Larger than a tile's 22 dp: this is one surface, not one of twelve. */
private val SHEET_CORNER = 32.dp

/** Inside the sheet, on all four sides. */
private val SHEET_PADDING = 24.dp

/** Between one reading and the next, and between the readings and the controls under them. */
private val SHEET_GAP = 16.dp

/**
 * How wide a reading's label column is. Fixed rather than measured, so the values line up down the
 * sheet instead of starting wherever the word before them ended.
 */
private val LABEL_WIDTH = 220.dp

/**
 * **What a tap on a tile opens: the whole device, on one surface over the wall.**
 *
 * The reference app splits a phone's screen the other way round — an almost empty tile, and
 * everything behind a tap. This panel refuses that split and PLAN.md says why: a wall panel is read
 * *without being touched*, so the tile is where detail belongs and **nothing moves off it into
 * here**. What this adds is the two things a 251 dp card genuinely had no room for.
 *
 * - **An age per reading.** The tile prints one — the oldest of what it shows — because four
 *   timestamps in one paragraph is what made the wall unreadable. On `ac-01` the two readings behind
 *   it are 81 days apart, and which of them the tile is under-claiming for is a fact the panel holds
 *   and had nowhere to say. See [SheetReading].
 * - **The actions that did not fit.** One 64 dp button is all a third-width tile has room for
 *   ([TileAction]); the sheet is 753 dp wide and can carry the whole verified set — and only the
 *   verified set, which is why there is no `Color` section, no `Modes` and no `reset` on it. See
 *   [SheetAction].
 *
 * **It draws what [sheet] answered and decides nothing**, on [TileCard]'s rule: which readings, which
 * actions and whether there is a level at all are pure functions a test can reach, and this lays them
 * out.
 *
 * **It cannot be in the way of an intercom call.** It is drawn inside the panel's own composition, so
 * Domonap's screen — another app's activity — is in front of it by construction, and the panel closes
 * it the moment a call starts so that nothing is left over the wall when the call ends. See
 * [closeOnCall] and [returnToHome].
 */
@Composable
internal fun DeviceSheet(
    sheet: TileSheet,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Flips the device's own on/off. The same call the tile's switch makes; no new poll. */
    onToggle: () -> Unit = {},
    /** Drives the one range the vendor reported. The same call the tile's slider makes. */
    onSetLevel: (Double) -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize()) {
        // The wall behind, dimmed and still readable, and tapping it puts the sheet away. No
        // indication: a ripple across a whole 753 dp wall says nothing a wall panel needs said.
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
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = SHEET_CORNER, topEnd = SHEET_CORNER),
            // A step of the same neutral ramp every tile sits on — the surfaces stopped carrying hue
            // and this one never started. The family is in the accents: the art, and whatever the
            // controls fill.
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(SHEET_PADDING),
                verticalArrangement = Arrangement.spacedBy(SHEET_GAP),
            ) {
                SheetHeading(sheet, onDismiss)
                // The bad news the tile was carrying, kept over the tap: a sheet that covered the
                // one line saying the panel has stopped reading this device would be the panel
                // hiding it.
                sheet.notUpdating?.let { reason ->
                    Text(
                        text = "not updating: $reason",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                sheet.readings.forEach { reading -> SheetReadingRow(reading) }
                SheetControls(sheet, onToggle, onSetLevel)
            }
        }
    }
}

/**
 * The art, the room, the name, and the way out. The room is above the name rather than beside it —
 * the same order the wall has, where the heading is what you find first and the tile under it says
 * which device.
 */
@Composable
private fun SheetHeading(
    sheet: TileSheet,
    onDismiss: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TileGlyph(sheet.art, tint = tileAccent(sheet.hue))
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
        // "done" rather than "close": the curtain's own action is called close, and a wall panel
        // with two controls a foot apart both labelled close is one that gets pressed wrong.
        OutlinedButton(onClick = onDismiss, modifier = Modifier.touchable()) {
            Text(text = "done", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** One reading: what it is, what it says, and how old it is. */
@Composable
private fun SheetReadingRow(reading: SheetReading) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = reading.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(LABEL_WIDTH),
        )
        Text(text = reading.value, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.weight(1f))
        // **The age of this one reading**, which is the thing the tile cannot say. It is quiet
        // because it is the answer to a question already asked, not something to be found.
        Text(
            text = reading.age,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * **What the sheet offers a finger, and nothing it was not offered.** A kind whose
 * [TileSheet.actions] is empty draws no control at all and no empty row where one would have been —
 * which is the door lock's whole answer, and the reason that answer is a set rather than a flag.
 *
 * **The controls carry no words of their own**, which is the tile's rule kept: a tile's switch has
 * no label either, and the reading directly above this one names it and says what it is doing. A
 * second "power" here would be the same word twice on a surface whose whole purpose is that each
 * fact is said once, with its age.
 */
@Composable
private fun SheetControls(
    sheet: TileSheet,
    onToggle: () -> Unit,
    onSetLevel: (Double) -> Unit,
) {
    if (sheet.actions.isEmpty()) return
    // The line between what this device says and what can be done to it. On the lock there is
    // nothing below it to divide, so there is no line either.
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    val level = sheet.level
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (SheetAction.Power in sheet.actions) {
            Box(modifier = Modifier.touchable(), contentAlignment = Alignment.CenterStart) {
                Switch(
                    checked = sheet.isOn == true,
                    onCheckedChange = { onToggle() },
                    // The mood and not the checkbox, on the tile's rule: a device whose poll failed
                    // keeps the switch it last reported and loses the colour, because a coloured
                    // switch there is the panel asserting a state nobody has confirmed.
                    colors = tileSwitchColors(sheet.hue, mood(sheet.isOn, sheet.notUpdating)),
                )
            }
            Spacer(modifier = Modifier.width(SHEET_GAP))
        }
        if (level != null) {
            // The dragged value is local, as on every tile with a slider: the state behind it only
            // changes on the next poll, so binding the handle straight to it would drag it back
            // under the finger.
            var dragged by
                remember(sheet.name, level.bounds) { mutableFloatStateOf(level.value.toFloat()) }
            SlimSlider(
                value = dragged,
                onValueChange = { dragged = it },
                valueRange = level.bounds.min.toFloat()..level.bounds.max.toFloat(),
                onValueChangeFinished = { onSetLevel(dragged.toDouble()) },
                hue = sheet.hue,
                modifier = Modifier.weight(1f),
            )
        }
    }
    // The ends of that range, one press each — the curtain's, and the only kind that has them. They
    // send the same `range` action the slider above sends, at the two values that are always on the
    // grid: **the vendor's own bounds**, never 0 and 100, because Yandex can only reject what is off
    // the grid and a rejected action reaches the wall as "not updating" for a reason that was ours.
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
                ) {
                    Text(text = end.label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
