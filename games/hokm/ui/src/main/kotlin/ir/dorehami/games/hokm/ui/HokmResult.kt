package ir.dorehami.games.hokm.ui

import ir.dorehami.uikit.toFa

/**
 * HK-029 (first slice) — reliable result/scorecard as **pure, unit-testable** logic.
 *
 * The roadmap asks that score/hand/previous stats be shown with clear vocabulary, that an
 * unfinished (abandoned) game is not confused with a loss, and that the result is
 * accessible. This object is the deterministic core: it turns raw match numbers into a
 * [Result] with a status, a Persian headline, and a score line. The Compose layer renders it.
 *
 * Status rules:
 *  - ABANDONED if [abandoned] is true (never a loss).
 *  - IN_PROGRESS if neither team reached [target] and not abandoned.
 *  - WIN/LOSS/DRAW based on which team reached [target] first (or both, or neither at end).
 */
object HokmResult {

    enum class Status { WIN, LOSS, DRAW, ABANDONED, IN_PROGRESS }

    data class Result(
        val status: Status,
        val headline: String,
        val scoreLine: String,
        val detail: String
    )

    fun compute(
        ourScore: Int,
        theirScore: Int,
        target: Int,
        handsPlayed: Int,
        abandoned: Boolean = false
    ): Result {
        if (abandoned) {
            return Result(
                Status.ABANDONED,
                "بازی ناتمام",
                "امتیاز: ${ourScore.toFa()} – ${theirScore.toFa()}",
                "این بازی به پایان نرسید و به‌عنوان باخت ثبت نشد."
            )
        }
        val reached = ourScore >= target || theirScore >= target
        if (!reached) {
            return Result(
                Status.IN_PROGRESS,
                "بازی در جریان",
                "امتیاز: ${ourScore.toFa()} – ${theirScore.toFa()} (هدف ${target.toFa()})",
                "دست‌های بازی‌شده: ${handsPlayed.toFa()}"
            )
        }
        return when {
            ourScore > theirScore -> Result(
                Status.WIN,
                "پیروزی",
                "امتیاز: ${ourScore.toFa()} – ${theirScore.toFa()}",
                "هدف ${target.toFa()} امتیاز؛ ${handsPlayed.toFa()} دست بازی شد."
            )
            theirScore > ourScore -> Result(
                Status.LOSS,
                "شکست",
                "امتیاز: ${ourScore.toFa()} – ${theirScore.toFa()}",
                "هدف ${target.toFa()} امتیاز؛ ${handsPlayed.toFa()} دست بازی شد."
            )
            else -> Result(
                Status.DRAW,
                "مساوی",
                "امتیاز: ${ourScore.toFa()} – ${theirScore.toFa()}",
                "هدف ${target.toFa()} امتیاز؛ ${handsPlayed.toFa()} دست بازی شد."
            )
        }
    }

    /** Whether the result is terminal (no more play expected). */
    fun isTerminal(r: Result): Boolean = r.status != Status.IN_PROGRESS

    /** Whether the result counts as a win for the player. */
    fun isWin(r: Result): Boolean = r.status == Status.WIN
}
