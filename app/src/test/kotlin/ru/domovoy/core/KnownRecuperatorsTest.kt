package ru.domovoy.core

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the panel remembers about the recuperators between runs, and why it remembers anything at
 * all: their tiles exist only once Tuya's inventory call has answered, and that call is made every
 * 6 minutes. A tablet that reboots while the Wi-Fi is still coming up therefore has no recuperators
 * on the wall for six minutes, with one line of error where five tiles should be.
 *
 * Only who they are is kept — id, name, room. Never a value: a switch position from before the
 * reboot is not something the panel has read, and a tile must not print it as if it had.
 */
class KnownRecuperatorsTest {
    @Test
    fun `the recuperators the panel read are the ones it comes back with`() {
        val prefs = FakeSharedPreferences()

        KnownRecuperators(prefs).remember(listOf(device("xfj-01", "Бризер зал", room = "Зал")))

        // A second store over the same file is what the panel does after a restart.
        val remembered = KnownRecuperators(prefs).remembered().single()
        assertEquals("xfj-01", remembered.id)
        assertEquals("Бризер зал", remembered.name)
        assertEquals("Зал", remembered.room)
        assertEquals(DeviceKind.Recuperator, remembered.kind)
    }

    @Test
    fun `a remembered recuperator carries no values at all, only who it is`() {
        // The point of the memory is the name and the place on the wall. Whether it was on three
        // days ago is not a reading, and a tile that printed it would be claiming a poll that
        // never happened.
        val prefs = FakeSharedPreferences()
        val read =
            device("xfj-01", "Бризер зал", room = "Зал").copy(
                onOff = OnOff(isOn = true, lastUpdated = Reading.At(Instant.EPOCH), stateChangedAt = Reading.Never),
            )

        KnownRecuperators(prefs).remember(listOf(read))

        val remembered = KnownRecuperators(prefs).remembered().single()
        assertNull(remembered.onOff)
        assertTrue(remembered.toggles.isEmpty())
        assertTrue(remembered.ranges.isEmpty())
        assertNull(remembered.online)
    }

    @Test
    fun `a recuperator the flat placed in no room comes back unplaced, not in a room called null`() {
        val prefs = FakeSharedPreferences()

        KnownRecuperators(prefs).remember(listOf(device("xfj-03", "Бризер данина комната", room = null)))

        assertNull(KnownRecuperators(prefs).remembered().single().room)
    }

    @Test
    fun `a panel that has read nothing yet remembers nothing`() {
        assertEquals(emptyList(), KnownRecuperators(FakeSharedPreferences()).remembered())
    }

    @Test
    fun `a store holding nonsense is empty rather than a wall that will not start`() {
        // Whatever put it there — a half-written file, a downgrade — the panel comes up with no
        // memory and polls, which is exactly what it did before it had one.
        val prefs = FakeSharedPreferences()
        prefs.edit().putString("tuya.recuperators", "{not json").apply()

        assertEquals(emptyList(), KnownRecuperators(prefs).remembered())
    }

    @Test
    fun `a tablet with no secure storage remembers nothing and still runs`() {
        // The keystore can be lost to a restored backup or a wipe, and MainActivity then has no
        // prefs to hand over. That is a panel with no memory, not a panel that crashes.
        val none = KnownRecuperators(null)

        none.remember(listOf(device("xfj-01", "Бризер зал", room = "Зал")))

        assertEquals(emptyList(), none.remembered())
    }

    @Test
    fun `the account losing a recuperator loses it from the memory too`() {
        val prefs = FakeSharedPreferences()
        val store = KnownRecuperators(prefs)

        store.remember(listOf(device("xfj-01", "Бризер зал", null), device("xfj-02", "Бризер спальня", null)))
        store.remember(listOf(device("xfj-01", "Бризер зал", null)))

        assertEquals(listOf("xfj-01"), KnownRecuperators(prefs).remembered().map { it.id })
    }

    private fun device(
        id: String,
        name: String,
        room: String?,
    ) = Device(id = id, name = name, kind = DeviceKind.Recuperator, room = room, onOff = null)
}
