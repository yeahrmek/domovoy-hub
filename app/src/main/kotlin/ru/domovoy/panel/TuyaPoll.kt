package ru.domovoy.panel

import ru.domovoy.integrations.tuya.TuyaClient
import java.time.Instant

/**
 * The panel's one read of the flat's recuperators, and the most expensive thing it does.
 *
 * Unlike the Yandex poll, this is not one call: the inventory is one, and then real state costs a
 * call *per recuperator* because the batch shadow route answers `40001900 No space permission`. So
 * a refresh is five calls today, against an allowance denominated in money — which is why whoever
 * owns the timer polls this in minutes, not seconds. See docs/tuya.md.
 *
 * A single device's read failing is not the group failing: the other four are handed on as normal
 * and the failure lands on that one tile.
 *
 * Nothing here schedules; [refresh] is called by whatever owns the timer. See [pollPausingForCalls].
 */
class TuyaPoll(
    private val client: TuyaClient,
    /**
     * Which room each recuperator is in, by device id, as [recuperatorRooms] read it out of
     * `local.properties`. Empty by default, and empty is a working panel: what is not in here
     * keeps the `room = null` Tuya gave it and shows up in the panel's unplaced section.
     */
    private val rooms: Map<String, String> = emptyMap(),
    /**
     * The panel's clock, as on [YandexPoll]: the recuperators go stale when nothing has read them
     * lately, and this is where the reading happens. Injectable so a test can say when.
     */
    private val now: () -> Instant = Instant::now,
) {
    val recuperators = RecuperatorTiles(client)

    /**
     * The inventory getting through is what stamps the group, whatever the five reads after it
     * did: a device whose own call failed says so on its own tile, and the poll behind all five
     * plainly ran. A failed inventory read nothing and stamps nothing.
     */
    suspend fun refresh() {
        client
            .devices()
            .onFailure { failure -> recuperators.showFailure(failure.describe()) }
            .onSuccess { devices ->
                // The one place the flat's answer is laid over the vendor's silence. Done here, on
                // the inventory, so both the read below and the fallback to the inventory carry it
                // — and so a tap, which re-reads from this same device, keeps it too.
                val inventory = devices.map { device -> device.copy(room = rooms[device.id] ?: device.room) }
                // Sequential rather than concurrent: five calls a few minutes apart are not worth
                // opening five sockets at once on a tablet that is mostly idle, and it keeps the
                // order of what the panel spends predictable.
                val read = inventory.map { device -> device to client.read(device) }
                recuperators.show(
                    // A device that failed to read falls back to what the inventory said about it,
                    // which is enough to keep its name and its place on the wall.
                    devices = read.map { (device, result) -> result.getOrDefault(device) },
                    failures =
                    read
                        .mapNotNull { (device, result) ->
                            result.exceptionOrNull()?.let { device.id to it.describe() }
                        }.toMap(),
                    polledAt = now(),
                )
            }
    }
}

/**
 * Which room each recuperator is in, as the flat itself recorded it.
 *
 * Tuya's API answers nothing about grouping — the Smart Life app has the recuperators in rooms, the
 * cloud does not say so, and `GET /v2.0/cloud/thing/device` carries no room field at all
 * (docs/tuya.md). The knowledge exists; only the vendor is not the one holding it. So it is written
 * down once, by hand, in `local.properties`:
 *
 * ```properties
 * tuya.rooms=xfj-01=Спальня;xfj-05=Зал
 * ```
 *
 * Device ids are apartment-identifying, so `local.properties` is the only place this can live — the
 * same reason the credentials are there. The names must be spelled as the Yandex response spells
 * its rooms, or the recuperator gets a section of its own next to the room it belongs to.
 *
 * Parsed rather than guessed from the device name on purpose. The names do carry a room — "Бризер
 * зал", "Бризер спальня" — but the name is a free-text field the owner can change in the Smart Life
 * app at any moment, and the panel would silently move a tile to another room the next time it was
 * renamed. It also does not work: one of the five is called "Бризер данина комната", which names no
 * room Yandex knows, so parsing places four and still needs somewhere to put the fifth. What is
 * unmapped is not invented — it falls to the panel's unplaced section, visibly, where it can be
 * seen and fixed. An entry with no `=`, or a blank on either side of it, is skipped for the same
 * reason: the device shows up unplaced on the wall, and a typo in a build constant must not take
 * the recuperators down.
 */
fun recuperatorRooms(spec: String): Map<String, String> = spec
    .split(';')
    .mapNotNull { entry ->
        val id = entry.substringBefore('=', missingDelimiterValue = "").trim()
        val room = entry.substringAfter('=', missingDelimiterValue = "").trim()
        if (id.isEmpty() || room.isEmpty()) null else id to room
    }.toMap()
