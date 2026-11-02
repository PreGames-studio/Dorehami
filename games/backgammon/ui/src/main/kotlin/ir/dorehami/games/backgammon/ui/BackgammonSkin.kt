package ir.dorehami.games.backgammon.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Board and checker designs of the backgammon module.
 *
 * Everything is vector data (no bitmaps per design), so switching a design costs nothing at runtime
 * and the shop adds no APK weight. Designs unlock with local match wins, exactly like the Hokm and
 * chess shops, so they work in a Bluetooth-only game.
 */
data class BackgammonBoardSkin(
    val id: String,
    val titleFa: String,
    val subtitleFa: String,
    val woodDark: Color,
    val woodLight: Color,
    val pointLight: Color,
    val pointDark: Color,
    val accent: Color,
    val winsRequired: Int = 0,
)

data class BackgammonPieceSkin(
    val id: String,
    val titleFa: String,
    val subtitleFa: String,
    val whiteFace: Color,
    val whiteRim: Color,
    val blackFace: Color,
    val blackRim: Color,
    val winsRequired: Int = 0,
)

data class BackgammonLook(
    val board: BackgammonBoardSkin = BackgammonSkins.DefaultBoard,
    val pieces: BackgammonPieceSkin = BackgammonSkins.DefaultPieces,
)

val LocalBackgammonLook = compositionLocalOf { BackgammonLook() }

@Composable
fun BackgammonLookProvider(boardId: String?, pieceId: String?, content: @Composable () -> Unit) {
    val look = BackgammonLook(
        board = BackgammonSkins.board(boardId),
        pieces = BackgammonSkins.pieces(pieceId),
    )
    CompositionLocalProvider(LocalBackgammonLook provides look, content = content)
}

object BackgammonSkins {
    /**
     * The default board speaks the app's language: bone playing field, the app's teal for the dark
     * points and its burnt orange for the accents — the same three colours the shell, the game
     * picker and chess use, instead of a separate dark-wood world.
     */
    val DefaultBoard = BackgammonBoardSkin(
        id = "paper",
        titleFa = "کاغذ و فیروزه",
        subtitleFa = "هم‌خانوادهٔ بقیهٔ اپ",
        woodDark = Color(0xFFE7D3A8),
        woodLight = Color(0xFFF7ECD6),
        pointLight = Color(0xFFF3E2BE),
        pointDark = Color(0xFF0E8F87),
        accent = Color(0xFFD9480F),
        winsRequired = 0,
    )

    val DefaultPieces = BackgammonPieceSkin(
        id = "classic",
        titleFa = "عاج و گردو",
        subtitleFa = "مهره‌های همیشگی",
        whiteFace = Color(0xFFFDFBF4),
        whiteRim = Color(0xFFC9A96B),
        blackFace = Color(0xFF3A2A12),
        blackRim = Color(0xFF7A5A2E),
        winsRequired = 0,
    )

    val boards: List<BackgammonBoardSkin> = listOf(
        DefaultBoard,
        BackgammonBoardSkin(
            id = "emerald",
            titleFa = "مخمل زمرد",
            subtitleFa = "سبز آرام با نقش طلایی",
            woodDark = Color(0xFF12302A),
            woodLight = Color(0xFF1D4A40),
            pointLight = Color(0xFFE8E0C8),
            pointDark = Color(0xFF0E8F87),
            accent = Color(0xFF5FD3C7),
            winsRequired = 0,
        ),
        BackgammonBoardSkin(
            id = "night",
            titleFa = "شب بنفش",
            subtitleFa = "هم‌رنگ بقیهٔ بازی‌ها",
            woodDark = Color(0xFF241F2E),
            woodLight = Color(0xFF3A3346),
            pointLight = Color(0xFFD2C8DC),
            pointDark = Color(0xFF6B4CC2),
            accent = Color(0xFFC9A5FF),
            winsRequired = 2,
        ),
        BackgammonBoardSkin(
            id = "ruby",
            titleFa = "یاقوت سرخ",
            subtitleFa = "گرم و پرانرژی",
            woodDark = Color(0xFF3A1216),
            woodLight = Color(0xFF5C1B22),
            pointLight = Color(0xFFF0D9C0),
            pointDark = Color(0xFF9E1F2E),
            accent = Color(0xFFFFB86B),
            winsRequired = 5,
        ),
        BackgammonBoardSkin(
            id = "marble",
            titleFa = "سنگ مرمر",
            subtitleFa = "سنگ روشن و مینیمال",
            woodDark = Color(0xFFD9D2C6),
            woodLight = Color(0xFFF2EDE4),
            pointLight = Color(0xFFB3202B),
            pointDark = Color(0xFF2A2433),
            accent = Color(0xFF8B6412),
            winsRequired = 8,
        ),
    )

    val pieces: List<BackgammonPieceSkin> = listOf(
        DefaultPieces,
        BackgammonPieceSkin(
            id = "copper",
            titleFa = "مس و فولاد",
            subtitleFa = "فلزی و درخشان",
            whiteFace = Color(0xFFE6A96B),
            whiteRim = Color(0xFFB57334),
            blackFace = Color(0xFF4B5563),
            blackRim = Color(0xFF9CA3AF),
            winsRequired = 0,
        ),
        BackgammonPieceSkin(
            id = "jade",
            titleFa = "یشم و عقیق",
            subtitleFa = "سبز و قرمز ایرانی",
            whiteFace = Color(0xFFBFE3CF),
            whiteRim = Color(0xFF7FB79B),
            blackFace = Color(0xFF8E1F2E),
            blackRim = Color(0xFFCA5C68),
            winsRequired = 3,
        ),
        BackgammonPieceSkin(
            id = "obsidian",
            titleFa = "شیشهٔ شب",
            subtitleFa = "تیره، براق و مدرن",
            whiteFace = Color(0xFF3A3346),
            whiteRim = Color(0xFFC9A5FF),
            blackFace = Color(0xFF14111B),
            blackRim = Color(0xFF5FD3C7),
            winsRequired = 7,
        ),
    )

    fun board(id: String?): BackgammonBoardSkin = boards.firstOrNull { it.id == id } ?: DefaultBoard

    fun pieces(id: String?): BackgammonPieceSkin = pieces.firstOrNull { it.id == id } ?: DefaultPieces

    fun unlocked(winsRequired: Int, wins: Int): Boolean = wins >= winsRequired

    fun unlocked(skin: BackgammonBoardSkin, wins: Int): Boolean = unlocked(skin.winsRequired, wins)

    fun unlocked(skin: BackgammonPieceSkin, wins: Int): Boolean = unlocked(skin.winsRequired, wins)
}
