package ru.domovoy.panel

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performScrollToNode
import kotlinx.coroutines.runBlocking
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

/**
 * Where the wall is scrolled to, which is the one thing about the stacked panel with no pure
 * function behind it — it is Compose state, and the two bugs it has had were both about when that
 * state is thrown away.
 *
 * The panel used to key its [LazyGridState] on the tile count, which did two things at once: it put
 * a rebooted tablet back at the top when the first poll landed *and* it threw away the scroll
 * position of whoever was reading a room when any device came or went. Only the first is wanted.
 * Both are asserted here, on the real [PanelRooms] — a fake grid would be a test of the fake.
 *
 * The same width and density as the screenshots, and for the same reason: this measures a panel, and
 * one measured at a width the tablet is not is a panel that does not exist.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelScrollTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a device appearing leaves whoever is scrolled where they are`() {
        // The bug. Somebody is standing at the panel reading a room some way down the scroll when a
        // poll lands holding one more bulb than the last one did — a device Yandex had dropped and
        // has found again — and the wall jumps back to Главная under their finger.
        val scroll = LazyGridState()
        var bulbs by mutableStateOf(Flat.bulbs)
        compose.setContent { Panel(bulbs = bulbs, scroll = scroll) }

        compose.runOnIdle { runBlocking { scroll.scrollToItem(6) } }
        val wasAt = compose.runOnIdle { scroll.firstVisibleItemIndex }
        bulbs = Flat.bulbs.copy(tiles = Flat.bulbs.tiles.dropLast(1))
        compose.waitForIdle()

        assertTrue(wasAt > 0, "the test has to have scrolled somewhere for this to say anything")
        assertEquals(
            wasAt,
            compose.runOnIdle { scroll.firstVisibleItemIndex },
            "a poll landing is not a reason to move the wall under somebody's hand",
        )
    }

    @Test
    fun `the first poll landing puts a rebooted panel back at the top`() {
        // The other half of what the keyed state was doing, and the half worth keeping. A tablet
        // that rebooted into a Wi-Fi that was not up yet comes up holding nothing but its launcher
        // tiles; when the first refresh lands, twenty tiles are inserted above them. Held in place
        // by its keys, the wall would come up from a reboot showing the last two tiles of the list.
        val scroll = LazyGridState()
        var polled by mutableStateOf(false)
        compose.setContent {
            Panel(
                acs = if (polled) Flat.acs else AcPanelState(),
                curtains = if (polled) Flat.curtains else CurtainPanelState(),
                strips = if (polled) Flat.strips else LightStripPanelState(),
                recuperators = if (polled) Flat.recuperators else RecuperatorPanelState(),
                bulbs = if (polled) Flat.bulbs else BulbPanelState(),
                scroll = scroll,
            )
        }

        // Whoever booted the tablet left it scrolled onto the launcher tiles, which is all there is.
        compose.runOnIdle { runBlocking { scroll.scrollToItem(2) } }
        polled = true
        compose.waitForIdle()

        assertEquals(0, compose.runOnIdle { scroll.firstVisibleItemIndex })
    }

    @Test
    fun `a room whose group failed wears the mark on its own heading`() {
        // The tab strip's rule, on the shape that replaced it: the bulbs' poll has failed, so every
        // room holding a bulb says so where its name is. Спальня has no bulb in this flat and is
        // not marked; Коридор does. Scrolled to rather than asserted on the first screenful,
        // because the point of moving the mark off the strip is that it travels with the room —
        // Ванная and Балкон used to carry a mark nobody could see.
        compose.setContent { Panel(bulbs = Flat.bulbs.copy(error = "HTTP 401")) }

        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Коридор •"))
    }

    @Composable
    private fun Panel(
        acs: AcPanelState = Flat.acs,
        curtains: CurtainPanelState = Flat.curtains,
        strips: LightStripPanelState = Flat.strips,
        recuperators: RecuperatorPanelState = Flat.recuperators,
        bulbs: BulbPanelState = Flat.bulbs,
        scroll: LazyGridState = LazyGridState(),
    ) {
        MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
            Surface {
                PanelRooms(
                    acs = acs,
                    curtains = curtains,
                    strips = strips,
                    recuperators = recuperators,
                    bulbs = bulbs,
                    launchers = Flat.launchers,
                    now = Flat.NOW,
                    yandexInterval = Flat.YANDEX_INTERVAL,
                    tuyaInterval = Flat.TUYA_INTERVAL,
                    scroll = scroll,
                )
            }
        }
    }
}
