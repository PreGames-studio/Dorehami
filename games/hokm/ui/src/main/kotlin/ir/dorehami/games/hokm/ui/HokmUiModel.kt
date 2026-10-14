package ir.dorehami.games.hokm.ui

import ir.dorehami.gamesdk.Phase
import ir.dorehami.games.hokm.Card
import ir.dorehami.games.hokm.HandPhase
import ir.dorehami.games.hokm.HandWinKind
import ir.dorehami.games.hokm.HokmRules
import ir.dorehami.games.hokm.HokmSession
import ir.dorehami.games.hokm.Play
import ir.dorehami.games.hokm.Suit
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ------------------------------------------------------------------ table model (pure)

/** A player as drawn on the table. */
data class SeatInfo(val seat: Int, val name: String, val isBot: Boolean)

/** Result of the last finished hand, as the UI needs it. */
data class HandSummary(
    val handNumber: Int,
    /** `null` for a void three-player hand. */
    val winnerTeam: Int?,
    val points: Int,
    val kind: HandWinKind,
    val hakem: Int,
    val tricks: List<Int>,
    val hokm: Suit? = null,
)

/** A sticker shown in a bubble over a seat. */
data class Sticker(val id: String, val emoji: String, val captionFa: String)

/** All stickers available in the tray; ids are what travels over the wire later. */
object Stickers {
    val all = listOf(
        Sticker("clap", "👏", "آفرین!"),
        Sticker("laugh", "😂", "خنده‌دار بود"),
        Sticker("cool", "😎", "حرفه‌ای"),
        Sticker("angry", "😤", "اَه!"),
        Sticker("think", "🤔", "صبر کن…"),
        Sticker("fire", "🔥", "ترکوندی"),
        Sticker("crown", "👑", "حاکم منم"),
        Sticker("heart", "❤️", "دمت گرم"),
        Sticker("cry", "😭", "باختیم…"),
        Sticker("fast", "⏱️", "زود باش!"),
        Sticker("tea", "🍵", "یه چایی بزنیم"),
        Sticker("gg", "🤝", "بازی خوبی بود"),
    )

    fun byId(id: String): Sticker? = all.firstOrNull { it.id == id }
}

/** A sticker currently visible over [seat]; [stamp] makes repeated identical stickers re-animate. */
data class SeatSticker(val seat: Int, val sticker: Sticker, val stamp: Long)

/** Something transient to say on the table (feed line / banner). */
data class Banner(val title: String, val subtitle: String? = null, val big: Boolean = false)

/** One-shot visual effects the screen renders on top of the table. */
enum class FxKind {
    /** A trump was thrown on a non-trump lead ("بریدن"): golden flash + sparks at that seat's slot. */
    CUT,
    /** Trick swept: a small sparkle at the winner. */
    TRICK_WIN,
    /** Kot / hakem kot: a big burst in the middle. */
    KOT,
    /** Match won by my side: confetti. */
    CONFETTI,
}

data class TableFx(val kind: FxKind, val seat: Int, val stamp: Long)

/**
 * Everything the screen draws. Built **only** from the human player's `privateView()` plus the
 * UI-side animation bookkeeping — the screen never touches the session.
 */
data class HokmUiState(
    val seats: List<SeatInfo> = emptyList(),
    val playerCount: Int = 4,
    val mySeat: Int = 0,
    val phase: Phase = Phase.PLAYING,
    val handPhase: HandPhase = HandPhase.CHOOSING_HOKM,
    val handNumber: Int = 1,
    val hakem: Int = 0,
    val hokm: Suit? = null,
    val turn: Int = 0,
    val trickNumber: Int = 0,
    val tricksWon: List<Int> = listOf(0, 0),
    val scores: List<Int> = listOf(0, 0),
    val targetPoints: Int = 7,
    val handCounts: List<Int> = listOf(0, 0, 0, 0),
    val myHand: List<Card> = emptyList(),
    val legalCards: Set<Card> = emptySet(),
    /** Two-player: the stock card shown to me while I decide keep/pass. */
    val revealed: Card? = null,
    /** Two-player: how many cards I must burn right now (0 = not my burn turn). */
    val discardCount: Int = 0,
    val stockCount: Int = 0,
    /** Cards I have tapped so far while burning. */
    val selectedForDiscard: Set<Card> = emptySet(),
    val stickers: List<SeatSticker> = emptyList(),
    /** Cards on the table right now (kept visible for a moment after a trick closes). */
    val tableCards: List<Play> = emptyList(),
    /** Which trick `tableCards` belong to — the felt is trick-scoped (HK-010). */
    val tableTrickNumber: Int = 0,
    /** Seat sweeping the trick frozen on the felt; null when the felt is calm (HK-010). */
    val sweepOf: Int? = null,
    /** Seat that is sweeping the current trick, while the collect animation runs. */
    val collectingTo: Int? = null,
    val lastHand: HandSummary? = null,
    val banner: Banner? = null,
    val hakemDraw: List<Play> = emptyList(),
    /** How many of [hakemDraw] are face-up during the opening animation (`null` = intro finished). */
    val introShown: Int? = null,
    val dealing: Boolean = false,
    val error: String? = null,
    val finished: Boolean = false,
    val winnerTeam: Int? = null,
    val feed: List<String> = emptyList(),
    val fx: List<TableFx> = emptyList(),
    /** Every finished hand so far (score sheet). */
    val handHistory: List<HandSummary> = emptyList(),
    val lastTrick: List<Play> = emptyList(),
    val lastTrickWinner: Int? = null,
    /** Wall-clock deadline for the current human move in rooms (0 = no timer). */
    val turnDeadlineMs: Long = 0L,
    val turnSeconds: Int = 0,
    /** Short label of the game mode shown in the score bar ("ربات", "بلوتوث", "آنلاین"). */
    val modeLabel: String = "",
    /** Session `version` of the newest private view applied here; older views are stale. */
    val viewVersion: Long = 0L,
) {
    val myTeam: Int get() = HokmRules.teamOf(mySeat, playerCount)
    fun teamOf(seat: Int): Int = HokmRules.teamOf(seat, playerCount)
    private val calm: Boolean get() = !finished && introShown == null && collectingTo == null && sweepOf == null && !dealing && banner == null
    val isMyTurn: Boolean get() = calm && handPhase == HandPhase.PLAYING && turn == mySeat
    val iChooseHokm: Boolean get() = calm && handPhase == HandPhase.CHOOSING_HOKM && hakem == mySeat
    val iDiscard: Boolean get() = calm && handPhase == HandPhase.DISCARDING && turn == mySeat && discardCount > 0
    val iDraw: Boolean get() = calm && handPhase == HandPhase.DRAWING && turn == mySeat && revealed != null
    val isTeamGame: Boolean get() = playerCount == 4
}

// ------------------------------------------------------------------ view → snapshot (HK-009)

/**
 * Independent model of the *game* on one table: everything one private view carries, typed and
 * checked — nothing about animation, dialogs, sound or one-shot effects. This is what a snapshot
 * of the match looks like for one seat; [HokmUiState] adds the table furniture on top of it.
 */
data class TableSnapshot(
    /** Session `version` this snapshot was taken at — the staleness key. */
    val version: Long,
    val mySeat: Int,
    val playerCount: Int,
    val finished: Boolean,
    val handPhase: HandPhase,
    val handNumber: Int,
    val hakem: Int,
    val hokm: Suit?,
    val turn: Int,
    val trickNumber: Int,
    val leadSuit: Suit?,
    /** Cards already played in the trick still open on the table. */
    val currentTrick: List<Play>,
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
    val lastTrick: List<Play>,
    val lastTrickWinner: Int?,
    val lastHand: HandSummary?,
    val winnerTeam: Int?,
)

/** Outcome of mapping a raw private view into the typed [TableSnapshot]. */
sealed class MappedView {
    data class Valid(val snapshot: TableSnapshot) : MappedView()

    /**
     * The view is incomplete, inconsistent or unparseable. The table must keep showing its
     * previous state instead of guessing or crashing.
     */
    data class Rejected(val code: String, val messageFa: String) : MappedView()
}

/**
 * The typed boundary between raw JSON views and the table model (HK-009).
 *
 * Pure Kotlin/JVM: no Compose, no Android, no I/O. [map] validates one private view (missing keys,
 * bad cards, impossible numbers and internal contradictions are all rejected at this boundary) and
 * [merge] applies a fresh snapshot onto [HokmUiState] while keeping the animation furniture, and
 * refusing snapshots that are older than the one already shown.
 */
object HokmUiMapper {

    private class ViewReject(val code: String, val messageFa: String) : Exception(messageFa)

    private fun reject(code: String, messageFa: String): Nothing = throw ViewReject(code, messageFa)

    /** Maps one private view; see [MappedView] for the two outcomes. */
    fun map(view: JsonObject?): MappedView {
        if (view == null) return MappedView.Rejected("view.missing", "نمای بازی هنوز آماده نیست")
        return try {
            MappedView.Valid(read(view))
        } catch (e: ViewReject) {
            MappedView.Rejected(e.code, e.messageFa)
        } catch (_: Exception) {
            MappedView.Rejected("view.invalid", "نمای بازی خوانده نشد")
        }
    }

    /**
     * Maps the payload of a `hand_finished` event / `lastHandResult` field. Returns `null` when the
     * payload is not a complete hand result.
     */
    fun handSummary(o: JsonObject): HandSummary? = try {
        val kind = o["kind"]?.jsonPrimitive?.content?.let { id -> HandWinKind.entries.firstOrNull { it.id == id } }
            ?: reject("view.invalid", "نوع نتیجهٔ دست ناشناخته است")
        HandSummary(
            handNumber = reqInt(o, "handNumber"),
            winnerTeam = o["winnerTeam"]?.jsonPrimitive?.content?.toIntOrNull(),
            points = reqInt(o, "points"),
            kind = kind,
            hakem = reqInt(o, "hakem"),
            tricks = intList(o, "tricks"),
            hokm = o["hokm"]?.jsonPrimitive?.content?.takeIf { it != "null" }?.let { Suit.parse(it) },
        )
    } catch (_: Exception) {
        null
    }

    /**
     * Applies a fresh [snap] onto [old]:
     * * game fields update from the snapshot; transient table furniture (stickers, feed, banners,
     *   fx, score-sheet history, room timer meta) survives;
     * * `tableCards` is rebuilt from the snapshot's current trick — **unless** an animation is
     *   showing those cards (collect / dealing / intro) **or** notices are still queued
     *   ([feltSync] false): then the notice stream keeps building the felt and the merge only
     *   updates truth, so a mid-burst resync can neither drop nor duplicate a card (HK-010);
     * * a snapshot older than [HokmUiState.viewVersion] is stale and ignored entirely;
     * * a snapshot for another seat or player count is refused (the table keeps its identity).
     */
    fun merge(old: HokmUiState, snap: TableSnapshot, feltSync: Boolean = true): HokmUiState {
        if (old.viewVersion > 0 && snap.version < old.viewVersion) return old
        if (snap.mySeat != old.mySeat || snap.playerCount != old.playerCount) return old
        val animating = old.collectingTo != null || old.dealing || old.introShown != null || old.sweepOf != null
        val feltFromSnapshot = feltSync && !animating
        return old.copy(
            phase = if (snap.finished) Phase.FINISHED else Phase.PLAYING,
            handPhase = snap.handPhase,
            handNumber = snap.handNumber,
            hakem = snap.hakem,
            hokm = snap.hokm,
            turn = if (old.sweepOf != null || old.collectingTo != null) -1 else snap.turn,
            trickNumber = snap.trickNumber,
            tricksWon = snap.tricksWon,
            scores = snap.scores,
            targetPoints = snap.targetPoints,
            handCounts = snap.handCounts,
            myHand = snap.myHand,
            legalCards = snap.legalCards,
            revealed = snap.revealed,
            discardCount = snap.discardCount,
            stockCount = snap.stockCount,
            hakemDraw = snap.hakemDraw,
            tableCards = if (feltFromSnapshot) snap.currentTrick else old.tableCards,
            tableTrickNumber = if (feltFromSnapshot) snap.trickNumber else old.tableTrickNumber,
            lastHand = snap.lastHand,
            lastTrick = snap.lastTrick,
            lastTrickWinner = snap.lastTrickWinner,
            finished = snap.finished,
            winnerTeam = snap.winnerTeam,
            selectedForDiscard = if (snap.handPhase == HandPhase.DISCARDING) old.selectedForDiscard else emptySet(),
            viewVersion = snap.version,
        )
    }

    // ------------------------------------------------------------------ reading

    private fun read(v: JsonObject): TableSnapshot {
        val playerCount = reqInt(v, "playerCount")
        if (playerCount !in HokmRules.SUPPORTED_PLAYERS) reject("view.invalid", "تعداد بازیکن $playerCount پشتیبانی نمی‌شود")
        val teams = HokmRules.teamCount(playerCount)
        val handPhaseId = reqStr(v, "handPhase")
        val handPhase = HandPhase.entries.firstOrNull { it.id == handPhaseId }
            ?: reject("view.invalid", "فاز دست ناشناخته است")
        val phaseName = reqStr(v, "phase")
        val finished = when (phaseName) {
            "FINISHED" -> true
            "PLAYING" -> false
            else -> reject("view.invalid", "فاز بازی ناشناخته است")
        }
        val mySeat = reqInt(v, "seat")
        if (mySeat !in 0 until playerCount) reject("view.invalid", "صندلی $mySeat خارج از میز است")
        val handNumber = reqInt(v, "handNumber")
        if (handNumber < 1) reject("view.invalid", "شمارهٔ دست $handNumber نامعتبر است")
        val trickNumber = reqInt(v, "trickNumber")
        if (trickNumber < 0) reject("view.invalid", "شمارهٔ ترفند نامعتبر است")
        val hakem = reqInt(v, "hakem")
        if (hakem !in 0 until playerCount) reject("view.invalid", "حاکم خارج از میز است")
        val turn = reqInt(v, "turn")
        if (turn !in 0 until playerCount) reject("view.invalid", "نوبت خارج از میز است")
        val tricksWon = intList(v, "tricksWon")
        if (tricksWon.size != teams) reject("view.inconsistent", "شمارندهٔ ترفند‌ها با تعداد تیم‌ها نمی‌خواند")
        val scores = intList(v, "scores")
        if (scores.size != teams) reject("view.inconsistent", "امتیازها با تعداد تیم‌ها نمی‌خواند")
        val handCounts = intList(v, "handCounts")
        if (handCounts.size != playerCount) reject("view.inconsistent", "شمار کارت‌ها با تعداد بازیکنان نمی‌خواند")
        val myHand = cardList(req(v, "hand"))
        if (myHand.size != myHand.toSet().size) reject("view.inconsistent", "کارت تکراری در دست من است")
        if (handCounts[mySeat] != myHand.size) reject("view.inconsistent", "شمار کارت دست من با نمای میز نمی‌خواند")
        val legalCards = cardList(req(v, "legalCards")).toSet()
        if (!myHand.containsAll(legalCards)) reject("view.inconsistent", "کارت مجاز خارج از دست من است")
        val currentTrick = playList(req(v, "currentTrick"))
        if (currentTrick.size >= playerCount) reject("view.inconsistent", "ترفند باز نمی‌تواند پر باشد")
        if (currentTrick.any { it.seat !in 0 until playerCount }) {
            reject("view.inconsistent", "بازیکن ترفند خارج از میز است")
        }
        val leadSuit = v["leadSuit"]?.jsonPrimitive?.content?.takeIf { it != "null" }?.let {
            Suit.parse(it) ?: reject("view.invalid", "خال زمین ناشناخته است")
        }
        if (currentTrick.isNotEmpty() && leadSuit != currentTrick.first().card.suit) {
            reject("view.inconsistent", "خال زمین با نخستین کارت ترفند نمی‌خواند")
        }
        if (currentTrick.map { it.seat }.toSet().size != currentTrick.size) {
            reject("view.inconsistent", "یک صندلی دو بار در ترفند بازی کرده است")
        }
        if (req(v, "players").jsonArray.size != playerCount) {
            reject("view.inconsistent", "فهرست بازیکنان با تعداد بازیکنان نمی‌خواند")
        }
        val scoresMax = scores.max()
        return TableSnapshot(
            version = reqLong(v, "version"),
            mySeat = mySeat,
            playerCount = playerCount,
            finished = finished,
            handPhase = handPhase,
            handNumber = handNumber,
            hakem = hakem,
            hokm = v["hokm"]?.jsonPrimitive?.content?.takeIf { it != "null" }?.let {
                Suit.parse(it) ?: reject("view.invalid", "خال حکم ناشناخته است")
            },
            turn = turn,
            trickNumber = trickNumber,
            leadSuit = leadSuit,
            currentTrick = currentTrick,
            tricksWon = tricksWon,
            scores = scores,
            targetPoints = reqInt(v, "targetPoints"),
            handCounts = handCounts,
            myHand = myHand,
            legalCards = legalCards,
            revealed = v[HokmSession.Keys.REVEALED]?.jsonPrimitive?.content?.takeIf { it != "null" }?.let {
                Card.parse(it) ?: reject("view.invalid", "کارت رو‌شده ناشناخته است")
            },
            discardCount = v[HokmSession.Keys.DISCARD_COUNT]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            stockCount = reqInt(v, "stockCount"),
            hakemDraw = playList(req(v, "hakemDraw")),
            lastTrick = (v["lastTrick"] as? JsonObject)?.get("cards")?.let { playList(it) } ?: emptyList(),
            lastTrickWinner = (v["lastTrick"] as? JsonObject)?.get("winner")?.jsonPrimitive?.content?.toIntOrNull(),
            lastHand = v["lastHandResult"]?.let { it as? JsonObject }?.let { handSummary(it) },
            winnerTeam = if (finished) scores.indexOf(scoresMax) else null,
        )
    }

    private fun req(v: JsonObject, key: String): JsonElement =
        v[key] ?: reject("view.incomplete", "کلید «$key» در نمای بازی نیست")

    private fun reqStr(v: JsonObject, key: String): String =
        req(v, key).jsonPrimitive.content.takeIf { it != "null" }
            ?: reject("view.incomplete", "مقدار «$key» در نمای بازی نیست")

    private fun reqInt(v: JsonObject, key: String): Int =
        reqStr(v, key).toIntOrNull() ?: reject("view.invalid", "مقدار «$key» عدد نیست")

    private fun reqLong(v: JsonObject, key: String): Long =
        reqStr(v, key).toLongOrNull() ?: reject("view.invalid", "مقدار «$key» عدد نیست")

    private fun intList(v: JsonObject, key: String): List<Int> =
        req(v, key).jsonArray.map {
            it.jsonPrimitive.content.toIntOrNull() ?: reject("view.invalid", "مقدار «$key» عدد نیست")
        }

    private fun cardList(e: JsonElement): List<Card> =
        e.jsonArray.map {
            Card.parse(it.jsonPrimitive.content) ?: reject("view.invalid", "کارت ناشناخته در نمای بازی است")
        }

    private fun playList(e: JsonElement): List<Play> =
        e.jsonArray.map { p ->
            val o = p.jsonObject
            val seat = o["seat"]?.jsonPrimitive?.content?.toIntOrNull()
                ?: reject("view.invalid", "بازیکن ترفند مشخص نیست")
            val card = o["card"]?.jsonPrimitive?.content?.let { Card.parse(it) }
                ?: reject("view.invalid", "کارت ناشناخته در ترفند است")
            Play(seat, card)
        }
}
