package ir.dorehami.games.hokm.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.dorehami.games.hokm.HokmEvents
import ir.dorehami.games.hokm.HokmModule
import ir.dorehami.games.hokm.HokmOptions
import ir.dorehami.games.hokm.HokmSounds
import ir.dorehami.games.hokm.Suit
import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.Emission
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.gamesdk.Player
import ir.dorehami.gamesdk.PlayerId
import ir.dorehami.gamesdk.SeededRng
import ir.dorehami.gamesdk.SystemClock
import ir.dorehami.gamesdk.view
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * مغزِ صفحهٔ حکم: موتورِ قواعد را می‌چرخاند، نوبتِ ربات‌ها را مدیریت می‌کند و وضعیت را برای رابط
 * کاربری آماده می‌سازد.
 *
 * نکتهٔ مهمِ معماری: این کلاس **هیچ قاعده‌ای را خودش پیاده نمی‌کند**؛ فقط `GameAction` می‌فرستد و
 * `privateView` را می‌خواند. به همین دلیل فردا که اتاقِ بلوتوثی و آنلاین ساخته شوند، همین صفحه
 * بدون تغییر کار می‌کند و فقط منبعِ رویداد عوض می‌شود.
 */
class HokmViewModel(
    seed: Long,
    private val options: HokmOptions = HokmOptions.default(),
    playerName: String = "شما",
    /** مکثِ ربات پیش از هر حرکت (برای اینکه بازی انسانی به نظر برسد). */
    private val botDelayMs: Long = 700L,
) : ViewModel() {

    private val youId = PlayerId(YOU_ID)
    private var engine: GameEngine = createEngine(seed, options, playerName)

    private val _state = MutableStateFlow(parseHokmView(engine.privateView(youId)))
    val state: StateFlow<HokmUiState> = _state.asStateFlow()

    private val _sounds = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val sounds: SharedFlow<String> = _sounds.asSharedFlow()

    private var banner: UiBanner? = null
    private var toast: String? = null
    private var botJob: Job? = null
    private var timerJob: Job? = null
    private var bannerJob: Job? = null
    private var bannerKey = 0L

    init {
        // افکت‌های لحظهٔ ساخت (پخشِ کارتِ اولیه)
        engine.bootEmissions.forEach { emission ->
            if (emission is Emission.Sound) _sounds.tryEmit(emission.name)
        }
        scheduleBots()
        scheduleTurnTimer()
    }

    // -------------------------------------------------------------------------
    // ورودیِ کاربر
    // -------------------------------------------------------------------------

    /** بازی کردنِ یک کارت از دستِ بازیکنِ محلی. */
    fun playCard(code: String) {
        submit(youId, GameAction(HokmModule.ACTION_PLAY_CARD, view { put("card", JsonPrimitive(code)) }))
    }

    /** تعیینِ خالِ حکم توسط بازیکنِ محلی (فقط وقتی حاکم است). */
    fun declareTrump(suit: Suit) {
        submit(youId, GameAction(HokmModule.ACTION_DECLARE_TRUMP, view { put("suit", JsonPrimitive(suit.name)) }))
    }

    /** شروعِ یک بازیِ تازه با سیدِ جدید. */
    fun restart(newSeed: Long = nextSeed()) {
        botJob?.cancel()
        timerJob?.cancel()
        bannerJob?.cancel()
        banner = null
        toast = null
        engine = createEngine(newSeed, options, _state.value.seats.firstOrNull { it.id == YOU_ID }?.name ?: "شما")
        engine.bootEmissions.forEach { emission ->
            if (emission is Emission.Sound) _sounds.tryEmit(emission.name)
        }
        refresh()
        scheduleBots()
    }

    /** پاک کردنِ پیامِ خطا بعد از نمایش. */
    fun consumeToast() {
        toast = null
        refresh()
    }

    // -------------------------------------------------------------------------
    // حلقهٔ داخلی
    // -------------------------------------------------------------------------

    private fun submit(player: PlayerId, action: GameAction) {
        val result = engine.submit(player, action)
        when (result) {
            is ApplyResult.Ok -> {
                handleEmissions(result.emissions)
                refresh()
                scheduleBots()
                scheduleTurnTimer()
            }
            is ApplyResult.Rejected -> {
                toast = result.messageFa
                _sounds.tryEmit(HokmSounds.INVALID)
                refresh()
            }
        }
    }

    private fun handleEmissions(emissions: List<Emission>) {
        emissions.forEach { emission ->
            when (emission) {
                is Emission.Sound -> _sounds.tryEmit(emission.name)
                is Emission.Public -> if (emission.type == HokmEvents.HAND_SCORED) showHandBanner(emission.payload)
                is Emission.Feed -> Unit // متنِ فید در همین کلاس به بنر/تُست تبدیل می‌شود
                is Emission.Private -> Unit // دستِ هر بازیکن فقط برای خودش می‌رود
            }
        }
    }

    private fun showHandBanner(payload: JsonObject) {
        val winnerTeam = payload.int("winnerTeam") ?: return
        val points = payload.int("points") ?: 1
        val isKot = payload.bool("kot")
        val isHakemKoti = payload.bool("hakemKoti")
        val yourTeam = _state.value.yourTeam

        val title = when {
            isHakemKoti -> "حاکم‌کُتی!"
            isKot -> "کوت!"
            winnerTeam == yourTeam -> "این دست را بردید"
            else -> "این دست را باختید"
        }
        val subtitle = when {
            isHakemKoti -> "تیمِ ${persianDigits(winnerTeam + 1)} حاکم را کوت کرد — ${persianDigits(points)} امتیاز"
            isKot -> "تیمِ ${persianDigits(winnerTeam + 1)} هفتِ اول را بُرد — ${persianDigits(points)} امتیاز"
            else -> "تیمِ ${persianDigits(winnerTeam + 1)} برنده شد — ${persianDigits(points)} امتیاز"
        }
        val kind = if (winnerTeam == yourTeam) BannerKind.GOOD else BannerKind.BAD
        bannerKey++
        banner = UiBanner(
            title = title,
            subtitle = subtitle,
            kind = if (isKot) BannerKind.BIG else kind,
            key = bannerKey,
        )
        bannerJob?.cancel()
        bannerJob = viewModelScope.launch {
            delay(BANNER_MS)
            banner = null
            refresh()
        }
    }

    /** وقتی نوبتِ ربات است، خودش بازی می‌کند (با مکث تا بازی انسانی به نظر برسد). */
    private fun scheduleBots() {
        botJob?.cancel()
        botJob = viewModelScope.launch {
            while (true) {
                val current = _state.value
                if (current.phase == "finished") break
                val actorId = if (current.stage == "trump") current.hakemId else current.turnId
                if (actorId.isBlank() || actorId == YOU_ID) break
                delay(botDelayMs)
                val result = engine.submitBotTurn(PlayerId(actorId)) ?: break
                if (result is ApplyResult.Rejected) break
                handleEmissions((result as ApplyResult.Ok).emissions)
                refresh()
            }
            // حالا یا نوبتِ شماست یا بازی تمام شده؛ در حالتِ اول باید مهلتِ نوبت را شروع کنیم.
            scheduleTurnTimer()
        }
    }

    /**
     * اگر مهلتِ نوبت فعال باشد و نوبتِ شما باشد، با تمام شدنِ زمان خودش حرکت می‌کند
     * (انداختنِ یک کارت، یا تعیینِ حکم اگر حاکم باشید).
     */
    private fun scheduleTurnTimer() {
        timerJob?.cancel()
        val seconds = options.turnSeconds
        if (seconds <= 0) return
        val current = _state.value
        if (!current.isYourTurn && !current.youMustDeclareTrump) return
        timerJob = viewModelScope.launch {
            delay(seconds * 1000L)
            val now = _state.value
            if (!now.isYourTurn && !now.youMustDeclareTrump) return@launch
            val action = HokmModule.botAction?.invoke(engine.session, youId) ?: return@launch
            submit(youId, action)
        }
    }

    private fun refresh() {
        _state.value = parseHokmView(engine.privateView(youId)).copy(banner = banner, toast = toast)
    }

    private fun createEngine(seed: Long, options: HokmOptions, playerName: String): GameEngine {
        val config = GameConfig(
            matchId = "local-$seed",
            seed = seed,
            options = options.toJson(),
            players = listOf(
                Player(PlayerId(YOU_ID), playerName, 0, isBot = false),
                Player(PlayerId("bot-1"), "رضا", 1, isBot = true),
                Player(PlayerId("bot-2"), "مریم", 2, isBot = true),
                Player(PlayerId("bot-3"), "علی", 3, isBot = true),
            ),
            clock = SystemClock,
            rng = SeededRng(seed),
        )
        return GameEngine(HokmModule, config)
    }

    private fun nextSeed(): Long {
        // تصادفِ سیستم فقط برای انتخابِ سیدِ یک بازیِ تازه است — بیرون از قواعد بازی،
        // بنابراین قطعیتِ دست (که فقط از سید می‌آید) حفظ می‌شود.
        return kotlin.math.abs(System.currentTimeMillis() % 1_000_000L) + 1L
    }

    override fun onCleared() {
        botJob?.cancel()
        timerJob?.cancel()
        bannerJob?.cancel()
        super.onCleared()
    }

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

    private fun JsonObject.bool(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false

    private companion object {
        const val YOU_ID = "you"
        const val BANNER_MS = 2_200L
    }
}
