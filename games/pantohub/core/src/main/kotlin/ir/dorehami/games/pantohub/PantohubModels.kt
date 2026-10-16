package ir.dorehami.games.pantohub

import kotlinx.serialization.Serializable

/** The three scoring levels used by the local pantomime word bank. */
@Serializable
enum class Difficulty(val labelFa: String, val defaultPoints: Int) {
    EASY("آسان", 1),
    MEDIUM("متوسط", 2),
    HARD("سخت", 3),
}

/** A single, reviewable word or phrase in the offline word bank. */
@Serializable
enum class PhraseType(val labelFa: String) {
    WORD("تک‌واژه"),
    PHRASE("عبارت"),
    IDIOM("اصطلاح"),
    PROVERB("ضرب‌المثل"),
    ACTION("فعالیت"),
    PERSON("شخصیت"),
    PLACE("مکان"),
}

/** Stable category identifiers; labels are kept in one place for the UI and imports. */
object WordCategories {
    const val ANIMALS = "animals"
    const val NATURE = "nature"
    const val HOME = "home"
    const val OBJECTS = "objects"
    const val FOOD = "food"
    const val JOBS = "jobs"
    const val SPORTS = "sports"
    const val ACTIONS = "actions"
    const val EMOTIONS = "emotions"
    const val PLACES = "places"
    const val TRANSPORT = "transport"
    const val CINEMA = "cinema"
    const val LITERATURE = "literature"
    const val IDIOMS = "idioms"
    const val TECHNOLOGY = "technology"
    const val SCHOOL = "school"
    const val TRAVEL = "travel"
    const val HUMOR = "humor"
    const val CULTURE = "culture"
    const val DAILY_LIFE = "daily_life"

    val labelsFa: Map<String, String> = linkedMapOf(
        ANIMALS to "حیوانات",
        NATURE to "طبیعت",
        HOME to "خانه",
        OBJECTS to "وسایل روزمره",
        FOOD to "خوراکی‌ها",
        JOBS to "مشاغل",
        SPORTS to "ورزش",
        ACTIONS to "فعالیت‌ها",
        EMOTIONS to "احساسات",
        PLACES to "مکان‌ها",
        TRANSPORT to "حمل‌ونقل",
        CINEMA to "سینما",
        LITERATURE to "ادبیات",
        IDIOMS to "اصطلاحات و ضرب‌المثل‌ها",
        TECHNOLOGY to "فناوری",
        SCHOOL to "مدرسه و علوم",
        TRAVEL to "سفر",
        HUMOR to "موقعیت‌های طنز",
        CULTURE to "فرهنگ عمومی",
        DAILY_LIFE to "زندگی روزمره",
    )

    fun labelFa(category: String): String = labelsFa[category] ?: category
}

@Serializable
data class WordRecord(
    val id: String,
    val text: String,
    val difficulty: Difficulty,
    val category: String,
    val type: PhraseType = PhraseType.WORD,
    val validated: Boolean = false,
    val active: Boolean = true,
    val sourceVersion: String = "",
)

@Serializable
data class Player(
    val id: String,
    val name: String,
)

@Serializable
data class Team(
    val id: String,
    val name: String,
    val colorArgb: Int,
    val players: List<Player>,
)

/** Optional one-use cards are intentionally small and have rules enforced by [PantohubEngine]. */
@Serializable
enum class SpecialCard(val titleFa: String, val descriptionFa: String) {
    DOUBLE_POINTS("امتیاز دوبرابر", "پاسخ درست بعدی این نوبت، دوبرابر امتیاز می‌گیرد."),
    FREE_SKIP("رد رایگان", "جریمهٔ رد کردن واژهٔ بعدی این نوبت حذف می‌شود."),
    EXTRA_TIME("۱۵ ثانیه فرصت", "۱۵ ثانیه به زمان همین نوبت اضافه می‌شود."),
}

/**
 * How a group match flows. SPEEDY keeps guessing words until the turn timer ends;
 * NORMAL ends the turn after one correct guess (skips keep the turn going).
 * [PantohubEngine] enforces the NORMAL turn end itself: a correct
 * [GameAction.ResolveWord] closes the turn and ignores any next word.
 * The per-turn difficulty/category choice of NORMAL is a UI concern.
 */
@Serializable
enum class PlayMode(val labelFa: String) {
    SPEEDY("سرعتی"),
    NORMAL("عادی"),
}

@Serializable
data class GameSettings(
    val playMode: PlayMode = PlayMode.SPEEDY,
    val turnSeconds: Int = 60,
    val roundsPerTeam: Int = 3,
    val allowedDifficulties: Set<Difficulty> = Difficulty.entries.toSet(),
    /** Empty means all active categories; no silent category fallback is performed. */
    val allowedCategories: Set<String> = emptySet(),
    val easyPoints: Int = 1,
    val mediumPoints: Int = 2,
    val hardPoints: Int = 3,
    val allowSkip: Boolean = true,
    val skipPenalty: Int = 0,
    val maxSkipsPerTurn: Int = 3,
    val cardsEnabled: Boolean = false,
    val finalRoundEnabled: Boolean = true,
    val finalRoundSeconds: Int = 60,
    val finalRoundMultiplier: Int = 2,
    val individualStatisticsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val animationsEnabled: Boolean = true,
) {
    fun pointsFor(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> easyPoints
        Difficulty.MEDIUM -> mediumPoints
        Difficulty.HARD -> hardPoints
    }
}

@Serializable
enum class GamePhase {
    READY,
    RUNNING,
    PAUSED,
    FINAL_READY,
    FINISHED,
}

@Serializable
enum class WordOutcome {
    CORRECT,
    SKIPPED,
    UNRESOLVED,
}

@Serializable
data class WordAttempt(
    val wordId: String,
    val text: String,
    val difficulty: Difficulty,
    val outcome: WordOutcome,
    val awardedPoints: Int,
    val atEpochMs: Long,
)

/** Monotonic time drives the timer; epoch time is only for dates and reports. */
data class GameMoment(
    val elapsedRealtimeMs: Long,
    val epochMs: Long,
)

@Serializable
data class TurnProgress(
    val teamId: String,
    val playerId: String,
    val playerName: String,
    val roundNumber: Int,
    val isFinalRound: Boolean,
    val startedAtElapsedMs: Long,
    val startedAtEpochMs: Long,
    val deadlineElapsedMs: Long? = null,
    val deadlineEpochMs: Long? = null,
    val remainingMs: Long,
    val currentWord: WordRecord? = null,
    val wordRevealed: Boolean = false,
    val skippedWords: Int = 0,
    val scoreEarned: Int = 0,
    val activeCards: Set<SpecialCard> = emptySet(),
    val attempts: List<WordAttempt> = emptyList(),
)

@Serializable
data class TurnRecord(
    val teamId: String,
    val playerId: String?,
    val playerName: String,
    val roundNumber: Int,
    val isFinalRound: Boolean,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long,
    val scoreEarned: Int,
    val attempts: List<WordAttempt>,
)

/** A complete, serializable local match snapshot. It contains private word data; never render it publicly. */
@Serializable
data class GameState(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val createdAtEpochMs: Long,
    val finishedAtEpochMs: Long? = null,
    val settings: GameSettings,
    val teams: List<Team>,
    val phase: GamePhase = GamePhase.READY,
    val nextTeamIndex: Int = 0,
    val completedTurnsByTeam: Map<String, Int> = emptyMap(),
    val nextPlayerIndexByTeam: Map<String, Int> = emptyMap(),
    val teamScores: Map<String, Int> = emptyMap(),
    val usedWordIds: Set<String> = emptySet(),
    val usedCardsByTeam: Map<String, Set<SpecialCard>> = emptyMap(),
    val currentTurn: TurnProgress? = null,
    val turns: List<TurnRecord> = emptyList(),
    val finalRoundTeamIds: List<String> = emptyList(),
    val finalRoundCursor: Int = 0,
    val winnerTeamIds: List<String> = emptyList(),
    val version: Long = 0,
)

/** Counts are derived from completed match events, never copied from a display counter. */
data class TeamStatistics(
    val name: String,
    val gamesPlayed: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val totalPoints: Int,
    val correctAnswers: Int,
    val turnsPlayed: Int,
    val easyCorrect: Int,
    val mediumCorrect: Int,
    val hardCorrect: Int,
) {
    val averagePointsPerGame: Double
        get() = if (gamesPlayed == 0) 0.0 else totalPoints.toDouble() / gamesPlayed
}

data class PlayerStatistics(
    val playerId: String,
    val name: String,
    val gamesPlayed: Int,
    val turnsPerformed: Int,
    val correctAnswers: Int,
    val wordsUsed: Int,
    val successfulTurns: Int,
    val pointsEarned: Int,
)
