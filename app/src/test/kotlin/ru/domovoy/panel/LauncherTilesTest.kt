package ru.domovoy.panel

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Launcher tiles are the panel's admission that two of the flat's five vendors are not integrations:
 * Domonap publishes no API worth calling and Xiaomi will not issue credentials, so the tile opens
 * the vendor's own app and claims nothing else.
 *
 * Everything here is driven off a `canOpen` function rather than a real [android.content.pm
 * .PackageManager]: whether a package resolves is the *only* thing the tile learns from the
 * framework, and nothing in `src/test/` should need a device to answer it.
 */
class LauncherTilesTest {
    @Test
    fun `the panel has a launcher tile for each app it can only open`() {
        val tiles = launcherTiles(canOpen = { true })

        assertEquals(listOf("com.domonap.app", "com.xiaomi.smarthome"), tiles.map { it.packageName })
        assertEquals(listOf("Домофон", "Пылесос"), tiles.map { it.name })
    }

    @Test
    fun `an installed app's tile says it opens, and shows no age because it reads nothing`() {
        val tile = launcherTiles(canOpen = { true }).single { it.packageName == "com.domonap.app" }

        assertTrue(tile.openable)
        assertEquals("opens the app", statusLine(tile))
        assertEquals("no state to read", detailLine(tile))
    }

    @Test
    fun `an app the tablet does not have names the missing package instead of tapping into nothing`() {
        val tile = launcherTiles(canOpen = { false }).single { it.packageName == "com.xiaomi.smarthome" }

        assertFalse(tile.openable)
        // **The package gets a line of its own**, which is the only place on the wall an identifier
        // may be cut short rather than wrapped. Run onto the end of "not installed · " it broke
        // mid-word across three lines of a 188 dp tile — `com.example.vacu` over `um`.
        assertEquals("not installed", statusLine(tile))
        assertEquals("com.xiaomi.smarthome", detailLine(tile))
    }

    @Test
    fun `one app missing leaves the other tile openable`() {
        // The panel's rule for vendors applies to their apps too: Mi Home never being installed on
        // the tablet must not take the intercom tile down with it.
        val tiles = launcherTiles(canOpen = { it == "com.domonap.app" })

        assertEquals(listOf(true, false), tiles.map { it.openable })
    }

    @Test
    fun `no launcher tile ever shows an age, because nothing ever polls one`() {
        // Every other tile on the wall has to say how old its reading is. These have no reading:
        // they hold one fact, read from the tablet itself, and an age printed here would be an age
        // for something the panel never asked a vendor about.
        val lines =
            listOf(true, false).flatMap { open ->
                launcherTiles { open }.flatMap { tile -> listOf(statusLine(tile), detailLine(tile)) }
            }

        assertTrue(
            lines.none { line -> "ago" in line || "never read" in line || "just now" in line },
            "a launcher tile printed an age: $lines",
        )
    }

    @Test
    fun `the intercom is in the hallway the panel hangs in, and the vacuum's app in no room`() {
        val tiles = launcherTiles(canOpen = { true }).associateBy { it.packageName }

        // The intercom is answered at the front door, which is the room the panel is in.
        assertEquals("Коридор", tiles.getValue("com.domonap.app").room)
        // The vacuum cleans every room and is docked in none the panel knows about; no room is the
        // honest answer, and it lands in the unplaced section rather than being invented one.
        assertNull(tiles.getValue("com.xiaomi.smarthome").room)
    }
}
