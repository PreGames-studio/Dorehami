package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmRngTest {

    @Test
    fun `next deterministic`() {
        assertEquals(HokmRng.next(42), HokmRng.next(42))
    }

    @Test
    fun `nextInt in range`() {
        val (v, _) = HokmRng.nextInt(42, 10)
        assertTrue(v in 0 until 10)
    }

    @Test
    fun `nextFloat in range`() {
        val (f, _) = HokmRng.nextFloat(42)
        assertTrue(f in 0f..1f)
    }
}
