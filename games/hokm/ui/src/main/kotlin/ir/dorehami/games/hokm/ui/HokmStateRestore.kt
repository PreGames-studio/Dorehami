package ir.dorehami.games.hokm.ui

/**
 * HK-011 (state restore slice) — state restoration as **pure, unit-testable** logic.
 */
object HokmStateRestore {

    enum class Result { RESTORED, EMPTY, FAILED }

    fun decide(hasSave: Boolean, saveValid: Boolean): Result = when {
        !hasSave -> Result.EMPTY
        saveValid -> Result.RESTORED
        else -> Result.FAILED
    }

    fun message(r: Result): String = when (r) {
        Result.RESTORED -> "بازی بازیابی شد"
        Result.EMPTY -> "بازی جدید"
        Result.FAILED -> "ذخیرهٔ قبلی قابل اعتماد نبود"
    }
}
