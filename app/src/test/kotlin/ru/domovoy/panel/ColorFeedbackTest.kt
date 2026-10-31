package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.core.ColorSetting
import ru.domovoy.core.Reading
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ColorFeedbackTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the confirmed RGB preset is selected on the sheet`() {
        val bulb = Flat.bulbs.tiles.first().copy(
            color = ColorSetting("rgb", 16777215.0, lastUpdated = Reading.At(Flat.NOW)),
        )
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                DeviceSheet(sheet(bulb, Flat.NOW, null), onDismiss = {})
            }
        }
        compose.onNodeWithContentDescription("Белый")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    @Test
    fun `a scene only becomes selected after the device reports it`() {
        val color = androidx.compose.runtime.mutableStateOf(
            ColorSetting("scene", null, listOf("movie", "rest"), Reading.At(Flat.NOW), scene = "movie"),
        )
        var sent: String? = null
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                ColorControls(color.value.scenes, {}, { sent = it }, color = color.value)
            }
        }
        compose.onNodeWithText("Кино")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.onNodeWithText("Отдых").performClick()
        compose.runOnIdle { kotlin.test.assertEquals("rest", sent) }
        compose.onNodeWithText("Кино")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        compose.runOnIdle { color.value = color.value.copy(scene = "rest") }
        compose.onNodeWithText("Отдых")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    @Test
    fun `the colour reading uses a human label instead of a hexadecimal value`() {
        val bulb = Flat.bulbs.tiles.first().copy(
            color = ColorSetting("rgb", 16711680.0, lastUpdated = Reading.At(Flat.NOW)),
        )
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                DeviceSheet(sheet(bulb, Flat.NOW, null), onDismiss = {})
            }
        }
        compose.onNodeWithText("#FF0000").assertDoesNotExist()
        compose.onNodeWithText("Красный").assertExists()
    }
}
