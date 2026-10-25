package ru.domovoy.panel

import ru.domovoy.core.Reading
import java.time.Instant

/**
 * One room's bulbs, split into the many and the few.
 *
 * 28 bulbs at a quarter of the panel each is what made Главная fourteen rows of lamps. They are
 * on/off only and they are the many, so they draw as **one group tile** — `7 lamps`, how many of
 * them are on, and one age — which opens into the seven named tiles when it is tapped. The few the
 * panel has nothing to say about never join it: they draw as named tiles of their own from the
 * start. See docs/ui.md, "The lights group".
 *
 * **This was a row of 72 dp discs until now.** Seven identical amber circles, unlabelled, under one
 * shared line: the most saturated thing on the wall and the biggest touch targets on it, and which
 * lamp was which could not be recovered from the wall at all. The eye landed there first and learned
 * nothing.
 */
data class BulbGroup(
    /** The bulbs the group tile is made of, in the order they arrived. */
    val lamps: List<BulbTileState>,
    /** The ones that never joined it and draw as their own named tile. */
    val brokenOut: List<BulbTileState>,
    /** How many of [lamps] are on. The ones that broke out are not counted — they are not off. */
    val on: Int,
    /**
     * The oldest reading among [lamps], which is the one age the group tile can honestly print.
     *
     * Null when there are no lamps — there is no tile, and so nothing for an age to sit under.
     */
    val oldest: Reading?,
)

/**
 * Who is behind the group tile and who is a tile of their own.
 *
 * The split is `isOn` being null and nothing else: the group tile is a claim that the panel knows
 * whether each of those lamps is on, and for a bulb that has reported no value at all it does not.
 * That one says "unknown" on a named tile, which is what its status line has always said.
 *
 * Staleness is deliberately not the split, and was the first draft of it. One `/v1.0/user/info`
 * call feeds every bulb in the flat, so either all of them are stale or none are — a rule that
 * fires on all 28 at once does not separate anything. What the group does carry is the *oldest*
 * reading of the bulbs in it, because a line quoting the freshest would hide a lamp that stopped
 * answering a week ago.
 *
 * **No `now`.** Finding the oldest of a set of readings needs no clock; formatting one does, and
 * that is [ageLine]'s job at the point of drawing, as on every other tile.
 *
 * A room with no bulbs comes back with nothing in it, which is nothing to draw rather than an empty
 * tile — as does a room whose every bulb broke out.
 */
fun bulbGroup(bulbs: List<BulbTileState>): BulbGroup {
    val (lamps, brokenOut) = bulbs.partition { it.isOn != null }
    return BulbGroup(
        lamps = lamps,
        brokenOut = brokenOut,
        on = lamps.count { it.isOn == true },
        // [oldest] is shared with every other kind of tile now — one age per tile is the rule, and
        // the group's was only the first of them. `isOn` and `last_updated` are different fields of
        // one capability, so a Never turns up *inside* the group rather than only outside it: three
        // of Коридор's four bulbs report a value with a `last_updated` of `0.0`.
        oldest = oldest(lamps.map { it.lastUpdated }),
    )
}

/** Russian lamp count. */
internal fun lampCount(group: BulbGroup): String {
    val count = group.lamps.size
    val noun = when {
        count % 100 in 11..14 -> "ламп"
        count % 10 == 1 -> "лампа"
        count % 10 in 2..4 -> "лампы"
        else -> "ламп"
    }
    return "$count $noun"
}

/** The promoted count carries power; supporting text only gives the vendor reading's age. */
internal fun bulbGroupLine(
    group: BulbGroup,
    now: Instant,
): String = group.oldest?.let { sheetAge(it, now) } ?: "Нет данных"
