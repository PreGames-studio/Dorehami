package ir.dorehami.app.store

import ir.dorehami.games.chess.ui.ChessShopItems

/** Local cosmetic catalog; ownership is consumed by each game's settings screen. */
internal object StoreCatalog {
    val items = listOf(
        StoreItem(
            id = ChessShopItems.BOARD_LUXURY,
            title = "تختهٔ لاکچری شطرنج",
            description = "طرح لاکچری را در تنظیمات شطرنج باز می‌کند.",
            emoji = "✨",
            price = 350,
        ),
        StoreItem(
            id = ChessShopItems.BOARD_TRADITIONAL,
            title = "تختهٔ سنتی شطرنج",
            description = "طرح سنتی را در تنظیمات شطرنج باز می‌کند.",
            emoji = "🎨",
            price = 250,
        ),
        StoreItem(
            id = ChessShopItems.PIECES_GLOSSY_METAL,
            title = "مهره‌های فلزی براق",
            description = "جنس فلزی براق مهره‌ها را در تنظیمات شطرنج باز می‌کند.",
            emoji = "♞",
            price = 400,
        ),
    )

    val coinPacks = listOf(
        CoinPack(id = "coins-starter", title = "بستهٔ آغاز", coins = 100),
        CoinPack(id = "coins-party", title = "بستهٔ دورهمی", coins = 550),
        CoinPack(id = "coins-club", title = "بستهٔ باشگاه", coins = 1_200),
    )
}

/** Display-only coin package. Real prices and receipts require a verified payment service. */
internal data class CoinPack(val id: String, val title: String, val coins: Long)
