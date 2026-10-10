package ir.dorehami.gamesdk

/**
 * ساعتِ تزریق‌شده به قواعد بازی.
 *
 * **هیچ بازی‌ای حق ندارد `System.currentTimeMillis()` را صدا بزند.** دلیلش در `docs/adr/ADR-0003`
 * است: بدون ساعتِ تزریقی، نه می‌توان دستی را بعد از بسته‌شدن برنامه دقیقاً از همان‌جا ادامه داد و
 * نه می‌توان نتیجه را با بازپخش اثبات کرد.
 */
interface Clock {
    /** زمان فعلی به میلی‌ثانیه (مبدأ مهم نیست؛ فقط یکنواخت بودن مهم است). */
    fun nowMs(): Long
}

/**
 * ساعتِ واقعیِ دستگاه.
 *
 * فقط لایه‌های بیرونی (موتور برای زمانِ لاگ، اتاق برای تایمرِ نوبت) از آن استفاده می‌کنند؛
 * **هیچ بازی‌ای حق ندارد** آن را صدا بزند (قاعدهٔ قطعیت در `docs/GAME_CONTRACT.md`).
 */
object SystemClock : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
}

/** ساعتِ دستی برای تست: فقط وقتی جلو می‌رود که خودِ تست آن را جلو بزند. */
class FixedClock(startMs: Long = 1_700_000_000_000L) : Clock {
    private var now = startMs

    override fun nowMs(): Long = now

    /** جلو بردن ساعت به اندازهٔ [deltaMs]. */
    fun advance(deltaMs: Long) {
        now += deltaMs
    }
}

/**
 * تصادفِ قطعیِ تزریق‌شده به قواعد بازی.
 *
 * پیاده‌سازی پیش‌فرض ([SeededRng]) از splitmix64 استفاده می‌کند: سریع، بی‌نیاز از حالتِ پنهان و
 * کاملاً بازتولیدپذیر روی JVM و اندروید.
 */
interface Rng {
    /** یک عدد ۶۴ بیتی تصادفی. */
    fun nextLong(): Long

    /** عددی در بازهٔ `[0, boundExclusive)`. */
    fun nextInt(boundExclusive: Int): Int

    /** جابه‌جاییِ کاملِ یک لیست (Fisher-Yates) بدون مصرفِ تصادفِ سیستم. */
    fun <T> shuffled(items: List<T>): List<T>
}

/**
 * تصادف قطعی بر پایهٔ splitmix64.
 *
 * @param seed سیدِ دست. برای یک دستِ تکراری، همان سید ⇒ همان ترتیب کارت‌ها.
 */
class SeededRng(seed: Long) : Rng {
    private var state: Long = seed

    private companion object {
        // ثابت‌های splitmix64. کاتلین (برخلافِ جاوا) لیترالِ هگزِ بزرگ‌تر از Long.MAX_VALUE را
        // نمی‌پذیرد، بنابراین مکملِ دویِ علامت‌دارِ همان ثابت‌ها نوشته شده است.
        // 0x9E3779B97F4A7C15 = -0x61C8864680B583EB
        const val GOLDEN_GAMMA: Long = -0x61C8864680B583EBL

        // 0xBF58476D1CE4E5B9 = -0x40A7B892E31B1A47
        const val MIX_A: Long = -0x40A7B892E31B1A47L

        // 0x94D049BB133111EB = -0x6B2FB644ECCEEE15
        const val MIX_B: Long = -0x6B2FB644ECCEEE15L
    }

    override fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX_A
        z = (z xor (z ushr 27)) * MIX_B
        return z xor (z ushr 31)
    }

    override fun nextInt(boundExclusive: Int): Int {
        require(boundExclusive > 0) { "boundExclusive must be positive" }
        // یک بیتِ علامت را کنار می‌گذاریم تا باقی‌مانده همیشه نامنفی باشد.
        val nonNegative = nextLong() ushr 1
        return (nonNegative % boundExclusive.toLong()).toInt()
    }

    override fun <T> shuffled(items: List<T>): List<T> {
        val result = items.toMutableList()
        for (i in result.size - 1 downTo 1) {
            val j = nextInt(i + 1)
            val tmp = result[i]
            result[i] = result[j]
            result[j] = tmp
        }
        return result
    }
}
