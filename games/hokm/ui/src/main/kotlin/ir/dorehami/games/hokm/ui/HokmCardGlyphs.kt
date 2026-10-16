package ir.dorehami.games.hokm.ui

import ir.dorehami.games.hokm.Suit

/**
 * GX-03 — card glyph geometry as *data*, not as drawing code.
 *
 * Every symbol on a card (the four suits, the crown/tiara/cap of the court figures, the robe,
 * the head, the props and the card-back motifs) is described here in unit space `0..1 × 0..1`
 * as a list of drawing commands. Three consumers read the very same list:
 *
 * * the Compose table ([PlayingCardView]) converts it to an `androidx.compose.ui.graphics.Path`;
 * * the review-sheet generator (`tools/hokm/cardsheet`) converts it to an SVG `d` attribute, so
 *   the owner can look at all 52 cards without building an APK;
 * * the JVM suite ([HokmCardGlyphsTest]) can *measure* the glyphs — inside the unit box, closed,
 *   non-degenerate area, distinct silhouettes.
 *
 * That is why this file is pure Kotlin: one source of truth for the artwork, no raster assets,
 * nothing to license, crisp at any size.
 *
 * Winding: all subpaths are emitted clockwise in a y-down coordinate system, so overlapping
 * subpaths (the spade stem, the club joint) union correctly under the non-zero fill rule used by
 * both Compose and SVG.
 */
sealed interface GlyphCommand {
    data class MoveTo(val x: Float, val y: Float) : GlyphCommand
    data class LineTo(val x: Float, val y: Float) : GlyphCommand
    data class QuadTo(val cx: Float, val cy: Float, val x: Float, val y: Float) : GlyphCommand
    data class CurveTo(
        val c1x: Float,
        val c1y: Float,
        val c2x: Float,
        val c2y: Float,
        val x: Float,
        val y: Float,
    ) : GlyphCommand

    data object Close : GlyphCommand
}

/** Axis-aligned bounds in unit space. */
data class GlyphBounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
}

/**
 * One closed (or multi-subpath) outline in unit space.
 *
 * [id] is stable and appears in tests and in the review sheets, so a reviewer can point at
 * "the sword of the king of spades" instead of "that line".
 */
data class CardGlyph(val id: String, val commands: List<GlyphCommand>) {

    /** Every anchor *and* control point — enough to bound a Bézier conservatively. */
    fun points(): List<Pair<Float, Float>> = commands.flatMap { c ->
        when (c) {
            is GlyphCommand.MoveTo -> listOf(c.x to c.y)
            is GlyphCommand.LineTo -> listOf(c.x to c.y)
            is GlyphCommand.QuadTo -> listOf(c.cx to c.cy, c.x to c.y)
            is GlyphCommand.CurveTo -> listOf(c.c1x to c.c1y, c.c2x to c.c2y, c.x to c.y)
            GlyphCommand.Close -> emptyList()
        }
    }

    fun bounds(): GlyphBounds {
        val pts = points()
        require(pts.isNotEmpty()) { "glyph '$id' has no points" }
        return GlyphBounds(
            minX = pts.minOf { it.first },
            minY = pts.minOf { it.second },
            maxX = pts.maxOf { it.first },
            maxY = pts.maxOf { it.second },
        )
    }

    /** True when the shape returns to its start (every subpath closed). */
    fun isClosed(): Boolean = commands.lastOrNull() == GlyphCommand.Close

    /** Number of subpaths (`MoveTo`s). */
    fun subpaths(): Int = commands.count { it is GlyphCommand.MoveTo }

    /**
     * Rough unsigned area of the outline (shoelace over every anchor *and* control point, summed
     * per subpath). Control points are kept in the polygon on purpose: a shape whose only corners
     * are Bézier controls (the jack's cap, for instance) is far from degenerate, and this estimate
     * must not call it empty. Never used for drawing.
     */
    fun anchorArea(): Float {
        var total = 0f
        var current = ArrayList<Pair<Float, Float>>()
        fun flush() {
            if (current.size >= 3) {
                var sum = 0f
                for (i in current.indices) {
                    val (x1, y1) = current[i]
                    val (x2, y2) = current[(i + 1) % current.size]
                    sum += x1 * y2 - x2 * y1
                }
                total += kotlin.math.abs(sum) / 2f
            }
            current = ArrayList()
        }
        for (c in commands) {
            when (c) {
                is GlyphCommand.MoveTo -> {
                    flush()
                    current.add(c.x to c.y)
                }
                is GlyphCommand.LineTo -> current.add(c.x to c.y)
                is GlyphCommand.QuadTo -> {
                    current.add(c.cx to c.cy)
                    current.add(c.x to c.y)
                }
                is GlyphCommand.CurveTo -> {
                    current.add(c.c1x to c.c1y)
                    current.add(c.c2x to c.c2y)
                    current.add(c.x to c.y)
                }
                GlyphCommand.Close -> flush()
            }
        }
        flush()
        return total
    }
}

/** Small builder so the glyphs below read like the artwork they describe. */
private class GlyphBuilder(val id: String) {
    private val out = ArrayList<GlyphCommand>()
    fun move(x: Float, y: Float) = apply { out += GlyphCommand.MoveTo(x, y) }
    fun line(x: Float, y: Float) = apply { out += GlyphCommand.LineTo(x, y) }
    fun quad(cx: Float, cy: Float, x: Float, y: Float) = apply { out += GlyphCommand.QuadTo(cx, cy, x, y) }
    fun curve(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) =
        apply { out += GlyphCommand.CurveTo(c1x, c1y, c2x, c2y, x, y) }

    fun close() = apply { out += GlyphCommand.Close }

    /** Circle as four clockwise cubics (κ = 0.5523) — identical geometry in Compose and SVG. */
    fun circle(cx: Float, cy: Float, r: Float) = apply {
        val k = r * 0.5523f
        move(cx + r, cy)
        curve(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
        curve(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
        curve(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
        curve(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
        close()
    }

    /** Regular polygon with [sides] vertices, first vertex pointing up. */
    fun polygon(cx: Float, cy: Float, r: Float, sides: Int, rotationDeg: Float = 0f) = apply {
        val step = (2.0 * Math.PI / sides).toFloat()
        val start = (-Math.PI / 2 + Math.toRadians(rotationDeg.toDouble())).toFloat()
        for (i in 0 until sides) {
            val a = start + step * i
            val x = cx + r * kotlin.math.cos(a)
            val y = cy + r * kotlin.math.sin(a)
            if (i == 0) move(x, y) else line(x, y)
        }
        close()
    }

    /**
     * Eight-pointed star. [inner] is the waist-to-tip ratio: 0.58 gives the two-square girih star
     * used on the backs, ~0.42 gives the sharper jewelled star used on the sceptre.
     */
    fun star8(cx: Float, cy: Float, outer: Float, inner: Float = 0.58f) = apply {
        val waist = outer * inner
        for (i in 0 until 16) {
            val a = (-Math.PI / 2 + i * Math.PI / 8).toFloat()
            val r = if (i % 2 == 0) outer else waist
            val x = cx + r * kotlin.math.cos(a)
            val y = cy + r * kotlin.math.sin(a)
            if (i == 0) move(x, y) else line(x, y)
        }
        close()
    }

    fun build(): CardGlyph = CardGlyph(id, out.toList())
}

/**
 * The artwork library. Ids are stable API: the review sheet and the tests name glyphs by id.
 */
object CardGlyphs {

    // ------------------------------------------------------------------ suits (shipped look)

    /** The four suit symbols — the exact outlines the table has always drawn. */
    fun suit(suit: Suit): CardGlyph = when (suit) {
        Suit.HEARTS -> GlyphBuilder("suit_hearts")
            .move(0.5f, 0.95f)
            .curve(0.1f, 0.62f, 0.0f, 0.42f, 0.05f, 0.26f)
            .curve(0.1f, 0.08f, 0.36f, 0.02f, 0.5f, 0.26f)
            .curve(0.64f, 0.02f, 0.9f, 0.08f, 0.95f, 0.26f)
            .curve(1.0f, 0.42f, 0.9f, 0.62f, 0.5f, 0.95f)
            .close()
            .build()

        Suit.DIAMONDS -> GlyphBuilder("suit_diamonds")
            .move(0.5f, 0.02f)
            .quad(0.72f, 0.32f, 0.95f, 0.5f)
            .quad(0.72f, 0.68f, 0.5f, 0.98f)
            .quad(0.28f, 0.68f, 0.05f, 0.5f)
            .quad(0.28f, 0.32f, 0.5f, 0.02f)
            .close()
            .build()

        Suit.SPADES -> GlyphBuilder("suit_spades")
            .move(0.5f, 0.02f)
            .curve(0.86f, 0.34f, 1.0f, 0.5f, 0.95f, 0.66f)
            .curve(0.9f, 0.84f, 0.64f, 0.9f, 0.52f, 0.68f)
            .line(0.48f, 0.68f)
            .curve(0.36f, 0.9f, 0.1f, 0.84f, 0.05f, 0.66f)
            .curve(0.0f, 0.5f, 0.14f, 0.34f, 0.5f, 0.02f)
            .close()
            .move(0.5f, 0.62f)
            .curve(0.52f, 0.8f, 0.6f, 0.9f, 0.7f, 0.98f)
            .line(0.3f, 0.98f)
            .curve(0.4f, 0.9f, 0.48f, 0.8f, 0.5f, 0.62f)
            .close()
            .build()

        Suit.CLUBS -> GlyphBuilder("suit_clubs")
            .circle(0.5f, 0.25f, 0.22f)
            .circle(0.26f, 0.58f, 0.22f)
            .circle(0.74f, 0.58f, 0.22f)
            .move(0.5f, 0.5f)
            .curve(0.52f, 0.78f, 0.6f, 0.9f, 0.7f, 0.98f)
            .line(0.3f, 0.98f)
            .curve(0.4f, 0.9f, 0.48f, 0.78f, 0.5f, 0.5f)
            .close()
            .build()
    }

    // ------------------------------------------------------------------ court heads

    /** King: tall five-point crown with a jewelled band. */
    val CROWN: CardGlyph = GlyphBuilder("crown_king")
        .move(0.5f, 0.02f)
        .line(0.63f, 0.34f)
        .line(0.78f, 0.12f)
        .line(0.86f, 0.52f)
        .line(0.98f, 0.34f)
        .line(0.9f, 0.86f)
        .line(0.1f, 0.86f)
        .line(0.02f, 0.34f)
        .line(0.14f, 0.52f)
        .line(0.22f, 0.12f)
        .line(0.37f, 0.34f)
        .close()
        .build()

    /** Queen: a low tiara — an arc band with three points and a jewel at the crest. */
    val TIARA: CardGlyph = GlyphBuilder("crown_queen")
        .move(0.06f, 0.96f)
        .line(0.10f, 0.46f)
        .quad(0.22f, 0.34f, 0.30f, 0.10f)
        .quad(0.38f, 0.36f, 0.50f, 0.20f)
        .quad(0.62f, 0.36f, 0.70f, 0.10f)
        .quad(0.78f, 0.34f, 0.90f, 0.46f)
        .line(0.94f, 0.96f)
        .close()
        .build()

    /** Jack: a flat cap with a single feather. */
    val CAP: CardGlyph = GlyphBuilder("cap_jack")
        .move(0.06f, 0.66f)
        .quad(0.5f, 0.18f, 0.94f, 0.62f)
        .close()
        .move(0.62f, 0.3f)
        .curve(0.94f, 0.02f, 1.04f, 0.16f, 0.98f, 0.42f)
        .curve(0.9f, 0.3f, 0.76f, 0.26f, 0.62f, 0.3f)
        .close()
        .build()

    /** The face: a clean oval, wide enough for a beard or a lock of hair to read at size. */
    val HEAD: CardGlyph = GlyphBuilder("head")
        .move(0.5f, 0.0f)
        .curve(0.78f, 0.0f, 0.94f, 0.24f, 0.94f, 0.5f)
        .curve(0.94f, 0.78f, 0.76f, 1.0f, 0.5f, 1.0f)
        .curve(0.24f, 1.0f, 0.06f, 0.78f, 0.06f, 0.5f)
        .curve(0.06f, 0.24f, 0.22f, 0.0f, 0.5f, 0.0f)
        .close()
        .build()

    /** King: full beard covering the chin, with a moustache band. */
    val BEARD: CardGlyph = GlyphBuilder("beard_king")
        .move(0.12f, 0.42f)
        .curve(0.2f, 0.9f, 0.36f, 1.06f, 0.5f, 1.06f)
        .curve(0.64f, 1.06f, 0.8f, 0.9f, 0.88f, 0.42f)
        .curve(0.72f, 0.6f, 0.28f, 0.6f, 0.12f, 0.42f)
        .close()
        .build()

    /** Queen: two long locks of hair framing the face. */
    val HAIR: CardGlyph = GlyphBuilder("hair_queen")
        .move(0.04f, 0.5f)
        .curve(0.0f, 0.86f, 0.1f, 1.06f, 0.3f, 1.08f)
        .curve(0.16f, 0.86f, 0.14f, 0.66f, 0.2f, 0.42f)
        .close()
        .move(0.96f, 0.5f)
        .curve(1.0f, 0.86f, 0.9f, 1.06f, 0.7f, 1.08f)
        .curve(0.84f, 0.86f, 0.86f, 0.66f, 0.8f, 0.42f)
        .close()
        .build()

    // ------------------------------------------------------------------ court bodies

    /** King/queen robe: broad shoulders, flared skirt, a belt line at the waist. */
    val ROBE_ROYAL: CardGlyph = GlyphBuilder("robe_royal")
        .move(0.5f, 0.0f)
        .line(0.86f, 0.1f)
        .line(0.98f, 0.42f)
        .line(0.86f, 0.56f)
        .line(0.94f, 1.0f)
        .line(0.06f, 1.0f)
        .line(0.14f, 0.56f)
        .line(0.02f, 0.42f)
        .line(0.14f, 0.1f)
        .close()
        .build()

    /** Jack: a slimmer tunic with a diagonal sash cut. */
    val ROBE_TUNIC: CardGlyph = GlyphBuilder("robe_tunic")
        .move(0.5f, 0.0f)
        .line(0.8f, 0.12f)
        .line(0.88f, 0.5f)
        .line(0.8f, 1.0f)
        .line(0.2f, 1.0f)
        .line(0.12f, 0.5f)
        .line(0.2f, 0.12f)
        .close()
        .build()

    /** Chest medallion: a ring that carries the suit symbol on the robe. */
    val MEDALLION: CardGlyph = GlyphBuilder("medallion")
        .circle(0.5f, 0.5f, 0.5f)
        .build()

    // ------------------------------------------------------------------ props

    /** Straight sword: blade, cross-guard, grip, pommel. */
    val SWORD: CardGlyph = GlyphBuilder("prop_sword")
        .move(0.5f, 0.0f)
        .line(0.6f, 0.12f)
        .line(0.59f, 0.66f)
        .line(0.41f, 0.66f)
        .line(0.4f, 0.12f)
        .close()
        .move(0.18f, 0.68f)
        .line(0.82f, 0.68f)
        .line(0.82f, 0.78f)
        .line(0.18f, 0.78f)
        .close()
        .move(0.42f, 0.78f)
        .line(0.58f, 0.78f)
        .line(0.58f, 0.95f)
        .line(0.42f, 0.95f)
        .close()
        .circle(0.5f, 0.96f, 0.06f)
        .build()

    /** Battle axe: a shaft with one broad crescent blade and a short top spike. */
    val AXE: CardGlyph = GlyphBuilder("prop_axe")
        .move(0.44f, 0.04f)
        .line(0.52f, 0.0f)
        .line(0.60f, 0.04f)
        .line(0.60f, 0.24f)
        .line(0.52f, 0.20f)
        .line(0.44f, 0.24f)
        .close()
        .move(0.42f, 0.0f)
        .line(0.58f, 0.0f)
        .line(0.58f, 1.0f)
        .line(0.42f, 1.0f)
        .close()
        .move(0.58f, 0.20f)
        .curve(0.99f, 0.24f, 0.96f, 0.62f, 0.56f, 0.70f)
        .curve(0.72f, 0.56f, 0.72f, 0.34f, 0.54f, 0.20f)
        .close()
        .build()

    /** Sceptre: a staff crowned with a jewelled eight-point star. */
    val SCEPTRE: CardGlyph = GlyphBuilder("prop_sceptre")
        .move(0.42f, 0.22f)
        .line(0.58f, 0.22f)
        .line(0.58f, 1.0f)
        .line(0.42f, 1.0f)
        .close()
        .star8(0.5f, 0.14f, 0.14f, inner = 0.42f)
        .build()

    /** Halberd: long shaft, a spear tip, one tall blade and a back hook. */
    val HALBERD: CardGlyph = GlyphBuilder("prop_halberd")
        .move(0.42f, 0.0f)
        .line(0.58f, 0.0f)
        .line(0.58f, 1.0f)
        .line(0.42f, 1.0f)
        .close()
        .move(0.5f, 0.0f)
        .line(0.66f, 0.16f)
        .line(0.5f, 0.13f)
        .line(0.38f, 0.16f)
        .close()
        .move(0.54f, 0.13f)
        .curve(0.94f, 0.2f, 0.92f, 0.52f, 0.56f, 0.62f)
        .line(0.62f, 0.46f)
        .line(0.62f, 0.28f)
        .line(0.56f, 0.13f)
        .close()
        .move(0.46f, 0.22f)
        .curve(0.28f, 0.24f, 0.26f, 0.4f, 0.46f, 0.44f)
        .close()
        .build()

    /** Fan of flowers: three buds and leaves on one stem — the queen's soft prop. */
    val FLOWER_FAN: CardGlyph = GlyphBuilder("prop_flower_fan")
        // stem
        .move(0.45f, 0.42f)
        .line(0.55f, 0.42f)
        .line(0.55f, 1.0f)
        .line(0.45f, 1.0f)
        .close()
        // centre bud
        .move(0.5f, 0.0f)
        .curve(0.64f, 0.06f, 0.64f, 0.26f, 0.5f, 0.3f)
        .curve(0.36f, 0.26f, 0.36f, 0.06f, 0.5f, 0.0f)
        .close()
        // left bud
        .move(0.24f, 0.14f)
        .curve(0.36f, 0.16f, 0.4f, 0.34f, 0.3f, 0.42f)
        .curve(0.16f, 0.4f, 0.12f, 0.22f, 0.24f, 0.14f)
        .close()
        // right bud
        .move(0.76f, 0.14f)
        .curve(0.88f, 0.22f, 0.84f, 0.4f, 0.7f, 0.42f)
        .curve(0.6f, 0.34f, 0.64f, 0.16f, 0.76f, 0.14f)
        .close()
        // leaves
        .move(0.28f, 0.5f)
        .curve(0.12f, 0.56f, 0.1f, 0.78f, 0.2f, 0.92f)
        .curve(0.34f, 0.78f, 0.36f, 0.6f, 0.28f, 0.5f)
        .close()
        .move(0.72f, 0.5f)
        .curve(0.88f, 0.56f, 0.9f, 0.78f, 0.8f, 0.92f)
        .curve(0.66f, 0.78f, 0.64f, 0.6f, 0.72f, 0.5f)
        .close()
        .build()

    // ------------------------------------------------------------------ motifs

    /** Card-back lattice cell. */
    val RHOMBUS: CardGlyph = GlyphBuilder("motif_rhombus")
        .move(0.5f, 0.0f)
        .line(1.0f, 0.5f)
        .line(0.5f, 1.0f)
        .line(0.0f, 0.5f)
        .close()
        .build()

    /** Card-back centre star. */
    val STAR8: CardGlyph = GlyphBuilder("motif_star8")
        .star8(0.5f, 0.5f, 0.5f)
        .build()

    /** Sharper eight-pointed star for the sceptre's head (jewelled, not the girih waist). */
    val STAR8_SHARP: CardGlyph = GlyphBuilder("motif_star8_sharp")
        .star8(0.5f, 0.5f, 0.5f, inner = 0.42f)
        .build()

    /** Chenar leaf (بته) used by the second back — a Persian motif drawn from scratch. */
    val BOTEH: CardGlyph = GlyphBuilder("motif_boteh")
        .move(0.5f, 0.98f)
        .curve(0.02f, 0.6f, 0.24f, 0.06f, 0.66f, 0.04f)
        .curve(0.62f, 0.34f, 0.78f, 0.56f, 0.5f, 0.98f)
        .close()
        .build()

    /** Props that can appear in a court figure's hand. */
    val PROPS: List<CardGlyph> = listOf(SWORD, AXE, SCEPTRE, HALBERD, FLOWER_FAN)

    /** Head-dress options, in the order kings, queens, jacks. */
    val HEAD_DRESS: List<CardGlyph> = listOf(CROWN, TIARA, CAP)

    fun prop(id: String): CardGlyph = PROPS.first { it.id == id }

    /** Every glyph in the library, keyed by its stable id (used by the sheet and the tests). */
    val ALL: List<CardGlyph> = buildList {
        Suit.entries.forEach { add(suit(it)) }
        addAll(listOf(CROWN, TIARA, CAP, HEAD, BEARD, HAIR, ROBE_ROYAL, ROBE_TUNIC, MEDALLION))
        addAll(PROPS)
        addAll(listOf(RHOMBUS, STAR8, STAR8_SHARP, BOTEH))
    }

    private val BY_ID: Map<String, CardGlyph> = ALL.associateBy { it.id }

    fun byId(id: String): CardGlyph = BY_ID[id] ?: error("unknown glyph '$id'")
}
