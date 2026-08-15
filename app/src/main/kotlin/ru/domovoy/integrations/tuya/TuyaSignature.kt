package ru.domovoy.integrations.tuya

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The Tuya Cloud request signature.
 *
 * Kept apart from the client because it is the one part of this integration that fails silently:
 * every mistake in it comes back as `1004 sign invalid` with nothing to say which of the four
 * concatenations was wrong. Spelled out here, and asserted against a hand-built string in
 * TuyaClientTest, so the failure is a red test rather than a day of guessing.
 *
 *   str          = client_id + access_token + t + nonce + stringToSign
 *   stringToSign = METHOD \n SHA256(body) \n signature_headers \n path?sorted_query
 *   sign         = HMAC-SHA256(str, client_secret), hex, uppercase
 *
 * [accessToken] is the empty string for the token call itself — there is no token yet. The nonce
 * and the signature-headers line are unused, and each contributes an empty string rather than
 * being left out: the newlines still have to be there. See docs/tuya.md.
 */
internal fun tuyaSignature(
    clientId: String,
    clientSecret: String,
    accessToken: String,
    timestampMillis: Long,
    method: String,
    /** Path *and* query, exactly as it goes on the wire — `/v1.0/token?grant_type=1`. */
    pathWithQuery: String,
    body: String = "",
): String {
    val stringToSign = "$method\n${sha256Hex(body)}\n\n$pathWithQuery"
    return hmacSha256UpperHex(
        value = clientId + accessToken + timestampMillis + stringToSign,
        secret = clientSecret,
    )
}

// Lowercase hex, and the well-known e3b0c442…b855 for an empty body — which is what almost every
// call the panel makes has.
private fun sha256Hex(value: String): String = MessageDigest
    .getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }

private fun hmacSha256UpperHex(
    value: String,
    secret: String,
): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
    return mac.doFinal(value.toByteArray()).joinToString("") { "%02X".format(it) }
}
