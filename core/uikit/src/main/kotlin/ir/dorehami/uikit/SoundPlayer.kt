package ir.dorehami.uikit

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Low-latency sound effects keyed by the names a game emits in `Emission.Sound`.
 *
 * Games never ship audio themselves: the game's UI module hands this player a `name → raw resource`
 * map, so the same name (`card_played`) can be voiced differently per game.
 */
class UiSoundPlayer(context: Context, sounds: Map<String, Int>) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids: Map<String, Int> = sounds.mapValues { (_, res) -> pool.load(context, res, 1) }
    private val loaded = HashSet<Int>()

    @Volatile
    var enabled: Boolean = true

    init {
        pool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loaded += sampleId }
    }

    /** Plays [name] if it is known and loaded; unknown names are silently ignored. */
    fun play(name: String, volume: Float = 1f) {
        if (!enabled) return
        val id = ids[name] ?: return
        if (id !in loaded) return
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun release() = pool.release()
}

/** Remembers a [UiSoundPlayer] for the lifetime of the composition and releases it afterwards. */
@Composable
fun rememberSoundPlayer(sounds: Map<String, Int>): UiSoundPlayer {
    val context = LocalContext.current.applicationContext
    val player = remember(sounds) { UiSoundPlayer(context, sounds) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}
