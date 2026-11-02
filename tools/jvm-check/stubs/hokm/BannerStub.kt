// SANDBOX-ONLY: the real `Banner` is declared in HokmTable.kt next to the Compose table. The bare
// kotlinc harness only compiles the Compose-free animation queue, so it needs this declaration to
// exist. Same fields, same package. Gradle/CI always uses the real one.
package ir.dorehami.games.hokm.ui

import ir.dorehami.games.hokm.Suit

data class Banner(
    val title: String,
    val subtitle: String? = null,
    val big: Boolean = false,
    val suit: Suit? = null,
)

/** Same shape as the real one in HokmTable.kt (see the note above). */
data class HandSummary(
    val handNumber: Int,
    val winnerTeam: Int?,
    val points: Int,
    val kind: ir.dorehami.games.hokm.HandWinKind,
    val hakem: Int,
    val tricks: List<Int>,
    val hokm: Suit? = null,
)
