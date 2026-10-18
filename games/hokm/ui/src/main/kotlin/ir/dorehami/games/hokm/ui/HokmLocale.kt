package ir.dorehami.games.hokm.ui

/**
 * GX-07 (locale slice) — locale model as **pure, unit-testable** logic.
 *
 * The roadmap mentions language choice. This object defines supported locales and
 * a resolver. The Android layer applies.
 */
object HokmLocale {

    enum class Locale { FA, EN }

    fun displayName(l: Locale): String = when (l) {
        Locale.FA -> "فارسی"
        Locale.EN -> "English"
    }

    fun isRtl(l: Locale): Boolean = l == Locale.FA

    fun defaultLocale(): Locale = Locale.FA

    fun fromCode(code: String): Locale = when (code.lowercase()) {
        "fa", "fa-ir", "persian" -> Locale.FA
        "en", "en-us", "english" -> Locale.EN
        else -> defaultLocale()
    }

    fun toCode(l: Locale): String = when (l) {
        Locale.FA -> "fa"
        Locale.EN -> "en"
    }
}
