package ir.dorehami.games.hokm

import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.Emission
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameSession
import ir.dorehami.gamesdk.MatchResult
import ir.dorehami.gamesdk.Phase
import ir.dorehami.gamesdk.Player
import ir.dorehami.gamesdk.PlayerId
import ir.dorehami.gamesdk.Standing
import ir.dorehami.gamesdk.TeamScore
import ir.dorehami.gamesdk.jsonInts
import ir.dorehami.gamesdk.jsonStrings
import ir.dorehami.gamesdk.view
import ir.dorehami.protocol.ErrCodes
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** مرحلهٔ ریزِ داخلِ یک دستِ حکم. */
internal enum class Stage(val key: String) {
    /** ۵ کارتِ اول پخش شده و منتظریم حاکم حکم را اعلام کند. */
    TRUMP("trump"),

    /** حکم معلوم شده و دست‌ها در جریان است. */
    PLAYING("playing"),

    /** کل بازی تمام شده است. */
    FINISHED("finished"),
}

/** یک کارتِ بازی‌شده در دستِ جاری. */
internal data class TrickPlay(val seat: Int, val card: Card)

/** دستِ تمام‌شده (برای انیمیشنِ جمع‌کردن و نمایشِ «چه کسی برد»). */
internal data class ResolvedTrick(
    val number: Int,
    val plays: List<TrickPlay>,
    val winnerSeat: Int,
    val team: Int,
    val ruffed: Boolean,
)

/** خلاصهٔ آخرین دستِ تمام‌شده (برای نمایش در بالای میز و صفحهٔ پایان). */
internal data class HandSummary(
    val number: Int,
    val winnerTeam: Int,
    val loserTeam: Int,
    val tricksWon: List<Int>,
    val points: Int,
    val isKot: Boolean,
    val isHakemKoti: Boolean,
)

/**
 * قواعدِ کاملِ بازی حکم (۴ نفره، دو تیمِ ثابت).
 *
 * منبعِ قوانین: **pagat.com/whist/hokm.html** (معتبرترین مرجعِ بین‌المللی برای حکم) هماهنگ با
 * روالِ رایج در ایران:
 *
 * * تعیینِ حاکم با رو کردنِ کارت: اولین کسی که **آس** بگیرد حاکم است و دومین آس یارِ او؛
 *   صندلی‌ها طوری چیده می‌شوند که یارها روبه‌روی هم بنشینند.
 * * پخش: دورِ اول ۵ کارت (یارِ حاکم هیچ کارتی نمی‌گیرد تا حکم معلوم شود)، بعد از تعیینِ حکم،
 *   بقیه تا ۱۳ کارت پخش می‌شود. پخش از حاکم شروع می‌شود و به آخرین نفر (ورق‌دهنده) ختم می‌شود.
 * * حاکم شروع‌کنندهٔ اولین دست است؛ هرکس باید خالِ زمین را جواب بدهد وگرنه می‌تواند هر کارتی
 *   بازی کند (از جمله حکم). برندهٔ هر دست، شروع‌کنندهٔ دستِ بعد است.
 * * اولین تیمی که **۷ دست** بگیرد برندهٔ آن دست است و یک امتیاز می‌گیرد. اگر بازنده هیچ دستی
 *   نگرفته باشد (۷ بر صفر) می‌شود **کوت**: ۲ امتیاز برای تیمِ حاکم و ۳ امتیاز برای حریف
 *   (**حاکم‌کُتی**).
 * * بازی تا رسیدنِ یک تیم به ۷ امتیاز ادامه دارد. اگر تیمِ حاکم برنده شود، حاکم می‌ماند؛ وگرنه
 *   نوبتِ پخش به نفرِ بعدی می‌رود.
 *
 * سه قاعدهٔ تغییرناپذیر رعایت شده‌اند: **قطعیت** (هیچ تصادف و ساعتِ سیستمی در کار نیست)،
 * **رد به‌جای استثنا** (هر حرکتِ بد یک `Rejected` با کد و پیام فارسی است) و
 * **پنهان‌سازیِ ساختاری** (کلیدِ `hand` در `publicView()` اصلاً وجود ندارد).
 */
class HokmSession internal constructor(
    private val config: GameConfig,
    private val options: HokmOptions,
    /**
     * برای تست: دست‌های از پیش‌آماده که به‌جای پخشِ تصادفی پخش می‌شوند.
     *
     * هر عنصر یک دستِ کامل است: چهار لیستِ ۱۳تایی به ترتیبِ صندلی (۰ تا ۳). وقتی این فهرست خالی
     * باشد پخش با [ir.dorehami.gamesdk.Rng] انجام می‌شود (حالتِ عادی).
     */
    private val scriptedHands: List<List<List<Card>>> = emptyList(),
    /**
     * فقط برای تست: تعیینِ حاکم با رو کردنِ آس را دور می‌زند و صندلیِ ۰ را حاکم می‌کند تا
     * سناریوهای قواعد با دست‌های دقیقاً معلوم نوشته شوند.
     */
    private val skipHakemFlip: Boolean = false,
) : GameSession {

    /** بازیکن‌ها به ترتیبِ صندلی (بعد از چیدمانِ «یارها روبه‌رو»). */
    private val seats: List<Player>

    /**
     * دستهٔ کارتِ فعلی و جایگاهِ پخش در آن.
     *
     * مقدارِ اولیه خالی است چون [prepareDeck] (که دسته را می‌چیند) از `init` صدا زده می‌شود
     * و کامپایلر قطعیّتِ مقداردهیِ درونِ تابع را تشخیص نمی‌دهد.
     */
    private var deck: List<Card> = emptyList()
    private var deckCursor = 0

    private val hands: Array<MutableList<Card>> = Array(PLAYERS) { mutableListOf() }
    private val tricksWon: IntArray = IntArray(TEAMS)
    private val teamScores: IntArray = IntArray(TEAMS)

    private var hakemSeat: Int = 0
    private var trump: Suit? = null
    private var stage: Stage = Stage.TRUMP
    private var handNumber: Int = 1
    private var trickNumber: Int = 1
    private var turnSeat: Int = 0
    private var leaderSeat: Int = 0

    private val trick = mutableListOf<TrickPlay>()
    private var lastTrick: ResolvedTrick? = null
    private var lastHand: HandSummary? = null
    private var matchResult: MatchResult? = null

    private val pendingEmissions = mutableListOf<Emission>()
    private var scriptedHandIndex = 0
    private var kotCount = 0

    override var version: Long = 0L
        private set

    override var phase: Phase = Phase.SETUP
        private set

    init {
        require(config.players.size == PLAYERS) {
            "حکم دقیقاً ۴ بازیکن می‌خواهد (تعداد فعلی: ${config.players.size})"
        }
        if (skipHakemFlip) {
            seats = config.players.mapIndexed { seat, player ->
                player.copy(seat = seat, team = teamOf(seat))
            }
            prepareDeck()
        } else {
            val flip = determineHakem()
            seats = buildSeating(flip.hakemIndex, flip.partnerIndex)
            deck = flip.deck
            deckCursor = 0
            announceHakemFlip(flip)
        }
        hakemSeat = 0
        leaderSeat = 0
        turnSeat = 0
        dealFirstRoundAndAnnounce()
    }

    /**
     * تعیینِ حاکم با رو کردنِ کارت: کارت‌ها یکی‌یکی و به نوبت رو می‌شوند؛ اولین کسی که **آس**
     * بگیرد حاکم است و دومین آس، یارِ او. حاکم بعد از آس آوردن کارتِ دیگری نمی‌گیرد.
     */
    private fun announceHakemFlip(flip: HakemFlip) {
        emit(
            Emission.Public(
                type = HokmEvents.HAKEM_FLIP,
                payload = view {
                    put("cards", JsonArray(
                        flip.revealed.map { (playerIndex, card, isAce) ->
                            buildJsonObject {
                                put("player", JsonPrimitive(config.players[playerIndex].id.value))
                                put("name", JsonPrimitive(config.players[playerIndex].displayName))
                                put("card", JsonPrimitive(card.code))
                                put("isAce", JsonPrimitive(isAce))
                            }
                        },
                    ))
                    put("hakem", JsonPrimitive(seats[0].id.value))
                    put("partner", JsonPrimitive(seats[2].id.value))
                    put("seats", seatsJson())
                },
            ),
        )
        emit(
            Emission.Feed(
                key = HokmFeed.HAKEM_CHOSEN,
                args = view {
                    put("hakem", JsonPrimitive(seats[0].displayName))
                    put("partner", JsonPrimitive(seats[2].displayName))
                },
                kind = "info",
            ),
        )
        emit(Emission.Sound(HokmSounds.DEAL))
    }

    // -------------------------------------------------------------------------
    // سؤال ۴: پذیرش یا ردِ یک حرکت
    // -------------------------------------------------------------------------

    override fun apply(player: PlayerId, action: GameAction): ApplyResult {
        val seat = seatOf(player) ?: return reject(ErrCodes.NOT_IN_MATCH, "شما عضو این دست نیستید")
        if (phase == Phase.FINISHED) return reject(ErrCodes.GAME_FINISHED, "این بازی تمام شده است")

        return when (action.type) {
            HokmModule.ACTION_DECLARE_TRUMP -> declareTrump(seat, action)
            HokmModule.ACTION_PLAY_CARD -> playCard(seat, action)
            else -> reject(ErrCodes.UNKNOWN_ACTION, "این حرکت در حکم وجود ندارد")
        }
    }

    private fun declareTrump(seat: Int, action: GameAction): ApplyResult {
        if (stage != Stage.TRUMP) {
            return reject(ErrCodes.WRONG_PHASE, "حکم قبلاً تعیین شده است")
        }
        if (seat != hakemSeat) {
            return reject(ErrCodes.NOT_YOUR_TURN, "فقط حاکم می‌تواند حکم را تعیین کند")
        }
        val raw = action.payload["suit"]?.let { it as? JsonPrimitive }?.content
            ?: return reject(ErrCodes.BAD_PAYLOAD, "خالِ حکم فرستاده نشده است")
        val suit = runCatching { Suit.valueOf(raw.uppercase()) }.getOrNull()
            ?: return reject(ErrCodes.BAD_PAYLOAD, "خال نامعتبر است ($raw)")

        trump = suit
        dealRestOfTheHand()

        val emissions = mutableListOf<Emission>()
        emissions += Emission.Public(
            type = HokmEvents.TRUMP_SET,
            payload = view {
                put("trump", JsonPrimitive(suit.name))
                put("trumpFa", JsonPrimitive(suit.faName))
                put("symbol", JsonPrimitive(suit.symbol))
                put("hakem", JsonPrimitive(seats[hakemSeat].id.value))
                put("hand", JsonPrimitive(handNumber))
            },
        )
        emissions += Emission.Feed(
            key = HokmFeed.TRUMP_DECLARED,
            args = view {
                put("suit", JsonPrimitive(suit.faName))
                put("symbol", JsonPrimitive(suit.symbol))
                put("hakem", JsonPrimitive(seats[hakemSeat].displayName))
            },
            kind = "info",
        )
        emissions += Emission.Sound(HokmSounds.TRUMP_SET)
        emissions += Emission.Public(
            type = HokmEvents.HAND_DEALT,
            payload = view { put("hand", JsonPrimitive(handNumber)) },
        )
        emissions += privateDealEmissions()

        stage = Stage.PLAYING
        phase = Phase.PLAYING
        turnSeat = hakemSeat
        leaderSeat = hakemSeat
        version++
        return ApplyResult.Ok(emissions + drain())
    }

    private fun playCard(seat: Int, action: GameAction): ApplyResult {
        if (stage != Stage.PLAYING) {
            return reject(ErrCodes.WRONG_PHASE, "صبر کن تا حاکم حکم را تعیین کند")
        }
        if (seat != turnSeat) {
            return reject(ErrCodes.NOT_YOUR_TURN, "نوبت شما نیست")
        }
        val raw = action.payload["card"]?.let { it as? JsonPrimitive }?.content
            ?: return reject(ErrCodes.BAD_PAYLOAD, "کارت فرستاده نشده است")
        val card = Card.fromCode(raw)
            ?: return reject(ErrCodes.BAD_PAYLOAD, "کارت نامعتبر است ($raw)")
        if (card !in hands[seat]) {
            return reject(ErrCodes.BAD_PAYLOAD, "این کارت در دست شما نیست")
        }
        if (card !in legalCardsFor(seat)) {
            return reject(ErrCodes.ILLEGAL_ACTION, illegalMessage(seat, card))
        }

        hands[seat].remove(card)
        trick += TrickPlay(seat, card)

        val emissions = mutableListOf<Emission>()
        emissions += Emission.Public(
            type = HokmEvents.CARD_PLAYED,
            payload = view {
                put("seat", JsonPrimitive(seat))
                put("player", JsonPrimitive(seats[seat].id.value))
                put("card", JsonPrimitive(card.code))
                put("hand", JsonPrimitive(handNumber))
                put("trickNumber", JsonPrimitive(trickNumber))
                put("index", JsonPrimitive(trick.size - 1))
            },
        )
        emissions += Emission.Sound(HokmSounds.PLAY)

        if (trick.size == PLAYERS) {
            resolveTrick(emissions)
        } else {
            turnSeat = nextSeat(seat)
            emissions += Emission.Private(to = seats[seat].id, view = privateView(seats[seat].id))
        }

        version++
        return ApplyResult.Ok(emissions + drain())
    }

    private fun resolveTrick(emissions: MutableList<Emission>) {
        val led = trick.first().card.suit
        val trumpSuit = requireNotNull(trump) { "حکم باید پیش از بازی معلوم شده باشد" }
        val winner = trick.maxBy { it.card.strength(trumpSuit, led) }
        val ruffed = trick.any { it.card.suit == trumpSuit } && led != trumpSuit
        val team = teamOf(winner.seat)
        tricksWon[team]++
        lastTrick = ResolvedTrick(
            number = trickNumber,
            plays = trick.toList(),
            winnerSeat = winner.seat,
            team = team,
            ruffed = ruffed,
        )

        emissions += Emission.Public(
            type = HokmEvents.TRICK_WON,
            payload = view {
                put("winner", JsonPrimitive(seats[winner.seat].id.value))
                put("winnerSeat", JsonPrimitive(winner.seat))
                put("team", JsonPrimitive(team))
                put("trickNumber", JsonPrimitive(trickNumber))
                put("ruffed", JsonPrimitive(ruffed))
                put("tricks", jsonInts(tricksWon.toList()))
            },
        )
        emissions += Emission.Feed(
            key = if (ruffed) HokmFeed.TRICK_RUFFED else HokmFeed.TRICK_WON,
            args = view {
                put("winner", JsonPrimitive(seats[winner.seat].displayName))
                put("card", JsonPrimitive(winner.card.code))
            },
            kind = "info",
        )
        emissions += Emission.Sound(if (ruffed) HokmSounds.RUFF else HokmSounds.TRICK_WON)

        trick.clear()
        trickNumber++

        val handOver = when (options.handEnd) {
            HandEndMode.SEVEN -> tricksWon[0] >= TRICKS_TO_WIN_HAND || tricksWon[1] >= TRICKS_TO_WIN_HAND
            HandEndMode.THIRTEEN -> trickNumber > TRICKS_IN_HAND
        }
        if (handOver) {
            scoreHand(emissions)
        } else {
            turnSeat = winner.seat
            leaderSeat = winner.seat
        }
    }

    private fun scoreHand(emissions: MutableList<Emission>) {
        val winnerTeam = if (tricksWon[0] > tricksWon[1]) 0 else 1
        val loserTeam = 1 - winnerTeam
        val hakemTeam = teamOf(hakemSeat)
        val isKot = tricksWon[loserTeam] == 0 &&
            (options.handEnd == HandEndMode.SEVEN || tricksWon[winnerTeam] == TRICKS_IN_HAND)
        val isHakemKoti = isKot && winnerTeam != hakemTeam
        val points = when {
            !isKot -> 1
            winnerTeam == hakemTeam -> options.kotHakemPoints
            else -> options.kotOpponentPoints
        }
        teamScores[winnerTeam] += points
        if (isKot) kotCount++
        lastHand = HandSummary(
            number = handNumber,
            winnerTeam = winnerTeam,
            loserTeam = loserTeam,
            tricksWon = tricksWon.toList(),
            points = points,
            isKot = isKot,
            isHakemKoti = isHakemKoti,
        )

        emissions += Emission.Public(
            type = HokmEvents.HAND_SCORED,
            payload = view {
                put("hand", JsonPrimitive(handNumber))
                put("winnerTeam", JsonPrimitive(winnerTeam))
                put("loserTeam", JsonPrimitive(loserTeam))
                put("tricks", jsonInts(tricksWon.toList()))
                put("points", JsonPrimitive(points))
                put("kot", JsonPrimitive(isKot))
                put("hakemKoti", JsonPrimitive(isHakemKoti))
                put("scores", jsonInts(teamScores.toList()))
            },
        )
        emissions += Emission.Feed(
            key = when {
                isHakemKoti -> HokmFeed.HAKEM_KOTI
                isKot -> HokmFeed.KOT
                else -> HokmFeed.HAND_SCORED
            },
            args = view {
                put("team", JsonPrimitive(winnerTeam + 1))
                put("points", JsonPrimitive(points))
                put("tricks", JsonPrimitive(tricksWon[winnerTeam]))
            },
            kind = if (isKot) "big" else "good",
        )
        emissions += Emission.Sound(
            when {
                isHakemKoti -> HokmSounds.HAKEM_KOTI
                isKot -> HokmSounds.KOT
                else -> HokmSounds.HAND_WON
            },
        )

        if (teamScores[winnerTeam] >= options.targetPoints) {
            finishMatch(winnerTeam, emissions)
        } else {
            startNextHand(winnerTeam, emissions)
        }
    }

    private fun finishMatch(winnerTeam: Int, emissions: MutableList<Emission>) {
        stage = Stage.FINISHED
        phase = Phase.FINISHED
        matchResult = buildResult(winnerTeam)

        emissions += Emission.Public(
            type = HokmEvents.MATCH_FINISHED,
            payload = view {
                put("winnerTeam", JsonPrimitive(winnerTeam))
                put("scores", jsonInts(teamScores.toList()))
                put("hands", JsonPrimitive(handNumber))
            },
        )
        emissions += Emission.Feed(
            key = HokmFeed.MATCH_FINISHED,
            args = view {
                put("team", JsonPrimitive(winnerTeam + 1))
                put("score", JsonPrimitive(teamScores[winnerTeam]))
            },
            kind = "big",
        )
    }

    private fun startNextHand(winnerTeam: Int, emissions: MutableList<Emission>) {
        val hakemTeam = teamOf(hakemSeat)
        if (winnerTeam != hakemTeam) {
            // باختِ تیمِ حاکم: حاکم ورق‌دهنده می‌شود و نفرِ بعدی حاکم.
            hakemSeat = nextSeat(hakemSeat)
        }
        handNumber++
        trickNumber = 1
        trick.clear()
        tricksWon.fill(0)
        hands.forEach { it.clear() }
        lastTrick = null
        trump = null
        stage = Stage.TRUMP
        phase = Phase.SETUP
        if (scriptedHandIndex < scriptedHands.size) scriptedHandIndex++
        prepareDeck()
        dealFirstRoundAndAnnounce(emissions)
    }

    // -------------------------------------------------------------------------
    // سؤال ۲: حرکت‌های مجاز
    // -------------------------------------------------------------------------

    override fun legalActions(player: PlayerId): List<String> {
        val seat = seatOf(player) ?: return emptyList()
        return when {
            phase == Phase.FINISHED -> emptyList()
            stage == Stage.TRUMP && seat == hakemSeat -> listOf(HokmModule.ACTION_DECLARE_TRUMP)
            stage == Stage.PLAYING && seat == turnSeat -> listOf(HokmModule.ACTION_PLAY_CARD)
            else -> emptyList()
        }
    }

    /**
     * کارت‌هایی که این بازیکن در همین لحظه مجاز است بازی کند.
     *
     * قاعده: اگر خالِ زمین را داری باید همان را بازی کنی؛ وگرنه هر کارتی مجاز است (از جمله حکم).
     * در حالتِ «حکم لازم» ([HokmLazimMode.OVERTRUMP]) اگر حکمی روی زمین افتاده باشد و بشود بالاتر
     * زد، فقط کارت‌هایی مجازند که آن حکم را ببرند.
     */
    /**
     * شناسهٔ بازیکنی که روی این صندلی نشسته است.
     *
     * با تعیینِ حاکم، صندلی‌ها بازچیده می‌شوند (حاکم همیشه صندلیِ ۰ می‌شود)، بنابراین
     * «شمارهٔ صندلی» با «شمارهٔ بازیکن در فهرستِ اولیه» یکی نیست؛ هر کسی که بخواهد برای یک
     * صندلی حرکت بفرستد باید شناسه را از اینجا بگیرد.
     */
    internal fun playerIdAt(seat: Int): PlayerId = seats[seat].id

    internal fun legalCardsFor(seat: Int): List<Card> {
        val hand = hands[seat]
        if (trick.isEmpty()) return hand.toList()
        val led = trick.first().card.suit
        val trumpSuit = trump ?: return hand.toList()

        val following = hand.filter { it.suit == led }
        if (following.isNotEmpty()) return following

        if (options.hokmLazim == HokmLazimMode.OVERTRUMP) {
            val playedTrumps = trick.map { it.card }.filter { it.suit == trumpSuit }
            if (playedTrumps.isNotEmpty()) {
                val highest = playedTrumps.maxOf { it.rank.order }
                val overTrumps = hand.filter { it.suit == trumpSuit && it.rank.order > highest }
                if (overTrumps.isNotEmpty()) return overTrumps
            }
        }
        return hand.toList()
    }

    private fun illegalMessage(seat: Int, card: Card): String {
        if (trick.isEmpty()) return "این کارت مجاز نیست"
        val led = trick.first().card.suit
        if (hands[seat].any { it.suit == led }) {
            return "شما ${led.faName} دارید؛ باید ${led.faName} بازی کنید"
        }
        val trumpSuit = trump
        if (options.hokmLazim == HokmLazimMode.OVERTRUMP && trumpSuit != null) {
            val playedTrumps = trick.map { it.card }.filter { it.suit == trumpSuit }
            if (playedTrumps.isNotEmpty()) {
                val highest = playedTrumps.maxOf { it.rank.order }
                if (hands[seat].any { it.suit == trumpSuit && it.rank.order > highest } &&
                    card.suit != trumpSuit
                ) {
                    return "حکم لازم است: باید با حکمِ بالاتر بزنی"
                }
            }
        }
        return "این کارت مجاز نیست"
    }

    // -------------------------------------------------------------------------
    // سؤال ۳: نمای عمومی و خصوصی
    // -------------------------------------------------------------------------

    override fun publicView(): JsonObject = view {
        put("game", JsonPrimitive(HokmModule.ID))
        put("phase", JsonPrimitive(phase.name.lowercase()))
        put("stage", JsonPrimitive(stage.key))
        put("version", JsonPrimitive(version))
        put("handNumber", JsonPrimitive(handNumber))
        put("trickNumber", JsonPrimitive(trickNumber))
        put("trump", trump?.let { JsonPrimitive(it.name) } ?: JsonNull)
        put("trumpFa", trump?.let { JsonPrimitive(it.faName) } ?: JsonNull)
        put("trumpSymbol", trump?.let { JsonPrimitive(it.symbol) } ?: JsonNull)
        put("turn", JsonPrimitive(seats[turnSeat].id.value))
        put("turnSeat", JsonPrimitive(turnSeat))
        put("leaderSeat", JsonPrimitive(leaderSeat))
        put("hakem", JsonPrimitive(seats[hakemSeat].id.value))
        put("hakemSeat", JsonPrimitive(hakemSeat))
        put("dealerSeat", JsonPrimitive(dealerSeat))
        put("seats", seatsJson())
        put("trick", trickJson())
        put("lastTrick", lastTrick?.let { resolvedTrickJson(it) } ?: JsonNull)
        put("tricks", jsonInts(tricksWon.toList()))
        put("scores", jsonInts(teamScores.toList()))
        put("cardsLeft", JsonArray((0 until PLAYERS).map { JsonPrimitive(hands[it].size) }))
        put("rules", rulesJson())
        put("lastHand", lastHand?.let { handSummaryJson(it) } ?: JsonNull)
        put("result", matchResult?.let { resultJson(it) } ?: JsonNull)
    }

    override fun privateView(player: PlayerId): JsonObject {
        val base = publicView().toMutableMap()
        val seat = seatOf(player)
        if (seat == null) return JsonObject(base)
        base["you"] = JsonPrimitive(seats[seat].id.value)
        base["youSeat"] = JsonPrimitive(seat)
        base["team"] = JsonPrimitive(teamOf(seat))
        base["isHakem"] = JsonPrimitive(seat == hakemSeat)
        base["isYourTurn"] = JsonPrimitive(seat == turnSeat && stage == Stage.PLAYING)
        // فقط دستِ خودِ بازیکن؛ کلیدِ hand هرگز در نمای عمومی ساخته نمی‌شود.
        base["hand"] = jsonStrings(hands[seat].map { it.code })
        base["legalCards"] = jsonStrings(
            if (stage == Stage.PLAYING && seat == turnSeat) {
                legalCardsFor(seat).map { it.code }
            } else {
                emptyList()
            },
        )
        base["legalActions"] = jsonStrings(legalActions(player))
        return JsonObject(base)
    }

    // -------------------------------------------------------------------------
    // سؤال‌های ۵ و ۷: نتیجه و وضعیت کامل
    // -------------------------------------------------------------------------

    override fun result(): MatchResult? = matchResult

    override fun snapshot(): JsonObject {
        val base = publicView().toMutableMap()
        base["hands"] = JsonObject(
            seats.associate { player ->
                player.id.value to jsonStrings(hands[player.seat].map { it.code })
            },
        )
        base["deckLeft"] = JsonPrimitive(deck.size - deckCursor)
        base["trumpCard"] = trump?.let { JsonPrimitive(it.name) } ?: JsonNull
        return JsonObject(base)
    }

    override fun drainPendingEmissions(): List<Emission> = drain()

    // -------------------------------------------------------------------------
    // سؤال ۶: ربات
    // -------------------------------------------------------------------------

    /** انتخابِ قطعیِ ربات برای صندلیِ [seat]. */
    internal fun botChoice(seat: Int): GameAction? {
        if (stage != Stage.PLAYING || seat != turnSeat) return null
        val legal = legalCardsFor(seat)
        if (legal.isEmpty()) return null
        val card = HokmBot.choose(
            BotView(
                hand = hands[seat].toList(),
                legal = legal,
                trick = trick.toList(),
                trump = trump,
                myTeam = teamOf(seat),
                tricksWon = tricksWon.copyOf(),
                position = trick.size,
            ),
        )
        return GameAction(
            type = HokmModule.ACTION_PLAY_CARD,
            payload = view { put("card", JsonPrimitive(card.code)) },
        )
    }

    /** انتخابِ ربات برای تعیینِ حکم (فقط وقتی ربات حاکم باشد). */
    internal fun botTrumpChoice(): GameAction? {
        if (stage != Stage.TRUMP) return null
        val suit = HokmBot.chooseTrump(hands[hakemSeat].toList())
        return GameAction(
            type = HokmModule.ACTION_DECLARE_TRUMP,
            payload = view { put("suit", JsonPrimitive(suit.name)) },
        )
    }

    /** حرکتِ بعدیِ ربات برای این بازیکن: تعیینِ حکم اگر حاکم است، وگرنه بازی کردن. */
    internal fun botActionFor(player: PlayerId): GameAction? {
        val seat = seatOf(player) ?: return null
        return when {
            stage == Stage.TRUMP && seat == hakemSeat -> botTrumpChoice()
            stage == Stage.PLAYING && seat == turnSeat -> botChoice(seat)
            else -> null
        }
    }

    // -------------------------------------------------------------------------
    // پخشِ کارت و setup
    // -------------------------------------------------------------------------

    private fun prepareDeck() {
        deck = config.rng.shuffled(Card.fullDeck())
        deckCursor = 0
    }

    private fun draw(): Card {
        if (deckCursor >= deck.size) {
            // در یک دستِ سالم هیچ‌وقت رخ نمی‌دهد؛ نگهبان برای جلوگیری از رفتارِ بی‌صدا.
            error("دستهٔ کارت تمام شد")
        }
        return deck[deckCursor++]
    }

    /** پخشِ کارت برای صندلیِ [seat] در دستِ جاری (از دستِ از پیش‌آماده اگر در تست باشیم). */
    private fun dealTo(seat: Int, count: Int) {
        val scripted = scriptedHands.getOrNull(scriptedHandIndex)
        if (scripted != null) {
            val target = scripted[seat]
            val already = hands[seat].size
            hands[seat].addAll(target.drop(already).take(count))
            return
        }
        repeat(count) { hands[seat].add(draw()) }
    }

    /** تعیینِ حاکم با رو کردنِ کارت: اولین آس = حاکم، دومین آس = یارِ حاکم. */
    private fun determineHakem(): HakemFlip {
        val shuffled = config.rng.shuffled(Card.fullDeck())
        var cursor = 0
        var pointer = 0
        val aces = mutableListOf<Int>()
        val revealed = mutableListOf<FlipCard>()

        while (aces.size < 2 && cursor < shuffled.size && pointer < FLIP_GUARD) {
            val playerIndex = pointer % PLAYERS
            pointer++
            if (playerIndex in aces) continue // حاکم دیگر کارتی نمی‌گیرد
            val card = shuffled[cursor++]
            revealed += FlipCard(playerIndex, card, card.rank == Rank.ACE)
            if (card.rank == Rank.ACE) aces += playerIndex
        }

        val hakemIndex = aces.getOrNull(0) ?: 0
        val partnerIndex = aces.getOrNull(1) ?: ((hakemIndex + 2) % PLAYERS)

        // کارت‌های رو شده به دسته برمی‌گردند و دسته دوباره بر زده می‌شود.
        val rest = shuffled.drop(cursor)
        val fullDeckAgain = rest + revealed.map { it.card }
        return HakemFlip(hakemIndex, partnerIndex, revealed, config.rng.shuffled(fullDeckAgain))
    }

    /** چیدمانِ صندلی‌ها: حاکم صندلیِ ۰، یارش صندلیِ ۲ (روبه‌رو)، بقیه به ترتیبِ نوبت. */
    private fun buildSeating(hakemIndex: Int, partnerIndex: Int): List<Player> {
        val others = (1 until PLAYERS)
            .map { (hakemIndex + it) % PLAYERS }
            .filter { it != partnerIndex }
        // بعد از تعیینِ حاکم، یارها باید روبه‌روی هم بنشینند؛ شمارهٔ صندلی و تیم اینجا تعیین می‌شود.
        val order = listOf(hakemIndex, others[0], partnerIndex, others[1])
        return order.mapIndexed { seat, playerIndex ->
            config.players[playerIndex].copy(seat = seat, team = teamOf(seat))
        }
    }

    private fun dealFirstRoundAndAnnounce(sink: MutableList<Emission>? = null) {
        val order = dealOrder()
        val partner = partnerOf(hakemSeat)
        for (seat in order) {
            if (seat == partner) continue // تا تعیینِ حکم، یارِ حاکم کارتی نمی‌گیرد
            dealTo(seat, CARDS_IN_FIRST_ROUND)
        }
        stage = Stage.TRUMP
        phase = Phase.SETUP

        val emissions = mutableListOf<Emission>()
        emissions += Emission.Public(
            type = HokmEvents.HAND_DEALT,
            payload = view {
                put("hand", JsonPrimitive(handNumber))
                put("partial", JsonPrimitive(true))
                put("counts", JsonArray((0 until PLAYERS).map { JsonPrimitive(hands[it].size) }))
            },
        )
        emissions += privateDealEmissions()
        emissions += Emission.Sound(HokmSounds.DEAL)

        if (sink == null) pendingEmissions += emissions else sink += emissions
    }

    /** بعد از تعیینِ حکم: دو دورِ ۴تایی برای همه و ۵ کارتِ عقب‌افتادهٔ یارِ حاکم. */
    private fun dealRestOfTheHand() {
        val order = dealOrder()
        val partner = partnerOf(hakemSeat)
        repeat(2) {
            for (seat in order) {
                dealTo(seat, CARDS_IN_LATER_ROUNDS)
            }
        }
        dealTo(partner, CARDS_IN_FIRST_ROUND)
    }

    private fun privateDealEmissions(): List<Emission> =
        seats.map { player -> Emission.Private(to = player.id, view = privateView(player.id)) }

    // -------------------------------------------------------------------------
    // ابزارها
    // -------------------------------------------------------------------------

    private fun emit(emission: Emission) {
        pendingEmissions += emission
    }

    private fun drain(): List<Emission> {
        if (pendingEmissions.isEmpty()) return emptyList()
        val copy = pendingEmissions.toList()
        pendingEmissions.clear()
        return copy
    }

    private fun reject(code: String, messageFa: String): ApplyResult =
        ApplyResult.Rejected(code, messageFa)

    private fun seatOf(player: PlayerId): Int? = seats.indexOfFirst { it.id == player }.takeIf { it >= 0 }

    private fun nextSeat(seat: Int): Int = (seat + 1) % PLAYERS

    private fun teamOf(seat: Int): Int = seat % TEAMS

    private fun partnerOf(seat: Int): Int = (seat + TEAMS) % PLAYERS

    private val dealerSeat: Int
        get() = (hakemSeat + PLAYERS - 1) % PLAYERS

    /** ترتیبِ پخش: از حاکم شروع می‌شود و به ورق‌دهنده ختم می‌شود. */
    private fun dealOrder(): List<Int> = (0 until PLAYERS).map { (hakemSeat + it) % PLAYERS }

    private fun buildResult(winnerTeam: Int): MatchResult {
        val standings = seats.sortedBy { teamOf(it.seat) }.map { player ->
            Standing(
                player = player.id,
                rank = if (teamOf(player.seat) == winnerTeam) 1 else 2,
                score = teamScores[teamOf(player.seat)],
            )
        }
        val teams = (0 until TEAMS).map { team ->
            TeamScore(
                team = team,
                rank = if (team == winnerTeam) 1 else 2,
                score = teamScores[team],
                members = seats.filter { teamOf(it.seat) == team }.map { it.id },
            )
        }.sortedBy { it.rank }
        return MatchResult(
            finishedAtMs = config.clock.nowMs(),
            standings = standings,
            teams = teams,
            summary = view {
                put("hands", JsonPrimitive(handNumber))
                put("targetPoints", JsonPrimitive(options.targetPoints))
                put("scores", jsonInts(teamScores.toList()))
                put("kots", JsonPrimitive(countKots()))
            },
        )
    }

    private fun countKots(): Int = kotCount

    private fun seatsJson(): JsonArray = JsonArray(
        seats.map { player ->
            buildJsonObject {
                put("seat", JsonPrimitive(player.seat))
                put("id", JsonPrimitive(player.id.value))
                put("name", JsonPrimitive(player.displayName))
                put("team", JsonPrimitive(teamOf(player.seat)))
                put("bot", JsonPrimitive(player.isBot))
                put("cards", JsonPrimitive(hands[player.seat].size))
            }
        },
    )

    private fun trickJson(): JsonArray = JsonArray(
        trick.map { play ->
            buildJsonObject {
                put("seat", JsonPrimitive(play.seat))
                put("player", JsonPrimitive(seats[play.seat].id.value))
                put("card", JsonPrimitive(play.card.code))
            }
        },
    )

    private fun resolvedTrickJson(resolved: ResolvedTrick): JsonObject = view {
        put("number", JsonPrimitive(resolved.number))
        put("winnerSeat", JsonPrimitive(resolved.winnerSeat))
        put("winner", JsonPrimitive(seats[resolved.winnerSeat].id.value))
        put("team", JsonPrimitive(resolved.team))
        put("ruffed", JsonPrimitive(resolved.ruffed))
        put("cards", JsonArray(
            resolved.plays.map { play ->
                buildJsonObject {
                    put("seat", JsonPrimitive(play.seat))
                    put("card", JsonPrimitive(play.card.code))
                }
            },
        ))
    }

    private fun handSummaryJson(summary: HandSummary): JsonObject = view {
        put("hand", JsonPrimitive(summary.number))
        put("winnerTeam", JsonPrimitive(summary.winnerTeam))
        put("loserTeam", JsonPrimitive(summary.loserTeam))
        put("tricks", jsonInts(summary.tricksWon.toList()))
        put("points", JsonPrimitive(summary.points))
        put("kot", JsonPrimitive(summary.isKot))
        put("hakemKoti", JsonPrimitive(summary.isHakemKoti))
    }

    private fun rulesJson(): JsonObject = view {
        put("targetPoints", JsonPrimitive(options.targetPoints))
        put("handEnd", JsonPrimitive(options.handEnd.key))
        put("handEndFa", JsonPrimitive(options.handEnd.faName))
        put("hokmLazim", JsonPrimitive(options.hokmLazim.key))
        put("hokmLazimFa", JsonPrimitive(options.hokmLazim.faName))
        put("kotHakemPoints", JsonPrimitive(options.kotHakemPoints))
        put("kotOpponentPoints", JsonPrimitive(options.kotOpponentPoints))
        put("turnSeconds", JsonPrimitive(options.turnSeconds))
        put("tricksToWinHand", JsonPrimitive(TRICKS_TO_WIN_HAND))
        put("tricksInHand", JsonPrimitive(TRICKS_IN_HAND))
    }

    private fun resultJson(result: MatchResult): JsonObject = view {
        put("winnerTeam", JsonPrimitive(result.winnerTeam ?: -1))
        put("scores", jsonInts(teamScores.toList()))
        put("hands", JsonPrimitive(handNumber))
        put("standings", JsonArray(
            result.standings.map { standing ->
                buildJsonObject {
                    put("player", JsonPrimitive(standing.player.value))
                    put("rank", JsonPrimitive(standing.rank))
                    put("score", JsonPrimitive(standing.score))
                }
            },
        ))
        put("teams", JsonArray(
            result.teams.orEmpty().map { team ->
                buildJsonObject {
                    put("team", JsonPrimitive(team.team))
                    put("rank", JsonPrimitive(team.rank))
                    put("score", JsonPrimitive(team.score))
                }
            },
        ))
    }

    private data class FlipCard(val playerIndex: Int, val card: Card, val isAce: Boolean)

    private data class HakemFlip(
        val hakemIndex: Int,
        val partnerIndex: Int,
        val revealed: List<FlipCard>,
        val deck: List<Card>,
    )

    companion object {
        const val PLAYERS = 4
        const val TEAMS = 2
        const val TRICKS_IN_HAND = 13
        const val TRICKS_TO_WIN_HAND = 7
        const val CARDS_IN_FIRST_ROUND = 5
        const val CARDS_IN_LATER_ROUNDS = 4

        /** سقفِ احتیاطی برای رو کردنِ کارت در تعیینِ حاکم. */
        private const val FLIP_GUARD = 500
    }
}
