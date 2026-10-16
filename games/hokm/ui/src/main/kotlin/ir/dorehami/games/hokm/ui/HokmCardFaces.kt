package ir.dorehami.games.hokm.ui

import ir.dorehami.games.hokm.Card
import ir.dorehami.games.hokm.Rank
import ir.dorehami.games.hokm.Suit

/**
 * GX-03 — the card deck as a *pure, testable catalogue*: exactly what is printed on all 52 faces,
 * what inks a four-colour deck uses, which backs exist and how thick a card is.
 *
 * Why pure Kotlin: the acceptance criterion for GX-03 is "rank/suit error = zero" plus "readable at
 * real size". Both are machine-checkable — but only if the deck can be described without Compose,
 * Android or a device. So this file knows the *content* of a card (pip spots, index, court figure,
 * inks, bezel), while [PlayingCardView] only knows how to paint it, `tools/hokm/cardsheet` only
 * knows how to export it as SVG for human review, and [HokmCardFacesTest] only knows how to prove
 * it is complete. No raster asset is involved anywhere (decision **D-10**), so nothing here needs
 * a licence, a hash or a decode budget: the artwork is this source file.
 */
enum class CardInk(val id: String, val titleFa: String) {
    BLACK("black", "مشکی"),
    RED("red", "قرمز"),
    BLUE("blue", "آبی"),
    GREEN("green", "سبز"),
}

/**
 * How the suits are inked. The classic two-colour deck stays the default (the classic taste of the
 * audience is respected); the four-colour deck is the readability option for low vision and
 * colour blindness, and is a *user choice*, never imposed.
 */
enum class HokmDeckStyle(val id: String, val titleFa: String, val detailFa: String) {
    CLASSIC("classic", "کلاسیک", "پیک و گشنیز مشکی، دل و خشت قرمز"),
    FOUR_COLOR("four_color", "چهاررنگ", "هر خال یک رنگ — خواناتر برای چشم ضعیف"),
    ;

    companion object {
        fun byId(id: String?): HokmDeckStyle = entries.firstOrNull { it.id == id } ?: CLASSIC
    }
}

/** Inks as opaque ARGB, with a verified AA contrast against the shipped card stock. */
object CardInks {
    const val BLACK: Long = HokmTokens.Color.INK_BLACK
    const val RED: Long = HokmTokens.Color.INK_RED
    const val BLUE: Long = HokmTokens.Color.INK_BLUE
    const val GREEN: Long = HokmTokens.Color.INK_GREEN

    /** On the dark felt the black ink becomes ivory and the colours brighten; never dark-on-dark. */
    const val BLACK_ON_DARK: Long = HokmTokens.Color.IVORY
    const val RED_ON_DARK: Long = HokmTokens.Color.DANGER
    const val BLUE_ON_DARK: Long = HokmTokens.Color.SKY_ON_DARK
    const val GREEN_ON_DARK: Long = HokmTokens.Color.ACCENT_MINT

    fun argb(ink: CardInk): Long = when (ink) {
        CardInk.BLACK -> BLACK
        CardInk.RED -> RED
        CardInk.BLUE -> BLUE
        CardInk.GREEN -> GREEN
    }

    /** The same ink, adjusted so a lonely suit symbol stays readable on the felt. */
    fun onDark(ink: CardInk): Long = when (ink) {
        CardInk.BLACK -> BLACK_ON_DARK
        CardInk.RED -> RED_ON_DARK
        CardInk.BLUE -> BLUE_ON_DARK
        CardInk.GREEN -> GREEN_ON_DARK
    }

    fun of(suit: Suit, style: HokmDeckStyle): CardInk = when (style) {
        HokmDeckStyle.CLASSIC ->
            if (suit == Suit.HEARTS || suit == Suit.DIAMONDS) CardInk.RED else CardInk.BLACK

        HokmDeckStyle.FOUR_COLOR -> when (suit) {
            Suit.SPADES -> CardInk.BLACK
            Suit.HEARTS -> CardInk.RED
            Suit.DIAMONDS -> CardInk.BLUE
            Suit.CLUBS -> CardInk.GREEN
        }
    }
}

/**
 * Geometry of a card as fractions of the card itself. One place for the numbers that used to be
 * scattered inside the drawing code, so the sheet, the table and the tests agree.
 */
object CardMetrics {
    /** Width : height of a poker card. */
    const val ASPECT: Float = 0.7f
    const val CORNER_RADIUS_FRACTION: Float = 0.09f

    /** Corner index height (jumbo, locked by GX-01/HK-015) and the shipped non-jumbo size. */
    const val INDEX_JUMBO_FRACTION: Float = HokmArtDirection.JUMBO_INDEX_FRACTION
    const val INDEX_STANDARD_FRACTION: Float = 0.19f
    const val INDEX_PAD_FRACTION: Float = 0.06f
    const val INDEX_SUIT_SCALE: Float = 0.78f

    /** Pip field: the safe area between the two corner indices (the shipped HK-015 geometry). */
    const val PIP_FIELD_LEFT: Float = 0.2f
    const val PIP_FIELD_WIDTH: Float = 0.6f
    const val PIP_FIELD_TOP: Float = 0.12f
    const val PIP_FIELD_HEIGHT: Float = 0.76f
    const val PIP_SIZE_FRACTION: Float = 0.2f

    /**
     * The `10` is the one face whose corner index is two glyphs wide. Measured against the shipped
     * geometry the jumbo index ran straight into the left pip column (a real collision the GX-03
     * layout guard caught), so the ten is handled as real jumbo decks handle it: a smaller index and
     * a slightly inset, slightly smaller pip field. Every other face keeps the approved numbers.
     */
    const val TEN_INDEX_SCALE: Float = 0.70f
    const val TEN_PIP_FIELD_LEFT: Float = 0.28f
    const val TEN_PIP_FIELD_WIDTH: Float = 0.44f
    const val TEN_PIP_SIZE_FRACTION: Float = 0.18f

    /**
     * Worst-case advance widths in em, used by the layout guard. A bold sans digit is about
     * 0.56 em wide; the two-glyph `10` is about twice that, so it is the index that decides how
     * much room the pips may use.
     */
    const val INDEX_GLYPH_EM: Float = 0.56f
    const val TEN_INDEX_EM: Float = 1.12f

    /** Index height for the requested setting (the jumbo lock lives here). */
    fun indexFraction(jumbo: Boolean): Float = if (jumbo) INDEX_JUMBO_FRACTION else INDEX_STANDARD_FRACTION

    /** Index height for one rank: the ten is condensed so it never touches the pip field. */
    fun indexFractionFor(rank: Rank, jumbo: Boolean): Float =
        if (rank == Rank.TEN) indexFraction(jumbo) * TEN_INDEX_SCALE else indexFraction(jumbo)

    /** Where the pips of one rank live inside the card. */
    fun pipField(rank: Rank, jumbo: Boolean): PipField =
        if (rank == Rank.TEN) {
            PipField(TEN_PIP_FIELD_LEFT, PIP_FIELD_TOP, TEN_PIP_FIELD_WIDTH, PIP_FIELD_HEIGHT, TEN_PIP_SIZE_FRACTION)
        } else {
            PipField(PIP_FIELD_LEFT, PIP_FIELD_TOP, PIP_FIELD_WIDTH, PIP_FIELD_HEIGHT, PIP_SIZE_FRACTION)
        }

    /** Advance width of a rank's index in card-width fractions (worst case, for the layout guard). */
    fun indexWidthFraction(rank: Rank, jumbo: Boolean): Float {
        val em = if (rank == Rank.TEN) TEN_INDEX_EM else INDEX_GLYPH_EM
        return em * indexFractionFor(rank, jumbo)
    }

    /** Ace symbol in the middle of the card (already ornate — halo plus symbol). */
    const val ACE_SIZE_FRACTION: Float = 0.42f

    /**
     * Court frame: the parchment inset that holds the two portraits. The rest of the card keeps the
     * plain stock, which is what makes a court card recognisable across the table.
     */
    const val COURT_FRAME_LEFT: Float = 0.19f
    const val COURT_FRAME_TOP: Float = 0.16f
    const val COURT_FRAME_WIDTH: Float = 0.62f
    const val COURT_FRAME_HEIGHT: Float = 0.68f
    const val COURT_SPLIT_ALPHA: Float = 0.35f

    /** Half the parchment band between the two portrait triangles, as a fraction of the frame height. */
    const val COURT_SPLIT_GAP: Float = 0.02f

    /** Paper edge: hairline, highlight/shade alphas of the bevel, and the visible side thickness. */
    const val BEZEL_EDGE_WIDTH_FRACTION: Float = 0.014f
    const val BEZEL_LIGHT_ALPHA: Float = 0.55f
    const val BEZEL_SHADE_ALPHA: Float = 0.22f
    const val THICKNESS_FRACTION: Float = 0.016f
}

/** Where a rank's pips sit inside the card (fractions of width/height) and how big they are. */
data class PipField(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val pipSize: Float,
)

/** One pip spot in *field* space (`0..1`); [rotated] marks the upside-down lower half. */
data class PipSlot(val x: Float, val y: Float, val rotated: Boolean = false)

/** Pip layouts for 2–10 — the shipped positions, now data instead of private locals. */
object PipLayouts {
    private const val LEFT = 0.26f
    private const val CENTRE = 0.5f
    private const val RIGHT = 0.74f
    private val ROWS = listOf(0.17f, 0.335f, 0.5f, 0.665f, 0.83f)
    private val TOP = ROWS[0]
    private val Q1 = ROWS[1]
    private val MID = ROWS[2]
    private val Q3 = ROWS[3]
    private val BOTTOM = ROWS[4]

    val ALL: Map<Rank, List<PipSlot>> = mapOf(
        Rank.TWO to listOf(PipSlot(CENTRE, TOP), PipSlot(CENTRE, BOTTOM, rotated = true)),
        Rank.THREE to listOf(PipSlot(CENTRE, TOP), PipSlot(CENTRE, MID), PipSlot(CENTRE, BOTTOM, rotated = true)),
        Rank.FOUR to listOf(
            PipSlot(LEFT, TOP), PipSlot(RIGHT, TOP),
            PipSlot(LEFT, BOTTOM, rotated = true), PipSlot(RIGHT, BOTTOM, rotated = true),
        ),
        Rank.FIVE to listOf(
            PipSlot(LEFT, TOP), PipSlot(RIGHT, TOP), PipSlot(CENTRE, MID),
            PipSlot(LEFT, BOTTOM, rotated = true), PipSlot(RIGHT, BOTTOM, rotated = true),
        ),
        Rank.SIX to listOf(
            PipSlot(LEFT, TOP), PipSlot(RIGHT, TOP), PipSlot(LEFT, MID), PipSlot(RIGHT, MID),
            PipSlot(LEFT, BOTTOM, rotated = true), PipSlot(RIGHT, BOTTOM, rotated = true),
        ),
        Rank.SEVEN to listOf(
            PipSlot(LEFT, TOP), PipSlot(RIGHT, TOP), PipSlot(CENTRE, (TOP + MID) / 2f),
            PipSlot(LEFT, MID), PipSlot(RIGHT, MID),
            PipSlot(LEFT, BOTTOM, rotated = true), PipSlot(RIGHT, BOTTOM, rotated = true),
        ),
        Rank.EIGHT to listOf(
            PipSlot(LEFT, TOP), PipSlot(RIGHT, TOP), PipSlot(CENTRE, (TOP + MID) / 2f),
            PipSlot(LEFT, MID), PipSlot(RIGHT, MID),
            PipSlot(CENTRE, (MID + BOTTOM) / 2f, rotated = true),
            PipSlot(LEFT, BOTTOM, rotated = true), PipSlot(RIGHT, BOTTOM, rotated = true),
        ),
        Rank.NINE to listOf(
            PipSlot(LEFT, TOP), PipSlot(RIGHT, TOP),
            PipSlot(LEFT, Q1 + 0.02f), PipSlot(RIGHT, Q1 + 0.02f), PipSlot(CENTRE, MID),
            PipSlot(LEFT, Q3 - 0.02f, rotated = true), PipSlot(RIGHT, Q3 - 0.02f, rotated = true),
            PipSlot(LEFT, BOTTOM, rotated = true), PipSlot(RIGHT, BOTTOM, rotated = true),
        ),
        Rank.TEN to listOf(
            PipSlot(LEFT, TOP), PipSlot(RIGHT, TOP), PipSlot(CENTRE, (TOP + Q1) / 2f + 0.02f),
            PipSlot(LEFT, Q1 + 0.02f), PipSlot(RIGHT, Q1 + 0.02f),
            PipSlot(LEFT, Q3 - 0.02f, rotated = true), PipSlot(RIGHT, Q3 - 0.02f, rotated = true),
            PipSlot(CENTRE, (Q3 + BOTTOM) / 2f - 0.02f, rotated = true),
            PipSlot(LEFT, BOTTOM, rotated = true), PipSlot(RIGHT, BOTTOM, rotated = true),
        ),
    )

    fun of(rank: Rank): List<PipSlot> = ALL[rank].orEmpty()
}

/** Head-dress of a court figure — the fastest way to tell king, queen and jack apart. */
enum class CourtHead(val id: String, val glyphId: String, val titleFa: String) {
    KING_CROWN("crown", CardGlyphs.CROWN.id, "تاجِ شاه"),
    QUEEN_TIARA("tiara", CardGlyphs.TIARA.id, "تاجِ بی‌بی"),
    JACK_CAP("cap", CardGlyphs.CAP.id, "کلاهِ سرباز"),
}

/**
 * One of the twelve original court designs. Everything the painter needs is a number or a glyph id
 * — no branch-per-card inside the drawing code, and every figure is measurably different from the
 * other eleven.
 */
data class CourtFigure(
    val id: String,
    val rank: Rank,
    val suit: Suit,
    val head: CourtHead,
    val headGlyphId: String,
    val robeGlyphId: String,
    val propGlyphId: String,
    val hasBeard: Boolean,
    val hasHair: Boolean,
    /** Suit accent on the robe — navy, lacquer, brass or turquoise (GX-01 palette). */
    val accentArgb: Long,
    /**
     * Sizes, all relative to the court frame (the parchment inset):
     * * [headScale], [headDressScale], [robeWidth], [medallionScale] are fractions of the frame
     *   **width** (the medallion figure is its diameter);
     * * [robeHeight] and [propHeight] are fractions of the frame **height**;
     * * [propWidth] is the prop's width as a fraction of its own height, so a sword stays slim and
     *   a posy stays round;
     * * [propAngleDeg] tilts the prop (positive = leaning right).
     *
     * Proportions are set so the bust reads as a person and not as a bar: the robe is about as tall
     * as it is wide, the head is a little over half the robe's width, and the prop is shorter than
     * the frame is tall so it can be held instead of crossing the whole card.
     */
    val headScale: Float,
    val headDressScale: Float,
    val robeWidth: Float,
    val robeHeight: Float,
    val propHeight: Float,
    val propWidth: Float,
    val propAngleDeg: Float,
    val medallionScale: Float,
) {
    val titleFa: String get() = "${rank.labelFa} ${suit.titleFa}"
}

object CourtFigures {

    /** Suit accents: navy for spades, lacquer for hearts, brass for diamonds, turquoise for clubs. */
    private fun accentOf(suit: Suit): Long = when (suit) {
        Suit.SPADES -> HokmArtDirection.NAVY
        Suit.HEARTS -> HokmArtDirection.LACQUER
        Suit.DIAMONDS -> HokmArtDirection.BRASS_LIGHT
        Suit.CLUBS -> HokmArtDirection.TURQUOISE
    }

    /**
     * Traditional iconography, our own drawing: kings hold steel, queens hold flowers or the
     * sceptre, jacks hold the halberd or the sword. Deterministic table — no randomness, no copy.
     */
    private fun propOf(rank: Rank, suit: Suit): String = when (rank) {
        Rank.KING -> when (suit) {
            Suit.SPADES -> CardGlyphs.SWORD.id
            Suit.HEARTS -> CardGlyphs.SCEPTRE.id
            Suit.DIAMONDS -> CardGlyphs.AXE.id
            Suit.CLUBS -> CardGlyphs.HALBERD.id
        }

        Rank.QUEEN -> when (suit) {
            Suit.SPADES -> CardGlyphs.SCEPTRE.id
            Suit.HEARTS -> CardGlyphs.FLOWER_FAN.id
            Suit.DIAMONDS -> CardGlyphs.FLOWER_FAN.id
            Suit.CLUBS -> CardGlyphs.FLOWER_FAN.id
        }

        Rank.JACK -> when (suit) {
            Suit.SPADES -> CardGlyphs.HALBERD.id
            Suit.HEARTS -> CardGlyphs.SWORD.id
            Suit.DIAMONDS -> CardGlyphs.AXE.id
            Suit.CLUBS -> CardGlyphs.HALBERD.id
        }

        else -> error("$rank is not a court rank")
    }

    private fun headOf(rank: Rank): CourtHead = when (rank) {
        Rank.KING -> CourtHead.KING_CROWN
        Rank.QUEEN -> CourtHead.QUEEN_TIARA
        else -> CourtHead.JACK_CAP
    }

    /**
     * The twelve figures, in a fixed order (kings, queens, jacks × suits). The sizes are tuned on
     * the rendered sheet so the head, the head-dress and the prop all read at 44 dp — the smallest
     * card on the table.
     */
    val ALL: List<CourtFigure> = listOf(Rank.KING, Rank.QUEEN, Rank.JACK).flatMap { rank ->
        Suit.entries.map { suit ->
            val head = headOf(rank)
            val jack = rank == Rank.JACK
            val steel = propOf(rank, suit) != CardGlyphs.FLOWER_FAN.id
            CourtFigure(
                id = "${rank.code}${suit.code}".lowercase() + "_" + head.id,
                rank = rank,
                suit = suit,
                head = head,
                headGlyphId = head.glyphId,
                robeGlyphId = if (jack) CardGlyphs.ROBE_TUNIC.id else CardGlyphs.ROBE_ROYAL.id,
                propGlyphId = propOf(rank, suit),
                hasBeard = rank == Rank.KING,
                hasHair = rank == Rank.QUEEN,
                accentArgb = accentOf(suit),
                headScale = if (jack) 0.24f else 0.26f,
                headDressScale = when (rank) {
                    Rank.KING -> 0.32f
                    Rank.QUEEN -> 0.30f
                    else -> 0.29f
                },
                robeWidth = if (jack) 0.44f else 0.50f,
                robeHeight = if (jack) 0.36f else 0.42f,
                propHeight = if (steel) 0.50f else 0.42f,
                propWidth = if (steel) 0.42f else 0.62f,
                propAngleDeg = when (rank) {
                    Rank.KING -> 14f
                    Rank.QUEEN -> -10f
                    else -> 22f
                },
                medallionScale = if (jack) 0.13f else 0.15f,
            )
        }
    }

    fun of(rank: Rank, suit: Suit): CourtFigure? =
        if (rank != Rank.JACK && rank != Rank.QUEEN && rank != Rank.KING) null
        else ALL.first { it.rank == rank && it.suit == suit }
}

/** Card-back motifs available; all three are original vector designs from this repository. */
enum class CardBackPattern(val id: String, val glyphId: String) {
    GIRIH_LATTICE("girih", CardGlyphs.RHOMBUS.id),
    BOTEH_GARDEN("boteh", CardGlyphs.BOTEH.id),
    EIGHT_STAR("star8", CardGlyphs.STAR8.id),
}

/**
 * One original card back: colours (ARGB), the motif and the geometry of its pattern. The default
 * keeps the HK-015 look that the owner approved; the other two are the "دست‌کم دو پشت اصیل" of
 * GX-03, offered as a player choice.
 */
data class CardBackSpec(
    val id: String,
    val titleFa: String,
    val pattern: CardBackPattern,
    val fieldArgb: Long,
    val fieldEdgeArgb: Long,
    val patternArgb: Long,
    val frameArgb: Long,
    val accentArgb: Long,
    val marginFraction: Float,
    val patternStepFraction: Float,
    val patternAlpha: Float,
    val medallionFraction: Float,
    val motifFraction: Float,
    val accentDotFraction: Float,
)

object HokmCardBacks {
    const val DEFAULT_ID: String = "emerald_girih"

    /** Emerald lattice — the approved HK-015 back, unchanged in spirit (field, frame, star). */
    val EMERALD_GIRIH = CardBackSpec(
        id = DEFAULT_ID,
        titleFa = "گره‌چینی زمردی",
        pattern = CardBackPattern.GIRIH_LATTICE,
        fieldArgb = HokmArtDirection.FELT_SPOTLIGHT,
        fieldEdgeArgb = HokmArtDirection.FELT_EDGE,
        patternArgb = HokmArtDirection.BRASS_LIGHT,
        frameArgb = HokmTokens.Color.GOLD_LIGHT,
        accentArgb = HokmArtDirection.TURQUOISE,
        marginFraction = 0.07f,
        patternStepFraction = 0.11f,
        patternAlpha = 0.55f,
        medallionFraction = 0.2f,
        motifFraction = 0.15f,
        accentDotFraction = 0.045f,
    )

    /** Midnight boteh — GX-01 direction "B" (navy + brass) as an alternative back. */
    val MIDNIGHT_BOTEH = CardBackSpec(
        id = "midnight_boteh",
        titleFa = "بته‌جقهٔ نیمه‌شب",
        pattern = CardBackPattern.BOTEH_GARDEN,
        fieldArgb = HokmArtDirection.NAVY,
        fieldEdgeArgb = 0xFF0A1428,
        patternArgb = HokmArtDirection.BRASS_LIGHT,
        frameArgb = HokmArtDirection.TURQUOISE,
        accentArgb = HokmTokens.Color.GOLD,
        marginFraction = 0.07f,
        patternStepFraction = 0.13f,
        patternAlpha = 0.5f,
        medallionFraction = 0.21f,
        motifFraction = 0.16f,
        accentDotFraction = 0.04f,
    )

    /** Turquoise star — the night sky of the felt: deep emerald field, turquoise star field. */
    val TURQUOISE_STAR = CardBackSpec(
        id = "turquoise_star",
        titleFa = "ستارهٔ فیروزه",
        pattern = CardBackPattern.EIGHT_STAR,
        fieldArgb = HokmArtDirection.FELT_EDGE,
        fieldEdgeArgb = 0xFF041F19,
        patternArgb = HokmArtDirection.TURQUOISE,
        frameArgb = HokmArtDirection.BRASS_LIGHT,
        accentArgb = HokmTokens.Color.GOLD_LIGHT,
        marginFraction = 0.07f,
        patternStepFraction = 0.16f,
        patternAlpha = 0.45f,
        medallionFraction = 0.19f,
        motifFraction = 0.15f,
        accentDotFraction = 0.042f,
    )

    /** All backs, default first — "at least two original backs" of GX-03 is three. */
    val ALL: List<CardBackSpec> = listOf(EMERALD_GIRIH, MIDNIGHT_BOTEH, TURQUOISE_STAR)

    fun byId(id: String?): CardBackSpec = ALL.firstOrNull { it.id == id } ?: EMERALD_GIRIH
}

/**
 * GX-03 — the three presentation choices the player owns, as one small value object so the table
 * can be told what to draw through a single Compose `CompositionLocal` (`LocalHokmCardStyle`,
 * declared next to the painter) instead of threading flags through every call site.
 */
data class HokmCardStyle(
    val deck: HokmDeckStyle = HokmDeckStyle.CLASSIC,
    val jumbo: Boolean = true,
    val back: String = HokmCardBacks.DEFAULT_ID,
) {
    companion object {
        val DEFAULT = HokmCardStyle()
    }
}

/** Corner index of a card: rank glyph, ink and how big it is. */
data class CornerIndexSpec(
    val glyph: String,
    val ink: CardInk,
    val fraction: Float,
    val padFraction: Float,
    val suitScale: Float,
)

/** Edge treatment: bevelled hairline plus the side thickness a stacked card shows. */
data class CardBezelSpec(
    val cornerRadiusFraction: Float,
    val edgeWidthFraction: Float,
    val lightAlpha: Float,
    val shadeAlpha: Float,
    val thicknessFraction: Float,
)

/**
 * Everything that distinguishes one of the 52 faces: the machine-readable "card proof". Two plans
 * are equal if and only if the same card looks the same, which is exactly what the test asserts.
 */
data class CardRenderPlan(
    val code: String,
    val rank: Rank,
    val suit: Suit,
    val ink: CardInk,
    val inkArgb: Long,
    val index: CornerIndexSpec,
    val pips: List<PipSlot>,
    val pipField: PipField,
    val aceOrnate: Boolean,
    val court: CourtFigure?,
    val bezel: CardBezelSpec,
    val deckStyle: HokmDeckStyle,
    val jumbo: Boolean,
) {
    val isCourt: Boolean get() = court != null
}

/** The whole deck, derived deterministically from the rules' `Card`/`Rank`/`Suit` enums. */
object CardFaceCatalog {

    /** All 52 faces in the given presentation, suit-major then rank (a stable, reviewable order). */
    fun all(style: HokmDeckStyle = HokmDeckStyle.CLASSIC, jumbo: Boolean = true): List<CardRenderPlan> =
        Suit.entries.flatMap { suit -> Rank.entries.map { rank -> plan(Card(rank, suit), style, jumbo) } }

    fun plan(card: Card, style: HokmDeckStyle = HokmDeckStyle.CLASSIC, jumbo: Boolean = true): CardRenderPlan {
        val ink = CardInks.of(card.suit, style)
        val indexFraction = CardMetrics.indexFractionFor(card.rank, jumbo)
        return CardRenderPlan(
            code = card.code,
            rank = card.rank,
            suit = card.suit,
            ink = ink,
            inkArgb = CardInks.argb(ink),
            index = CornerIndexSpec(
                glyph = when (card.rank) {
                    Rank.TEN -> "10"
                    else -> card.rank.code.toString()
                },
                ink = ink,
                fraction = indexFraction,
                padFraction = CardMetrics.INDEX_PAD_FRACTION,
                suitScale = CardMetrics.INDEX_SUIT_SCALE,
            ),
            pips = PipLayouts.of(card.rank),
            pipField = CardMetrics.pipField(card.rank, jumbo),
            aceOrnate = card.rank == Rank.ACE,
            court = CourtFigures.of(card.rank, card.suit),
            bezel = CardBezelSpec(
                cornerRadiusFraction = CardMetrics.CORNER_RADIUS_FRACTION,
                edgeWidthFraction = CardMetrics.BEZEL_EDGE_WIDTH_FRACTION,
                lightAlpha = CardMetrics.BEZEL_LIGHT_ALPHA,
                shadeAlpha = CardMetrics.BEZEL_SHADE_ALPHA,
                thicknessFraction = CardMetrics.THICKNESS_FRACTION,
            ),
            deckStyle = style,
            jumbo = jumbo,
        )
    }

    fun plan(code: String, style: HokmDeckStyle = HokmDeckStyle.CLASSIC, jumbo: Boolean = true): CardRenderPlan {
        val card = Card.parse(code) ?: error("'$code' is not a card code")
        return plan(card, style, jumbo)
    }

    /**
     * A stable fingerprint of the *entire* deck in every presentation (both inks, jumbo on/off).
     * The test pins it: any accidental change to a pip spot, an index size, an ink or a court
     * figure turns this red, and the reviewer must then decide on purpose.
     */
    /**
     * A stable fingerprint of the **whole vector deck**: every face in both deck styles at both
     * index sizes (rank, ink, index and pip geometry), every court figure's parts and every glyph
     * outline the figures and backs are built from, plus the three backs.
     *
     * This is the deck's "asset hash" (decision D-10): the artwork is source code, so this 64-bit
     * FNV-1a value identifies it as completely as a file hash would, and `HokmCardFacesTest` pins it.
     * Change any outline, pip position, ink or back colour and the pin fails on purpose.
     */
    fun fingerprint(): String {
        val canonical = buildString {
            for (style in HokmDeckStyle.entries) {
                for (jumbo in listOf(true, false)) {
                    for (plan in all(style, jumbo)) {
                        append(plan.code).append('|').append(plan.ink.id).append('|')
                        append(plan.index.fraction).append('|').append(plan.index.glyph).append('|')
                        plan.pips.forEach { append(it.x).append(',').append(it.y).append(if (it.rotated) 'r' else 'u') }
                        append('|').append(plan.pipField.left).append(',').append(plan.pipField.width).append(',').append(plan.pipField.pipSize)
                        append('|').append(plan.court?.id ?: "-")
                        plan.court?.let { court ->
                            append(':').append(court.headGlyphId).append(',').append(court.robeGlyphId)
                            append(',').append(court.propGlyphId).append(',')
                            append(court.headScale).append(',').append(court.headDressScale).append(',')
                            append(court.robeWidth).append(',').append(court.robeHeight).append(',')
                            append(court.propHeight).append(',').append(court.propWidth).append(',')
                            append(court.propAngleDeg).append(',').append(court.medallionScale).append(',')
                            append(court.accentArgb.toString(16))
                        }
                        append('|').append(plan.bezel.cornerRadiusFraction).append('\n')
                    }
                }
            }
            // the glyph library itself — the outlines, not just the ids
            for (glyph in CardGlyphs.ALL) {
                append(glyph.id).append(':')
                glyph.commands.forEach { command ->
                    when (command) {
                        is GlyphCommand.MoveTo -> append("M${command.x},${command.y}")
                        is GlyphCommand.LineTo -> append("L${command.x},${command.y}")
                        is GlyphCommand.QuadTo -> append("Q${command.cx},${command.cy},${command.x},${command.y}")
                        is GlyphCommand.CurveTo ->
                            append("C${command.c1x},${command.c1y},${command.c2x},${command.c2y},${command.x},${command.y}")

                        GlyphCommand.Close -> append('Z')
                    }
                    append(' ')
                }
                append('\n')
            }
            for (back in HokmCardBacks.ALL) {
                append(back.id).append(':').append(back.pattern.id).append(':')
                append(back.fieldArgb.toString(16)).append(':').append(back.fieldEdgeArgb.toString(16)).append(':')
                append(back.patternArgb.toString(16)).append(':').append(back.frameArgb.toString(16)).append(':')
                append(back.accentArgb.toString(16)).append(':').append(back.marginFraction).append(':')
                append(back.patternStepFraction).append(':').append(back.patternAlpha).append(':')
                append(back.medallionFraction).append(':').append(back.motifFraction).append(':')
                append(back.accentDotFraction).append('\n')
            }
        }
        // FNV-1a 64-bit — small, dependency-free and stable across platforms.
        var hash = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
        for (ch in canonical) {
            hash = hash xor ch.code.toLong()
            hash *= 0x100000001b3L
        }
        return hash.toULong().toString(16).padStart(16, '0')
    }
}
