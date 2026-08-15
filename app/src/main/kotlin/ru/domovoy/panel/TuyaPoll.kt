package ru.domovoy.panel

import ru.domovoy.integrations.tuya.TuyaClient

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
) {
    val recuperators = RecuperatorTiles(client)

    suspend fun refresh() {
        client
            .devices()
            .onFailure { failure -> recuperators.showFailure(failure.describe()) }
            .onSuccess { inventory ->
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
                )
            }
    }
}
