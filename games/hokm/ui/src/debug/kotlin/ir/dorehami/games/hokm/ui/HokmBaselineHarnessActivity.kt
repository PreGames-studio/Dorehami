package ir.dorehami.games.hokm.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ir.dorehami.uikit.toFa
import kotlinx.coroutines.delay

/**
 * HK-013.a — the on-device playback harness for the fixed baseline scenario (HK-013).
 *
 * **Debug builds only** (this file lives in `src/debug`): it replays
 * [HokmBaselinePlayback]'s plan on the *real* table — the same `HokmScreen` a player sees,
 * fed the same typed snapshots a measurement must describe — while the operator runs
 * `dumpsys gfxinfo`, `screencap` or a TalkBack pass from the outside (BASELINE.md §4/§6).
 * Every checkpoint is logged as `checkpoint=<id> step=<n>` under the [TAG] log tag, so a
 * capture script can line its files up with the stations without guessing timings.
 *
 * The harness never touches the rules: it only *shows* snapshots the pure scenario already
 * captured, through the same [HokmUiMapper.merge] the live table uses. Every tap is a no-op;
 * nothing here can be reached from, or affect, a real match.
 *
 * Launch (from the repo root, after installing a debug APK):
 * ```
 * adb shell am start -n ir.dorehami.app/ir.dorehami.games.hokm.ui.HokmBaselineHarnessActivity \
 *   --es scenario 4p --ei seat 0 --el stepMs 250
 * ```
 * Extras: `scenario` = `4p` (default) or `2p`; `seat` = view seat (default 0);
 * `stepMs` = dwell per step (checkpoints always hold longer, see the plan).
 */
class HokmBaselineHarnessActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val twoPlayer = intent.getStringExtra(EXTRA_SCENARIO) == "2p"
        val scenario = if (twoPlayer) HokmBaselineScenario.twoPlayer else HokmBaselineScenario.fourPlayer
        val viewSeat = intent.getIntExtra(EXTRA_SEAT, 0).coerceIn(0, scenario.playerCount - 1)
        val stepMs = intent.getLongExtra(EXTRA_STEP_MS, HokmBaselinePlayback.DEFAULT_STEP_MS).coerceAtLeast(16L)
        val plan = HokmBaselinePlayback.plan(scenario, viewSeat = viewSeat, stepMs = stepMs)

        Log.i(TAG, "plan scenario=${if (twoPlayer) "2p" else "4p"} seat=$viewSeat steps=${plan.stepCount} stepMs=$stepMs")
        plan.checkpoints.forEach { Log.i(TAG, "planned checkpoint=${it.id} step=${it.stepIndex}") }

        setContent {
            var state by remember(plan) { mutableStateOf(initialState(plan)) }
            LaunchedEffect(plan) {
                for (i in 0 until plan.stepCount) {
                    state = HokmUiMapper.merge(state, plan.snapshotAt(i))
                    plan.checkpointAt(i)?.let { checkpoint ->
                        Log.i(TAG, "checkpoint=${checkpoint.id} step=$i")
                    }
                    delay(plan.dwellMs(i))
                }
                Log.i(TAG, "playback finished steps=${plan.stepCount}")
            }
            HokmScreen(
                state = state,
                onPlayCard = {},
                onChooseHokm = {},
                onToggleDiscard = {},
                onConfirmDiscard = {},
                onKeepCard = {},
                onPassCard = {},
                onSticker = {},
                onClearError = {},
                onPlayAgain = {},
                onExit = { finish() },
                playAgainAvailable = false,
                hapticsEnabled = false,
            )
        }
    }

    /** The table furniture the snapshots do not carry: seat names, from the harness itself. */
    private fun initialState(plan: HokmBaselinePlayback.Plan): HokmUiState = HokmUiState(
        seats = (0 until plan.scenario.playerCount).map { seat ->
            SeatInfo(
                seat = seat,
                name = if (seat == plan.viewSeat) "شما" else "ربات ${seat.toFa()}",
                isBot = seat != plan.viewSeat,
            )
        },
        playerCount = plan.scenario.playerCount,
        mySeat = plan.viewSeat,
        modeLabel = "خط پایه",
    )

    private companion object {
        const val TAG = "HokmBaseline"
        const val EXTRA_SCENARIO = "scenario"
        const val EXTRA_SEAT = "seat"
        const val EXTRA_STEP_MS = "stepMs"
    }
}
