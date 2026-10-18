package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class HokmNameGenTest {

    @Test
    fun `generate non-empty`() {
        assertTrue(HokmNameGen.generate(42).isNotEmpty())
    }

    @Test
    fun `generate deterministic`() {
        assertTrue(HokmNameGen.generate(42) == HokmNameGen.generate(42))
    }

    @Test
    fun `generated name valid`() {
        assertTrue(HokmNameGen.isValid(HokmNameGen.generate(42)))
    }
}
