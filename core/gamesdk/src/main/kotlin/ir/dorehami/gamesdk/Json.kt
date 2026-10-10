package ir.dorehami.gamesdk

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** تنظیماتِ یکدستِ JSON برای کل پلتفرم (لاگ، رکورد، viewها). */
val GameJson = Json {
    encodeDefaults = true
    prettyPrint = false
    ignoreUnknownKeys = true
}

/** شیءِ JSON خالیِ بدون تخصیص دوباره. */
fun emptyJson(): JsonObject = JsonObject(emptyMap())

/** ساختِ یک [JsonObject] با بلاکِ آشنا (همان `view { … }` که قرارداد وعده داده است). */
fun view(build: MutableMap<String, JsonElement>.() -> Unit): JsonObject {
    val map = linkedMapOf<String, JsonElement>()
    map.build()
    return JsonObject(map)
}

/** قرائتِ امنِ یک رشته از view/بدنهٔ حرکت. */
fun JsonObject.optString(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

/** قرائتِ امنِ یک عددِ صحیح. */
fun JsonObject.optInt(key: String): Int? =
    this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

/** قرائتِ امنِ یک بولین. */
fun JsonObject.optBoolean(key: String): Boolean? =
    this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()

/** ساختِ آرایه از رشته‌ها. */
fun jsonStrings(values: Iterable<String>): JsonArray =
    JsonArray(values.map { JsonPrimitive(it) })

/** ساختِ آرایه از اعداد. */
fun jsonInts(values: Iterable<Int>): JsonArray =
    JsonArray(values.map { JsonPrimitive(it) })

/**
 * تبدیلِ قطعیِ هر [JsonElement] به رشته: کلیدها مرتب و بدون فضای خالی.
 *
 * این تابع ستونِ فقراتِ «امضای دست» ([MatchRecord.digest]) است: چون خروجی برای دو وضعیتِ یکسان
 * همیشه یکی است، می‌توان با مقایسهٔ دو امضا فهمید که یک دست واقعاً تکرار شده یا دستکاری شده است.
 */
fun canonicalJson(element: JsonElement): String = buildString {
    appendCanonical(element)
}

private fun StringBuilder.appendCanonical(element: JsonElement) {
    when (element) {
        JsonNull -> append("null")
        is JsonPrimitive -> append(element.content)
        is JsonArray -> {
            append('[')
            element.forEachIndexed { index, item ->
                if (index > 0) append(',')
                appendCanonical(item)
            }
            append(']')
        }
        is JsonObject -> {
            append('{')
            element.keys.sorted().forEachIndexed { index, key ->
                if (index > 0) append(',')
                append(key)
                append(':')
                val value = element[key] ?: JsonNull
                if (value is JsonObject || value is JsonArray) {
                    appendCanonical(value)
                } else {
                    append('"')
                    appendCanonical(value)
                    append('"')
                }
            }
            append('}')
        }
    }
}

/** امضای SHA-256ِ یک رشته به صورت هگزِ کوچک (برای `digest`؛ استفادهٔ امنیتی ندارد). */
fun sha256Hex(text: String): String {
    val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

/** کمکی برای ساختِ سریعِ یک شیءِ تک‌کلیدی (برای بدنهٔ حرکت‌ها). */
fun payload(key: String, value: String): JsonObject = buildJsonObject { put(key, JsonPrimitive(value)) }
