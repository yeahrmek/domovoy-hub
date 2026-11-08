package ru.domovoy.panel

import org.junit.jupiter.api.Test
import ru.domovoy.R
import ru.domovoy.core.Device
import ru.domovoy.core.DeviceKind
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceArtworkTest {
    @Test
    fun `each discovered household device has hardware art without gaining controls`() {
        val artwork = mapOf(
            "sensor.climate" to R.drawable.device_art_climate_sensor,
            "openable.door_lock" to R.drawable.device_art_door_lock,
            "openable" to R.drawable.device_art_intercom,
            "vacuum_cleaner" to R.drawable.device_art_vacuum,
            "smart_speaker.yandex.station.cucumber" to R.drawable.device_art_station_midi_green,
            "media_device.tv.yandex.magritte" to R.drawable.device_art_television,
            "media_device.tv" to R.drawable.device_art_television,
            "switch" to R.drawable.device_art_switch,
            "socket" to R.drawable.device_art_socket,
            "humidifier" to R.drawable.device_art_humidifier,
        )
        artwork.forEach { (type, expected) ->
            val tile = Device("fixture", "Устройство", DeviceKind.Other, "Зал", null, vendorType = "devices.types.$type")
            val sheet = sheet(tile, Flat.NOW, null)
            assertEquals(expected, sheet.art, type)
            assertTrue(sheet.actions.isEmpty(), type)
        }
    }

    @Test
    fun `unknown hardware has a fallback without being mistaken for a Midi`() {
        listOf(null, "devices.types.future", "devices.types.smart_speaker.yandex.station", "devices.types.sensor.future").forEach { type ->
            val tile = Device("fixture", "Устройство", DeviceKind.Other, null, null, vendorType = type)
            assertEquals(R.drawable.device_art_generic, sheet(tile, Flat.NOW, null).art, type)
        }
    }

    @Test
    fun `only the bedroom breezer uses Airnanny art including in details and room summaries`() {
        val bedroom = Flat.recuperators.tiles.first().copy(room = "Спальня")
        val hall = bedroom.copy(room = "Зал")
        assertEquals(R.drawable.device_art_airnanny_a7, art(bedroom))
        assertEquals(R.drawable.device_art_airnanny_a7, sheet(bedroom, Flat.NOW, null).art)
        assertEquals(R.drawable.device_art_airnanny_a7, art(listOf(bedroom)))
        assertEquals(R.drawable.device_art_recuperator, art(hall))
        assertEquals(R.drawable.device_art_recuperator, art(listOf(bedroom, hall)))
        assertEquals(R.drawable.device_art_recuperator, art(emptyList<RecuperatorTileState>()))
    }
}
