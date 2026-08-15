package ru.domovoy.integrations.tuya

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Every Tuya response has this envelope, success and failure alike, and a failure arrives as
 * HTTP 200 with `success: false`. A client that only checked the status code would hand the panel
 * an empty device list and call it a good poll.
 */
@Serializable
internal data class TuyaResponseDto<T>(
    val success: Boolean = false,
    /** Only on a failure — `1004` sign invalid, `40001900` no space permission. */
    val code: Long? = null,
    val msg: String? = null,
    val result: T? = null,
)

@Serializable
internal data class TuyaTokenDto(
    @SerialName("access_token") val accessToken: String,
    /**
     * Seconds *left* on the token, not a fixed lifetime: the account answered 5433 and then 5385
     * on two probes minutes apart, because Tuya hands out the token it already issued. So the
     * expiry is the moment of the call plus this, and it can be far short of the documented 7200.
     */
    @SerialName("expire_time") val expireTime: Long = 0,
)

/**
 * One device from `/v1.0/users/{uid}/devices`. The response also carries `local_key`, `ip`, `lat`,
 * `lon`, `uid` and `owner_id` — a per-device secret, the flat's WAN address and its coordinates.
 * Unknown keys are ignored, so leaving them out here means they are never parsed, never held and
 * never logged.
 */
@Serializable
internal data class TuyaDeviceDto(
    val id: String,
    val name: String,
    /** `xfj` for the recuperators; the account also holds `dj`, `mjj`, `wg2` and `kg`. */
    val category: String,
    val online: Boolean = false,
)

@Serializable
internal data class TuyaShadowPropertiesDto(
    val properties: List<TuyaPropertyDto> = emptyList(),
)

/** One datapoint of the thing model — the real device surface, 13 of them on a recuperator. */
@Serializable
internal data class TuyaPropertyDto(
    val code: String,
    /** `bool` or `value` on this product; the type is what says how to read [value]. */
    val type: String = "",
    /** `true`/`false` for a bool, a whole number for a value — so it stays untyped. */
    val value: JsonElement? = null,
    /** Milliseconds, and per datapoint: the humidity moves while the switch has not for days. */
    val time: Long = 0,
)

/**
 * The body of `POST /v2.0/cloud/thing/{id}/shadow/properties/issue`. [properties] is a JSON object
 * *encoded as a string* inside the JSON body — Tuya's own shape, not a mistake here.
 *
 * UNVERIFIED: nothing has ever been written to these devices. See [TuyaClient.setOn].
 */
@Serializable
internal data class TuyaIssueDto(
    val properties: String,
)
