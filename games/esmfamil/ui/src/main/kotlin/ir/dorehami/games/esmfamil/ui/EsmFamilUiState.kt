package ir.dorehami.games.esmfamil.ui

import ir.dorehami.games.esmfamil.EsmFamilSheet

/** Everything the sheet screen draws, whichever table is driving it (local or a room). */
data class EsmFamilUiState(
    val round: Int = 1,
    val rounds: Int = 5,
    val letter: String = "",
    val categories: List<String> = EsmFamilSheet.DEFAULT_IDS,
    val phase: EsmFamilPhase = EsmFamilPhase.WRITING,
    val seats: List<EsmFamilSeat> = emptyList(),
    /** Pass-and-play: whose sheet is being written. A room: my own seat. */
    val writingSeat: Int = 0,
    /** What the writer has typed so far (local) / what I have typed (room). */
    val draft: Map<String, String> = emptyMap(),
    /** My submitted sheet, as the engine sees it. */
    val mine: Map<String, String> = emptyMap(),
    val submittedIds: Set<String> = emptySet(),
    val rows: List<EsmFamilRow> = emptyList(),
    val totals: Map<String, Int> = emptyMap(),
    val standings: List<EsmFamilStanding> = emptyList(),
    val modeLabel: String = "آفلاین · روی همین گوشی",
    val isHost: Boolean = true,
    val error: String? = null,
    /** How many seats still owe a sheet (a room shows it as "منتظر ۲ نفر"). */
    val waitingForOthers: Int = 0,
) {
    val finished: Boolean get() = phase == EsmFamilPhase.FINISHED

    /** Name of the person whose turn it is to write (pass-and-play). */
    val writerName: String get() = seats.getOrNull(writingSeat)?.name.orEmpty()

    /** True when the person who is supposed to write has already handed a sheet in. */
    val hasWritten: Boolean
        get() = seats.getOrNull(writingSeat)?.id?.let { it in submittedIds } == true
}
