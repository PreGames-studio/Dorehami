package ir.dorehami.games.pantohub

/** Actions are versioned so a repeated/stale UI event cannot score the same word twice. */
sealed interface GameAction {
    val expectedVersion: Long

    data class StartTurn(
        override val expectedVersion: Long,
        val word: WordRecord,
        val moment: GameMoment,
    ) : GameAction

    data class RevealWord(
        override val expectedVersion: Long,
        val moment: GameMoment,
    ) : GameAction

    data class HideWord(
        override val expectedVersion: Long,
        val moment: GameMoment,
    ) : GameAction

    data class ResolveWord(
        override val expectedVersion: Long,
        val outcome: WordOutcome,
        /** Null means the filtered, unused bank is exhausted; filters are never changed silently. */
        val nextWord: WordRecord?,
        val moment: GameMoment,
    ) : GameAction

    data class PauseTurn(
        override val expectedVersion: Long,
        val moment: GameMoment,
    ) : GameAction

    data class ResumeTurn(
        override val expectedVersion: Long,
        val moment: GameMoment,
    ) : GameAction

    data class UseCard(
        override val expectedVersion: Long,
        val card: SpecialCard,
        val moment: GameMoment,
    ) : GameAction

    data class EndTurn(
        override val expectedVersion: Long,
        val moment: GameMoment,
    ) : GameAction

    data class Tick(
        override val expectedVersion: Long,
        val moment: GameMoment,
    ) : GameAction

    data class EndGame(
        override val expectedVersion: Long,
        val moment: GameMoment,
    ) : GameAction
}

sealed interface GameActionResult {
    data class Accepted(val state: GameState) : GameActionResult
    data class Rejected(val messageFa: String) : GameActionResult
}

/** Input for creating a match. IDs and both clocks are supplied by the caller. */
data class NewGameRequest(
    val id: String,
    val name: String,
    val createdAtEpochMs: Long,
    val teams: List<Team>,
    val settings: GameSettings,
)

/** Pure, deterministic rules for the local team game. No system clock, I/O, or random source is used. */
object PantohubEngine {
    private const val EXTRA_TIME_MS = 15_000L

    fun createGame(request: NewGameRequest): GameActionResult {
        val validation = validateRequest(request)
        if (validation != null) return GameActionResult.Rejected(validation)

        val teams = request.teams.map { team ->
            team.copy(
                name = team.name.trim(),
                players = team.players.map { it.copy(name = it.name.trim()) },
            )
        }
        val initialScore = teams.associate { it.id to 0 }
        return GameActionResult.Accepted(
            GameState(
                id = request.id,
                name = request.name.trim().ifEmpty { "پانتومیم" },
                createdAtEpochMs = request.createdAtEpochMs,
                settings = request.settings,
                teams = teams,
                completedTurnsByTeam = teams.associate { it.id to 0 },
                nextPlayerIndexByTeam = teams.associate { it.id to 0 },
                teamScores = initialScore,
                usedCardsByTeam = teams.associate { it.id to emptySet() },
            ),
        )
    }

    fun apply(state: GameState, action: GameAction): GameActionResult {
        if (state.phase == GamePhase.FINISHED) {
            return GameActionResult.Rejected("این بازی پایان یافته است.")
        }
        if (action.expectedVersion != state.version) {
            return GameActionResult.Rejected("این فرمان مربوط به وضعیت قبلی بازی است؛ دوباره تلاش کن.")
        }

        val moment = action.momentOrNull()
        if (state.phase == GamePhase.RUNNING && moment != null && action !is GameAction.Tick && action !is GameAction.EndGame) {
            if (remainingMs(state, moment) == 0L) {
                return GameActionResult.Accepted(
                    closeTurn(state, moment).copy(version = state.version + 1),
                )
            }
        }

        if (action is GameAction.Tick) {
            if (state.phase != GamePhase.RUNNING || moment == null || remainingMs(state, moment) > 0L) {
                return GameActionResult.Accepted(state)
            }
            return GameActionResult.Accepted(closeTurn(state, moment).copy(version = state.version + 1))
        }

        val changed = when (action) {
            is GameAction.StartTurn -> startTurn(state, action)
            is GameAction.RevealWord -> revealWord(state)
            is GameAction.HideWord -> hideWord(state)
            is GameAction.ResolveWord -> resolveWord(state, action)
            is GameAction.PauseTurn -> pauseTurn(state, action.moment)
            is GameAction.ResumeTurn -> resumeTurn(state, action.moment)
            is GameAction.UseCard -> useCard(state, action)
            is GameAction.EndTurn -> endTurn(state, action.moment)
            is GameAction.EndGame -> endGame(state, action.moment)
            // Tick is returned above; keep this unreachable branch typed as InternalResult.
            is GameAction.Tick -> InternalResult.Success(state)
        }
        return when (changed) {
            is InternalResult.Error -> GameActionResult.Rejected(changed.messageFa)
            is InternalResult.Success -> GameActionResult.Accepted(changed.state.copy(version = state.version + 1))
        }
    }

    /**
     * Process-restart recovery is fail-safe: an in-progress turn is paused and any revealed word is
     * covered before the shared-device screen is drawn. Expired turns close through the normal reducer.
     */
    fun recoverAfterProcessRestart(state: GameState, moment: GameMoment): GameState {
        if (state.phase != GamePhase.RUNNING) return state
        return when (val result = apply(state, GameAction.PauseTurn(state.version, moment))) {
            is GameActionResult.Accepted -> result.state
            is GameActionResult.Rejected -> {
                val turn = state.currentTurn ?: return state.copy(phase = GamePhase.PAUSED, version = state.version + 1)
                val remaining = remainingMs(state, moment)
                if (remaining == 0L) {
                    closeTurn(state, moment).copy(version = state.version + 1)
                } else {
                    state.copy(
                        phase = GamePhase.PAUSED,
                        currentTurn = turn.copy(
                            remainingMs = remaining,
                            deadlineElapsedMs = null,
                            deadlineEpochMs = null,
                            wordRevealed = false,
                        ),
                        version = state.version + 1,
                    )
                }
            }
        }
    }

    /** Remaining duration is based on monotonic time. Epoch time is only a reboot recovery fallback. */
    fun remainingMs(state: GameState, moment: GameMoment): Long {
        val turn = state.currentTurn ?: return 0L
        if (state.phase == GamePhase.PAUSED) return turn.remainingMs.coerceAtLeast(0L)
        if (state.phase != GamePhase.RUNNING) return 0L
        val elapsedDeadline = turn.deadlineElapsedMs ?: return turn.remainingMs.coerceAtLeast(0L)
        val epochDeadline = turn.deadlineEpochMs ?: return (elapsedDeadline - moment.elapsedRealtimeMs).coerceAtLeast(0L)
        val left = if (moment.elapsedRealtimeMs >= turn.startedAtElapsedMs) {
            elapsedDeadline - moment.elapsedRealtimeMs
        } else {
            // elapsedRealtime resets at device reboot; epoch fallback avoids granting a fresh full turn.
            epochDeadline - moment.epochMs
        }
        return left.coerceAtLeast(0L)
    }

    fun currentTeam(state: GameState): Team? {
        val teamId = when (state.phase) {
            GamePhase.FINAL_READY -> state.finalRoundTeamIds.getOrNull(state.finalRoundCursor)
            GamePhase.READY -> state.teams.getOrNull(state.nextTeamIndex)?.id
            else -> state.currentTurn?.teamId
        }
        return state.teams.firstOrNull { it.id == teamId }
    }

    fun currentPlayer(state: GameState): Player? {
        val team = currentTeam(state) ?: return null
        if (team.players.isEmpty()) return null
        val activeId = state.currentTurn?.playerId
        if (activeId != null) return team.players.firstOrNull { it.id == activeId }
        val index = state.nextPlayerIndexByTeam[team.id] ?: 0
        return team.players.getOrNull(index.mod(team.players.size))
    }

    /**
     * True when [after] — the state accepted for a correct-guess [GameAction.ResolveWord] on
     * [before] — really counted [before]'s word as guessed, rather than closing an expired turn.
     */
    fun scoredCorrect(before: GameState, after: GameState): Boolean {
        val word = before.currentTurn?.currentWord ?: return false
        if (after.id != before.id || after.version != before.version + 1) return false
        val attempts = after.currentTurn?.attempts ?: after.turns.lastOrNull()?.attempts ?: return false
        val last = attempts.lastOrNull() ?: return false
        return last.wordId == word.id && last.outcome == WordOutcome.CORRECT
    }

    private fun startTurn(state: GameState, action: GameAction.StartTurn): InternalResult {
        if (state.phase != GamePhase.READY && state.phase != GamePhase.FINAL_READY) {
            return InternalResult.Error("اکنون زمان شروع نوبت نیست.")
        }
        val wordError = validateChosenWord(state, action.word, state.usedWordIds)
        if (wordError != null) return InternalResult.Error(wordError)
        val team = currentTeam(state) ?: return InternalResult.Error("تیم نوبت بعد پیدا نشد.")
        val player = currentPlayer(state) ?: return InternalResult.Error("برای این تیم بازیکنی ثبت نشده است.")
        val isFinal = state.phase == GamePhase.FINAL_READY
        val durationMs = (if (isFinal) state.settings.finalRoundSeconds else state.settings.turnSeconds) * 1_000L
        val turn = TurnProgress(
            teamId = team.id,
            playerId = player.id,
            playerName = player.name,
            roundNumber = (state.completedTurnsByTeam[team.id] ?: 0) + 1,
            isFinalRound = isFinal,
            startedAtElapsedMs = action.moment.elapsedRealtimeMs,
            startedAtEpochMs = action.moment.epochMs,
            deadlineElapsedMs = action.moment.elapsedRealtimeMs + durationMs,
            deadlineEpochMs = action.moment.epochMs + durationMs,
            remainingMs = durationMs,
            currentWord = action.word,
        )
        return InternalResult.Success(
            state.copy(
                phase = GamePhase.RUNNING,
                currentTurn = turn,
                usedWordIds = state.usedWordIds + action.word.id,
            ),
        )
    }

    private fun revealWord(state: GameState): InternalResult {
        if (state.phase != GamePhase.RUNNING) return InternalResult.Error("نوبت در حال اجرا نیست.")
        val turn = state.currentTurn ?: return InternalResult.Error("نوبت فعالی پیدا نشد.")
        if (turn.currentWord == null) return InternalResult.Error("واژه‌ای برای نمایش باقی نمانده است.")
        if (turn.wordRevealed) return InternalResult.Error("این واژه از قبل نمایش داده شده است.")
        return InternalResult.Success(state.copy(currentTurn = turn.copy(wordRevealed = true, wordSeen = true)))
    }

    private fun hideWord(state: GameState): InternalResult {
        if (state.phase != GamePhase.RUNNING) return InternalResult.Error("نوبت در حال اجرا نیست.")
        val turn = state.currentTurn ?: return InternalResult.Error("نوبت فعالی پیدا نشد.")
        if (!turn.wordRevealed) return InternalResult.Error("واژه از قبل پوشیده است.")
        return InternalResult.Success(state.copy(currentTurn = turn.copy(wordRevealed = false)))
    }

    private fun resolveWord(state: GameState, action: GameAction.ResolveWord): InternalResult {
        if (state.phase != GamePhase.RUNNING) return InternalResult.Error("نوبت در حال اجرا نیست.")
        val turn = state.currentTurn ?: return InternalResult.Error("نوبت فعالی پیدا نشد.")
        val word = turn.currentWord ?: return InternalResult.Error("واژهٔ فعالی وجود ندارد؛ نوبت را تمام کن.")
        if (!turn.wordRevealed && !turn.wordSeen) return InternalResult.Error("اول واژه را به اجراکننده نشان بده.")

        if (action.outcome == WordOutcome.UNRESOLVED) {
            return InternalResult.Error("نتیجهٔ واژه را درست یا ردشده ثبت کن.")
        }
        if (action.outcome == WordOutcome.SKIPPED) {
            if (!state.settings.allowSkip) return InternalResult.Error("رد کردن واژه در قوانین این بازی غیرفعال است.")
            if (turn.skippedWords >= state.settings.maxSkipsPerTurn) {
                return InternalResult.Error("سهمیهٔ رد کردن واژه در این نوبت تمام شده است.")
            }
        }

        val endsTurn = action.outcome == WordOutcome.CORRECT && state.settings.playMode == PlayMode.NORMAL
        val nextWord = if (endsTurn) null else action.nextWord
        if (nextWord != null) {
            val error = validateChosenWord(state, nextWord, state.usedWordIds)
            if (error != null) return InternalResult.Error(error)
            if (nextWord.id == word.id) return InternalResult.Error("واژهٔ بعدی تکراری است.")
        }

        val freeSkip = action.outcome == WordOutcome.SKIPPED && SpecialCard.FREE_SKIP in turn.activeCards
        val awarded = when (action.outcome) {
            WordOutcome.CORRECT -> {
                val base = state.settings.pointsFor(word.difficulty) *
                    if (turn.isFinalRound) state.settings.finalRoundMultiplier else 1
                base * if (SpecialCard.DOUBLE_POINTS in turn.activeCards) 2 else 1
            }
            WordOutcome.SKIPPED -> if (freeSkip) 0 else -state.settings.skipPenalty
            WordOutcome.UNRESOLVED -> 0
        }
        val attempt = WordAttempt(
            wordId = word.id,
            text = word.text,
            difficulty = word.difficulty,
            outcome = action.outcome,
            awardedPoints = awarded,
            atEpochMs = action.moment.epochMs,
        )
        val cardsAfterUse = when {
            action.outcome == WordOutcome.CORRECT -> turn.activeCards - SpecialCard.DOUBLE_POINTS
            freeSkip -> turn.activeCards - SpecialCard.FREE_SKIP
            else -> turn.activeCards
        }
        val score = (state.teamScores[turn.teamId] ?: 0) + awarded
        val updatedTurn = turn.copy(
            currentWord = nextWord,
            wordRevealed = false,
            wordSeen = false,
            skippedWords = turn.skippedWords + if (action.outcome == WordOutcome.SKIPPED) 1 else 0,
            scoreEarned = turn.scoreEarned + awarded,
            activeCards = cardsAfterUse,
            attempts = turn.attempts + attempt,
        )
        val resolved = state.copy(
            currentTurn = updatedTurn,
            teamScores = state.teamScores + (turn.teamId to score),
            usedWordIds = if (nextWord == null) state.usedWordIds else state.usedWordIds + nextWord.id,
        )
        return InternalResult.Success(if (endsTurn) closeTurn(resolved, action.moment) else resolved)
    }

    private fun pauseTurn(state: GameState, moment: GameMoment): InternalResult {
        if (state.phase != GamePhase.RUNNING) return InternalResult.Error("نوبتی برای توقف وجود ندارد.")
        val turn = state.currentTurn ?: return InternalResult.Error("نوبت فعالی پیدا نشد.")
        val remaining = remainingMs(state, moment)
        if (remaining == 0L) return InternalResult.Success(closeTurn(state, moment))
        return InternalResult.Success(
            state.copy(
                phase = GamePhase.PAUSED,
                currentTurn = turn.copy(
                    remainingMs = remaining,
                    deadlineElapsedMs = null,
                    deadlineEpochMs = null,
                    wordRevealed = false,
                ),
            ),
        )
    }

    private fun resumeTurn(state: GameState, moment: GameMoment): InternalResult {
        if (state.phase != GamePhase.PAUSED) return InternalResult.Error("بازی متوقف نشده است.")
        val turn = state.currentTurn ?: return InternalResult.Error("نوبت فعالی پیدا نشد.")
        if (turn.remainingMs <= 0L) return InternalResult.Success(closeTurn(state, moment))
        return InternalResult.Success(
            state.copy(
                phase = GamePhase.RUNNING,
                currentTurn = turn.copy(
                    startedAtElapsedMs = moment.elapsedRealtimeMs,
                    deadlineElapsedMs = moment.elapsedRealtimeMs + turn.remainingMs,
                    deadlineEpochMs = moment.epochMs + turn.remainingMs,
                ),
            ),
        )
    }

    private fun useCard(state: GameState, action: GameAction.UseCard): InternalResult {
        if (!state.settings.cardsEnabled) return InternalResult.Error("کارت‌های ویژه در این بازی خاموش‌اند.")
        if (state.phase != GamePhase.RUNNING) return InternalResult.Error("کارت فقط هنگام اجرای نوبت قابل استفاده است.")
        val turn = state.currentTurn ?: return InternalResult.Error("نوبت فعالی پیدا نشد.")
        val used = state.usedCardsByTeam[turn.teamId].orEmpty()
        if (action.card in used) return InternalResult.Error("این تیم قبلاً از این کارت استفاده کرده است.")
        if (action.card in turn.activeCards) return InternalResult.Error("این کارت از قبل برای نوبت فعال است.")

        val updatedTurn = when (action.card) {
            SpecialCard.DOUBLE_POINTS,
            SpecialCard.FREE_SKIP -> turn.copy(activeCards = turn.activeCards + action.card)

            SpecialCard.EXTRA_TIME -> {
                val deadlineElapsed = turn.deadlineElapsedMs ?: return InternalResult.Error("زمان‌سنج نوبت فعال نیست.")
                val deadlineEpoch = turn.deadlineEpochMs ?: return InternalResult.Error("زمان‌سنج نوبت فعال نیست.")
                turn.copy(
                    deadlineElapsedMs = deadlineElapsed + EXTRA_TIME_MS,
                    deadlineEpochMs = deadlineEpoch + EXTRA_TIME_MS,
                    remainingMs = remainingMs(state, action.moment) + EXTRA_TIME_MS,
                )
            }
        }
        return InternalResult.Success(
            state.copy(
                currentTurn = updatedTurn,
                usedCardsByTeam = state.usedCardsByTeam + (turn.teamId to (used + action.card)),
            ),
        )
    }

    private fun endTurn(state: GameState, moment: GameMoment): InternalResult {
        if (state.phase != GamePhase.RUNNING && state.phase != GamePhase.PAUSED) {
            return InternalResult.Error("نوبتی برای پایان دادن وجود ندارد.")
        }
        if (state.currentTurn == null) return InternalResult.Error("نوبت فعالی پیدا نشد.")
        return InternalResult.Success(closeTurn(state, moment))
    }

    private fun endGame(state: GameState, moment: GameMoment): InternalResult {
        val afterClosing = if (state.currentTurn != null) closeTurn(state, moment) else state
        return InternalResult.Success(finishGame(afterClosing, moment.epochMs))
    }

    private fun closeTurn(state: GameState, moment: GameMoment): GameState {
        val turn = state.currentTurn ?: return state
        val unresolved = turn.currentWord?.let { word ->
            WordAttempt(
                wordId = word.id,
                text = word.text,
                difficulty = word.difficulty,
                outcome = WordOutcome.UNRESOLVED,
                awardedPoints = 0,
                atEpochMs = moment.epochMs,
            )
        }
        val attempts = if (unresolved == null) turn.attempts else turn.attempts + unresolved
        val record = TurnRecord(
            teamId = turn.teamId,
            playerId = turn.playerId.takeIf { state.settings.individualStatisticsEnabled },
            playerName = turn.playerName.takeIf { state.settings.individualStatisticsEnabled } ?: "بدون آمار فردی",
            roundNumber = turn.roundNumber,
            isFinalRound = turn.isFinalRound,
            startedAtEpochMs = turn.startedAtEpochMs,
            endedAtEpochMs = moment.epochMs,
            scoreEarned = turn.scoreEarned,
            attempts = attempts,
        )
        val team = state.teams.firstOrNull { it.id == turn.teamId }
        val completedTurns = state.completedTurnsByTeam +
            (turn.teamId to ((state.completedTurnsByTeam[turn.teamId] ?: 0) + if (turn.isFinalRound) 0 else 1))
        val nextPlayerIndex = if (team == null || team.players.isEmpty()) {
            state.nextPlayerIndexByTeam
        } else {
            state.nextPlayerIndexByTeam + (turn.teamId to ((team.players.indexOfFirst { it.id == turn.playerId } + 1)
                .mod(team.players.size)))
        }
        val closed = state.copy(
            currentTurn = null,
            completedTurnsByTeam = completedTurns,
            nextPlayerIndexByTeam = nextPlayerIndex,
            turns = state.turns + record,
        )

        return if (turn.isFinalRound) {
            finishFinalRoundTurn(closed, moment.epochMs)
        } else {
            advanceRegularTurn(closed, turn.teamId, moment.epochMs)
        }
    }

    private fun advanceRegularTurn(state: GameState, finishedTeamId: String, epochMs: Long): GameState {
        val allRegularTurnsDone = state.teams.all {
            (state.completedTurnsByTeam[it.id] ?: 0) >= state.settings.roundsPerTeam
        }
        if (allRegularTurnsDone) {
            val leaders = leadingTeamIds(state)
            if (state.settings.finalRoundEnabled && leaders.size > 1) {
                val firstIndex = state.teams.indexOfFirst { it.id == leaders.first() }.coerceAtLeast(0)
                return state.copy(
                    phase = GamePhase.FINAL_READY,
                    finalRoundTeamIds = leaders,
                    finalRoundCursor = 0,
                    nextTeamIndex = firstIndex,
                )
            }
            return finishGame(state, epochMs)
        }

        val currentIndex = state.teams.indexOfFirst { it.id == finishedTeamId }.coerceAtLeast(0)
        val nextIndex = (1..state.teams.size)
            .asSequence()
            .map { (currentIndex + it) % state.teams.size }
            .firstOrNull { index ->
                val candidateId = state.teams[index].id
                (state.completedTurnsByTeam[candidateId] ?: 0) < state.settings.roundsPerTeam
            } ?: currentIndex
        return state.copy(phase = GamePhase.READY, nextTeamIndex = nextIndex)
    }

    private fun finishFinalRoundTurn(state: GameState, epochMs: Long): GameState {
        val nextCursor = state.finalRoundCursor + 1
        if (nextCursor >= state.finalRoundTeamIds.size) return finishGame(state, epochMs)
        val nextId = state.finalRoundTeamIds[nextCursor]
        val nextIndex = state.teams.indexOfFirst { it.id == nextId }.coerceAtLeast(0)
        return state.copy(
            phase = GamePhase.FINAL_READY,
            finalRoundCursor = nextCursor,
            nextTeamIndex = nextIndex,
        )
    }

    private fun finishGame(state: GameState, epochMs: Long): GameState = state.copy(
        phase = GamePhase.FINISHED,
        currentTurn = null,
        finishedAtEpochMs = epochMs,
        winnerTeamIds = leadingTeamIds(state),
    )

    private fun leadingTeamIds(state: GameState): List<String> {
        val highest = state.teams.maxOfOrNull { state.teamScores[it.id] ?: 0 } ?: return emptyList()
        return state.teams.filter { (state.teamScores[it.id] ?: 0) == highest }.map { it.id }
    }

    private fun validateRequest(request: NewGameRequest): String? {
        if (request.id.isBlank()) return "شناسهٔ بازی نامعتبر است."
        if (request.teams.size < 2) return "برای شروع بازی دست‌کم دو تیم لازم است."
        if (request.teams.map { it.id }.toSet().size != request.teams.size) return "شناسهٔ تیم‌ها باید یکتا باشد."
        if (request.teams.any { it.name.isBlank() }) return "نام همهٔ تیم‌ها را وارد کن."
        if (request.teams.any { it.players.isEmpty() }) return "هر تیم باید دست‌کم یک بازیکن داشته باشد."
        if (request.teams.any { team -> team.players.any { it.id.isBlank() || it.name.isBlank() } }) {
            return "نام و شناسهٔ همهٔ بازیکنان را وارد کن."
        }
        val playerIds = request.teams.flatMap { it.players }.map { it.id }
        if (playerIds.toSet().size != playerIds.size) return "شناسهٔ بازیکن تکراری است؛ هر جایگاه بازیکن باید شناسهٔ جداگانه داشته باشد."
        if (request.settings.turnSeconds !in 15..600) return "زمان هر نوبت باید بین ۱۵ ثانیه و ۱۰ دقیقه باشد."
        if (request.settings.finalRoundSeconds !in 15..600) return "زمان دور پایانی باید بین ۱۵ ثانیه و ۱۰ دقیقه باشد."
        if (request.settings.roundsPerTeam !in 1..100) return "تعداد نوبت‌های هر تیم باید دست‌کم ۱ باشد."
        if (request.settings.allowedDifficulties.isEmpty()) return "حداقل یک سطح دشواری را انتخاب کن."
        if (request.settings.easyPoints !in 1..100 || request.settings.mediumPoints !in 1..100 || request.settings.hardPoints !in 1..100) {
            return "امتیاز هر سطح باید بین ۱ تا ۱۰۰ باشد."
        }
        if (request.settings.skipPenalty !in 0..100 || request.settings.maxSkipsPerTurn !in 0..100) {
            return "جریمه و سقف رد کردن باید در بازهٔ منطقی باشند."
        }
        if (request.settings.finalRoundMultiplier !in 1..10) return "ضریب دور پایانی باید بین ۱ و ۱۰ باشد."
        return null
    }

    private fun validateChosenWord(state: GameState, word: WordRecord, usedIds: Set<String>): String? {
        if (word.id.isBlank() || !word.active || !word.validated) return "این واژه فعال و تأییدشده نیست."
        if (word.id in usedIds) return "این واژه قبلاً در همین بازی استفاده شده است."
        if (word.difficulty !in state.settings.allowedDifficulties) return "سطح دشواری این واژه در قوانین بازی انتخاب نشده است."
        if (state.settings.allowedCategories.isNotEmpty() && word.category !in state.settings.allowedCategories) {
            return "دستهٔ موضوعی این واژه در قوانین بازی انتخاب نشده است."
        }
        if (WordBankRules.validate(word).isNotEmpty()) return "اطلاعات این واژه معتبر نیست."
        return null
    }

    private fun GameAction.momentOrNull(): GameMoment? = when (this) {
        is GameAction.StartTurn -> moment
        is GameAction.ResolveWord -> moment
        is GameAction.PauseTurn -> moment
        is GameAction.ResumeTurn -> moment
        is GameAction.UseCard -> moment
        is GameAction.EndTurn -> moment
        is GameAction.Tick -> moment
        is GameAction.EndGame -> moment
        is GameAction.RevealWord -> moment
        is GameAction.HideWord -> moment
    }

    private sealed interface InternalResult {
        data class Success(val state: GameState) : InternalResult
        data class Error(val messageFa: String) : InternalResult
    }
}
