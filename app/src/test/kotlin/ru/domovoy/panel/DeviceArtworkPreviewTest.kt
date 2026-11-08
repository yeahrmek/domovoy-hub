package ru.domovoy.panel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import ru.domovoy.panelDarkScheme
import ru.domovoy.panelLightScheme
import ru.domovoy.panelTypography
import java.io.File
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w753dp-h1204dp-port-340dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeviceArtworkPreviewTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `hardware art on light cards`() = capture(dark = false)

    @Test
    fun `hardware art on dark cards`() = capture(dark = true)

    private fun capture(dark: Boolean) {
        val types = listOf(
            "openable.door_lock" to "Умный замок",
            "sensor.climate" to "Датчик климата",
            "smart_speaker.yandex.station.cucumber" to "Станция Миди",
            "media_device.tv.yandex.magritte" to "ТВ Станция",
            "switch" to "Кнопка",
            "socket" to "Розетка",
            "humidifier" to "Увлажнитель",
            "future" to "Устройство",
            "openable" to "Домофон",
            "vacuum_cleaner" to "Пылесос",
        )
        val anatomy = types.map { (type, name) ->
            val tile = Device(type, name, DeviceKind.Other, "Зал", null, vendorType = "devices.types.$type")
            TileAnatomy(art = sheet(tile, Flat.NOW, null).art, action = null, name = name, room = "Зал", promoted = null, status = "", detail = null)
        } + listOf(
            anatomy(Flat.recuperators.tiles.first().copy(room = "Спальня"), Flat.NOW, null),
            anatomy(Flat.recuperators.tiles.first(), Flat.NOW, null),
        )
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) panelDarkScheme else panelLightScheme, typography = panelTypography) {
                Surface {
                    Column {
                        anatomy.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { card ->
                                    HomeDeviceTile(
                                        anatomy = card,
                                        paint = paint(null, null, null),
                                        polledAt = Flat.NOW,
                                        now = Flat.NOW,
                                        interval = 1.minutes,
                                        onOpen = {},
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage(File("build/outputs/device-artwork/cards-${if (dark) "dark" else "light"}.png"))
    }
}
