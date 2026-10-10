package ir.dorehami.gamesdk

/**
 * فهرستِ بازی‌های نصب‌شده روی این دستگاه/سرور.
 *
 * رجیستری جایی است که اپ و سرور به بازی وصل می‌شوند؛ تنها نقطه‌ای است که «نام یک بازی» را می‌شناسد.
 * ثبتِ دو بازی با یک شناسه خطا است (جلوی تداخلِ دو ماژول را همان اول می‌گیرد).
 */
class GameRegistry(modules: List<GameModule>) {

    private val byId: Map<String, GameModule>

    init {
        val map = linkedMapOf<String, GameModule>()
        modules.forEach { module ->
            val id = module.meta.id.value
            require(!map.containsKey(id)) { "بازی با شناسهٔ «$id» دوبار ثبت شده است" }
            map[id] = module
        }
        byId = map
    }

    constructor(vararg modules: GameModule) : this(modules.toList())

    /** همهٔ بازی‌ها به ترتیب ثبت. */
    fun all(): List<GameModule> = byId.values.toList()

    /** ماژولِ یک بازی با شناسه‌اش؛ اگر نباشد `null`. */
    fun find(gameId: String): GameModule? = byId[gameId]

    /** ماژولِ یک بازی؛ اگر نباشد استثنا می‌اندازد (برای مسیرهایی که بازی قطعاً هست). */
    fun require(gameId: String): GameModule =
        byId[gameId] ?: error("بازی «$gameId» در این نسخه نصب نیست")
}
