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
import ir.dorehami.games.chess.ui.ChessEntry

/** Screens of the entry flow. The games themselves arrive later, one module per game. */
sealed interface Screen {
    data object Home : Screen
    data object Games : Screen
    data object Settings : Screen

    /** Chess table (3D board, `games/chess`). */
    data object Chess : Screen
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

    when (screen) {
        Screen.Home -> HomeScreen(
            onEnterGame = { screen = Screen.Games },
            onSettings = { screen = Screen.Settings },
        )

        Screen.Games -> GamesScreen(
            onBack = { screen = Screen.Home },
            onOpenChess = { screen = Screen.Chess },
        )

        Screen.Chess -> ChessEntry(onExit = { screen = Screen.Games })
        Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home })
    }
}
