package ru.domovoy.panel

import ru.domovoy.integrations.yandex.YandexClient

/**
 * The panel's one read of the Yandex house.
 *
 * `GET /v1.0/user/info` returns every device of every household in one body, so a call per tile
 * group was the same request sent three times — and it grew by one with every tile type added.
 * Yandex publishes no rate limit for this endpoint (docs/yandex.md), which is a reason to send
 * fewer calls rather than a licence to send more.
 *
 * What is shared is the fetch. Each group still holds its own tiles, its own error and its own
 * ages: the bulb, the curtain and the air conditioner were read at different times — 81 days apart
 * on ac-01 alone — and one "last read" for the whole panel would be a lie about most of them.
 *
 * Nothing here schedules; [refresh] is called by whatever owns the timer. See [pollPausingForCalls].
 */
class YandexPoll(
    private val client: YandexClient,
) {
    // Each group re-reads through this poller after an action, so a toggle costs one shared read
    // rather than one per group. The lambda is not evaluated until then, when `this` is built.
    val bulbs = BulbTiles(client) { refresh() }
    val curtains = CurtainTiles(client) { refresh() }
    val acs = AcTiles(client) { refresh() }

    /**
     * Reads the house once and hands the same answer to every group. On failure every group goes
     * into its error state at once — one call failed, so nothing on the panel is being updated,
     * and a tile that kept quiet about it would be showing a stale value as a current one.
     */
    suspend fun refresh() {
        client
            .devices()
            .onSuccess { devices ->
                bulbs.show(devices)
                curtains.show(devices)
                acs.show(devices)
            }
            .onFailure { failure ->
                val reason = failure.describe()
                bulbs.showFailure(reason)
                curtains.showFailure(reason)
                acs.showFailure(reason)
            }
    }
}
