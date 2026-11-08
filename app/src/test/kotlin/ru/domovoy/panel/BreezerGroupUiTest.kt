package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.core.Reading
import ru.domovoy.panelDarkScheme
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography
import java.io.File
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BreezerGroupUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val base = Flat.recuperators.tiles.first().copy(powerLastUpdated = Reading.At(Flat.NOW))
    private val devices = listOf(
        base.copy(id = "hall", room = "Зал", isOn = false),
        base.copy(id = "study", room = "Кабинет", isOn = true),
        base.copy(id = "small", room = "Маленькая детская", isOn = false),
        base.copy(id = "child", room = "Детская", isOn = false),
        base.copy(id = "bedroom", room = "Спальня", isOn = null, online = null, error = "Бризер не найден в локальной сети"),
    )
    private val home = hasAnyAncestor(hasTestTag("section:Главная"))

    @Test
    fun `one missing breather keeps healthy states visible and opens only its own details`() {
        panel()
        compose.onNode(hasContentDescription("Показать бризеры: Бризеры, Все комнаты") and home)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Свёрнуто"))
        icon("Зал").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Выключен"))
        icon("Кабинет").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Включён"))
        icon("Спальня").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Нет связи"))
        capture("partial-light")
        icon("Спальня").performClick()
        compose.onNodeWithText("Спальня").assertExists()
        compose.onNodeWithText("Нет обновления: Бризер не найден в локальной сети").assertExists()
    }

    @Test
    fun `group power targets responding units without commanding the failed unit`() {
        val writes = mutableListOf<String>()
        panel(onToggle = { writes += it }, dark = true)
        capture("partial-dark")
        compose.onNode(hasContentDescription("Питание: Бризеры") and home).assertIsEnabled().performClick()
        assertEquals(listOf("hall", "small", "child"), writes)
    }

    @Test
    fun `group power switches all responding units off even when the missing unit has a cached off state`() {
        val writes = mutableListOf<String>()
        panel(tiles = devices.map { it.copy(isOn = it.error == null) }, onToggle = { writes += it })
        icon("Спальня").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Нет связи"))
        compose.onNode(hasContentDescription("Питание: Бризеры") and home).performClick()
        assertEquals(listOf("hall", "study", "small", "child"), writes)
    }

    @Test
    fun `fresh climate without power is unknown rather than unreachable`() {
        panel(tiles = listOf(base.copy(isOn = null, room = "Зал", online = true)))
        icon("Зал").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Нет данных"))
    }

    @Test
    fun `shared failure marks every icon without labelling cached states current`() {
        panel(error = "Нет сети")
        icon("Зал").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Нет обновления"))
        compose.onNode(hasContentDescription("Питание: Бризеры") and home).assertIsNotEnabled()
        capture("shared-failure")
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp-port-340dpi")
    fun `small screen and large text keep every breather reachable`() {
        panel(fontScale = 1.5f)
        devices.forEach { icon(it.room!!).assertExists() }
        capture("narrow-large-text")
    }

    private fun icon(room: String) = compose.onNode(hasContentDescription("Показать бризер: Бризер, $room") and home)

    private fun panel(
        tiles: List<RecuperatorTileState> = devices,
        error: String? = null,
        dark: Boolean = false,
        fontScale: Float = 1f,
        onToggle: (String) -> Unit = {},
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MaterialTheme(colorScheme = if (dark) panelDarkScheme else panelLightScheme, typography = panelTypography) {
                    Surface {
                        PanelRooms(
                            acs = AcPanelState(), curtains = CurtainPanelState(), strips = LightStripPanelState(),
                            recuperators = RecuperatorPanelState(tiles = tiles, error = error, lastPolledAt = Flat.NOW),
                            bulbs = BulbPanelState(), launchers = emptyList(), now = Flat.NOW,
                            yandexInterval = Flat.YANDEX_INTERVAL, tuyaInterval = Flat.TUYA_INTERVAL,
                            onToggleRecuperator = onToggle,
                        )
                    }
                }
            }
        }
    }

    private fun capture(name: String) {
        compose.onRoot().captureRoboImage(File("build/outputs/breezer-status/$name.png"))
    }
}
