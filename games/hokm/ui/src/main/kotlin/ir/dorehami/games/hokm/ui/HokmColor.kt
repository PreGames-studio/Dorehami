package ir.dorehami.games.hokm.ui

/**
 * GX-02 (color slice) — color helpers as **pure, unit-testable** logic.
 */
object HokmColor {

    data class RGB(val r: Int, val g: Int, val b: Int)

    fun toHex(c: RGB): String = "#%02X%02X%02X".format(c.r, c.g, c.b)

    fun fromHex(hex: String): RGB? {
        val h = hex.trim().removePrefix("#")
        if (h.length != 6) return null
        val r = h.substring(0, 2).toIntOrNull(16) ?: return null
        val g = h.substring(2, 4).toIntOrNull(16) ?: return null
        val b = h.substring(4, 6).toIntOrNull(16) ?: return null
        return RGB(r, g, b)
    }

    fun isDark(c: RGB): Boolean = (c.r * 0.299 + c.g * 0.587 + c.b * 0.114) < 128
}
