package ir.dorehami.games.hokm.ui

/**
 * HK-020 (room code slice) — room code generation/validation as **pure, unit-testable** logic.
 *
 * The roadmap mentions shareable room codes. This object generates and validates room codes
 * avoiding look-alike letters (I, O, 0, 1).
 */
object HokmRoomCode {

    const val LENGTH: Int = 6
    private val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generate(seed: Long): String {
        var s = seed
        val sb = StringBuilder(LENGTH)
        repeat(LENGTH) {
            s = s * 1103515245 + 12345
            val idx = ((s ushr 16) % ALPHABET.length).toInt()
            sb.append(ALPHABET[idx])
        }
        return sb.toString()
    }

    fun isValid(code: String): Boolean {
        if (code.length != LENGTH) return false
        return code.all { it in ALPHABET }
    }

    fun normalize(code: String): String = code.trim().uppercase().filter { it in ALPHABET }

    fun shareText(code: String): String = "کد اتاق حکم: $code"
}
