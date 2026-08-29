package ru.domovoy.panel

import ru.domovoy.core.Bounds
import ru.domovoy.core.ColorSetting
import ru.domovoy.core.Reading
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * One flat's worth of tiles, held still, so that a screenshot of the panel is a picture of the
 * layout rather than of the weather.
 *
 * Every value here is fixed and every id is fake. Nothing is read from a fixture file — the vendor
 * JSON in `src/test/resources/` is what the *clients* are tested against, and by the time a tile
 * state exists the vendor is already behind us. What these have to be is plausible in shape: the
 * flat has many bulbs and few of everything else, one curtain, three ACs, five recuperators, and
 * that shape is what the mosaic was laid out against.
 *
 * The dates are the one thing worth stating outright: [NOW] and every reading below are constants,
 * because a tile prints how old its reading is and a clock in a screenshot test is a test that
 * fails every minute. See docs/ui.md, "Screenshots".
 */
internal object Flat {
    /** The instant the panel is drawn at. Nothing here derives from the system clock. */
    val NOW: Instant = Instant.parse("2026-08-16T21:20:00Z")

    /** The Yandex poll interval, as `MainActivity` sets it — what makes those four groups stale. */
    val YANDEX_INTERVAL = 15.seconds

    /** The recuperators', 24 times longer, which would call every Yandex tile stale. */
    val TUYA_INTERVAL = 6.minutes

    /** A reading taken [seconds] before [NOW]. */
    private fun ago(seconds: Long) = Reading.At(NOW.minusSeconds(seconds))

    /** The last poll that landed, [seconds] before [NOW]. Distinct from a reading — see "Stale". */
    private fun polled(seconds: Long): Instant = NOW.minusSeconds(seconds)

    private val PERCENT = Bounds(min = 1.0, max = 100.0, precision = 1.0)
    private val OPEN = Bounds(min = 0.0, max = 100.0, precision = 1.0)
    private val CELSIUS = Bounds(min = 16.0, max = 30.0, precision = 1.0)

    val acs = AcPanelState(
        tiles = listOf(
            AcTileState(
                id = "ac-01",
                name = "Кондиционер",
                room = "Зал",
                isOn = true,
                powerLastUpdated = ago(90),
                targetTemperature = 22.0,
                bounds = CELSIUS,
                unit = "unit.temperature.celsius",
                // 81 days apart from the power reading, as on the real `ac-01`: the tile prints
                // both ages and one number for the pair would have to lie about the older.
                temperatureLastUpdated = ago(81 * 24 * 60 * 60),
            ),
            AcTileState(
                id = "ac-02",
                name = "Кондиционер",
                room = "Спальня",
                isOn = false,
                powerLastUpdated = ago(90),
                targetTemperature = 24.0,
                bounds = CELSIUS,
                unit = "unit.temperature.celsius",
                temperatureLastUpdated = ago(3 * 24 * 60 * 60),
            ),
        ),
        lastPolledAt = polled(12),
    )

    val curtains = CurtainPanelState(
        tiles = listOf(
            CurtainTileState(
                id = "curtain-01",
                name = "Штора",
                room = "Зал",
                openPercent = 40.0,
                bounds = OPEN,
                lastUpdated = ago(600),
                stateChangedAt = ago(600),
            ),
        ),
        lastPolledAt = polled(12),
    )

    val strips = LightStripPanelState(
        tiles = listOf(
            LightStripTileState(
                id = "strip-01",
                name = "Лента",
                room = "Коридор",
                isOn = true,
                powerLastUpdated = ago(240),
                brightnessPercent = 60.0,
                bounds = PERCENT,
                unit = "unit.percent",
                brightnessLastUpdated = ago(240),
                color = ColorSetting(
                    instance = "temperature_k",
                    value = 4500.0,
                    lastUpdated = ago(240),
                    stateChangedAt = ago(240),
                ),
            ),
            LightStripTileState(
                id = "strip-02",
                name = "Подсветка",
                room = "Коридор",
                isOn = false,
                powerLastUpdated = ago(240),
                brightnessPercent = 20.0,
                bounds = PERCENT,
                unit = "unit.percent",
                brightnessLastUpdated = ago(240),
                color = null,
            ),
        ),
        lastPolledAt = polled(12),
    )

    val recuperators = RecuperatorPanelState(
        tiles = listOf(
            recuperator(id = "xfj-01", room = "Зал", temperature = 26.4, humidity = 41.0),
            recuperator(id = "xfj-02", room = "Спальня", temperature = 24.1, humidity = 46.5),
            // The one that failed on its own. Tuya state is a call per device, so this is the
            // fifth timing out and not the other four — and that is why the tile carries an error
            // no other tile type has. See docs/ui.md.
            recuperator(
                id = "xfj-03",
                room = "Детская",
                temperature = null,
                humidity = null,
                isOn = null,
                error = "timeout",
            ),
        ),
        lastPolledAt = polled(200),
    )

    /**
     * Коридор's four lamps and Зал's two. Three of Коридор's report a value, and the fourth has
     * never reported one at all — which is the bulb that leaves the row of circles and draws as its
     * own named tile. That split is the whole point of the lights group, so a fixture without a
     * `Never` bulb in it would screenshot the easy half of it.
     */
    val bulbs = BulbPanelState(
        tiles = listOf(
            bulb(id = "light-01", name = "Лампа 1", room = "Коридор", isOn = true),
            bulb(id = "light-02", name = "Лампа 2", room = "Коридор", isOn = true),
            bulb(id = "light-03", name = "Лампа 3", room = "Коридор", isOn = false),
            bulb(id = "light-04", name = "Бра", room = "Коридор", isOn = null),
            bulb(id = "light-05", name = "Люстра", room = "Зал", isOn = true),
            bulb(id = "light-06", name = "Торшер", room = "Зал", isOn = false),
        ),
        lastPolledAt = polled(12),
    )

    val launchers = listOf(
        LauncherTileState(packageName = "com.example.intercom", name = "Домофон", room = "Коридор", openable = true),
        // The one that is not installed. A launcher tile whose app is gone still draws, and says so
        // — a tile that vanished would be the panel hiding the thing that broke.
        LauncherTileState(packageName = "com.example.vacuum", name = "Пылесос", room = null, openable = false),
    )

    private fun recuperator(
        id: String,
        room: String,
        temperature: Double?,
        humidity: Double?,
        isOn: Boolean? = true,
        error: String? = null,
    ) = RecuperatorTileState(
        id = id,
        name = "Бризер",
        room = room,
        isOn = isOn,
        powerLastUpdated = ago(200),
        speeds = listOf(FanSpeed.Low, FanSpeed.Medium, FanSpeed.High),
        speedLastUpdated = ago(200),
        temperature = temperature,
        temperatureLastUpdated = if (temperature == null) Reading.Never else ago(200),
        humidity = humidity,
        humidityLastUpdated = if (humidity == null) Reading.Never else ago(200),
        online = error == null,
        error = error,
    )

    private fun bulb(
        id: String,
        name: String,
        room: String,
        isOn: Boolean?,
    ) = BulbTileState(
        id = id,
        name = name,
        room = room,
        isOn = isOn,
        // A bulb with no value has no reading either — that is one capability, and 33 of the 116
        // recorded ones are exactly this.
        lastUpdated = if (isOn == null) Reading.Never else ago(20 * 24 * 60 * 60),
        stateChangedAt = if (isOn == null) Reading.Never else ago(20 * 24 * 60 * 60),
    )
}
