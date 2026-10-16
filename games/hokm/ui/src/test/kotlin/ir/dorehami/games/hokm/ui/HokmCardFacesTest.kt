package ir.dorehami.games.hokm.ui

import ir.dorehami.games.hokm.Card
import ir.dorehami.games.hokm.Rank
import ir.dorehami.games.hokm.Suit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * GX-03 guard — the acceptance criterion "rank/suit error = zero" as a machine check.
 *
 * The deck is a pure catalogue, so it can be audited without a device: every one of the 52 faces
 * exists exactly once, carries the right rank glyph and pip count, the right ink for the chosen
 * deck, a measurably different court figure, and every ink passes AA on the shipped stock. The
 * fingerprint pins the *whole* deck so an accidental edit to a pip spot, an index size or a figure
 * turns the suite red and forces a deliberate review.
 */
class HokmCardFacesTest {

    @Test
    fun `the deck holds all 52 faces exactly once`() {
        val codes = CardFaceCatalog.all().map { it.code }
        assertEquals(52, codes.size)
        assertEquals(52, codes.toSet().size)
        val expected = Suit.entries.flatMap { suit -> Rank.entries.map { rank -> Card(rank, suit).code } }.toSet()
        assertEquals(expected, codes.toSet())
    }

    @Test
    fun `every face prints the right rank glyph and the right number of pips`() {
        // Rank glyphs are what a player reads first: A K Q J 10 9 … 2
        val expectedGlyph = mapOf(
            Rank.ACE to "A",
            Rank.KING to "K",
            Rank.QUEEN to "Q",
            Rank.JACK to "J",
            Rank.TEN to "10",
            Rank.NINE to "9",
            Rank.EIGHT to "8",
            Rank.SEVEN to "7",
            Rank.SIX to "6",
            Rank.FIVE to "5",
            Rank.FOUR to "4",
            Rank.THREE to "3",
            Rank.TWO to "2",
        )
        CardFaceCatalog.all().forEach { plan ->
            assertEquals(expectedGlyph[plan.rank], plan.index.glyph, "${plan.code}: wrong corner glyph")
            assertEquals(Card.parse(plan.code)!!.suit, plan.suit, "${plan.code}: suit must survive the mapping")
            when (plan.rank) {
                Rank.ACE -> {
                    assertTrue(plan.pips.isEmpty(), "${plan.code}: an ace has no pips")
                    assertTrue(plan.aceOrnate, "${plan.code}: the ace must use the ornate symbol")
                }

                Rank.KING, Rank.QUEEN, Rank.JACK -> {
                    assertTrue(plan.pips.isEmpty(), "${plan.code}: a court card has no pips")
                    assertTrue(plan.isCourt, "${plan.code}: a court card needs a figure")
                }

                else -> {
                    assertEquals(plan.rank.value, plan.pips.size, "${plan.code}: pip count must equal the rank")
                    assertTrue(plan.court == null, "${plan.code}: a number card has no figure")
                    assertTrue(!plan.aceOrnate, "${plan.code}: only the ace is ornate")
                }
            }
        }
    }

    @Test
    fun `suit identity is encoded in the plan, not guessed by the painter`() {
        CardFaceCatalog.all().forEach { plan ->
            val card = Card.parse(plan.code)!!
            assertEquals(card.suit, plan.suit, "${plan.code}: suit drifted from the rules enum")
            assertEquals(card.rank, plan.rank, "${plan.code}: rank drifted from the rules enum")
        }
        // The four suits must be visually distinguishable: each has its own glyph id.
        val ids = Suit.entries.map { CardGlyphs.suit(it).id }
        assertEquals(4, ids.toSet().size)
    }

    @Test
    fun `classic deck inks two colours and four colour deck inks four`() {
        val classic = CardFaceCatalog.all(HokmDeckStyle.CLASSIC).groupBy { it.ink }.keys
        assertEquals(setOf(CardInk.BLACK, CardInk.RED), classic)
        val four = CardFaceCatalog.all(HokmDeckStyle.FOUR_COLOR).groupBy { it.ink }.keys
        assertEquals(setOf(CardInk.BLACK, CardInk.RED, CardInk.BLUE, CardInk.GREEN), four)

        // And the mapping is the conventional one.
        fun ink(style: HokmDeckStyle, suit: Suit) = CardInks.of(suit, style)
        assertEquals(CardInk.BLACK, ink(HokmDeckStyle.CLASSIC, Suit.SPADES))
        assertEquals(CardInk.RED, ink(HokmDeckStyle.CLASSIC, Suit.HEARTS))
        assertEquals(CardInk.RED, ink(HokmDeckStyle.CLASSIC, Suit.DIAMONDS))
        assertEquals(CardInk.BLACK, ink(HokmDeckStyle.CLASSIC, Suit.CLUBS))
        assertEquals(CardInk.BLUE, ink(HokmDeckStyle.FOUR_COLOR, Suit.DIAMONDS))
        assertEquals(CardInk.GREEN, ink(HokmDeckStyle.FOUR_COLOR, Suit.CLUBS))
    }

    @Test
    fun `every ink is readable on the card stock and on the felt`() {
        CardInk.entries.forEach { ink ->
            val onStock = HokmArtDirection.contrastRatio(CardInks.argb(ink), HokmTokens.Color.CARD_IVORY)
            assertTrue(onStock >= 4.5, "$ink on the ivory stock is only ${"%.2f".format(onStock)}:1")
            val onFelt = HokmArtDirection.contrastRatio(CardInks.onDark(ink), HokmTokens.Color.FELT_EDGE)
            assertTrue(onFelt >= 4.5, "$ink on the dark felt is only ${"%.2f".format(onFelt)}:1")
        }
    }

    @Test
    fun `jumbo and standard indices are both inside the readable band`() {
        val jumbo = CardMetrics.indexFraction(jumbo = true)
        val standard = CardMetrics.indexFraction(jumbo = false)
        assertTrue(jumbo >= 0.24f, "GX-01 locks the jumbo index at 24% of the card width or more")
        assertTrue(standard >= 0.17f, "even the standard index must stay legible")
        assertTrue(jumbo > standard, "the jumbo setting must actually be bigger")
        // No face may exceed the jumbo lock, and no face may shrink out of recognition. The `10` is
        // deliberately smaller than the rest — a two-glyph index at 27% would eat the pip field.
        Rank.entries.forEach { rank ->
            val jumboTen = CardMetrics.indexFractionFor(rank, jumbo = true)
            assertTrue(jumboTen <= jumbo, "$rank: index bigger than the jumbo lock")
            assertTrue(jumboTen >= 0.13f, "$rank: index too small to read")
            if (rank != Rank.TEN) assertEquals(jumbo, jumboTen, "$rank must use the full jumbo index")
            val standardTen = CardMetrics.indexFractionFor(rank, jumbo = false)
            assertTrue(standardTen in 0.1f..standard, "$rank: standard index out of the band")
        }
    }

    @Test
    fun `the corner index never collides with the pip field`() {
        // The measured worst case: the two-glyph `10`. Its index must end before the left pip
        // column begins, in both the jumbo and the standard presentation, on every rank.
        Rank.entries.forEach { rank ->
            listOf(true, false).forEach { jumbo ->
                val indexRight = CardMetrics.INDEX_PAD_FRACTION + CardMetrics.indexWidthFraction(rank, jumbo)
                val field = CardMetrics.pipField(rank, jumbo)
                val leftColumnEdge = field.left + field.width * 0.26f - field.pipSize / 2f
                assertTrue(
                    leftColumnEdge >= indexRight,
                    "$rank (jumbo=$jumbo): the index ends at ${"%.3f".format(indexRight)} but the " +
                        "first pip column starts at ${"%.3f".format(leftColumnEdge)}",
                )
                // Mirror symmetry: the right column must not run into the mirrored index either.
                val rightColumnEdge = field.left + field.width * 0.74f + field.pipSize / 2f
                assertTrue(
                    1f - rightColumnEdge >= indexRight,
                    "$rank (jumbo=$jumbo): the right pip column runs into the mirrored index",
                )
            }
        }
    }

    @Test
    fun `every court figure differs from the other eleven`() {
        val figures = CourtFigures.ALL
        assertEquals(12, figures.size)
        assertEquals(12, figures.map { it.id }.toSet().size, "figure ids must be unique")
        assertEquals(12, figures.map { "${it.rank}${it.suit}" }.toSet().size, "one figure per court card")

        // Rank silhouette: king = crown + beard, queen = tiara + hair, jack = cap, no beard/hair.
        figures.forEach { f ->
            when (f.rank) {
                Rank.KING -> {
                    assertEquals(CourtHead.KING_CROWN, f.head)
                    assertTrue(f.hasBeard && !f.hasHair, "${f.id}: a king wears a beard, not hair")
                }

                Rank.QUEEN -> {
                    assertEquals(CourtHead.QUEEN_TIARA, f.head)
                    assertTrue(f.hasHair && !f.hasBeard, "${f.id}: a queen wears hair, not a beard")
                }

                else -> {
                    assertEquals(CourtHead.JACK_CAP, f.head)
                    assertTrue(!f.hasHair && !f.hasBeard, "${f.id}: a jack is bare-headed")
                }
            }
        }

        // Suit accent: four different accents, one per suit, so the suit is legible in the figure too.
        val accentsBySuit = figures.groupBy { it.suit }.mapValues { (_, list) ->
            list.map { it.accentArgb }.toSet()
        }
        accentsBySuit.forEach { (suit, accents) ->
            assertEquals(1, accents.size, "$suit must have exactly one accent colour")
        }
        assertEquals(4, accentsBySuit.values.map { it.first() }.toSet().size, "each suit needs its own accent")
    }

    @Test
    fun `kings queens and jacks hold different props`() {
        val kings = CourtFigures.ALL.filter { it.rank == Rank.KING }.map { it.propGlyphId }
        val queens = CourtFigures.ALL.filter { it.rank == Rank.QUEEN }.map { it.propGlyphId }
        val jacks = CourtFigures.ALL.filter { it.rank == Rank.JACK }.map { it.propGlyphId }
        // A king never holds a flower, a queen never holds the halberd, a jack never holds a sceptre:
        assertEquals(0, kings.count { it == CardGlyphs.FLOWER_FAN.id })
        assertEquals(0, queens.count { it == CardGlyphs.HALBERD.id })
        assertEquals(0, jacks.count { it == CardGlyphs.SCEPTRE.id })
        // And the props are all real glyphs.
        (kings + queens + jacks).forEach { id -> CardGlyphs.byId(id) }
    }

    @Test
    fun `there are at least two original backs and the approved one is the default`() {
        assertTrue(HokmCardBacks.ALL.size >= 2, "GX-03 requires at least two original backs")
        assertEquals(HokmCardBacks.EMERALD_GIRIH, HokmCardBacks.byId(HokmCardBacks.DEFAULT_ID))
        assertEquals(HokmCardBacks.DEFAULT_ID, HokmCardStyle.DEFAULT.back)
        assertEquals(3, HokmCardBacks.ALL.map { it.id }.toSet().size, "back ids must be unique")
        HokmCardBacks.ALL.forEach { back ->
            assertTrue(back.marginFraction in 0.04f..0.12f, "${back.id}: margin out of range")
            assertTrue(back.patternAlpha in 0.3f..0.8f, "${back.id}: pattern too faint or too loud")
            assertTrue(back.motifFraction in 0.08f..0.25f, "${back.id}: centre motif out of range")
            // Frame and centre motif have to be readable against the field and the medallion.
            assertTrue(
                HokmArtDirection.contrastRatio(back.frameArgb, back.fieldArgb) >= 3.0,
                "${back.id}: the frame barely shows on the bright centre of its field",
            )
            assertTrue(
                HokmArtDirection.contrastRatio(back.frameArgb, back.fieldEdgeArgb) >= 3.0,
                "${back.id}: the centre motif vanishes on the medallion",
            )
            assertTrue(
                HokmArtDirection.contrastRatio(back.accentArgb, back.fieldEdgeArgb) >= 2.0,
                "${back.id}: the accent dot disappears",
            )
            CardGlyphs.byId(back.pattern.glyphId)
        }
    }

    @Test
    fun `every glyph is closed, inside its unit box and not degenerate`() {
        CardGlyphs.ALL.forEach { glyph ->
            assertTrue(glyph.isClosed(), "${glyph.id}: every subpath must be closed")
            assertTrue(glyph.subpaths() >= 1, "${glyph.id}: needs at least one subpath")
            val bounds = glyph.bounds()
            assertTrue(bounds.minX >= -0.06f && bounds.minY >= -0.06f, "${glyph.id}: escapes the unit box (min)")
            assertTrue(bounds.maxX <= 1.06f && bounds.maxY <= 1.12f, "${glyph.id}: escapes the unit box (max)")
            assertTrue(bounds.width >= 0.1f && bounds.height >= 0.05f, "${glyph.id}: degenerate bounds")
            assertTrue(glyph.anchorArea() >= 0.01f, "${glyph.id}: degenerate area")
            val pts = glyph.points()
            assertTrue(pts.all { it.first.isFinite() && it.second.isFinite() }, "${glyph.id}: NaN in a control point")
        }
    }

    @Test
    fun `glyph ids referenced by the catalogue all exist`() {
        CourtFigures.ALL.forEach { figure ->
            CardGlyphs.byId(figure.headGlyphId)
            CardGlyphs.byId(figure.robeGlyphId)
            CardGlyphs.byId(figure.propGlyphId)
            assertTrue(CardGlyphs.HEAD.id.isNotEmpty())
        }
        CardGlyphs.ALL.map { it.id }.forEach { id ->
            assertNotEquals("", id.trim(), "glyph ids must not be blank")
        }
        CardGlyphs.ALL.groupBy { it.id }.forEach { (id, list) ->
            assertEquals(1, list.size, "glyph id '$id' is used twice")
        }
    }

    @Test
    fun `the edge treatment matches the shipped geometry`() {
        val plan = CardFaceCatalog.plan("AS")
        assertEquals(CardMetrics.CORNER_RADIUS_FRACTION, plan.bezel.cornerRadiusFraction)
        assertEquals(0.7f, CardMetrics.ASPECT)
        assertTrue(plan.bezel.thicknessFraction in 0.005f..0.05f, "side thickness must be a visible sliver")
        assertTrue(plan.bezel.edgeWidthFraction > 0f)
    }

    @Test
    fun `the whole deck fingerprint is pinned`() {
        // If this fails, the vector deck *changed* — outlines, pips, inks, figures or backs.
        // Review the diff in `games/hokm/design/gx03` (re-run `tools/hokm/cardsheet.sh`), then
        // update the pin deliberately.
        assertEquals("808a1ee5174fd0ec", CardFaceCatalog.fingerprint())
    }
}
