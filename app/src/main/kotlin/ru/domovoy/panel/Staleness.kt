package ru.domovoy.panel

import ru.domovoy.core.Reading
import java.time.Instant
import kotlin.time.Duration

/**
 * How many of its own poll intervals a reading may fall behind before the panel calls it stale.
 *
 * Eight is a guess and is written down as one. The number that matters is how long a device can be
 * quiet before somebody would want to know, and nobody has measured that on this wall — see
 * docs/ui.md, "Open". What is *not* a guess is that it is a multiple rather than a duration:
 * a flat threshold short enough to catch a dead bulb (Yandex, every 15 s) marks every recuperator
 * (Tuya, every 6 minutes) permanently, and the panel would hang a warning on five tiles that are
 * working exactly as designed.
 */
private const val INTERVALS_BEFORE_STALE = 8

/**
 * Whether a reading is too old to be shown as current, judged against the interval of the poll
 * that produced it: 2 minutes for the Yandex tiles, 48 minutes for the recuperators.
 *
 * [Reading.Never] is stale at every interval — 33 of the 116 recorded capabilities have never
 * reported, and a tile showing a value nobody ever confirmed is exactly what this is for.
 *
 * Tied to the interval rather than to a constant of its own so that retuning either cadence carries
 * staleness with it instead of quietly falling out of step.
 */
fun isStale(
    reading: Reading,
    now: Instant,
    interval: Duration,
): Boolean = when (reading) {
    Reading.Never -> true
    // Milliseconds, not nanoseconds: both vendors timestamp to the second at best, and this way a
    // reading dated in the future — a device whose clock runs ahead — is fresh rather than absurd.
    is Reading.At -> now.toEpochMilli() - reading.instant.toEpochMilli() > (interval * INTERVALS_BEFORE_STALE).inWholeMilliseconds
}

/** A tile is only as fresh as its oldest reading: either one going quiet makes the tile stale. */
internal fun isStale(
    readings: List<Reading>,
    now: Instant,
    interval: Duration,
): Boolean = readings.any { isStale(it, now, interval) }

// The readings each tile type prints, and only those: an age the tile does not show is an age
// nobody can act on. Two on the air conditioner and two on the light strip because they are
// timestamped separately — on ac-01 they were read 81 days apart — and one age for both would have
// to lie about the older of the two.

internal fun readings(tile: AcTileState): List<Reading> = listOf(tile.powerLastUpdated, tile.temperatureLastUpdated)

internal fun readings(tile: CurtainTileState): List<Reading> = listOf(tile.lastUpdated)

internal fun readings(tile: LightStripTileState): List<Reading> = listOf(tile.powerLastUpdated, tile.brightnessLastUpdated)

internal fun readings(tile: BulbTileState): List<Reading> = listOf(tile.lastUpdated)

/**
 * The recuperator's own, which are four when it reports climate and two when it does not: the
 * temperature and humidity ages are on the tile only when there is a value beside them to age, and
 * a device that reports neither has no second line at all — see [climateLine].
 */
internal fun readings(tile: RecuperatorTileState): List<Reading> = listOf(tile.powerLastUpdated, tile.speedLastUpdated) +
    listOfNotNull(
        tile.temperatureLastUpdated.takeIf { tile.temperature != null },
        tile.humidityLastUpdated.takeIf { tile.humidity != null },
    )

/**
 * Every reading a room shows, each paired with the interval of the poll behind it — Yandex's for
 * the four groups one `/v1.0/user/info` call feeds, Tuya's for the recuperators.
 *
 * The launcher tiles contribute nothing, and that is not an oversight: nothing polls them, they
 * show nothing the panel read, and there is no age on them that could go stale.
 */
internal fun readings(
    section: RoomSection,
    yandex: Duration,
    tuya: Duration,
): List<Pair<Reading, Duration>> = buildList {
    section.acs.forEach { tile -> readings(tile).forEach { add(it to yandex) } }
    section.curtains.forEach { tile -> readings(tile).forEach { add(it to yandex) } }
    section.strips.forEach { tile -> readings(tile).forEach { add(it to yandex) } }
    section.bulbs.forEach { tile -> readings(tile).forEach { add(it to yandex) } }
    section.recuperators.forEach { tile -> readings(tile).forEach { add(it to tuya) } }
}
