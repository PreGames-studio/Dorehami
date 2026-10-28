package ir.dorehami.games.hokm.ui

import ir.dorehami.games.hokm.Card
import ir.dorehami.games.hokm.HandPhase
import ir.dorehami.games.hokm.HandWinKind
import ir.dorehami.games.hokm.HokmRules
import ir.dorehami.games.hokm.HokmSession
import ir.dorehami.games.hokm.Play
import ir.dorehami.games.hokm.Suit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * HK-009 — the typed, independent model of one table snapshot.
 *
 * The screen used to read the raw JSON of `privateView()` with `!!` and a dozen `jsonPrimitive`
 * calls inside `HokmTable.refresh()`: a missing key was a crash, an unknown `handPhase` silently
 * became "choosing hokm", a stale view silently overwrote a newer table and the current trick was
 * never restored from the snapshot (risk R-06). This file is the single pure place that turns that
 * JSON into [TableSnapshot] or into an explicit refusal, so it can be tested on the JVM.
 *
 * Nothing here knows about Compose, coroutines or the session: hand it the view object.
 */

/**
 * Schema of the private view this build understands. It is a *local* constant, not a field of the
 * view: the game emits `version` (a monotonic revision) but no schema tag yet. If a future build
 * adds a `schema` field, [TableSnapshotMapper] refuses anything above this number instead of
 * drawing a table it only half understands.
 */
const val TABLE_VIEW_SCHEMA = 1

/** Everything one `privateView()` says about the table, already parsed and validated. */
data class TableSnapshot(
    val schemaVersion: Int,
    /** Monotonic session revision; a smaller one is older than what the table already shows. */
    val revision: Long,
    val playerCount: Int,
    /** My seat in this snapshot (`-1` when the view does not name one). */
    val mySeat: Int,
    val finished: Boolean,
    val handPhase: HandPhase,
    val handNumber: Int,
    val hakem: Int,
    val turn: Int,
    val hokm: Suit?,
    val trickNumber: Int,
    val tricksWon: List<Int>,
    val scores: List<Int>,
    val targetPoints: Int,
    val handCounts: List<Int>,
    val myHand: List<Card>,
    val legalCards: Set<Card>,
    val revealed: Card?,
    val discardCount: Int,
    val stockCount: Int,
    val hakemDraw: List<Play>,
    /** Cards of the trick being played right now — the snapshot's truth for the table (R-06). */
    val currentTrick: List<Play>,
    val lastTrick: List<Play>,
    val lastTrickWinner: Int?,
    val lastHand: HandSummary?,
)

/** What reading a view produced. Refusals are values, not exceptions. */
sealed interface TableRead {
    /** No session view yet (still in the lobby, or the host has not dealt). */
    data object Absent : TableRead

    /** A newer schema: refuse instead of drawing a table this build only half understands. */
    data class Unsupported(val schemaVersion: Int) : TableRead

    /** Missing or unusable values; [missing] names the keys so a bug report is actionable. */
    data class Incomplete(val missing: List<String>) : TableRead

    data class Ready(val snapshot: TableSnapshot) : TableRead
}

/** What the table should do with a view it has just read. */
sealed interface TableDecision {
    data class Apply(val snapshot: TableSnapshot) : TableDecision

    /** Older revision than the one on screen: keep the table as it is. */
    data object Stale : TableDecision

    data object Absent : TableDecision

    data class Rejected(val missing: List<String>) : TableDecision

    data class Unsupported(val schemaVersion: Int) : TableDecision
}

/**
 * Pure mapper: the human's private view → [TableSnapshot].
 *
 * Order of decisions:
 * 1. `null`, or a view that names neither a seat nor a hand (the session has not started) ⇒
 *    [TableRead.Absent].
 * 2. A `schema` field newer than [TABLE_VIEW_SCHEMA] ⇒ [TableRead.Unsupported].
 * 3. A missing/unparsable required key, an unknown `handPhase`, an unknown suit or card, a seat or
 *    table shape that contradicts the table being drawn ⇒ [TableRead.Incomplete] naming the keys.
 * 4. Otherwise [TableRead.Ready].
 */
object TableSnapshotMapper {

    /** Keys this build needs; any missing one refuses the whole view. */
    val REQUIRED_KEYS = listOf(
        "version", "playerCount", "phase", "handPhase", "handNumber", "hakem", "turn",
        "trickNumber", "tricksWon", "scores", "targetPoints", "handCounts", "hand",
        "legalCards", "stockCount", "hakemDraw",
    )

    /**
     * Reads [view]. [expectedSeat] and [expectedPlayers] are what the table itself believes (its
     * constructor seat and its seat list); when given, a disagreement is refused instead of drawing
     * a table where my cards sit on somebody else's chair.
     */
    fun read(view: JsonObject?, expectedSeat: Int? = null, expectedPlayers: Int? = null): TableRead {
        if (view == null) return TableRead.Absent
        if (isPreSession(view)) return TableRead.Absent

        val missing = ArrayList<String>()
        fun primitive(key: String): JsonPrimitive? = view[key] as? JsonPrimitive
        fun int(key: String): Int? {
            val value = primitive(key)?.intOrNull
            if (value == null) missing += key
            return value
        }

        fun ints(key: String, expectedSize: Int?): List<Int>? {
            val array = view[key] as? JsonArray
            if (array == null) {
                missing += key
                return null
            }
            val values = array.map { (it as? JsonPrimitive)?.intOrNull }
            if (values.any { it == null }) {
                missing += key
                return null
            }
            val out = values.filterNotNull()
            if (expectedSize != null && out.size != expectedSize) {
                missing += key
                return null
            }
            return out
        }

        fun cards(key: String): List<Card>? {
            val array = view[key] as? JsonArray
            if (array == null) {
                missing += key
                return null
            }
            val parsed = array.map { it.parseCardOrNull() }
            if (parsed.any { it == null }) {
                missing += key
                return null
            }
            return parsed.filterNotNull()
        }

        fun plays(key: String, required: Boolean): List<Play>? {
            if (!required && !view.containsKey(key)) return emptyList()
            val parsed = parsePlays(view[key] as? JsonArray)
            if (parsed == null) missing += key
            return parsed
        }

        val revision = primitive("version")?.contentOrNull?.toLongOrNull()
        if (revision == null) missing += "version"
        val schema = primitive("schema")?.intOrNull ?: TABLE_VIEW_SCHEMA
        if (schema > TABLE_VIEW_SCHEMA) return TableRead.Unsupported(schema)

        val players = int("playerCount")
        if (players != null && players !in HokmRules.SUPPORTED_PLAYERS) missing += "playerCount"
        val phase = primitive("phase")?.contentOrNull
        if (phase != "PLAYING" && phase != "FINISHED") missing += "phase"
        val handPhaseId = primitive("handPhase")?.contentOrNull
        val handPhase = HandPhase.entries.firstOrNull { it.id == handPhaseId }
        if (handPhase == null) missing += "handPhase"
        val handNumber = int("handNumber")
        val hakem = int("hakem")
        val turn = int("turn")
        val trickNumber = int("trickNumber")
        val targetPoints = int("targetPoints")
        val stockCount = int("stockCount")
        val myHand = cards(HokmSession.Keys.HAND)
        val legalCards = cards(HokmSession.Keys.LEGAL_CARDS)
        val hakemDraw = plays("hakemDraw", required = true)
        val currentTrick = plays("currentTrick", required = false) ?: emptyList()
        val seat = primitive(HokmSession.Keys.SEAT)?.intOrNull ?: -1

        val teams = players?.let { HokmRules.teamCount(it) }
        val tricksWon = ints("tricksWon", teams)
        val scores = ints("scores", teams)
        val handCounts = ints("handCounts", players)

        val revealed = view.optionalCard(HokmSession.Keys.REVEALED, missing)
        val hokm = primitive("hokm")?.contentOrNull?.takeIf { it != "null" }?.let { text ->
            Suit.parse(text).also { if (it == null) missing += "hokm" }
        }
        val discardCount = primitive(HokmSession.Keys.DISCARD_COUNT)?.contentOrNull?.toIntOrNull() ?: 0

        // My seat must be the chair this table draws for me.
        if (expectedSeat != null && expectedSeat >= 0 && seat >= 0 && seat != expectedSeat) {
            missing += HokmSession.Keys.SEAT
        }
        if (expectedPlayers != null && expectedPlayers > 0 && players != null && players != expectedPlayers) {
            missing += "playerCount"
        }

        val lastHandObject = view["lastHandResult"] as? JsonObject
        val lastHand = lastHandObject?.let { parseHand(it) }
        if (lastHandObject != null && lastHand == null) missing += "lastHandResult"

        val lastTrickObject = view["lastTrick"] as? JsonObject
        val lastTrick = lastTrickObject?.let { parsePlays(it["cards"] as? JsonArray) }
        if (lastTrickObject != null && lastTrick == null) missing += "lastTrick"
        val lastTrickWinner = (lastTrickObject?.get("winner") as? JsonPrimitive)?.intOrNull

        if (missing.isNotEmpty()) return TableRead.Incomplete(missing.distinct())

        return TableRead.Ready(
            TableSnapshot(
                schemaVersion = schema,
                revision = revision!!,
                playerCount = players!!,
                mySeat = seat,
                finished = phase == "FINISHED",
                handPhase = handPhase!!,
                handNumber = handNumber!!,
                hakem = hakem!!,
                turn = turn!!,
                hokm = hokm,
                trickNumber = trickNumber!!,
                tricksWon = tricksWon!!,
                scores = scores!!,
                targetPoints = targetPoints!!,
                handCounts = handCounts!!,
                myHand = myHand!!,
                legalCards = legalCards!!.toSet(),
                revealed = revealed,
                discardCount = discardCount,
                stockCount = stockCount!!,
                hakemDraw = hakemDraw!!,
                currentTrick = currentTrick,
                lastTrick = lastTrick ?: emptyList(),
                lastTrickWinner = lastTrickWinner,
                lastHand = lastHand,
            ),
        )
    }

    /**
     * Reads [view] and decides what the table should do, including the staleness rule:
     * a revision below [currentRevision] never wins, so a late packet cannot rewind the table.
     */
    fun decide(
        view: JsonObject?,
        currentRevision: Long,
        expectedSeat: Int? = null,
        expectedPlayers: Int? = null,
    ): TableDecision = when (val ready = read(view, expectedSeat, expectedPlayers)) {
        TableRead.Absent -> TableDecision.Absent
        is TableRead.Unsupported -> TableDecision.Unsupported(ready.schemaVersion)
        is TableRead.Incomplete -> TableDecision.Rejected(ready.missing)
        is TableRead.Ready -> if (ready.snapshot.revision < currentRevision) {
            TableDecision.Stale
        } else {
            TableDecision.Apply(ready.snapshot)
        }
    }

    /** `null` when the object does not describe a finished hand this build understands. */
    fun parseHand(o: JsonObject): HandSummary? {
        val handNumber = (o["handNumber"] as? JsonPrimitive)?.intOrNull ?: return null
        val points = (o["points"] as? JsonPrimitive)?.intOrNull ?: return null
        val hakem = (o["hakem"] as? JsonPrimitive)?.intOrNull ?: return null
        val kindId = (o["kind"] as? JsonPrimitive)?.contentOrNull ?: return null
        val kind = HandWinKind.entries.firstOrNull { it.id == kindId } ?: return null
        val trickValues = (o["tricks"] as? JsonArray)?.map { (it as? JsonPrimitive)?.intOrNull } ?: return null
        if (trickValues.any { it == null }) return null
        val winnerText = (o["winnerTeam"] as? JsonPrimitive)?.contentOrNull ?: return null
        val winner = if (winnerText == "null" || winnerText == "NULL") null else winnerText.toIntOrNull() ?: return null
        val hokm = (o["hokm"] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }?.let(Suit::parse)
        return HandSummary(
            handNumber = handNumber,
            winnerTeam = winner,
            points = points,
            kind = kind,
            hakem = hakem,
            tricks = trickValues.filterNotNull(),
            hokm = hokm,
        )
    }

    /** A view that names neither a seat nor a hand has no table yet (lobby / pre-deal). */
    private fun isPreSession(v: JsonObject): Boolean =
        !v.containsKey("hakemDraw") || (!v.containsKey(HokmSession.Keys.HAND) && !v.containsKey(HokmSession.Keys.SEAT))

    private fun JsonObject.optionalCard(key: String, missing: MutableList<String>): Card? {
        val text = (this[key] as? JsonPrimitive)?.contentOrNull
        if (text == null || text == "null") return null
        val card = Card.parse(text)
        if (card == null) missing += key
        return card
    }

    private fun JsonElement.parseCardOrNull(): Card? = (this as? JsonPrimitive)?.contentOrNull?.let(Card::parse)

    private fun parsePlays(array: JsonArray?): List<Play>? {
        if (array == null) return null
        val parsed = array.map { element ->
            val o = element as? JsonObject ?: return@map null
            val seat = (o["seat"] as? JsonPrimitive)?.intOrNull ?: return@map null
            val card = (o["card"] as? JsonPrimitive)?.contentOrNull?.let(Card::parse) ?: return@map null
            Play(seat, card)
        }
        if (parsed.any { it == null }) return null
        return parsed.filterNotNull()
    }
}

/**
 * The snapshot is the source of truth for the cards lying on the table.
 *
 * While the table is calm (no collect animation, banner, deal or opening draw), whatever the
 * snapshot says is on the table is drawn — that is how a re-entry or a fresh private view restores
 * a half-played trick instead of showing an empty table (risk R-06). During an animation the
 * event-driven list wins, so cards are neither duplicated nor wiped mid-flight.
 */
object TableSync {
    fun tableCards(snapshot: TableSnapshot, shown: List<Play>, calm: Boolean): List<Play> =
        if (calm) snapshot.currentTrick else shown
}
