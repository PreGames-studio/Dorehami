package ir.dorehami.games.pantohub.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ir.dorehami.games.pantohub.GameAction
import ir.dorehami.games.pantohub.GameActionResult
import ir.dorehami.games.pantohub.GameMoment
import ir.dorehami.games.pantohub.Difficulty
import ir.dorehami.games.pantohub.GamePhase
import ir.dorehami.games.pantohub.GameSettings
import ir.dorehami.games.pantohub.PlayMode
import ir.dorehami.games.pantohub.WordCategories
import ir.dorehami.games.pantohub.GameState
import ir.dorehami.games.pantohub.PantohubEngine
import ir.dorehami.games.pantohub.SpecialCard
import ir.dorehami.games.pantohub.TurnProgress
import ir.dorehami.games.pantohub.WordOutcome
import ir.dorehami.games.pantohub.WordRecord
import ir.dorehami.games.pantohub.WordSelectionResult
import ir.dorehami.games.pantohub.ui.data.PantohubRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private fun gameMoment() = GameMoment(
    elapsedRealtimeMs = SystemClock.elapsedRealtime(),
    epochMs = System.currentTimeMillis(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PantohubGameScreen(
    state: GameState,
    repository: PantohubRepository,
    storageError: String?,
    onStateChange: (GameState) -> Unit,
    onBack: () -> Unit,
    onReturnHome: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 65) }.getOrNull() }
    val latestState by rememberUpdatedState(state)
    val latestOnStateChange by rememberUpdatedState(onStateChange)
    var now by remember { mutableStateOf(gameMoment()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showEndGameDialog by remember { mutableStateOf(false) }

    // The newest state this screen produced. The parent hands a new state back only on the next
    // recomposition, so until then [latestState] is the snapshot from before an action accepted a
    // moment ago — a follow-up action built on it was rejected as stale (PH-B01). Every action is
    // therefore built on [freshState], which is never older than what this screen last produced.
    val produced = remember { NewestState() }
    fun freshState(): GameState {
        val fromParent = latestState
        val mine = produced.value
        return if (mine != null && mine.id == fromParent.id && mine.version > fromParent.version) mine else fromParent
    }

    DisposableEffect(tone) {
        onDispose { tone?.release() }
    }

    fun dispatch(action: GameAction): GameState? {
        val before = freshState()
        return when (val result = PantohubEngine.apply(before, action)) {
            is GameActionResult.Rejected -> {
                errorMessage = result.messageFa
                null
            }
            is GameActionResult.Accepted -> {
                errorMessage = null
                if (result.state.version != before.version) {
                    produced.value = result.state
                    latestOnStateChange(result.state)
                }
                result.state
            }
        }
    }

    fun leaveAndPause() {
        val current = freshState()
        if (current.phase == GamePhase.RUNNING) {
            dispatch(GameAction.PauseTurn(current.version, gameMoment()))
        }
        onBack()
    }

    BackHandler(enabled = true) { leaveAndPause() }

    // PH-B04 (ADR-0006 §4): leaving the app — home button, a phone call, another app, the power
    // button — pauses a running turn instead of letting its clock burn in the background. The
    // engine's pause also covers the performer's word, so nobody sees it when the phone comes back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val current = freshState()
                if (current.phase == GamePhase.RUNNING) {
                    dispatch(GameAction.PauseTurn(current.version, gameMoment()))
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // PH-B02: nobody touches the phone while the performer acts, so the screen must not time out
    // in the middle of a turn. Only while the turn runs: paused and between-turn screens may still
    // sleep as usual.
    val window = LocalContext.current.findActivity()?.window
    val keepScreenOn = state.phase == GamePhase.RUNNING
    DisposableEffect(window, keepScreenOn) {
        val alreadyOn = window != null && (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        if (keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (keepScreenOn && !alreadyOn) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(state.id) {
        while (isActive) {
            val moment = gameMoment()
            now = moment
            val current = freshState()
            if (current.phase == GamePhase.RUNNING) {
                val result = PantohubEngine.apply(current, GameAction.Tick(current.version, moment))
                if (result is GameActionResult.Accepted && result.state.version != current.version) {
                    produced.value = result.state
                    // Time ran out and the turn closed: a message about that turn must not follow
                    // the phone to the next team's screen (PH-B06).
                    errorMessage = null
                    latestOnStateChange(result.state)
                }
            }
            delay(200)
        }
    }

    // NORMAL mode: the acting player picks this turn's difficulty and category first.
    var turnDifficulty by remember(state.id) { mutableStateOf(Difficulty.MEDIUM) }
    var turnCategory by remember(state.id) { mutableStateOf<String?>(null) }
    var turnCategories by remember(state.id) { mutableStateOf<List<String>>(emptyList()) }
    val isNormalMode = state.settings.playMode == PlayMode.NORMAL
    LaunchedEffect(state.id) {
        turnCategories = runCatching { withContext(Dispatchers.IO) { repository.listCategories() } }
            .getOrDefault(emptyList())
    }

    fun pickSettings(): GameSettings = if (isNormalMode) {
        state.settings.copy(
            allowedDifficulties = setOf(turnDifficulty),
            allowedCategories = turnCategory?.let { setOf(it) } ?: emptySet(),
        )
    } else {
        state.settings
    }

    fun startNextTurn() {
        val current = freshState()
        scope.launch {
            val selection = runCatching { withContext(Dispatchers.IO) { repository.pickWord(pickSettings(), current.usedWordIds) } }
            if (selection.isFailure) {
                errorMessage = "واژه بارگذاری نشد؛ فضای ذخیره‌سازی را بررسی کن."
                return@launch
            }
            // A double tap already started this turn while the word was loading: nothing to do,
            // rather than a stale-command error on the running turn's screen.
            if (freshState().version != current.version) return@launch
            when (val pick = selection.getOrThrow()) {
                is WordSelectionResult.Selected -> dispatch(GameAction.StartTurn(current.version, pick.word, gameMoment()))
                WordSelectionResult.Empty -> errorMessage = "برای دسته‌ها و سطح‌های انتخاب‌شده واژهٔ بازبینی‌شدهٔ استفاده‌نشده‌ای باقی نمانده؛ فیلترها را تغییر بده یا بانک را تکمیل کن."
                WordSelectionResult.InvalidRandomIndex -> errorMessage = "انتخاب واژه ناموفق بود؛ دوباره تلاش کن."
            }
        }
    }


    // The next word is prefetched as soon as a word becomes current, so scoring a
    // word dispatches immediately on tap — the same synchronous path as Reveal/Hide.
    var nextWord by remember { mutableStateOf<WordRecord?>(null) }
    var nextWordReady by remember { mutableStateOf(false) }
    LaunchedEffect(state.id, state.currentTurn?.currentWord?.id, state.phase) {
        nextWord = null
        nextWordReady = false
        if (state.phase == GamePhase.RUNNING && state.currentTurn?.currentWord != null) {
            val used = state.usedWordIds
            val result = runCatching { withContext(Dispatchers.IO) { repository.pickWord(pickSettings(), used) } }
            nextWord = (result.getOrNull() as? WordSelectionResult.Selected)?.word
            nextWordReady = true
        }
    }

    fun finishResolve(before: GameState, outcome: WordOutcome, next: WordRecord?) {
        val after = dispatch(GameAction.ResolveWord(before.version, outcome, next, gameMoment())) ?: return
        // Celebrate only an answer that counted — not one tapped after the time had run out (PH-B07).
        if (outcome == WordOutcome.CORRECT && before.settings.soundEnabled && PantohubEngine.scoredCorrect(before, after)) {
            tone?.startTone(ToneGenerator.TONE_PROP_ACK, 110)
        }
        if (next == null && after.phase == GamePhase.RUNNING) {
            errorMessage = "واژهٔ دیگری مطابق فیلترهای این بازی پیدا نشد. سطح یا دسته را آگاهانه تغییر بده؛ انتخاب خودکار جایگزین انجام نشد."
        }
    }

    fun resolve(outcome: WordOutcome) {
        val current = freshState()
        // Only a word the performer has seen can be scored — on the word screen, or on the shared
        // screen after covering it. A second tap in the same frame, or a tap after the turn closed,
        // is ignored instead of reported as an error.
        val turnNow = current.currentTurn
        if (current.phase != GamePhase.RUNNING || turnNow == null || !(turnNow.wordRevealed || turnNow.wordSeen)) return
        // NORMAL: the engine ends the turn on a correct guess — no next word is needed (PH-B01).
        if (isNormalMode && outcome == WordOutcome.CORRECT) {
            finishResolve(current, outcome, null)
            return
        }
        if (nextWordReady) {
            finishResolve(current, outcome, nextWord)
            return
        }
        scope.launch {
            // The same filters as the prefetch: in NORMAL that is this turn's difficulty/category.
            val selection = runCatching { withContext(Dispatchers.IO) { repository.pickWord(pickSettings(), current.usedWordIds) } }
            if (selection.isFailure) {
                errorMessage = "واژه بارگذاری نشد؛ پاسخ این واژه هنوز ثبت نشده است."
                return@launch
            }
            // Another tap (or the timer) already moved the game on while the next word was loading.
            if (freshState().version != current.version) return@launch
            finishResolve(current, outcome, (selection.getOrThrow() as? WordSelectionResult.Selected)?.word)
        }
    }

    val currentTurn = state.currentTurn
    val remaining = PantohubEngine.remainingMs(state, now)
    val remainingSeconds = ((remaining + 999L) / 1_000L).toInt().coerceAtLeast(0)
    val currentTeam = PantohubEngine.currentTeam(state)
    val currentPlayer = PantohubEngine.currentPlayer(state)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                title = {
                    Text(
                        when {
                            state.phase == GamePhase.FINISHED -> "نتیجهٔ بازی"
                            state.phase == GamePhase.RUNNING && currentTurn?.wordRevealed == true -> "واژهٔ اجراکننده"
                            else -> "پانتومیم"
                        },
                    )
                },
                navigationIcon = { TextButton(onClick = ::leaveAndPause) { Text("بازگشت") } },
                actions = {
                    if (!(state.phase == GamePhase.RUNNING && currentTurn?.wordRevealed == true)) {
                        TextButton(onClick = { showEndGameDialog = true }, enabled = state.phase != GamePhase.FINISHED) {
                            Text("پایان بازی")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            PantohubBackdrop()
            if (state.phase == GamePhase.RUNNING && currentTurn?.wordRevealed == true) {
                SecretWordScreen(
                    modifier = Modifier.padding(padding).fillMaxWidth().padding(20.dp),
                    state = state,
                    turn = currentTurn!!,
                    remainingMs = remaining,
                    remainingSeconds = remainingSeconds,
                    storageError = storageError,
                    errorMessage = errorMessage,
                    onHide = { dispatch(GameAction.HideWord(freshState().version, gameMoment())) },
                    onResolve = ::resolve,
                )
            } else {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    storageError?.let {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Scoreboard(state)

                    when (state.phase) {
                        GamePhase.READY, GamePhase.FINAL_READY -> {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    // What the team that just played earned — the scoreboard above already
                                    // holds the new totals. The word itself is not shown (ADR-0006 §10).
                                    state.turns.lastOrNull()?.let { last ->
                                        val lastTeam = state.teams.firstOrNull { it.id == last.teamId }?.name.orEmpty()
                                        val points = kotlin.math.abs(last.scoreEarned).toString().toPersianDigits()
                                        Text(
                                            when {
                                                last.scoreEarned > 0 -> "$lastTeam در نوبت قبل $points امتیاز گرفت."
                                                last.scoreEarned < 0 -> "$lastTeam در نوبت قبل $points امتیاز از دست داد."
                                                else -> "$lastTeam در نوبت قبل امتیازی نگرفت."
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            textAlign = TextAlign.Center,
                                        )
                                        Spacer(Modifier.height(10.dp))
                                    }
                                    Text(
                                        if (state.phase == GamePhase.FINAL_READY) "دور پایانی" else "نوبت بعدی",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(currentTeam?.name.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    Text("اجراکننده: ${currentPlayer?.name.orEmpty()}", style = MaterialTheme.typography.titleMedium)
                                    if (state.phase == GamePhase.READY && currentTeam != null) {
                                        val done = state.completedTurnsByTeam[currentTeam.id] ?: 0
                                        Text("دور ${(done + 1).toString().toPersianDigits()} از ${state.settings.roundsPerTeam.toString().toPersianDigits()}")
                                    } else {
                                        Text("هر تیم واجد شرایط یک نوبت اضافه می‌گیرد؛ ضریب امتیاز ${state.settings.finalRoundMultiplier.toString().toPersianDigits()} برابر است.")
                                    }
                                    if (isNormalMode) {
                                        Spacer(Modifier.height(14.dp))
                                        NormalTurnChoices(
                                            difficulty = turnDifficulty,
                                            onDifficulty = { turnDifficulty = it },
                                            category = turnCategory,
                                            categories = turnCategories,
                                            onCategory = { turnCategory = it },
                                        )
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Button(onClick = ::startNextTurn, modifier = Modifier.fillMaxWidth()) {
                                        Text("شروع نوبت و انتخاب واژه")
                                    }
                                }
                            }
                            if (errorMessage != null) ErrorCard(errorMessage!!)
                            Text("پیش از شروع، گوشی را به اجراکننده بده. واژه به‌صورت خودکار به صفحهٔ عمومی نشان داده نمی‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        GamePhase.RUNNING, GamePhase.PAUSED -> {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "${currentTeam?.name.orEmpty()}  •  ${currentTurn?.playerName.orEmpty()}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    TimerView(state, remaining, remainingSeconds)
                                    if (state.phase == GamePhase.PAUSED) {
                                        Text("بازی متوقف است؛ زمان باقی‌مانده حفظ شده.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    val currentWord = currentTurn?.currentWord
                                    if (currentWord == null) {
                                        Text(
                                            "واژهٔ دیگری مطابق فیلترها پیدا نشد.",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.error,
                                            textAlign = TextAlign.Center,
                                        )
                                        Text("بازی سطح یا دسته را خودکار عوض نمی‌کند.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                                    } else if (currentTurn?.wordRevealed != true) {
                                        if (currentTurn?.wordSeen == true) {
                                            Text("واژه پوشیده است", fontWeight = FontWeight.SemiBold)
                                            Text("اجراکننده واژه را دیده است؛ اگر تیم درست حدس زد، همین‌جا «حدس درست» را بزن.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                        } else {
                                            Text("واژه فقط برای اجراکننده است", fontWeight = FontWeight.SemiBold)
                                            Text("گوشی را به او بده و بعد دکمهٔ نمایش را بزن.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Button(
                                            onClick = { dispatch(GameAction.RevealWord(freshState().version, gameMoment())) },
                                            enabled = state.phase == GamePhase.RUNNING,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) { Text("نمایش واژه برای اجراکننده") }
                                    }
                                    Spacer(Modifier.height(12.dp))
                                    if (state.phase == GamePhase.PAUSED) {
                                        Button(onClick = { dispatch(GameAction.ResumeTurn(freshState().version, gameMoment())) }, modifier = Modifier.fillMaxWidth()) {
                                            Text("ادامهٔ نوبت")
                                        }
                                    } else {
                                        // The performer has seen the word and covered it: the guess can be
                                        // recorded right here. Before anyone has seen the word there is nothing
                                        // to score, so no buttons — they used to sit here permanently disabled
                                        // (PH-B05).
                                        val seenTurn = currentTurn?.takeIf { it.wordSeen }
                                        if (currentWord != null && seenTurn != null) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                                Button(
                                                    onClick = { resolve(WordOutcome.CORRECT) },
                                                    modifier = Modifier.weight(1f),
                                                ) { Text("حدس درست") }
                                                if (state.settings.allowSkip) {
                                                    OutlinedButton(
                                                        onClick = { resolve(WordOutcome.SKIPPED) },
                                                        enabled = seenTurn.skippedWords < state.settings.maxSkipsPerTurn,
                                                        modifier = Modifier.weight(1f),
                                                    ) { Text("رد واژه") }
                                                }
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = { dispatch(GameAction.PauseTurn(freshState().version, gameMoment())) },
                                            modifier = Modifier.fillMaxWidth(),
                                        ) { Text("توقف موقت") }
                                    }
                                    OutlinedButton(
                                        onClick = { dispatch(GameAction.EndTurn(freshState().version, gameMoment())) },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text("پایان نوبت") }
                                }
                            }
                            if (state.settings.cardsEnabled && state.phase == GamePhase.RUNNING) {
                                SpecialCardsRow(state, onUse = { card -> dispatch(GameAction.UseCard(freshState().version, card, gameMoment())) })
                            }
                            if (errorMessage != null) ErrorCard(errorMessage!!)
                            Text("اگر گوشی را از اجراکننده می‌گیری، واژه را بپوشان یا نوبت را متوقف کن.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        GamePhase.FINISHED -> {
                            val winners = state.teams.filter { it.id in state.winnerTeamIds }
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("🏆", style = MaterialTheme.typography.displaySmall)
                                    Text(
                                        if (winners.size == 1) "قهرمان: ${winners.single().name}" else "بازی با تساوی تمام شد",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                    )
                                    Text("امتیاز نهایی از نتیجهٔ واقعی نوبت‌ها محاسبه شده است.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                                    Spacer(Modifier.height(10.dp))
                                    state.teams.sortedByDescending { state.teamScores[it.id] ?: 0 }.forEach { team ->
                                        Text("${team.name}: ${(state.teamScores[team.id] ?: 0).toString().toPersianDigits()} امتیاز")
                                    }
                                    Spacer(Modifier.height(14.dp))
                                    Button(onClick = onReturnHome, modifier = Modifier.fillMaxWidth()) { Text("بازگشت به پانتومیم") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEndGameDialog) {
        AlertDialog(
            onDismissRequest = { showEndGameDialog = false },
            title = { Text("پایان بازی؟") },
            text = { Text("نتیجهٔ فعلی ثبت می‌شود و بازی دیگر ادامه‌پذیر نخواهد بود.") },
            confirmButton = {
                TextButton(onClick = {
                    showEndGameDialog = false
                    dispatch(GameAction.EndGame(freshState().version, gameMoment()))
                }) { Text("پایان و ثبت نتیجه") }
            },
            dismissButton = { TextButton(onClick = { showEndGameDialog = false }) { Text("ادامهٔ بازی") } },
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NormalTurnChoices(
    difficulty: Difficulty,
    onDifficulty: (Difficulty) -> Unit,
    category: String?,
    categories: List<String>,
    onCategory: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("سختی این نوبت", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Difficulty.entries.forEach { level ->
                FilterChip(
                    colors = pantohubChipColors(PantohubTint.Mint),
                    selected = difficulty == level,
                    onClick = { onDifficulty(level) },
                    label = { Text(level.labelFa) },
                )
            }
        }
        Text("موضوع این نوبت", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                colors = pantohubChipColors(PantohubTint.Mint),
                selected = category == null,
                onClick = { onCategory(null) },
                label = { Text("همهٔ موضوعات") },
            )
        }
        categories.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    FilterChip(
                        colors = pantohubChipColors(PantohubTint.Mint),
                        selected = category == item,
                        onClick = { onCategory(item) },
                        label = { Text(WordCategories.labelFa(item)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SecretWordScreen(
    modifier: Modifier,
    state: GameState,
    turn: TurnProgress,
    remainingMs: Long,
    remainingSeconds: Int,
    storageError: String?,
    errorMessage: String?,
    onHide: () -> Unit,
    onResolve: (WordOutcome) -> Unit,
) {
    val window = LocalContext.current.findActivity()?.window
    val hadSecureFlag = window != null && (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
    DisposableEffect(window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (window != null && !hadSecureFlag) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        storageError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
        Text("${turn.playerName}  •  فقط اجراکننده نگاه کند", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        TimerView(state, remainingMs, remainingSeconds)
        val wordCard: @Composable () -> Unit = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(PantohubDesign.Radius.CardLarge),
                border = BorderStroke(1.5.dp, PantohubDesign.Violet.copy(alpha = 0.65f)),
            ) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(turn.currentWord?.difficulty?.labelFa.orEmpty(), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        turn.currentWord?.text.orEmpty(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("گوشی را از بقیه دور نگه دار.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (state.settings.animationsEnabled) {
            AnimatedVisibility(
                visible = true,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { wordCard() }
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { wordCard() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { onResolve(WordOutcome.CORRECT) },
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text("حدس درست") }
            if (state.settings.allowSkip) {
                OutlinedButton(
                    onClick = { onResolve(WordOutcome.SKIPPED) },
                    enabled = turn.skippedWords < state.settings.maxSkipsPerTurn,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("رد واژه") }
            }
        }
        Button(onClick = onHide, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("پوشاندن سریع واژه")
        }
        Text("نتیجه را همین‌جا ثبت کن؛ واژهٔ بعدی خودکار پوشیده نمایش داده می‌شود.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TimerView(state: GameState, remainingMs: Long, remainingSeconds: Int) {
    val durationSeconds = if (state.currentTurn?.isFinalRound == true) state.settings.finalRoundSeconds else state.settings.turnSeconds
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(112.dp)) {
        CircularProgressIndicator(
            progress = { (remainingMs.toFloat() / (durationSeconds * 1_000f)).coerceIn(0f, 1f) },
            modifier = Modifier.size(104.dp),
            strokeWidth = 7.dp,
            color = if (remainingSeconds <= 10) PantohubDesign.Orange else PantohubDesign.Teal,
        )
        Text(formatClock(remainingSeconds), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Scoreboard(state: GameState) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.teams.forEach { team ->
            Card(modifier = Modifier.widthIn(min = 112.dp)) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(team.name, maxLines = 1, fontWeight = FontWeight.SemiBold)
                    Text(
                        (state.teamScores[team.id] ?: 0).toString().toPersianDigits(),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(team.colorArgb),
                        fontWeight = FontWeight.Bold,
                    )
                    val completed = state.completedTurnsByTeam[team.id] ?: 0
                    if (state.phase != GamePhase.FINAL_READY && state.phase != GamePhase.FINISHED) {
                        Text("${completed.toString().toPersianDigits()}/${state.settings.roundsPerTeam.toString().toPersianDigits()} نوبت", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecialCardsRow(state: GameState, onUse: (SpecialCard) -> Unit) {
    val teamId = state.currentTurn?.teamId ?: return
    val used = state.usedCardsByTeam[teamId].orEmpty()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("کارت‌های همین تیم", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SpecialCard.entries.forEach { card ->
                FilterChip(
                    colors = pantohubChipColors(PantohubTint.Butter),
                    selected = card in state.currentTurn!!.activeCards,
                    enabled = card !in used,
                    onClick = { onUse(card) },
                    label = { Text(card.titleFa) },
                )
            }
        }
        Text("دوبرابر برای پاسخ درست بعدی؛ رد رایگان جریمهٔ یک رد را حذف می‌کند؛ فرصت اضافه ۱۵ ثانیه است.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * The newest [GameState] a game screen produced, kept until the parent hands it back through
 * recomposition. A plain holder on purpose: reading it must never wait for a frame.
 */
private class NewestState {
    var value: GameState? = null
}

private fun formatClock(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds).toPersianDigits()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
