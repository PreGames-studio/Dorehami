package ir.dorehami.uikit

private val FA_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

/** `"12"` → `"۱۲"`. Every number shown to the user goes through this (AGENTS.md section 4.7). */
fun String.toFaDigits(): String {
    val sb = StringBuilder(length)
    for (c in this) sb.append(if (c in '0'..'9') FA_DIGITS[c - '0'] else c)
    return sb.toString()
}

fun Int.toFa(): String = toString().toFaDigits()

fun Long.toFa(): String = toString().toFaDigits()
