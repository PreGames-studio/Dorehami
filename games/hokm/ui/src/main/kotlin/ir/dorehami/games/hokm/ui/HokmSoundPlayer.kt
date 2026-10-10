package ir.dorehami.games.hokm.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import ir.dorehami.games.hokm.HokmSounds

/**
 * پخشِ افکت‌های صوتیِ حکم.
 *
 * بازی فقط «نامِ» افکت را می‌فرستد (`Emission.Sound`) و این کلاس آن را به فایلِ صوتی وصل می‌کند؛
 * یعنی قواعدِ بازی هیچ وابستگی‌ای به صدا ندارند و می‌توان صدا را کاملاً خاموش کرد.
 *
 * فایل‌ها در `res/raw` هستند (`hokm_card_deal` و بقیه) و با `SoundPool` پخش می‌شوند تا تأخیر
 * نداشته باشند (برای صدای ورق که پشت‌سرهم پخش می‌شود مهم است).
 */
class HokmSoundPlayer(context: Context) {

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val pool = SoundPool.Builder()
        .setMaxStreams(5)
        .setAudioAttributes(attributes)
        .build()

    private val ids = HashMap<String, Int>()
    private var loaded = false

    @Volatile
    var enabled: Boolean = true

    init {
        val appContext = context.applicationContext
        ids[HokmSounds.DEAL] = pool.load(appContext, R.raw.hokm_card_deal, 1)
        ids[HokmSounds.PLAY] = pool.load(appContext, R.raw.hokm_card_played, 1)
        ids[HokmSounds.TRICK_WON] = pool.load(appContext, R.raw.hokm_trick_won, 1)
        ids[HokmSounds.RUFF] = pool.load(appContext, R.raw.hokm_trick_ruffed, 1)
        ids[HokmSounds.TRUMP_SET] = pool.load(appContext, R.raw.hokm_trump_set, 1)
        ids[HokmSounds.HAND_WON] = pool.load(appContext, R.raw.hokm_hand_won, 1)
        ids[HokmSounds.HAND_LOST] = pool.load(appContext, R.raw.hokm_hand_lost, 1)
        ids[HokmSounds.KOT] = pool.load(appContext, R.raw.hokm_kot, 1)
        ids[HokmSounds.HAKEM_KOTI] = pool.load(appContext, R.raw.hokm_hakem_koti, 1)
        ids[HokmSounds.MATCH_WON] = pool.load(appContext, R.raw.hokm_match_won, 1)
        ids[HokmSounds.MATCH_LOST] = pool.load(appContext, R.raw.hokm_match_lost, 1)
        ids[HokmSounds.YOUR_TURN] = pool.load(appContext, R.raw.hokm_your_turn, 1)
        ids[HokmSounds.INVALID] = pool.load(appContext, R.raw.hokm_invalid_move, 1)
        loaded = true
    }

    /** پخشِ یک افکت بر اساسِ نامش (همان نامی که قواعد بازی منتشر می‌کنند). */
    fun play(name: String) {
        if (!enabled || !loaded) return
        val id = ids[name] ?: return
        pool.play(id, VOLUME, VOLUME, 1, 0, 1f)
    }

    /** آزاد کردنِ منابع (در `onDispose`). */
    fun release() {
        loaded = false
        pool.release()
    }

    private companion object {
        const val VOLUME = 0.75f
    }
}
