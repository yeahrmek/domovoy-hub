package ru.domovoy.panel

import org.junit.jupiter.api.Test
import ru.domovoy.R
import kotlin.test.assertEquals

class CapabilityControlsTest {
    @Test
    fun `AC modes get purpose-specific glyphs and fan speeds share the fan glyph`() {
        assertEquals(R.drawable.ic_mode_cool, capabilityGlyph("thermostat", "cool"))
        assertEquals(R.drawable.ic_mode_heat, capabilityGlyph("thermostat", "heat"))
        assertEquals(R.drawable.ic_mode_dry, capabilityGlyph("thermostat", "dry"))
        assertEquals(R.drawable.ic_mode_fan, capabilityGlyph("thermostat", "fan_only"))
        assertEquals(R.drawable.ic_mode_auto, capabilityGlyph("thermostat", "auto"))
        assertEquals(R.drawable.ic_mode_fan, capabilityGlyph("fan_speed", "quiet"))
        assertEquals(R.drawable.ic_mode_swing, capabilityGlyph("swing", "vertical"))
    }

    @Test
    fun `secondary AC toggles and RGB scenes get distinct glyphs`() {
        assertEquals(R.drawable.ic_ionization, toggleGlyph("ionization"))
        assertEquals(R.drawable.ic_keep_warm, toggleGlyph("keep_warm"))
        assertEquals(R.drawable.ic_backlight, toggleGlyph("backlight"))
        assertEquals(R.drawable.ic_scene_candle, sceneGlyph("candle"))
        assertEquals(R.drawable.ic_scene_rest, sceneGlyph("rest"))
        assertEquals(R.drawable.ic_scene_movie, sceneGlyph("movie"))
        assertEquals(R.drawable.ic_scene_sunrise, sceneGlyph("sunrise"))
    }

    @Test
    fun `vendor names are converted to short labels without losing unknown values`() {
        assertEquals("Fan", capabilityLabel("fan_speed"))
        assertEquals("Keep warm", capabilityLabel("keep_warm"))
        assertEquals("Quiet", capabilityLabel("quiet"))
        assertEquals("Vendor special", capabilityLabel("vendor_special"))
    }
}
