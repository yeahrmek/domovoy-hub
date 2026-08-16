package ru.domovoy.panel

import org.junit.jupiter.api.Test
import ru.domovoy.core.Reading
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Главная is defined in code, in one place, the way `ROOM_ORDER` is: the rooms switched on the way
 * in and on the way out, the launchers because the intercom is why somebody walks up to this panel
 * at all, and anything anywhere that is failing or stale — so a mark on a room's tab has somewhere
 * to lead.
 */
class FavouritesTest {
    private val yandex = 15.seconds
    private val tuya = 6.minutes
    private val now = Instant.ofEpochSecond(1_786_000_000)

    @Test
    fun `every tile in коридор and зал is on главная, fresh or not`() {
        val sections =
            sections(
                bulbs = listOf(bulb("light-01", "Коридор"), bulb("light-02", "Зал")),
                acs = listOf(ac("ac-03", "Зал")),
            )

        val home = favourites(sections, GroupErrors(), now, yandex, tuya)

        assertEquals(listOf("light-01", "light-02"), home.bulbs.map { it.id })
        assertEquals(listOf("ac-03"), home.acs.map { it.id })
    }

    @Test
    fun `every launcher tile is on главная, whatever room it is in`() {
        // The интерком is in the коридор and Mi Home is in no room at all; both are on Главная.
        val sections = sections(launchers = launcherTiles(canOpen = { true }))

        val home = favourites(sections, GroupErrors(), now, yandex, tuya)

        assertEquals(
            listOf("com.domonap.app", "com.xiaomi.smarthome"),
            home.launchers.map { it.packageName },
        )
    }

    @Test
    fun `a stale tile from another room is pulled in`() {
        val sections = sections(bulbs = listOf(bulb("light-20", "Спальня", secondsAgo(180))))

        val home = favourites(sections, GroupErrors(), now, yandex, tuya)

        assertEquals(listOf("light-20"), home.bulbs.map { it.id })
    }

    @Test
    fun `a fresh tile from another room is not`() {
        val sections = sections(bulbs = listOf(bulb("light-20", "Спальня", secondsAgo(90))))

        val home = favourites(sections, GroupErrors(), now, yandex, tuya)

        assertTrue(home.bulbs.isEmpty(), "a fresh спальня bulb belongs on its own tab: ${home.bulbs}")
    }

    @Test
    fun `a tile whose group failed is pulled in even though its reading is fresh`() {
        // The reading is 90 s old and would be fresh; the group stopped updating a moment ago and
        // that is the thing somebody at the wall has to be told without opening every tab.
        val sections = sections(bulbs = listOf(bulb("light-20", "Спальня", secondsAgo(90))))

        val home = favourites(sections, GroupErrors(bulbs = "HTTP 500"), now, yandex, tuya)

        assertEquals(listOf("light-20"), home.bulbs.map { it.id })
    }

    @Test
    fun `a recuperator whose own read failed is pulled in`() {
        // A recuperator fails on its own, one device at a time: four working ones must not drag the
        // fifth's failure onto Главная, and the fifth must not hide behind them.
        val sections =
            sections(
                recuperators =
                listOf(
                    recuperator("xfj-01", "Кабинет", minutesAgo(2)),
                    recuperator("xfj-05", "Кабинет", minutesAgo(2), error = "timeout"),
                ),
            )

        val home = favourites(sections, GroupErrors(), now, yandex, tuya)

        assertEquals(listOf("xfj-05"), home.recuperators.map { it.id })
    }

    @Test
    fun `a tile the favourite rooms hold is on главная once, not twice`() {
        // The коридор's launcher is both "a tile of the коридор" and "a launcher"; the коридор's
        // stale bulb is both "a tile of the коридор" and "stale".
        val sections =
            sections(
                bulbs = listOf(bulb("light-01", "Коридор", secondsAgo(600))),
                launchers = launcherTiles(canOpen = { true }),
            )

        val home = favourites(sections, GroupErrors(), now, yandex, tuya)

        assertEquals(listOf("light-01"), home.bulbs.map { it.id })
        assertEquals(1, home.launchers.count { it.packageName == "com.domonap.app" })
    }

    private fun sections(
        acs: List<AcTileState> = emptyList(),
        recuperators: List<RecuperatorTileState> = emptyList(),
        bulbs: List<BulbTileState> = emptyList(),
        launchers: List<LauncherTileState> = emptyList(),
    ) = roomSections(
        acs = acs,
        curtains = emptyList(),
        strips = emptyList(),
        recuperators = recuperators,
        bulbs = bulbs,
        launchers = launchers,
    )

    private fun bulb(
        id: String,
        room: String?,
        lastUpdated: Reading = secondsAgo(30),
    ) = BulbTileState(
        id = id,
        name = id,
        room = room,
        isOn = true,
        lastUpdated = lastUpdated,
        stateChangedAt = Reading.Never,
    )

    private fun ac(
        id: String,
        room: String?,
        lastUpdated: Reading = secondsAgo(30),
    ) = AcTileState(
        id = id,
        name = id,
        room = room,
        isOn = true,
        powerLastUpdated = lastUpdated,
        targetTemperature = 22.0,
        bounds = null,
        unit = null,
        temperatureLastUpdated = lastUpdated,
    )

    private fun recuperator(
        id: String,
        room: String?,
        lastUpdated: Reading = minutesAgo(2),
        error: String? = null,
    ) = RecuperatorTileState(
        id = id,
        name = id,
        room = room,
        isOn = true,
        powerLastUpdated = lastUpdated,
        speeds = listOf(FanSpeed.Low),
        speedLastUpdated = lastUpdated,
        temperature = null,
        temperatureLastUpdated = Reading.Never,
        humidity = null,
        humidityLastUpdated = Reading.Never,
        online = true,
        error = error,
    )

    private fun secondsAgo(seconds: Long) = Reading.At(now.minusSeconds(seconds))

    private fun minutesAgo(minutes: Long) = secondsAgo(minutes * 60)
}
