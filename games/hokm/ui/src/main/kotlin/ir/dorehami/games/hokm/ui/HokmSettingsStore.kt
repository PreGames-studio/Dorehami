package ir.dorehami.games.hokm.ui

/**
 * HK-030 (first slice) — settings persistence as **pure, unit-testable** logic.
 *
 * The roadmap asks for reliable save/load of settings. This object encodes/decodes a
 * [HokmSettings.Settings] to/from a compact string (key=value pairs) so the Android layer
 * can store it in SharedPreferences/DataStore without depending on serialization libs.
 */
object HokmSettingsStore {

    fun encode(s: HokmSettings.Settings): String = buildString {
        append("muted="); append(s.muted)
        append(";speed="); append(s.animSpeed.name)
        append(";lang="); append(s.language.name)
        append(";largeText="); append(s.largeText)
        append(";reducedMotion="); append(s.reducedMotion)
        append(";table="); append(s.tableSkin.name)
        append(";deck="); append(s.deckSkin.name)
        append(";coaching="); append(s.showCoaching)
        append(";quickPlay="); append(s.quickPlay)
    }

    fun decode(text: String?): HokmSettings.Settings {
        if (text.isNullOrBlank()) return HokmSettings.DEFAULTS
        val map = text.split(";").mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq < 0) null else part.substring(0, eq) to part.substring(eq + 1)
        }.toMap()
        return HokmSettings.Settings(
            muted = map["muted"]?.toBooleanStrictOrNull() ?: false,
            animSpeed = runCatching { HokmSettings.AnimSpeed.valueOf(map["speed"] ?: "NORMAL") }.getOrDefault(HokmSettings.AnimSpeed.NORMAL),
            language = runCatching { HokmSettings.Language.valueOf(map["lang"] ?: "FA") }.getOrDefault(HokmSettings.Language.FA),
            largeText = map["largeText"]?.toBooleanStrictOrNull() ?: false,
            reducedMotion = map["reducedMotion"]?.toBooleanStrictOrNull() ?: false,
            tableSkin = runCatching { HokmSettings.TableSkin.valueOf(map["table"] ?: "EMERALD") }.getOrDefault(HokmSettings.TableSkin.EMERALD),
            deckSkin = runCatching { HokmSettings.DeckSkin.valueOf(map["deck"] ?: "CLASSIC") }.getOrDefault(HokmSettings.DeckSkin.CLASSIC),
            showCoaching = map["coaching"]?.toBooleanStrictOrNull() ?: false,
            quickPlay = map["quickPlay"]?.toBooleanStrictOrNull() ?: false
        )
    }

    fun roundTrip(s: HokmSettings.Settings): HokmSettings.Settings = decode(encode(s))
}
