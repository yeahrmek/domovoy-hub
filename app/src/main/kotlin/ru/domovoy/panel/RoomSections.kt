package ru.domovoy.panel

/**
 * The order the panel shows the rooms in, and the only place that order is decided.
 *
 * It is a hallway panel: it hangs in the коридор, is looked at on the way in and on the way out,
 * and is touched a few times a day. So the order is by how far a room is from the hand that
 * reaches for the tablet, not by how many devices a room has or by whatever order the vendor
 * happened to list its rooms in.
 *
 *  1. **Коридор** — the room the panel is in. Arriving and leaving, this is the one being switched.
 *  2. **Зал**, **Гостиная** — the shared rooms, walked into next and driven from the wall most.
 *  3. **Спальня**, **Спальня взрослая**, **Детская**, **Маленькая детская** — the rooms someone is
 *     in, or about to be. "Маленькая детская" is here even though no Yandex device is: the room's
 *     only tile is a recuperator, and it reaches the panel through `tuya.rooms` (see
 *     [recuperatorRooms]). Left out of this list it would sort after every named room, down by the
 *     bathrooms, which is not where a bedroom belongs.
 *  4. **Кабинет**, **Гардероб** — entered on purpose, and rarely on the way past.
 *  5. **Ванная**, **Детская ванная**, **Балкон** — switched at their own door, standing in them;
 *     a wall panel in the hallway is the long way round for these, so they are last.
 *
 * A room not named here — the flat gains one, or a vendor renames one — is not lost: it renders
 * after all of these, in name order, so it lands in the same place every poll rather than moving
 * about as devices come and go. Anything the vendors placed in no room at all comes last of all;
 * see [roomSections].
 */
private val ROOM_ORDER =
    listOf(
        "Коридор",
        "Зал",
        "Гостиная",
        "Спальня",
        "Спальня взрослая",
        "Детская",
        "Маленькая детская",
        "Кабинет",
        "Гардероб",
        "Ванная",
        "Детская ванная",
        "Балкон",
    )

/**
 * One room's worth of the panel: every tile in that room, whatever the device type and whatever
 * vendor it came from — the flat's one curtain sits with the bedroom's bulbs rather than in a
 * curtain section of its own.
 *
 * The tiles are kept apart by type inside the section because each renders through its own
 * composable, and within a room they are shown in this order: what heats, cools and moves first,
 * then the lights, which are the many, and last the launcher tiles — which change nothing in the
 * room and only send whoever tapped them into another app.
 */
data class RoomSection(
    /**
     * The room's name as the vendor spells it, or null for the section holding the tiles that are
     * in no room. Rooms are matched by name and not by id, because a name is all
     * [ru.domovoy.core.Device] carries — so the six rooms the recorded response calls "Спальня"
     * are one section here, which is what someone who named them all the same would expect.
     */
    val room: String?,
    val acs: List<AcTileState> = emptyList(),
    val curtains: List<CurtainTileState> = emptyList(),
    val strips: List<LightStripTileState> = emptyList(),
    val recuperators: List<RecuperatorTileState> = emptyList(),
    val bulbs: List<BulbTileState> = emptyList(),
    val launchers: List<LauncherTileState> = emptyList(),
)

/**
 * Lays the whole panel out by room.
 *
 * Sections are built from the tiles rather than from the vendor's list of rooms, so a room with
 * nothing in it never appears — the recorded response has seven such rooms, plus a "Гостиная"
 * holding only a vacuum and a tv, neither of which the panel has a tile for.
 *
 * The last section is the one for the tiles in no room, and it is never dropped. Two different
 * things land there, both of them on purpose:
 *
 * - Tuya names no room for the recuperators (docs/tuya.md), so unless the flat's own answer reached
 *   [TuyaPoll] they arrive with `room = null` — and a device falling off the wall because no vendor
 *   said where it is would be a bug, not a tidy panel.
 * - The vacuum's launcher tile has no room because it *has* no room: see [launcherTiles]. That one
 *   is not a gap waiting to be filled.
 *
 * Every tile group is a parameter here, launchers included and none of them defaulted. A tile group
 * that could be left out by forgetting to pass it is exactly how a section of the wall goes missing
 * without anybody being told.
 */
fun roomSections(
    acs: List<AcTileState>,
    curtains: List<CurtainTileState>,
    strips: List<LightStripTileState>,
    recuperators: List<RecuperatorTileState>,
    bulbs: List<BulbTileState>,
    launchers: List<LauncherTileState>,
): List<RoomSection> {
    val rooms =
        buildList {
            addAll(acs.map { it.room })
            addAll(curtains.map { it.room })
            addAll(strips.map { it.room })
            addAll(recuperators.map { it.room })
            addAll(bulbs.map { it.room })
            addAll(launchers.map { it.room })
        }.distinct()
            .sortedWith(compareBy<String?> { rank(it) }.thenBy { it.orEmpty() })
    return rooms.map { room ->
        RoomSection(
            room = room,
            acs = acs.filter { it.room == room },
            curtains = curtains.filter { it.room == room },
            strips = strips.filter { it.room == room },
            recuperators = recuperators.filter { it.room == room },
            bulbs = bulbs.filter { it.room == room },
            launchers = launchers.filter { it.room == room },
        )
    }
}

/**
 * How many tiles are under one heading — every group, since any of them can be the one that arrives.
 *
 * Two things ask: whether a section is worth a heading at all ([panelHeadings]), and whether the
 * wall has anything polled on it yet, which is what puts a rebooted panel back at the top. One
 * count for both, so a group added later cannot be counted by one of them and not the other.
 */
internal fun RoomSection.tileCount(): Int = acs.size + curtains.size + strips.size + recuperators.size +
    bulbs.size + launchers.size

// Named rooms in the order above, then the ones the order does not name, then the unplaced —
// which is not a room and so cannot be anywhere but last.
private fun rank(room: String?): Int = when {
    room == null -> Int.MAX_VALUE
    else -> ROOM_ORDER.indexOf(room).takeIf { it >= 0 } ?: ROOM_ORDER.size
}

/**
 * The failures no tile can carry: a group that has never had a tile has nothing to hang its reason
 * on, and without a line of its own it would simply be missing from the wall with no reason given.
 *
 * A group that failed *with* tiles on the wall is not here — each of its tiles already says "not
 * updating" next to the last value it had, and saying it again at the top of the panel would be
 * one error printed twice.
 *
 * The launcher tiles are not a group here and cannot be: nothing polls them, so there is no call
 * to fail. The one thing that can go wrong for them — the app not being installed — is on the tile
 * itself, where it names the missing package.
 */
internal fun groupFailures(
    acs: AcPanelState,
    curtains: CurtainPanelState,
    strips: LightStripPanelState,
    recuperators: RecuperatorPanelState,
    bulbs: BulbPanelState,
): List<String> = listOfNotNull(
    failure("Кондиционеры", acs.tiles.isEmpty(), acs.error),
    failure("Шторы", curtains.tiles.isEmpty(), curtains.error),
    failure("Подсветка", strips.tiles.isEmpty(), strips.error),
    failure("Бризеры", recuperators.tiles.isEmpty(), recuperators.error),
    failure("Лампы", bulbs.tiles.isEmpty(), bulbs.error),
)

private fun failure(
    group: String,
    hasNoTiles: Boolean,
    error: String?,
): String? = if (hasNoTiles && error != null) "$group: not updating: $error" else null
