package ir.dorehami.games.pantohub.ui

/**
 * Persian digits for every label in the module.
 *
 * Kept in its own Android-free file so the wizard maths around it stays unit-testable on the JVM.
 */
internal fun Int.toPersianDigits(): String = toString().toPersianDigits()

internal fun String.toPersianDigits(): String = map { char ->
    if (char in '0'..'9') '۰' + (char - '0') else char
}.joinToString("")
