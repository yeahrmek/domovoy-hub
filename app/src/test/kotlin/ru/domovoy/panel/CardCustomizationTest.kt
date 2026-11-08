package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
class CardCustomizationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `home breezers start in one card and expand to individual controls`() {
        panel()
        compose.onNodeWithContentDescription("Показать бризеры: Бризеры, Все комнаты").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Настроить: ${Flat.recuperators.tiles.first().name}, ${Flat.recuperators.tiles.first().room}").assertIsDisplayed()
        compose.onNodeWithContentDescription("Свернуть: Бризеры, Все комнаты").performClick()
        compose.onNodeWithContentDescription("Настроить: ${Flat.recuperators.tiles.first().name}, ${Flat.recuperators.tiles.first().room}").assertDoesNotExist()
    }

    @Test
    fun `home sorts directly without an edit mode`() {
        panel()
        compose.onNodeWithText("Изменить").assertDoesNotExist()
        compose.onNodeWithText("Готово").assertDoesNotExist()
    }

    @Test
    fun `room cards keep the same dimensions as favourites`() {
        panel(withAc = true)
        val matcher = hasContentDescription("Настроить: Кондиционер, Зал")
        val home = compose.onNode(matcher and hasAnyAncestor(hasTestTag("section:Главная"))).getUnclippedBoundsInRoot()
        val room = compose.onNode(matcher and hasAnyAncestor(hasTestTag("section:Зал"))).performScrollTo().getUnclippedBoundsInRoot()
        assertEquals(home.right - home.left, room.right - room.left)
        assertEquals(home.bottom - home.top, room.bottom - room.top)
    }

    @Test
    fun `accessible card actions reorder and remove and restore a favourite from its room`() {
        var saved = CardPreferences()
        panel(withAc = true, onChange = { saved = it })
        compose.onNode(hasCardAction("Ниже: Бризеры") and hasAnyAncestor(hasTestTag("section:Главная"))).performCustomAccessibilityActionWithLabel("Ниже: Бризеры")
        assertEquals(listOf("ac:${Flat.acs.tiles.first().id}", "breezers"), saved.orders["Главная"])
        compose.onNode(hasCardAction("Убрать из избранного: Бризеры") and hasAnyAncestor(hasTestTag("section:Главная"))).performCustomAccessibilityActionWithLabel("Убрать из избранного: Бризеры")
        assertTrue("breezers" !in saved.favourites.orEmpty())
        val room = Flat.recuperators.tiles.first().room
        compose.onNode(hasCardAction("Добавить в избранное: Бризеры") and hasAnyAncestor(hasTestTag("section:$room"))).performScrollTo().performCustomAccessibilityActionWithLabel("Добавить в избранное: Бризеры")
        assertTrue("breezers" in saved.favourites.orEmpty())
    }

    @Test
    fun `an individually favourited bulb stays on Home when its power becomes known`() {
        val lamp = Flat.bulbs.tiles.first().copy(isOn = null)
        var bulbs by mutableStateOf(Flat.bulbs.copy(tiles = listOf(lamp)))
        panel(preferences = CardPreferences(favourites = listOf("bulb:${lamp.id}")), bulbs = { bulbs })
        val card = hasContentDescription("Настроить: ${lamp.name}, ${lamp.room}") and hasAnyAncestor(hasTestTag("section:Главная"))
        compose.onNode(card).assertIsDisplayed()
        bulbs = bulbs.copy(tiles = listOf(lamp.copy(isOn = true)))
        compose.onNode(card).assertIsDisplayed()
    }

    @Test
    fun `a favourite light group stays on Home when all its readings become unknown`() {
        val lamp = Flat.bulbs.tiles.first().copy(isOn = true)
        var bulbs by mutableStateOf(Flat.bulbs.copy(tiles = listOf(lamp)))
        panel(preferences = CardPreferences(favourites = listOf("lamps:${lamp.room}")), bulbs = { bulbs })
        val card = hasContentDescription("Показать лампы: Свет, ${lamp.room}") and hasAnyAncestor(hasTestTag("section:Главная"))
        compose.onNode(card).assertIsDisplayed()
        bulbs = bulbs.copy(tiles = listOf(lamp.copy(isOn = null)))
        compose.onNode(card).assertIsDisplayed()
    }

    private fun panel(withAc: Boolean = false, onChange: (CardPreferences) -> Unit = {}, preferences: CardPreferences = CardPreferences(), bulbs: () -> BulbPanelState = { BulbPanelState(lastPolledAt = Flat.NOW) }) {
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                Surface {
                    PanelRooms(
                        acs = if (withAc) Flat.acs.copy(tiles = listOf(Flat.acs.tiles.first())) else AcPanelState(lastPolledAt = Flat.NOW),
                        curtains = CurtainPanelState(lastPolledAt = Flat.NOW),
                        strips = LightStripPanelState(lastPolledAt = Flat.NOW),
                        recuperators = Flat.recuperators,
                        bulbs = bulbs(),
                        cardPreferences = preferences,
                        launchers = emptyList(), now = Flat.NOW,
                        onCardPreferencesChange = onChange,
                        yandexInterval = Flat.YANDEX_INTERVAL, tuyaInterval = Flat.TUYA_INTERVAL,
                    )
                }
            }
        }
    }
}
