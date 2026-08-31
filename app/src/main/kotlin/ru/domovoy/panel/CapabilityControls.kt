package ru.domovoy.panel

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.domovoy.R
import ru.domovoy.core.Mode
import ru.domovoy.core.Toggle

private val CONTROL_CORNER = 18.dp
private val CONTROL_GAP = 12.dp
private val CONTROL_ICON = 26.dp
private val CONTROL_PADDING = 14.dp

/** A vendor spelling made short and readable while leaving unknown future values intact. */
internal fun capabilityLabel(value: String): String = value.replace('_', ' ').replaceFirstChar { it.uppercase() }
    .replace("Fan speed", "Fan")

@DrawableRes
internal fun capabilityGlyph(
    instance: String,
    value: String,
): Int = when (instance) {
    "fan_speed" -> R.drawable.ic_mode_fan
    "swing" -> R.drawable.ic_mode_swing
    "thermostat" ->
        when (value) {
            "cool" -> R.drawable.ic_mode_cool
            "heat" -> R.drawable.ic_mode_heat
            "dry" -> R.drawable.ic_mode_dry
            "fan_only" -> R.drawable.ic_mode_fan
            else -> R.drawable.ic_mode_auto
        }
    else -> R.drawable.ic_mode_auto
}

@DrawableRes
internal fun toggleGlyph(instance: String): Int = when (instance) {
    "ionization" -> R.drawable.ic_ionization
    "keep_warm" -> R.drawable.ic_keep_warm
    "backlight" -> R.drawable.ic_backlight
    else -> R.drawable.ic_mode_auto
}

@DrawableRes
internal fun sceneGlyph(scene: String): Int = when (scene) {
    "candle" -> R.drawable.ic_scene_candle
    "rest" -> R.drawable.ic_scene_rest
    "movie" -> R.drawable.ic_scene_movie
    "sunrise" -> R.drawable.ic_scene_sunrise
    else -> R.drawable.ic_mode_auto
}

@Composable
internal fun ModeControls(
    modes: Map<String, Mode>,
    onSetMode: (String, String) -> Unit,
) {
    val order = listOf("thermostat", "fan_speed", "swing")
    modes.entries.sortedBy { order.indexOf(it.key).let { index -> if (index < 0) Int.MAX_VALUE else index } }
        .forEach { (instance, mode) ->
            ControlSection(capabilityLabel(instance)) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(CONTROL_GAP),
                ) {
                    mode.available.forEach { value ->
                        CapabilityButton(
                            label = capabilityLabel(value),
                            glyph = capabilityGlyph(instance, value),
                            selected = mode.current == value,
                            onClick = { onSetMode(instance, value) },
                        )
                    }
                }
            }
        }
}

@Composable
internal fun ToggleControls(
    toggles: Map<String, Toggle>,
    onSetToggle: (String, Boolean) -> Unit,
) {
    if (toggles.isEmpty()) return
    ControlSection("Options") {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CONTROL_GAP),
        ) {
            toggles.forEach { (instance, toggle) ->
                CapabilityButton(
                    label = capabilityLabel(instance),
                    glyph = toggleGlyph(instance),
                    selected = toggle.isOn == true,
                    onClick = { onSetToggle(instance, toggle.isOn != true) },
                )
            }
        }
    }
}

@Composable
internal fun FanSpeedControls(
    speeds: List<FanSpeed>,
    selected: List<FanSpeed>,
    enabled: Boolean,
    onSetSpeed: (FanSpeed) -> Unit,
) {
    if (speeds.isEmpty()) return
    ControlSection("Fan speed") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CONTROL_GAP)) {
            speeds.forEach { speed ->
                CapabilityButton(
                    label = capabilityLabel(speed.label),
                    glyph = R.drawable.ic_mode_fan,
                    selected = speed in selected,
                    enabled = enabled,
                    onClick = { onSetSpeed(speed) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun ColorControls(
    scenes: List<String>,
    onSetRgb: (Int) -> Unit,
    onSetScene: (String) -> Unit,
) {
    ControlSection("Color") {
        Row(horizontalArrangement = Arrangement.spacedBy(CONTROL_GAP), verticalAlignment = Alignment.CenterVertically) {
            RgbSwatch("red", Color.Red, onClick = { onSetRgb(255 shl 16) })
            RgbSwatch("green", Color.Green, onClick = { onSetRgb(255 shl 8) })
            RgbSwatch("blue", Color.Blue, onClick = { onSetRgb(255) })
            RgbSwatch("white", Color.White, onClick = { onSetRgb((255 shl 16) or (255 shl 8) or 255) })
        }
    }
    if (scenes.isNotEmpty()) {
        ControlSection("Scenes") {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(CONTROL_GAP),
            ) {
                scenes.forEach { scene ->
                    CapabilityButton(
                        label = capabilityLabel(scene),
                        glyph = sceneGlyph(scene),
                        selected = false,
                        onClick = { onSetScene(scene) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CONTROL_GAP)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun CapabilityButton(
    label: String,
    @DrawableRes glyph: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val container =
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val content =
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier =
        modifier.heightIn(min = MIN_TOUCH).clip(RoundedCornerShape(CONTROL_CORNER))
            .background(container.copy(alpha = if (enabled) 1f else 0.45f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = CONTROL_PADDING, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(glyph),
            contentDescription = null,
            tint = content.copy(alpha = if (enabled) 1f else 0.45f),
            modifier = Modifier.size(CONTROL_ICON),
        )
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

@Composable
private fun RgbSwatch(
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
        Modifier.size(MIN_TOUCH).clip(CircleShape).background(color)
            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .semantics { contentDescription = label }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Spacer(modifier = Modifier.width(1.dp))
    }
}
