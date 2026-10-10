package ir.dorehami.gamesdk

import kotlinx.serialization.json.JsonObject

/**
 * پاسخِ «سؤال ۱» قرارداد: این بازی چیست، چند نفره است و چه قابلیت‌هایی دارد.
 *
 * این داده کاملاً ایستا است (وابسته به هیچ نشستی نیست) و در فهرست بازی‌های اپ، کارتِ بازی در لابی،
 * صف حریف تصادفی و `GET /v1/games` سرور مصرف می‌شود.
 *
 * @property id شناسهٔ یکتا و پایدار بازی.
 * @property titleFa نام فارسی (نمایش در رابط کاربری).
 * @property titleEn نام انگلیسی (برای لاگ و سرور).
 * @property taglineFa یک خط توضیح برای کارتِ بازی.
 * @property minPlayers / [maxPlayers] بازهٔ مجاز تعداد بازیکن.
 * @property teamCount تعداد تیم‌ها؛ `0` یعنی بازی انفرادی است.
 * @property playersPerTeam تعداد اعضای هر تیم (برای بازی‌های تیمی).
 * @property iconKey کلید آیکون در `core/uikit` (بدون وابستگی به منبع تصویر).
 * @property estimatedMinutes میانگینِ زمانِ یک دست، برای نمایش در کارت بازی.
 * @property capabilities مجموعهٔ قابلیت‌ها (نام‌ها از [Capability.key]).
 * @property defaultOptions تنظیمات پیشنهادیِ میزبان؛ ورودیِ `validateOptions` همین است.
 */
data class GameMeta(
    val id: GameId,
    val titleFa: String,
    val titleEn: String,
    val taglineFa: String,
    val minPlayers: Int,
    val maxPlayers: Int,
    val teamCount: Int = 0,
    val playersPerTeam: Int = 0,
    val iconKey: String = id.value,
    val estimatedMinutes: Int = 10,
    val capabilities: Set<String> = emptySet(),
    val defaultOptions: JsonObject = JsonObject(emptyMap()),
) {
    init {
        require(minPlayers >= 1) { "minPlayers must be >= 1" }
        require(maxPlayers >= minPlayers) { "maxPlayers must be >= minPlayers" }
        require(teamCount == 0 || teamCount * playersPerTeam >= minPlayers) {
            "team setup does not cover minPlayers"
        }
    }

    /** آیا این بازی می‌تواند با ربات پر شود؟ */
    val supportsBots: Boolean
        get() = capabilities.contains(Capability.BOTS.key)

    /** آیا این بازی تیمی است؟ */
    val isTeamGame: Boolean
        get() = teamCount > 0
}
