package ir.dorehami.games.hokm.ui

/**
 * HK-053 (first slice) — app version / force-update as **pure, unit-testable** logic.
 *
 * The roadmap asks for release gates. This object defines version comparison and
 * force-update policy. The Android layer fetches the remote version.
 */
object HokmVersion {

    data class Version(val major: Int, val minor: Int, val patch: Int) : Comparable<Version> {
        override fun compareTo(other: Version): Int {
            if (major != other.major) return major.compareTo(other.major)
            if (minor != other.minor) return minor.compareTo(other.minor)
            return patch.compareTo(other.patch)
        }
        override fun toString(): String = "$major.$minor.$patch"
    }

    fun parse(text: String): Version? {
        val parts = text.trim().split(".")
        if (parts.size != 3) return null
        val nums = parts.map { it.toIntOrNull() } 
        if (nums.any { it == null }) return null
        return Version(nums[0]!!, nums[1]!!, nums[2]!!)
    }

    enum class UpdatePolicy { NONE, OPTIONAL, FORCE }

    fun policy(current: Version, minimum: Version, latest: Version): UpdatePolicy = when {
        current < minimum -> UpdatePolicy.FORCE
        current < latest -> UpdatePolicy.OPTIONAL
        else -> UpdatePolicy.NONE
    }

    fun isSupported(current: Version, minimum: Version): Boolean = current >= minimum
}
