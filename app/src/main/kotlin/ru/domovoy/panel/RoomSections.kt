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
 *  3. **Спальня**, **Спальня взрослая**, **Детская** — the rooms someone is in, or about to be.
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
 * then the lights, which are the many.
 */
data class RoomSection(
    /**
     * The room's name as the vendor spells it, or null for the section holding the devices no
     * vendor placed. Rooms are matched by name and not by id, because a name is all
     * [ru.domovoy.core.Device] carries — so the six rooms the recorded response calls "Спальня"
     * are one section here, which is what someone who named them all the same would expect.
     */
    val room: String?,
    val acs: List<AcTileState> = emptyList(),
    val curtains: List<CurtainTileState> = emptyList(),
    val strips: List<LightStripTileState> = emptyList(),
    val recuperators: List<RecuperatorTileState> = emptyList(),
    val bulbs: List<BulbTileState> = emptyList(),
)

/**
 * Lays the whole panel out by room.
 *
 * Sections are built from the tiles rather than from the vendor's list of rooms, so a room with
 * nothing in it never appears — the recorded response has seven such rooms, plus a "Гостиная"
 * holding only a vacuum and a tv, neither of which the panel has a tile for.
 *
 * The section for the unplaced devices is last and is never dropped. Tuya names no room for the
 * recuperators (docs/tuya.md), so unless the flat's own answer reached [TuyaPoll] they arrive with
 * `room = null` — and a device falling off the wall because no vendor said where it is would be a
 * bug, not a tidy panel.
 */
fun roomSections(
    acs: List<AcTileState>,
    curtains: List<CurtainTileState>,
    strips: List<LightStripTileState>,
    recuperators: List<RecuperatorTileState>,
    bulbs: List<BulbTileState>,
): List<RoomSection> {
    val rooms =
        buildList {
            addAll(acs.map { it.room })
            addAll(curtains.map { it.room })
            addAll(strips.map { it.room })
            addAll(recuperators.map { it.room })
            addAll(bulbs.map { it.room })
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
        )
    }
}

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
