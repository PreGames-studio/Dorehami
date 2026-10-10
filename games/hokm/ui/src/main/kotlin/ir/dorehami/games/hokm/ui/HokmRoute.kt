package ir.dorehami.games.hokm.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.dorehami.games.hokm.HokmOptions
import ir.dorehami.games.hokm.HokmSounds
import ir.dorehami.games.hokm.Suit

/**
 * مسیرِ بازیِ حکم: از تنظیمات تا میز.
 *
 * اینجا تنها جایی است که صفحه به «منبعِ وضعیت» وصل می‌شود. امروز آن منبع موتورِ محلی است؛
 * فردا همان صفحه می‌تواند به `SessionController` (بلوتوث/آنلاین) وصل شود بدون اینکه یک خط از
 * صفحه یا قواعد عوض شود.
 */
@Composable
fun HokmRoute(
    config: HokmLaunchConfig,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HokmViewModel = viewModel(
        key = "hokm-${config.seed}",
        factory = HokmViewModelFactory(config.seed, config.options),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val soundPlayer = remember { HokmSoundPlayer(context) }

    DisposableEffect(soundPlayer) {
        onDispose { soundPlayer.release() }
    }
    LaunchedEffect(config.soundEnabled) {
        soundPlayer.enabled = config.soundEnabled
    }
    LaunchedEffect(Unit) {
        viewModel.sounds.collect { name -> soundPlayer.play(name) }
    }

    // صدای پایانِ بازی، جدا از صداهایی که خودِ قواعد منتشر می‌کنند.
    val result = state.result
    LaunchedEffect(result != null) {
        if (result != null) {
            soundPlayer.play(if (result.youWon) HokmSounds.MATCH_WON else HokmSounds.MATCH_LOST)
        }
    }

    HokmGameScreen(
        state = state,
        onPlayCard = viewModel::playCard,
        onDeclareTrump = { suit: Suit -> viewModel.declareTrump(suit) },
        onRestart = { viewModel.restart() },
        onExit = onExit,
        onToastShown = viewModel::consumeToast,
        modifier = modifier,
    )
}

/** کارخانهٔ ساختِ ViewModel با تنظیماتِ انتخاب‌شده در صفحهٔ تنظیمات. */
class HokmViewModelFactory(
    private val seed: Long,
    private val options: HokmOptions,
    private val playerName: String = "شما",
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(HokmViewModel::class.java)) {
            "این کارخانه فقط HokmViewModel می‌سازد"
        }
        return HokmViewModel(seed = seed, options = options, playerName = playerName) as T
    }
}
