package ir.dorehami.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import ir.dorehami.app.ui.GamesScreen
import ir.dorehami.app.ui.HomeScreen
import ir.dorehami.app.ui.SettingsScreen
import ir.dorehami.app.ui.theme.DorehamiTheme
import ir.dorehami.games.hokm.ui.HokmLaunchConfig
import ir.dorehami.games.hokm.ui.HokmRoute
import ir.dorehami.games.hokm.ui.HokmSetupScreen

/** Screens of the entry flow. The games themselves live in their own modules. */
sealed interface Screen {
    data object Home : Screen
    data object Games : Screen
    data object Settings : Screen
    data object HokmSetup : Screen
    data class Hokm(val config: HokmLaunchConfig) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DorehamiTheme {
                // Persian-first app: force RTL regardless of the system language.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    EntryApp()
                }
            }
        }
    }
}

@Composable
private fun EntryApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var hokmConfig by remember { mutableStateOf(HokmLaunchConfig(seed = freshSeed())) }

    when (val current = screen) {
        Screen.Home -> HomeScreen(
            onEnterGame = { screen = Screen.Games },
            onSettings = { screen = Screen.Settings },
        )

        Screen.Games -> GamesScreen(
            onBack = { screen = Screen.Home },
            onPickGame = { gameId ->
                // فقط بازی‌های ساخته‌شده باز می‌شوند؛ بقیه هنوز «به‌زودی» هستند.
                if (gameId == HOKM_ID) {
                    screen = Screen.HokmSetup
                }
            },
        )

        Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home })

        Screen.HokmSetup -> HokmSetupScreen(
            config = hokmConfig,
            onConfigChange = { hokmConfig = it },
            onStart = { screen = Screen.Hokm(hokmConfig) },
            onBack = { screen = Screen.Games },
        )

        is Screen.Hokm -> HokmRoute(
            config = current.config,
            onExit = { screen = Screen.Games },
        )
    }
}

private fun freshSeed(): Long = kotlin.math.abs(System.currentTimeMillis() % 1_000_000L) + 1L

private const val HOKM_ID = "hokm"
