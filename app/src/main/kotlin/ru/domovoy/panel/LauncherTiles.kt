package ru.domovoy.panel

/**
 * What one launcher tile renders. There is no vendor client behind it and no reading on it: a
 * launcher tile holds one fact, and that fact is about the tablet rather than about the flat.
 */
data class LauncherTileState(
    /** The app this tile opens. Also what the tile prints when the app is not there to open. */
    val packageName: String,
    /** What the tile is called on the wall — the device, not the app: "Домофон", not "Domonap". */
    val name: String,
    /** Which room the tile is shown in, or null for the ones that belong to no room; see below. */
    val room: String?,
    /**
     * Whether the tablet can open the app right now — strictly, whether it resolves a launch
     * intent, which needs the app installed, carrying a launcher activity, and visible to us
     * through the `<queries>` block in the manifest. With that block naming both packages, the
     * only remaining way for this to be false is the app genuinely not being installed, which is
     * what the tile prints.
     */
    val openable: Boolean,
)

/**
 * The two tiles that only open somebody else's app, and why each of them is one:
 *
 * - **Домофон** — Domonap has no public API worth calling, and the panel deliberately calls none
 *   of it (docs/domonap.md). The call *takeover* is Domonap's own screen and arrives on its own;
 *   this tile is the other direction — opening the app on purpose, to look at the call log or let
 *   someone in before they ring.
 * - **Пылесос** — Xiaomi will not issue credentials to a project like this one (docs/xiaomi.md),
 *   and the decision recorded there is that Mi Home shows more of a vacuum than any tile the
 *   shared device model could hold. Whether Mi Home is embedded as a hosted AppWidget instead is
 *   still open; a launcher tile is what that note names as the fallback, and it needs nothing
 *   verified beyond the package name.
 *
 * The rooms, since a tile that cannot say where it belongs is as bad as one that cannot say how
 * old it is:
 *
 * - The intercom is answered at the front door, so its tile is in the **Коридор** — which is also
 *   the room the panel hangs in, and the first section on the wall.
 * - Mi Home is in the **Коридор** too, because that is where the vacuum docks. It used to be
 *   roomless, and that was the honest answer while nobody had recorded where it sits: the vacuum
 *   cleans every room, so no single room owns it. What settled it is the dock — a vacuum is fetched
 *   from and sent out from one place, and that place is the hallway the panel hangs in.
 *
 *   The cost of the old answer was where the tile rendered: `roomSections` puts unplaced tiles last,
 *   so "Пылесос" sat under **Без комнаты**, one scroll past all fourteen rooms, and Главная was the
 *   only place it could be reached without scrolling the whole wall.
 *
 *   **The humidifier the same app holds is somewhere else again**, and this does not answer for it.
 *   Mi Home is one launcher tile named for the vacuum; if the humidifier ever gets a tile of its
 *   own it gets its own room, and that room is not this one — see docs/xiaomi.md.
 *
 * [canOpen] is the one thing that comes from the framework, passed in rather than reached for so
 * that nothing in `src/test/` needs a `PackageManager` to answer it. It is asked again on every
 * refresh: an app installed on the tablet this afternoon has to reach the wall without the panel
 * being restarted.
 */
fun launcherTiles(canOpen: (String) -> Boolean): List<LauncherTileState> = CATALOGUE.map { entry ->
    LauncherTileState(
        packageName = entry.packageName,
        name = entry.name,
        room = entry.room,
        openable = canOpen(entry.packageName),
    )
}

/**
 * The line under the name: what this tile is, in two words. It carries no age, and that is the
 * point — every other tile on the wall has to say how old its reading is, and this one has no
 * reading. Nothing polls it, nothing about the flat is shown on it, so there is nothing here that
 * can be stale.
 */
internal fun statusLine(tile: LauncherTileState): String = if (tile.openable) {
    "opens the app"
} else {
    "not installed"
}

/**
 * The second line: the honest version of the age this tile does not have, or — when the app is not
 * there — **the package, on a line of its own where it may be cut short**.
 *
 * This is the one identifier on the wall the panel is allowed to truncate, and the reason it is
 * split off the line above rather than joined to it. `not installed · com.example.vacuum` is 34
 * characters on a 188 dp tile that holds about sixteen, so it wrapped, and it wrapped *mid-word*:
 * `com.example.vacu` over `um`, three lines deep. A package name has no shorter honest form, and a
 * device name — which the panel refuses to truncate at any width — is a different thing entirely:
 * this one is read at 30 cm by whoever is about to go and install it, not from four metres.
 *
 * "no state to read" is what the openable one says, and printing "just now" next to a name instead
 * would claim a freshness the tile never earned.
 */
internal fun detailLine(tile: LauncherTileState): String = if (tile.openable) {
    "no state to read"
} else {
    tile.packageName
}

/** One row of the catalogue; the tiles differ only in these three values. */
private class LauncherApp(
    val packageName: String,
    val name: String,
    val room: String?,
)

/**
 * Both packages named here rather than imported from the integrations: `DomonapCalls` knows
 * `com.domonap.app` as the sender of a notification, which is a different fact about the same
 * string, and Xiaomi has no package under `integrations/` at all to import from.
 *
 * Both are verified on the tablet, and both resolve a MAIN/LAUNCHER activity — which is the part
 * that matters, since `getLaunchIntentForPackage` answers null without one:
 * `com.domonap.app/.ui.main.MainActivity` and `com.xiaomi.smarthome/.SmartHomeMainActivity`. See
 * docs/domonap.md and docs/xiaomi.md; the manifest's `<queries>` block names the same two.
 */
private val CATALOGUE =
    listOf(
        LauncherApp(packageName = "com.domonap.app", name = "Домофон", room = "Коридор"),
        LauncherApp(packageName = "com.xiaomi.smarthome", name = "Пылесос", room = "Коридор"),
    )
