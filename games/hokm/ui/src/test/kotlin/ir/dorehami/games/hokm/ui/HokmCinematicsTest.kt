package ir.dorehami.games.hokm.ui

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** JVM regression tests for the 2.5D choreography. No Android/Compose rendering is involved. */
class HokmCinematicsTest {

    private fun close(a: Float, b: Float, tolerance: Float = 0.001f): Boolean = abs(a - b) <= tolerance

    @Test
    fun `motion levels never affect rules and OFF is exactly zero`() {
        assertEquals(1f, HokmCinematics.motionScale(HokmMotionLevel.FULL))
        assertEquals(0.45f, HokmCinematics.motionScale(HokmMotionLevel.REDUCED))
        assertEquals(0f, HokmCinematics.motionScale(HokmMotionLevel.OFF))
        assertEquals(18, HokmCinematics.particleCount(18, HokmMotionLevel.FULL))
        assertEquals(6, HokmCinematics.particleCount(18, HokmMotionLevel.REDUCED))
        assertEquals(0, HokmCinematics.particleCount(18, HokmMotionLevel.OFF))
        assertEquals(HokmMotionLevel.REDUCED, HokmMotionLevel.fromStored("reduced"))
        assertEquals(HokmMotionLevel.OFF, HokmMotionLevel.fromStored("OFF"))
        assertEquals(HokmMotionLevel.FULL, HokmMotionLevel.fromStored("legacy-value"))
        assertEquals(HokmMotionLevel.FULL, HokmMotionLevel.fromStored(null))
    }

    @Test
    fun `ring dots are evenly distributed and deterministic`() {
        val first = HokmCinematics.ringDot(0, 8, 100f, 40f, 0f)
        val opposite = HokmCinematics.ringDot(4, 8, 100f, 40f, 0f)
        assertTrue(close(first.first, -opposite.first))
        assertTrue(close(first.second, -opposite.second))
        assertEquals(first, HokmCinematics.ringDot(0, 8, 100f, 40f, 0f))
    }

    @Test
    fun `deal arc hits both endpoints and lifts at the midpoint`() {
        val start = HokmCinematics.dealArc(0f, 0f, 200f, 100f, 0f, 50f, 180f)
        val end = HokmCinematics.dealArc(0f, 0f, 200f, 100f, 1f, 50f, 180f)
        val middle = HokmCinematics.dealArc(0f, 0f, 200f, 100f, 0.5f, 50f, 180f)
        assertTrue(close(start.x, 0f) && close(start.y, 0f) && close(start.rotationDeg, 0f))
        assertTrue(close(end.x, 200f) && close(end.y, 100f) && close(end.rotationDeg, 180f))
        assertTrue(close(middle.x, 100f))
        assertTrue(close(middle.y, 0f), "midpoint should be lifted by exactly 50px above the straight line")
        // Clamped and pure: repeated calls are identical, out-of-range progress cannot escape.
        assertEquals(end, HokmCinematics.dealArc(0f, 0f, 200f, 100f, 4f, 50f, 180f))
    }

    @Test
    fun `collect swirl lands exactly on the winner pile and separates the four cards`() {
        val a = HokmCinematics.collectSwirl(10f, 10f, 110f, 10f, 0f, 30f, cardIndex = 0)
        val b = HokmCinematics.collectSwirl(10f, 10f, 110f, 10f, 1f, 30f, cardIndex = 0)
        val other = HokmCinematics.collectSwirl(10f, 10f, 110f, 10f, 0.5f, 30f, cardIndex = 1)
        val first = HokmCinematics.collectSwirl(10f, 10f, 110f, 10f, 0.5f, 30f, cardIndex = 0)
        assertTrue(close(a.x, 10f) && close(a.y, 10f))
        assertTrue(close(b.x, 110f) && close(b.y, 10f))
        assertTrue(close(b.rotationDeg, 220f))
        assertFalse(close(first.y, other.y), "cards must not stack on the same pixel mid-flight")
        // Degenerate path must not divide by zero.
        val still = HokmCinematics.collectSwirl(5f, 5f, 5f, 5f, 0.5f, 30f, cardIndex = 2)
        assertTrue(still.x.isFinite() && still.y.isFinite())
    }

    @Test
    fun `arc fan is symmetric and keeps the middle card flat`() {
        assertEquals(0f, HokmCinematics.fanAngle(0, 1, 8f))
        assertTrue(close(HokmCinematics.fanAngle(0, 5, 8f), -8f))
        assertTrue(close(HokmCinematics.fanAngle(4, 5, 8f), 8f))
        assertTrue(close(HokmCinematics.fanAngle(2, 5, 8f), 0f))
        assertTrue(close(HokmCinematics.fanLift(2, 5, 12f), 0f))
        assertTrue(close(HokmCinematics.fanLift(0, 5, 12f), 12f))
        assertTrue(close(HokmCinematics.fanLift(4, 5, 12f), 12f))
    }

    @Test
    fun `screen shake decays and disappears when motion is off`() {
        val start = HokmCinematics.shake(0L, 400L, 8f, 1f)
        val later = HokmCinematics.shake(300L, 400L, 8f, 1f)
        assertTrue(abs(later.first) < abs(start.first) || close(start.first, 0f))
        assertEquals(0f to 0f, HokmCinematics.shake(100L, 400L, 8f, 0f))
        assertEquals(0f to 0f, HokmCinematics.shake(500L, 400L, 8f, 1f))
        assertEquals(HokmCinematics.shake(120L, 400L, 8f, 1f), HokmCinematics.shake(120L, 400L, 8f, 1f))
    }

    @Test
    fun `timer warning starts in the last three seconds and expiry is presentation only`() {
        assertEquals(TurnUrgency.EXPIRED, HokmCinematics.turnUrgency(0))
        assertEquals(TurnUrgency.WARNING, HokmCinematics.turnUrgency(1))
        assertEquals(TurnUrgency.WARNING, HokmCinematics.turnUrgency(3))
        assertEquals(TurnUrgency.GOLD, HokmCinematics.turnUrgency(4))
        assertEquals(TurnUrgency.CALM, HokmCinematics.turnUrgency(6))
        assertEquals(TurnUrgency.WARNING, HokmCinematics.turnUrgency(2, totalSeconds = 6))
        assertTrue(HokmCinematics.warningPulse(2, 0, 1f) <= 1f)
        assertTrue(HokmCinematics.warningPulse(2, 3, 1f) >= 0.45f)
        assertEquals(1f, HokmCinematics.warningPulse(8, 3, 1f))
        assertEquals(1f, HokmCinematics.warningPulse(2, 3, 0f))
    }

    @Test
    fun `foil sweep and persian counter digits stay readable`() {
        assertTrue(close(HokmCinematics.foilSweep(0f), 0f))
        assertTrue(close(HokmCinematics.foilSweep(0.5f), 1f))
        assertTrue(close(HokmCinematics.foilSweep(1f), 0f))
        assertEquals("۰", HokmCinematics.faDigits(0))
        assertEquals("۱۳", HokmCinematics.faDigits(13))
        assertEquals("\u2212۲", HokmCinematics.faDigits(-2))
    }
}
