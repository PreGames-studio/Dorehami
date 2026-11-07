package ir.dorehami.games.esmfamil.ui

import ir.dorehami.games.esmfamil.EsmFamilSession
import ir.dorehami.games.esmfamil.EsmFamilSheet
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * The pure part of the table: everything the screen needs, parsed out of the game views with no
 * Compose and no Android — so the sheet layout and the scoring badges can be tested on the JVM
 * (`EsmFamilUiModelTest`) the same way the rules are.
 */

/** Where the table is. Mirrors the session's round phase plus "the match is over". */
enum class EsmFamilPhase { WRITING, REVEAL, FINISHED }

/** One seat of the table. */
data class EsmFamilSeat(val id: String, val name: String, val isMe: Boolean)

/** One line of the final table. */
data class EsmFamilStanding(val name: String, val rank: Int, val score: Int, val isMe: Boolean = false)

/** Everything the reveal screen draws for one player. */
data class EsmFamilRow(
    val playerId: String,
    val name: String,
    val answers: Map<String, String>,
    val points: Map<String, Int>,
    val total: Int,
    val isMe: Boolean,
)

/** The public state of a round, as read from `publicView()`. */
data class EsmFamilPublic(
    val round: Int = 1,
    val rounds: Int = 5,
    val letter: String = "",
    val phase: EsmFamilPhase = EsmFamilPhase.WRITING,
    val categories: List<String> = EsmFamilSheet.DEFAULT_IDS,
    val submitted: Set<String> = emptySet(),
    val totals: Map<String, Int> = emptyMap(),
    val sheet: Map<String, Map<String, String>> = emptyMap(),
    val roundPoints: Map<String, Map<String, Int>> = emptyMap(),
    val standings: List<Pair<String, Pair<Int, Int>>> = emptyList(),
)

object EsmFamilUiModel {

    fun public(view: JsonObject): EsmFamilPublic {
        val phaseText = view.string(EsmFamilSession.Keys.PHASE) ?: "WRITING"
        val finished = (view[EsmFamilSession.Keys.FINISHED] as? JsonPrimitive)?.contentOrNull == "true"
        val phase = when {
            finished || phaseText == "FINISHED" -> EsmFamilPhase.FINISHED
            phaseText == "REVEAL" -> EsmFamilPhase.REVEAL
            else -> EsmFamilPhase.WRITING
        }
        val categories = (view[EsmFamilSession.Keys.CATEGORIES] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.ifEmpty { null }
            ?: EsmFamilSheet.DEFAULT_IDS
        val submitted = (view[EsmFamilSession.Keys.SUBMITTED] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.toSet()
            .orEmpty()
        val totals = (view[EsmFamilSession.Keys.SCORES] as? JsonObject)
            ?.mapValues { (_, value) -> (value as? JsonPrimitive)?.intOrNull ?: 0 }
            .orEmpty()
        val sheet = (view[EsmFamilSession.Keys.SHEET] as? JsonObject)
            ?.mapValues { (_, row) -> rowOf(row as? JsonObject) }
            .orEmpty()
        val points = (view[EsmFamilSession.Keys.ROUND_POINTS] as? JsonObject)
            ?.mapValues { (_, row) ->
                (row as? JsonObject)
                    ?.mapValues { (_, value) -> (value as? JsonPrimitive)?.intOrNull ?: 0 }
                    .orEmpty()
            }
            .orEmpty()
        val standings = ((view[EsmFamilSession.Keys.STANDINGS] as? JsonObject)?.get("table") as? JsonArray)
            ?.mapNotNull { entry ->
                val obj = entry as? JsonObject ?: return@mapNotNull null
                val player = obj.string("player") ?: return@mapNotNull null
                val rank = (obj["rank"] as? JsonPrimitive)?.intOrNull ?: 0
                val score = (obj["score"] as? JsonPrimitive)?.intOrNull ?: 0
                player to (rank to score)
            }
            .orEmpty()
        return EsmFamilPublic(
            round = (view[EsmFamilSession.Keys.ROUND] as? JsonPrimitive)?.intOrNull ?: 1,
            rounds = (view[EsmFamilSession.Keys.ROUNDS] as? JsonPrimitive)?.intOrNull ?: 5,
            letter = view.string(EsmFamilSession.Keys.LETTER).orEmpty(),
            phase = phase,
            categories = categories,
            submitted = submitted,
            totals = totals,
            sheet = sheet,
            roundPoints = points,
            standings = standings,
        )
    }

    /** My own row out of my private view (`mine`), empty when I have not written yet. */
    fun mine(privateView: JsonObject): Map<String, String> =
        rowOf(privateView[EsmFamilSession.Keys.MINE] as? JsonObject)

    /** Builds the reveal rows in seat order, with names resolved from the room where possible. */
    fun rows(
        state: EsmFamilPublic,
        seats: List<EsmFamilSeat>,
    ): List<EsmFamilRow> {
        val order = if (seats.isEmpty()) state.totals.keys.toList() else seats.map { it.id }
        return order.map { id ->
            val seat = seats.firstOrNull { it.id == id }
            EsmFamilRow(
                playerId = id,
                name = seat?.name ?: id,
                answers = state.sheet[id].orEmpty(),
                points = state.roundPoints[id].orEmpty(),
                total = state.totals[id] ?: 0,
                isMe = seat?.isMe == true,
            )
        }
    }

    /** `"س"` → the chip shown on the letter card. Empty letters fall back to `—`. */
    fun letterChip(letter: String): String = letter.trim().ifEmpty { "—" }

    /** Points → the colour bucket the badge uses. */
    fun badgeOf(points: Int): EsmFamilBadge = when {
        points >= EsmFamilSheet.POINTS_UNIQUE -> EsmFamilBadge.Full
        points > 0 -> EsmFamilBadge.Shared
        else -> EsmFamilBadge.None
    }

    /** How many columns of my sheet are still empty (the "ثبت" button stays honest). */
    fun emptyColumns(mine: Map<String, String>, categories: List<String>): Int =
        categories.count { mine[it].orEmpty().isBlank() }

    private fun rowOf(row: JsonObject?): Map<String, String> =
        row?.mapValues { (_, value) -> (value as? JsonPrimitive)?.contentOrNull.orEmpty() }.orEmpty()
}

/** How a cell scored, for the badge colour. */
enum class EsmFamilBadge { Full, Shared, None }

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
