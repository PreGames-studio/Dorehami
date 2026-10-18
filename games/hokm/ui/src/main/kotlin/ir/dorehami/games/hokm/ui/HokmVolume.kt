package ir.dorehami.games.hokm.ui

/**
 * HK-022/HK-023 (volume slice) — volume control as **pure, unit-testable** logic.
 */
object HokmVolume {

    data class Volumes(val sfx: Float = 1.0f, val music: Float = 0.5f, val master: Float = 1.0f)

    fun clamp(v: Float): Float = v.coerceIn(0f, 1f)

    fun effectiveSfx(v: Volumes): Float = clamp(v.master) * clamp(v.sfx)

    fun effectiveMusic(v: Volumes): Float = clamp(v.master) * clamp(v.music)

    fun muted(v: Volumes): Boolean = clamp(v.master) == 0f

    fun setSfx(v: Volumes, value: Float): Volumes = v.copy(sfx = clamp(value))

    fun setMusic(v: Volumes, value: Float): Volumes = v.copy(music = clamp(value))

    fun setMaster(v: Volumes, value: Float): Volumes = v.copy(master = clamp(value))
}
