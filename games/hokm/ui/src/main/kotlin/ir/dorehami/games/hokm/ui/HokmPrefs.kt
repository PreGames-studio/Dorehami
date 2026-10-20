package ir.dorehami.games.hokm.ui

import android.content.Context
import android.content.SharedPreferences
import ir.dorehami.games.hokm.HokmModule

/** Lifetime statistics of the local player (all modes). */
data class HokmStats(val games: Int = 0, val wins: Int = 0, val kots: Int = 0, val hands: Int = 0, val handsWon: Int = 0) {
    val winRate: Int get() = if (games == 0) 0 else wins * 100 / games
}

/**
 * Remembered settings and statistics. Plain SharedPreferences: tiny, synchronous, and nothing here
 * is worth a database.
 */
class HokmPrefs(context: Context) {
    private val sp: SharedPreferences = context.applicationContext.getSharedPreferences("hokm", Context.MODE_PRIVATE)

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) = sp.edit().putString("name", v).apply()
    var soundOn: Boolean
        get() = sp.getBoolean("sound", true)
        set(v) = sp.edit().putBoolean("sound", v).apply()
    var hapticsOn: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = sp.edit().putBoolean("haptics", v).apply()
    var playerCount: Int
        get() = sp.getInt("players", 4).coerceIn(2, 4)
        set(v) = sp.edit().putInt("players", v).apply()
    var targetPoints: Int
        get() = sp.getInt("target", HokmModule.DEFAULT_TARGET_POINTS)
        set(v) = sp.edit().putInt("target", v).apply()
    var botLevel: String
        get() = sp.getString("botLevel", HokmModule.BOT_PRO) ?: HokmModule.BOT_PRO
        set(v) = sp.edit().putString("botLevel", v).apply()
    var serverUrl: String
        get() = sp.getString("serverUrl", DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(v) = sp.edit().putString("serverUrl", v).apply()

    val stats: HokmStats
        get() = HokmStats(
            games = sp.getInt("st.games", 0),
            wins = sp.getInt("st.wins", 0),
            kots = sp.getInt("st.kots", 0),
            hands = sp.getInt("st.hands", 0),
            handsWon = sp.getInt("st.handsWon", 0),
        )

    /** Called once per finished match. [kots] = hands my side won by kot / hakem kot. */
    fun recordMatch(won: Boolean, hands: Int, handsWon: Int, kots: Int) {
        val s = stats
        sp.edit()
            .putInt("st.games", s.games + 1)
            .putInt("st.wins", s.wins + if (won) 1 else 0)
            .putInt("st.kots", s.kots + kots)
            .putInt("st.hands", s.hands + hands)
            .putInt("st.handsWon", s.handsWon + handsWon)
            .apply()
    }

    fun resetStats() {
        sp.edit().remove("st.games").remove("st.wins").remove("st.kots").remove("st.hands").remove("st.handsWon").apply()
    }
}
