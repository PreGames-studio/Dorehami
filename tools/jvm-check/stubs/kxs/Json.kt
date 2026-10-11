// SANDBOX-ONLY stub mirroring the subset of kotlinx.serialization.json used by the repo,
// so the pure-JVM modules can be compiled and tested with a bare kotlinc when Maven is unreachable.
// Never on the Gradle classpath: the real artifact comes from Maven there.
package kotlinx.serialization.json

sealed class JsonElement

object JsonNull : JsonElement() { override fun toString() = "null" }

class JsonPrimitive private constructor(val content: String, val isString: Boolean) : JsonElement() {
    constructor(value: String?) : this(value ?: "null", value != null)
    constructor(value: Number?) : this(value?.toString() ?: "null", false)
    constructor(value: Boolean?) : this(value?.toString() ?: "null", false)
    override fun toString(): String = if (isString) quote(content) else content
    override fun equals(other: Any?) = other is JsonPrimitive && other.content == content && other.isString == isString
    override fun hashCode() = content.hashCode() * 31 + isString.hashCode()
}

class JsonObject(private val content: Map<String, JsonElement>) : JsonElement(), Map<String, JsonElement> by content {
    override fun toString() = content.entries.joinToString(",", "{", "}") { quote(it.key) + ":" + it.value }
    override fun equals(other: Any?) = other is JsonObject && other.content == content
    override fun hashCode() = content.hashCode()
}

class JsonArray(private val content: List<JsonElement>) : JsonElement(), List<JsonElement> by content {
    override fun toString() = content.joinToString(",", "[", "]")
    override fun equals(other: Any?) = other is JsonArray && other.content == content
    override fun hashCode() = content.hashCode()
}

internal fun quote(s: String): String {
    val sb = StringBuilder("\"")
    for (c in s) when (c) {
        '"' -> sb.append("\\\""); '\\' -> sb.append("\\\\"); '\n' -> sb.append("\\n"); '\r' -> sb.append("\\r"); '\t' -> sb.append("\\t")
        else -> if (c < ' ') sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
    }
    return sb.append('"').toString()
}

class JsonObjectBuilder {
    private val map = LinkedHashMap<String, JsonElement>()
    fun put(key: String, element: JsonElement): JsonElement? = map.put(key, element)
    fun build() = JsonObject(map)
}
fun JsonObjectBuilder.put(key: String, value: String?) = put(key, JsonPrimitive(value))
fun JsonObjectBuilder.put(key: String, value: Number?) = put(key, JsonPrimitive(value))
fun JsonObjectBuilder.put(key: String, value: Boolean?) = put(key, JsonPrimitive(value))
inline fun buildJsonObject(block: JsonObjectBuilder.() -> Unit): JsonObject = JsonObjectBuilder().apply(block).build()

class JsonArrayBuilder {
    private val list = ArrayList<JsonElement>()
    fun add(element: JsonElement): Boolean = list.add(element)
    fun build() = JsonArray(list)
}
inline fun buildJsonArray(block: JsonArrayBuilder.() -> Unit): JsonArray = JsonArrayBuilder().apply(block).build()

val JsonElement.jsonPrimitive: JsonPrimitive get() = this as? JsonPrimitive ?: throw IllegalArgumentException("not a primitive")
val JsonElement.jsonObject: JsonObject get() = this as? JsonObject ?: throw IllegalArgumentException("not an object")
val JsonElement.jsonArray: JsonArray get() = this as? JsonArray ?: throw IllegalArgumentException("not an array")
val JsonPrimitive.int: Int get() = content.toInt()
val JsonPrimitive.intOrNull: Int? get() = content.toIntOrNull()
val JsonPrimitive.long: Long get() = content.toLong()
val JsonPrimitive.longOrNull: Long? get() = content.toLongOrNull()
val JsonPrimitive.boolean: Boolean get() = content.toBoolean()
val JsonPrimitive.booleanOrNull: Boolean? get() = content.toBooleanStrictOrNull()
val JsonPrimitive.contentOrNull: String? get() = if (!isString && content == "null") null else content

object Json {
    fun encodeToString(e: JsonElement): String = e.toString()
    fun parseToJsonElement(s: String): JsonElement = Parser(s).parse()
}

private class Parser(val s: String) {
    var i = 0
    fun parse(): JsonElement { ws(); val v = value(); ws(); return v }
    fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
    fun value(): JsonElement {
        ws()
        return when (s[i]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> JsonPrimitive(str())
            't' -> { i += 4; JsonPrimitive(true) }
            'f' -> { i += 5; JsonPrimitive(false) }
            'n' -> { i += 4; JsonNull }
            else -> num()
        }
    }
    fun obj(): JsonObject {
        i++; val m = LinkedHashMap<String, JsonElement>(); ws()
        if (s[i] == '}') { i++; return JsonObject(m) }
        while (true) {
            ws(); val k = str(); ws(); i++; val v = value(); m[k] = v; ws()
            if (s[i] == ',') { i++; continue }
            i++; return JsonObject(m)
        }
    }
    fun arr(): JsonArray {
        i++; val l = ArrayList<JsonElement>(); ws()
        if (s[i] == ']') { i++; return JsonArray(l) }
        while (true) {
            l += value(); ws()
            if (s[i] == ',') { i++; continue }
            i++; return JsonArray(l)
        }
    }
    fun str(): String {
        i++; val sb = StringBuilder()
        while (s[i] != '"') {
            if (s[i] == '\\') {
                i++
                when (s[i]) {
                    'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                    'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                    'u' -> { sb.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                    else -> sb.append(s[i])
                }
                i++
            } else sb.append(s[i++])
        }
        i++; return sb.toString()
    }
    fun num(): JsonPrimitive {
        val st = i
        while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
        val t = s.substring(st, i)
        return if (t.contains('.') || t.contains('e') || t.contains('E')) JsonPrimitive(t.toDouble()) else JsonPrimitive(t.toLong())
    }
}
