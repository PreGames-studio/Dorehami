package ir.dorehami.games.hokm

import ir.dorehami.gamesdk.GameConfigException
import ir.dorehami.gamesdk.GameId
import ir.dorehami.gamesdk.GameRegistry
import ir.dorehami.gamesdk.view
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** تستِ تنظیماتِ میزبان و ثبتِ بازی در رجیستری. */
class HokmOptionsTest {

    @Test
    fun `تنظیماتِ پیش‌فرض همان حکمِ استاندارد ایران است`() {
        val options = HokmOptions.parse(HokmModule.defaultOptions())
        assertEquals(7, options.targetPoints)
        assertEquals(HandEndMode.SEVEN, options.handEnd)
        assertEquals(HokmLazimMode.OFF, options.hokmLazim)
        assertEquals(2, options.kotHakemPoints)
        assertEquals(3, options.kotOpponentPoints)
        assertEquals(0, options.turnSeconds)
    }

    @Test
    fun `تنظیماتِ نامعتبر با پیام فارسی رد می‌شود`() {
        val badTarget = assertThrows<GameConfigException> {
            HokmModule.validateOptions(view { put(HokmOptions.KEY_TARGET_POINTS, JsonPrimitive(99)) })
        }
        assertTrue(badTarget.message!!.contains("امتیازِ هدف"), badTarget.message)

        val badHandEnd = assertThrows<GameConfigException> {
            HokmModule.validateOptions(view { put(HokmOptions.KEY_HAND_END, JsonPrimitive("nine")) })
        }
        assertTrue(badHandEnd.message!!.contains("پایانِ دست"), badHandEnd.message)

        val badLazim = assertThrows<GameConfigException> {
            HokmModule.validateOptions(view { put(HokmOptions.KEY_HOKM_LAZIM, JsonPrimitive("maybe")) })
        }
        assertTrue(badLazim.message!!.contains("حکمِ لازم"), badLazim.message)

        val badClock = assertThrows<GameConfigException> {
            HokmModule.validateOptions(view { put(HokmOptions.KEY_TURN_SECONDS, JsonPrimitive(9999)) })
        }
        assertTrue(badClock.message!!.contains("مهلتِ هر نوبت"), badClock.message)
    }

    @Test
    fun `می‌توان مهلتِ نوبت و امتیازِ کوت را تنظیم کرد`() {
        val options = HokmOptions.parse(
            view {
                put(HokmOptions.KEY_TURN_SECONDS, JsonPrimitive(30))
                put(HokmOptions.KEY_KOT_HAKEM, JsonPrimitive(4))
                put(HokmOptions.KEY_HOKM_LAZIM, JsonPrimitive("overtrump"))
                put(HokmOptions.KEY_HAND_END, JsonPrimitive("thirteen"))
            },
        )
        assertEquals(30, options.turnSeconds)
        assertTrue(options.hasTurnClock)
        assertEquals(4, options.kotHakemPoints)
        assertEquals(HokmLazimMode.OVERTRUMP, options.hokmLazim)
        assertEquals(HandEndMode.THIRTEEN, options.handEnd)
        assertEquals(7, options.targetPoints, "مقدارِ پیش‌فرضِ بقیهٔ گزینه‌ها حفظ می‌شود")
    }

    @Test
    fun `بازی در رجیستری ثبت می‌شود و تکراری رد می‌گردد`() {
        val registry = GameRegistry(HokmModule)
        assertEquals(HokmModule, registry.find(HokmModule.ID))
        assertEquals(GameId(HokmModule.ID), registry.require(HokmModule.ID).meta.id)
        assertEquals(4, registry.require(HokmModule.ID).preferredPartySize())

        assertThrows<IllegalArgumentException> { GameRegistry(HokmModule, HokmModule) }
    }

    @Test
    fun `فرادادهٔ بازی کامل است`() {
        val meta = HokmModule.meta
        assertEquals("حکم", meta.titleFa)
        assertEquals(4, meta.minPlayers)
        assertEquals(4, meta.maxPlayers)
        assertEquals(2, meta.teamCount)
        assertEquals(2, meta.playersPerTeam)
        assertTrue(meta.capabilities.contains("bots"))
        assertTrue(meta.capabilities.contains("bluetooth_offline"))
        assertTrue(meta.capabilities.contains("online"))
        assertTrue(meta.capabilities.contains("random_match"))
    }
}
