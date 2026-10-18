package ir.dorehami.games.hokm.ui

/**
 * GX-05 (material slice) — material helpers as **pure, unit-testable** logic.
 */
object HokmMaterial {

    enum class Finish { MATTE, GLOSS, SATIN }

    fun reflectivity(f: Finish): Float = when (f) {
        Finish.MATTE -> 0.1f
        Finish.GLOSS -> 0.9f
        Finish.SATIN -> 0.5f
    }

    fun cardFinish(): Finish = Finish.SATIN
}
