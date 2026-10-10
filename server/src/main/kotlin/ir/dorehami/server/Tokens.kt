package ir.dorehami.server

import ir.dorehami.protocol.PlayerId
import ir.dorehami.session.PlayerInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Stateless guest identity: `base64url(playerJson).hmac`. The server never stores sessions, so a
 * restart does not log anyone out. Secret from `DOREHAMI_TOKEN_SECRET` (docs/DEPLOY.md).
 */
class Tokens(secret: String) {
    private val key = SecretKeySpec(secret.toByteArray(), "HmacSHA256")

    fun issue(player: PlayerInfo): String {
        val body = b64(player.toJson().toString().toByteArray())
        return "$body.${sign(body)}"
    }

    fun verify(token: String?): PlayerInfo? {
        if (token.isNullOrBlank()) return null
        val dot = token.lastIndexOf('.')
        if (dot <= 0) return null
        val body = token.substring(0, dot)
        if (!constantTimeEquals(sign(body), token.substring(dot + 1))) return null
        return runCatching { PlayerInfo.fromJson(Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(body))).jsonObject) }.getOrNull()
    }

    private fun sign(body: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(key) }
        return b64(mac.doFinal(body.toByteArray()))
    }

    private fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].code xor b[i].code)
        return r == 0
    }

    companion object {
        fun newPlayerId(): String = "p_" + java.util.UUID.randomUUID().toString().replace("-", "").take(12)
    }
}
