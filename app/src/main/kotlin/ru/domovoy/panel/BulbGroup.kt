package ru.domovoy.panel

import ru.domovoy.core.Reading
import java.time.Instant

/**
 * One room's bulbs, split into the many and the few.
 *
 * 28 bulbs at a third of the panel each is what made Главная fourteen rows of lamps. They are on/off
 * only and they are the many, so they draw as one wrapping row of 72 dp circles with a single line
 * under it — and the few the panel has nothing to say about draw as named tiles of their own. See
 * docs/ui.md, "The lights group".
 */
data class BulbGroup(
    /** The bulbs the row of circles is made of, in the order they arrived. */
    val circles: List<BulbTileState>,
    /** The ones that left the row and draw as their own named tile. */
    val brokenOut: List<BulbTileState>,
    /** How many of [circles] are on. The ones that broke out are not counted — they are not off. */
    val on: Int,
    /**
     * The oldest reading among [circles], which is the one age the row can honestly print.
     *
     * Null when there are no circles — there is no row, and so nothing for an age to sit under.
     */
    val oldest: Reading?,
)

/**
 * Who is a circle and who is a tile of their own.
 *
 * The split is `isOn` being null and nothing else: a circle is a claim that the panel knows whether
 * that lamp is on, and for a bulb that has reported no value at all it does not. That one says
 * "unknown" on a named tile, which is what its status line has always said.
 *
 * Staleness is deliberately not the split, and was the first draft of it. One `/v1.0/user/info`
 * call feeds every bulb in the flat, so either all of them are stale or none are — a rule that
 * fires on all 28 at once does not separate anything. What the row does carry is the *oldest*
 * reading of the bulbs in it, because a line quoting the freshest would hide a lamp that stopped
 * answering a week ago.
 *
 * **No `now`.** Finding the oldest of a set of readings needs no clock; formatting one does, and
 * that is [ageLabel]'s job at the point of drawing, as on every other tile.
 *
 * A room with no bulbs comes back with nothing in it, which is nothing to draw rather than an empty
 * row — as does a room whose every bulb broke out.
 */
fun bulbGroup(bulbs: List<BulbTileState>): BulbGroup {
    val (circles, brokenOut) = bulbs.partition { it.isOn != null }
    return BulbGroup(
        circles = circles,
        brokenOut = brokenOut,
        on = circles.count { it.isOn == true },
        oldest = oldest(circles.map { it.lastUpdated }),
    )
}

/**
 * The oldest of a set of readings, with [Reading.Never] older than any instant: a capability that
 * has never reported is the least fresh thing the row can be holding, and 33 of the 116 recorded
 * capabilities are exactly that. `isOn` and `last_updated` are different fields of the same
 * capability, so a `Never` turns up inside the group rather than only outside it — three of
 * Коридор's four bulbs report a value with a `last_updated` of `0.0`.
 */
private fun oldest(readings: List<Reading>): Reading? = when {
    readings.isEmpty() -> null
    readings.any { it == Reading.Never } -> Reading.Never
    else -> Reading.At(readings.filterIsInstance<Reading.At>().minOf { it.instant })
}

/**
 * The one line under the row: how many lamps it holds, how many of them are on, and how old the
 * oldest of their readings is.
 *
 * [notUpdating] is the group's own bad news — a failed poll, or a poll that has stopped landing —
 * and the row says it once for all 28 instead of every tile saying it in turn. It is [notUpdating]
 * the function's answer, asked where the panel knows the interval; [error] only names the reason
 * when there is one to name, since a group can stop being read without any call having failed.
 */
internal fun bulbGroupLine(
    group: BulbGroup,
    now: Instant,
    notUpdating: Boolean,
    error: String?,
): String {
    val lamps = if (group.circles.size == 1) "1 lamp" else "${group.circles.size} lamps"
    // A group with no circles has no line, so the fallback is never printed; Never is the honest
    // answer for it anyway.
    val age = ageLabel(group.oldest ?: Reading.Never, now)
    val line = "$lamps · ${group.on} on · $age"
    return when {
        !notUpdating -> line
        error == null -> "$line · not updating"
        else -> "$line · not updating: $error"
    }
}
