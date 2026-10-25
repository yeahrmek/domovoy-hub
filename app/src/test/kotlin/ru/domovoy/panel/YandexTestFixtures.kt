package ru.domovoy.panel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import ru.domovoy.integrations.yandex.YandexClient
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal fun MockWebServer.yandexClient(
    timeout: Duration = 10.seconds,
    token: () -> String = { "test-token" },
) = YandexClient(
    http = OkHttpClient(),
    token = token,
    householdId = "household-flat",
    baseUrl = url("/"),
    timeout = timeout,
)

internal fun yandexFixture(): String = checkNotNull(YandexClient::class.java.getResourceAsStream("/yandex/user_info.json")) {
    "missing fixture app/src/test/resources/yandex/user_info.json"
}.use { it.readBytes().decodeToString() }

internal fun sentYandexAction(
    body: String?,
    deviceId: String,
    type: String = "devices.capabilities.range",
): JsonObject {
    val device = Json.parseToJsonElement(requireNotNull(body) { "the action was sent with no body" })
        .jsonObject["devices"]!!
        .jsonArray
        .single()
    assertEquals(deviceId, device.jsonObject["id"]!!.jsonPrimitive.content)
    val action = device.jsonObject["actions"]!!.jsonArray.single().jsonObject
    assertEquals(type, action["type"]!!.jsonPrimitive.content)
    return action["state"]!!.jsonObject
}
