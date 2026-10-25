package ru.domovoy.panel

import org.junit.jupiter.api.Test
import ru.domovoy.core.Reading
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Home membership distinguishes explicit provider failures, silent stale polls and individual failures. */
class FavouritesTest {
    private val yandex = 15.seconds
    private val tuya = 6.minutes
    private val now = Instant.ofEpochSecond(1_786_000_000)

    /** Every group read a moment ago: the panel is working, whatever the tiles' own ages say. */
    private val polling =
        GroupPolls(
            acs = polledSecondsAgo(5),
            curtains = polledSecondsAgo(5),
            strips = polledSecondsAgo(5),
            recuperators = polledSecondsAgo(5),
            bulbs = polledSecondsAgo(5),
        )

    @Test
    fun `every tile in коридор and зал is on главная, fresh or not`() {
        val sections =
            sections(
                bulbs = listOf(bulb("light-01", "Коридор"), bulb("light-02", "Зал")),
                acs = listOf(ac("ac-03", "Зал")),
            )

        val home = favourites(sections, GroupErrors(), polling, now, yandex, tuya)

        assertEquals(listOf("light-01", "light-02"), home.bulbs.map { it.id })
        assertEquals(listOf("ac-03"), home.acs.map { it.id })
    }

    @Test
    fun `every launcher tile is on главная, whatever room it is in`() {
        // The интерком is in the коридор and Mi Home is in no room at all; both are on Главная.
        val sections = sections(launchers = launcherTiles(canOpen = { true }))

        val home = favourites(sections, GroupErrors(), polling, now, yandex, tuya)

        assertEquals(
            listOf("com.domonap.app", "com.xiaomi.smarthome"),
            home.launchers.map { it.packageName },
        )
    }

    @Test
    fun `a tile whose group has stopped being polled is pulled in`() {
        // The bulb's own reading is 30 s old and looks fine; nothing has read it for three minutes.
        val sections = sections(bulbs = listOf(bulb("light-20", "Спальня")))

        val home =
            favourites(sections, GroupErrors(), polling.copy(bulbs = polledSecondsAgo(180)), now, yandex, tuya)

        assertEquals(listOf("light-20"), home.bulbs.map { it.id })
    }

    @Test
    fun `a tile with an old reading is not pulled in while its group is polling`() {
        // Three weeks since this lamp was touched, and every poll since has read it. It belongs on
        // Спальня's own tab; Главная is for what the panel has stopped reading.
        val sections =
            sections(
                bulbs =
                listOf(
                    bulb("light-20", "Спальня", secondsAgo(21 * 86_400)),
                    bulb("light-21", "Спальня", Reading.Never),
                ),
            )

        val home = favourites(sections, GroupErrors(), polling, now, yandex, tuya)

        assertTrue(home.bulbs.isEmpty(), "a steady спальня bulb belongs on its own tab: ${home.bulbs}")
    }

    @Test
    fun `an explicit group error keeps non-favourite devices in their rooms`() {
        val sections = sections(
            bulbs = listOf(bulb("light-home", "Коридор"), bulb("light-bedroom", "Спальня", secondsAgo(90))),
        )

        val home = favourites(sections, GroupErrors(bulbs = "HTTP 500"), polling, now, yandex, tuya)

        assertEquals(listOf("light-home"), home.bulbs.map { it.id })
        assertEquals(listOf("light-bedroom"), sections.single { it.room == "Спальня" }.bulbs.map { it.id })
    }

    @Test
    fun `shared errors do not copy other rooms onto Home even after polls expire or never succeed`() {
        val sections = sections(
            acs = listOf(ac("ac-home", "Зал"), ac("ac-bedroom", "Спальня")),
            curtains = listOf(curtain("curtain-home", "Зал"), curtain("curtain-bedroom", "Спальня")),
            strips = listOf(strip("strip-home", "Зал"), strip("strip-bedroom", "Спальня")),
            bulbs = listOf(bulb("light-home", "Зал"), bulb("light-bedroom", "Спальня")),
            recuperators = listOf(recuperator("fan-home", "Зал"), recuperator("fan-bedroom", "Спальня")),
        )
        val failure = "Подписка истекла"
        val errors = GroupErrors(failure, failure, failure, failure, failure)
        val expired = polledSecondsAgo(7200)
        for (polls in listOf(GroupPolls(expired, expired, expired, expired, expired), GroupPolls(null, null, null, null, null))) {
            val home = favourites(sections, errors, polls, now, yandex, tuya)

            assertEquals(listOf("ac-home"), home.acs.map { it.id })
            assertEquals(listOf("curtain-home"), home.curtains.map { it.id })
            assertEquals(listOf("strip-home"), home.strips.map { it.id })
            assertEquals(listOf("light-home"), home.bulbs.map { it.id })
            assertEquals(listOf("fan-home"), home.recuperators.map { it.id })
        }
        val bedroom = sections.single { it.room == "Спальня" }
        assertEquals(listOf("ac-bedroom"), bedroom.acs.map { it.id })
        assertEquals(listOf("curtain-bedroom"), bedroom.curtains.map { it.id })
        assertEquals(listOf("strip-bedroom"), bedroom.strips.map { it.id })
        assertEquals(listOf("light-bedroom"), bedroom.bulbs.map { it.id })
        assertEquals(listOf("fan-bedroom"), bedroom.recuperators.map { it.id })
    }

    @Test
    fun `silent stale polls still surface every device whose group stopped answering`() {
        val sections = sections(
            acs = listOf(ac("ac-bedroom", "Спальня")),
            curtains = listOf(curtain("curtain-bedroom", "Спальня")),
            strips = listOf(strip("strip-bedroom", "Спальня")),
            bulbs = listOf(bulb("light-bedroom", "Спальня")),
            recuperators = listOf(recuperator("fan-bedroom", "Спальня")),
        )
        val expired = polledSecondsAgo(7200)
        for (polls in listOf(GroupPolls(expired, expired, expired, expired, expired), GroupPolls(null, null, null, null, null))) {
            val home = favourites(sections, GroupErrors(), polls, now, yandex, tuya)

            assertEquals(listOf("ac-bedroom"), home.acs.map { it.id })
            assertEquals(listOf("curtain-bedroom"), home.curtains.map { it.id })
            assertEquals(listOf("strip-bedroom"), home.strips.map { it.id })
            assertEquals(listOf("light-bedroom"), home.bulbs.map { it.id })
            assertEquals(listOf("fan-bedroom"), home.recuperators.map { it.id })
        }
    }

    @Test
    fun `a recuperators own failure remains on Home during a shared provider error`() {
        val sections = sections(
            recuperators = listOf(
                recuperator("fan-home", "Зал"),
                recuperator("fan-healthy", "Кабинет"),
                recuperator("fan-failed", "Спальня", error = "Нет связи"),
            ),
        )

        val home = favourites(
            sections,
            GroupErrors(recuperators = "Подписка истекла"),
            polling.copy(recuperators = null),
            now,
            yandex,
            tuya,
        )

        assertEquals(listOf("fan-home", "fan-failed"), home.recuperators.map { it.id })
        assertEquals(listOf("fan-healthy"), sections.single { it.room == "Кабинет" }.recuperators.map { it.id })
        assertEquals(listOf("fan-failed"), sections.single { it.room == "Спальня" }.recuperators.map { it.id })
    }

    @Test
    fun `the recuperators' poll stopping leaves the yandex tiles where they are`() {
        // Two polls, two answers. Tuya going quiet says nothing about the bulbs — they are fed by
        // a different call on a different timer, and pulling them in would be Главная crying wolf.
        val sections =
            sections(
                bulbs = listOf(bulb("light-20", "Спальня")),
                recuperators = listOf(recuperator("xfj-01", "Кабинет")),
            )

        val home =
            favourites(sections, GroupErrors(), polling.copy(recuperators = polledSecondsAgo(3600)), now, yandex, tuya)

        assertTrue(home.bulbs.isEmpty(), "the bulbs are read by Yandex, which is answering: ${home.bulbs}")
        assertEquals(listOf("xfj-01"), home.recuperators.map { it.id })
    }

    @Test
    fun `a recuperator whose own read failed is pulled in`() {
        // A recuperator fails on its own, one device at a time: four working ones must not drag the
        // fifth's failure onto Главная, and the fifth must not hide behind them.
        val sections =
            sections(
                recuperators =
                listOf(
                    recuperator("xfj-01", "Кабинет"),
                    recuperator("xfj-05", "Кабинет", error = "timeout"),
                ),
            )

        val home = favourites(sections, GroupErrors(), polling, now, yandex, tuya)

        assertEquals(listOf("xfj-05"), home.recuperators.map { it.id })
    }

    @Test
    fun `a tile the favourite rooms hold is on главная once, not twice`() {
        // The коридор's launcher is both "a tile of the коридор" and "a launcher"; the коридор's
        // bulb is both "a tile of the коридор" and one whose group has stopped being read.
        val sections =
            sections(
                bulbs = listOf(bulb("light-01", "Коридор")),
                launchers = launcherTiles(canOpen = { true }),
            )

        val home =
            favourites(sections, GroupErrors(), polling.copy(bulbs = polledSecondsAgo(600)), now, yandex, tuya)

        assertEquals(listOf("light-01"), home.bulbs.map { it.id })
        assertEquals(1, home.launchers.count { it.packageName == "com.domonap.app" })
    }

    private fun sections(
        acs: List<AcTileState> = emptyList(),
        recuperators: List<RecuperatorTileState> = emptyList(),
        curtains: List<CurtainTileState> = emptyList(),
        strips: List<LightStripTileState> = emptyList(),
        bulbs: List<BulbTileState> = emptyList(),
        launchers: List<LauncherTileState> = emptyList(),
    ) = roomSections(
        acs = acs,
        curtains = curtains,
        strips = strips,
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

    private fun curtain(id: String, room: String?) = Flat.curtains.tiles.first().copy(id = id, room = room)

    private fun strip(id: String, room: String?) = Flat.strips.tiles.first().copy(id = id, room = room)

    private fun secondsAgo(seconds: Long) = Reading.At(now.minusSeconds(seconds))

    private fun minutesAgo(minutes: Long) = secondsAgo(minutes * 60)

    private fun polledSecondsAgo(seconds: Long): Instant = now.minusSeconds(seconds)
}
