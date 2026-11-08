package ru.domovoy.panel

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CompactCardFeedbackTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `home failures show a dash and retain their reason for accessibility`() {
        val tile = Flat.acs.tiles.first()
        val error = "Не удалось выполнить запрос"
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                HomeDeviceTile(
                    anatomy(tile, Flat.NOW, error),
                    paint(tile, error),
                    Flat.NOW,
                    Flat.NOW,
                    Flat.YANDEX_INTERVAL,
                    onOpen = {},
                    commandError = error,
                )
            }
        }
        compose.onNodeWithText(error).assertDoesNotExist()
        compose.onNodeWithText("—").assertExists()
        compose.onNodeWithContentDescription("Настроить: Кондиционер, Зал")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, error))
    }

    @Test
    fun `unknown card readings use a dash without no data copy`() {
        val tile = Flat.acs.tiles.first().copy(isOn = null, targetTemperature = null)
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                TileCard(anatomy(tile, Flat.NOW, null), paint(tile, null))
            }
        }
        compose.onNodeWithText("Нет данных").assertDoesNotExist()
        compose.onNodeWithText("—").assertExists()
    }

    @Test
    fun `device details keep the full request failure`() {
        compose.setContent {
            MaterialTheme(colorScheme = panelLightScheme, typography = panelTypography) {
                DeviceSheet(sheet(Flat.acs.tiles.first(), Flat.NOW, "Не удалось выполнить запрос"), onDismiss = {})
            }
        }
        compose.onNodeWithText("Не удалось выполнить запрос", substring = true).assertExists()
    }
}
