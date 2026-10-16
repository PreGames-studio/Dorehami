package ir.dorehami.tools.hokm

import ir.dorehami.games.hokm.ui.CardBackDrawing
import ir.dorehami.games.hokm.ui.CardBackSpec
import ir.dorehami.games.hokm.Suit
import ir.dorehami.games.hokm.ui.CardFaceCatalog
import ir.dorehami.games.hokm.ui.CardGlyph
import ir.dorehami.games.hokm.ui.CardGlyphs
import ir.dorehami.games.hokm.ui.CardFaceDrawing
import ir.dorehami.games.hokm.ui.CardMetrics
import ir.dorehami.games.hokm.ui.CardOp
import ir.dorehami.games.hokm.ui.CardRenderPlan
import ir.dorehami.games.hokm.ui.GlyphCommand
import ir.dorehami.games.hokm.ui.GradientKind
import ir.dorehami.games.hokm.ui.GradientSpec
import ir.dorehami.games.hokm.ui.HokmCardBacks
import ir.dorehami.games.hokm.ui.HokmDeckStyle
import ir.dorehami.games.hokm.ui.PlacedGlyph
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * GX-03 review sheet — renders the *very same* op list the app paints ([CardFaceDrawing] /
 * [CardBackDrawing]) into SVG, so the owner can review all 52 faces, both deck styles and the three
 * backs in a browser, without building an APK.
 *
 *   usage: tools/hokm/cardsheet.sh [out-dir]      (default: games/hokm/design/gx03)
 *
 * Paths use the same maths as the Compose painter (unit box → placed box → rotation), so a glyph
 * drawn here is the glyph on the table. Text is the one honest difference: the sheet uses a generic
 * family and an anchored run, the app measures the platform font — same position, different
 * letterform. Nothing here ships in the app; it is review material.
 */
object CardSheet {

    /** Card size on the sheets, in SVG user units. */
    const val CARD_W = 210f
    val CARD_H = CARD_W / CardMetrics.ASPECT

    private const val BG = "#0B2B22"
    private const val LABEL = "#F8F4E9"
    private const val MUTED = "#8FB8A8"
    const val FILL = "#F8F4E9"

    fun svg(body: String, width: Float, height: Float): String =
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $height" width="$width" """ +
            """height="$height" font-family="system-ui, sans-serif">""" +
            """<rect width="$width" height="$height" fill="$BG"/>""" + body + "</svg>"

    /** One card face as an SVG group, drawn from the shared op list. */
    fun card(plan: CardRenderPlan, x: Float, y: Float, scale: Float = 1f, withLabel: Boolean = true): String {
        val name = "کارت ${plan.code}"
        val body = group(CardFaceDrawing.of(plan), x, y, scale, name)
        return if (withLabel) body + label(plan.code, x + CARD_W * scale / 2f, y + CARD_H * scale + 20f * scale) else body
    }

    /** One card back as an SVG group, drawn from the shared op list. */
    fun back(spec: CardBackSpec, x: Float, y: Float, scale: Float = 1f, withLabel: Boolean = true): String {
        val name = "پشت کارت ${spec.titleFa}"
        val body = group(CardBackDrawing.of(spec), x, y, scale, name)
        return if (withLabel) body + label(spec.titleFa, x + CARD_W * scale / 2f, y + CARD_H * scale + 20f * scale) else body
    }

    fun label(text: String, cx: Float, cy: Float): String =
        """<text x="$cx" y="$cy" fill="$LABEL" font-size="15" text-anchor="middle">$text</text>"""

    fun heading(main: String, sub: String, cx: Float): String =
        """<text x="$cx" y="40" fill="$LABEL" font-size="24" text-anchor="middle">$main</text>""" +
            """<text x="$cx" y="64" fill="$MUTED" font-size="14" text-anchor="middle">$sub</text>"""

    private fun group(ops: List<CardOp>, ox: Float, oy: Float, scale: Float, name: String): String = buildString {
        append("""<g role="img" aria-label="$name"><title>$name</title>""")
        append("""<defs>""")
        ops.forEachIndexed { index, op ->
            val gradient = when (op) {
                is CardOp.Rect -> op.fill
                is CardOp.Paths -> op.fill
                else -> null
            }
            if (gradient != null) {
                val id = "g-${name.hashCode().toUInt().toString(16)}-$index"
                append(gradientDef(gradient, id, op, ox, oy, scale))
            }
        }
        append("</defs>")
        ops.forEachIndexed { index, op ->
            append(element(op, ox, oy, scale, "g-${name.hashCode().toUInt().toString(16)}-$index", "b-${name.hashCode().toUInt().toString(16)}-$index"))
        }
        append("</g>")
    }

    private fun gradientDef(
        gradient: GradientSpec,
        id: String,
        op: CardOp,
        ox: Float,
        oy: Float,
        scale: Float,
    ): String = when (gradient.kind) {
        GradientKind.VERTICAL -> """<linearGradient id="$id" x1="0" y1="0" x2="0" y2="1">""" +
            """<stop offset="0" stop-color="${hex(gradient.from)}"/>""" +
            """<stop offset="1" stop-color="${hex(gradient.to)}"/></linearGradient>"""

        GradientKind.RADIAL -> {
            // userSpaceOnUse on the op's own box, so the circle stays a circle on a tall card
            val (x, y, w, h) = when (op) {
                is CardOp.Rect -> listOf(ox + CARD_W * op.left * scale, oy + CARD_H * op.top * scale, CARD_W * op.width * scale, CARD_H * op.height * scale)
                else -> listOf(ox, oy, CARD_W * scale, CARD_H * scale)
            }
            val cx = x + w / 2f
            val cy = y + h / 2f
            val r = maxOf(w, h) * 0.62f
            """<radialGradient id="$id" gradientUnits="userSpaceOnUse" cx="$cx" cy="$cy" r="$r">""" +
                """<stop offset="0" stop-color="${hex(gradient.from)}"/>""" +
                """<stop offset="1" stop-color="${hex(gradient.to)}"/></radialGradient>"""
        }
    }

    private fun element(op: CardOp, ox: Float, oy: Float, scale: Float, gradientId: String, bezelId: String): String = when (op) {
        is CardOp.Rect -> {
            val w = CARD_W * op.width * scale
            val h = CARD_H * op.height * scale
            val x = ox + CARD_W * op.left * scale
            val y = oy + CARD_H * op.top * scale
            val fill = op.fill?.let { "url(#$gradientId)" } ?: op.fillArgb?.let { hex(it) } ?: "none"
            val stroke = op.strokeArgb?.let { """ stroke="${hex(it)}" stroke-width="${CARD_W * op.strokeFraction * scale}"""" } ?: ""
            """<rect x="$x" y="$y" width="$w" height="$h" rx="${CARD_W * op.radius * scale}" """ +
                """fill="$fill"$stroke opacity="${op.alpha}"/>"""
        }

        is CardOp.Paths -> {
            val d = op.shapes.joinToString(" ") { path(it, ox, oy, scale) }
            val fill = op.fill?.let { "url(#$gradientId)" } ?: op.fillArgb?.let { hex(it) } ?: "none"
            val stroke = op.strokeArgb?.let {
                """ stroke="${hex(it)}" stroke-width="${CARD_W * op.strokeFraction * scale}" stroke-linejoin="round""""
            } ?: ""
            """<path d="$d" fill="$fill"$stroke opacity="${op.alpha}" fill-rule="nonzero"/>"""
        }

        is CardOp.Line -> """<line x1="${ox + CARD_W * op.x1 * scale}" y1="${oy + CARD_H * op.y1 * scale}" """ +
            """x2="${ox + CARD_W * op.x2 * scale}" y2="${oy + CARD_H * op.y2 * scale}" """ +
            """stroke="${hex(op.argb)}" stroke-width="${CARD_W * op.strokeFraction * scale}" opacity="${op.alpha}"/>"""

        is CardOp.Dot -> {
            val cx = ox + CARD_W * op.cx * scale
            val cy = oy + CARD_H * op.cy * scale
            val r = CARD_W * op.radius * scale
            if (op.stroke) {
                """<circle cx="$cx" cy="$cy" r="$r" fill="none" stroke="${hex(op.argb)}" """ +
                    """stroke-width="${CARD_W * op.strokeFraction * scale}" opacity="${op.alpha}"/>"""
            } else {
                """<circle cx="$cx" cy="$cy" r="$r" fill="${hex(op.argb)}" opacity="${op.alpha}"/>"""
            }
        }

        is CardOp.Text -> {
            // centre of the run's box; a generic font stands in for the platform font
            val sizePx = CARD_W * op.sizeFraction * scale
            val runWidth = sizePx * if (op.text.length > 1) 0.9f else 0.5f
            val runLeft = ox + CARD_W * op.left * scale
            val cx = runLeft + runWidth / 2f
            val baseline = oy + CARD_H * op.top * scale + sizePx * 0.82f
            val turn = if (op.rotationDeg == 0f) {
                ""
            } else {
                val pivotX = op.rotationPivot?.let { ox + CARD_W * it.first * scale } ?: cx
                val pivotY = op.rotationPivot?.let { oy + CARD_H * it.second * scale }
                    ?: (baseline - sizePx * 0.32f)
                """ transform="rotate(${op.rotationDeg} $pivotX $pivotY)""""
            }
            """<text x="$cx" y="$baseline" fill="${hex(op.argb)}" font-size="$sizePx" font-weight="700" """ +
                """text-anchor="middle"$turn>${op.text}</text>"""
        }

        is CardOp.Clip -> {
            val clipId = "c-$bezelId"
            val region = walk(op.region) { x, y -> (ox + CARD_W * x * scale) to (oy + CARD_H * y * scale) }
            """<defs><clipPath id="$clipId"><path d="$region" clip-rule="nonzero"/></clipPath></defs>""" +
                """<g clip-path="url(#$clipId)">""" +
                op.ops.mapIndexed { i, inner -> element(inner, ox, oy, scale, "$gradientId-$i", "$bezelId-$i") }.joinToString("") +
                "</g>"
        }

        is CardOp.Bezel -> {
            val edge = CARD_W * op.edgeWidth * scale
            val x = ox + edge / 2f
            val y = oy + edge / 2f
            val w = CARD_W * scale - edge
            val h = CARD_H * scale - edge
            """<defs><linearGradient id="$bezelId" x1="0" y1="0" x2="$w" y2="$h" gradientUnits="userSpaceOnUse">""" +
                """<stop offset="0" stop-color="${hex(op.lightArgb)}" stop-opacity="${op.lightAlpha}"/>""" +
                """<stop offset="0.5" stop-color="#000000" stop-opacity="0"/>""" +
                """<stop offset="1" stop-color="${hex(op.shadeArgb)}" stop-opacity="${op.shadeAlpha}"/>""" +
                """</linearGradient></defs>""" +
                """<rect x="$x" y="$y" width="$w" height="$h" rx="${CARD_W * op.cornerRadius * scale}" """ +
                """fill="none" stroke="url(#$bezelId)" stroke-width="$edge"/>"""
        }
    }



    /** A glyph drawn in raw SVG units (used by the glyph library sheet, not by the card sheets). */
    fun unitPath(glyph: CardGlyph, cx: Float, cy: Float, size: Float, fill: String): String {
        val d = walk(glyph.commands) { x, y -> (cx + (x - 0.5f) * size) to (cy + (y - 0.5f) * size) }
        return """<path d="$d" fill="$fill" fill-rule="nonzero"/>"""
    }

    /** A glyph outline as an SVG path, with the same unit → placed mapping the painter uses. */
    fun path(glyph: PlacedGlyph, ox: Float, oy: Float, scale: Float): String {
        val boxW = CARD_W * glyph.width * scale
        val boxH = CARD_W * glyph.height * scale
        val centerX = ox + CARD_W * glyph.cx * scale
        val centerY = oy + CARD_H * glyph.cy * scale
        val radians = (glyph.rotationDeg * Math.PI / 180.0).toFloat()
        val cosA = cos(radians)
        val sinA = sin(radians)
        return walk(glyph.commands) { x, y ->
            val lx = (x - 0.5f) * boxW
            val ly = (y - 0.5f) * boxH
            val rx = if (glyph.rotationDeg == 0f) lx else lx * cosA - ly * sinA
            val ry = if (glyph.rotationDeg == 0f) ly else lx * sinA + ly * cosA
            (centerX + rx) to (centerY + ry)
        }
    }

    /** Walks a glyph's commands through [map] and emits an SVG path string. */
    private fun walk(commands: List<GlyphCommand>, map: (Float, Float) -> Pair<Float, Float>): String = buildString {
        fun f(v: Float): String {
            val rounded = Math.round(v * 100f) / 100f
            return if (rounded == rounded.toLong().toFloat()) rounded.toLong().toString() else rounded.toString()
        }

        commands.forEach { command ->
            when (command) {
                is GlyphCommand.MoveTo -> map(command.x, command.y).let { append("M${f(it.first)} ${f(it.second)} ") }
                is GlyphCommand.LineTo -> map(command.x, command.y).let { append("L${f(it.first)} ${f(it.second)} ") }
                is GlyphCommand.QuadTo -> {
                    val c = map(command.cx, command.cy)
                    val p = map(command.x, command.y)
                    append("Q${f(c.first)} ${f(c.second)} ${f(p.first)} ${f(p.second)} ")
                }

                is GlyphCommand.CurveTo -> {
                    val c1 = map(command.c1x, command.c1y)
                    val c2 = map(command.c2x, command.c2y)
                    val p = map(command.x, command.y)
                    append("C${f(c1.first)} ${f(c1.second)} ${f(c2.first)} ${f(c2.second)} ${f(p.first)} ${f(p.second)} ")
                }

                GlyphCommand.Close -> append("Z ")
            }
        }
    }.trim()

    private fun hex(argb: Long): String = "#%06X".format(argb and 0xFFFFFF)
}

/** Writes the review sheets to [outDir] (default: `games/hokm/design/gx03`). */
fun main(args: Array<String>) {
    val outDir = File(args.firstOrNull()?.takeIf { it.isNotBlank() } ?: "games/hokm/design/gx03").apply { mkdirs() }

    fun deckSheet(style: HokmDeckStyle, jumbo: Boolean, file: String, title: String, subtitle: String) {
        val plans = CardFaceCatalog.all(style, jumbo).sortedWith(
            compareBy({ it.suit.ordinal }, { -it.rank.value }),
        )
        val cols = 13
        val gapX = 18f
        val gapY = 56f
        val padX = 30f
        val padY = 84f
        val cellW = CardSheet.CARD_W + gapX
        val rows = (plans.size + cols - 1) / cols
        val width = padX * 2 + cellW * cols
        val height = padY + rows * (CardSheet.CARD_H + gapY) + 20f
        val body = buildString {
            append(CardSheet.heading(title, subtitle, width / 2f))
            plans.forEachIndexed { i, plan ->
                val r = i / cols
                val c = i % cols
                append(
                    CardSheet.card(
                        plan,
                        padX + c * cellW,
                        padY + r * (CardSheet.CARD_H + gapY),
                    ),
                )
            }
        }
        File(outDir, file).writeText(CardSheet.svg(body, width, height))
    }

    // ---- single-face blow-ups (used to eyeball one composition at a time)
    if (args.size > 1 && args[1].isNotBlank()) {
        args[1].split(",").forEach { code ->
            val plan = CardFaceCatalog.plan(code.trim())
            val scale = 3f
            val width = CardSheet.CARD_W * scale + 80f
            val height = CardSheet.CARD_H * scale + 140f
            val body = CardSheet.heading("${plan.code} — ${plan.rank.labelFa} ${plan.suit.titleFa}", "بررسیِ تک‌کارت (۳ برابر)", width / 2f) +
                CardSheet.card(plan, 40f, 100f, scale)
            File(outDir, "one-${plan.code}.svg").writeText(CardSheet.svg(body, width, height))
        }
    }

    val credit = "GX-03 · همهٔ شکل‌ها از دادهٔ برداری خودِ اپ ساخته شده‌اند (بدون فایل تصویری)"
    deckSheet(HokmDeckStyle.CLASSIC, true, "cards-classic-jumbo.svg", "دستهٔ کلاسیک — ایندکس درشت", credit)
    deckSheet(HokmDeckStyle.CLASSIC, false, "cards-classic-standard.svg", "دستهٔ کلاسیک — ایندکس معمولی", credit)
    deckSheet(HokmDeckStyle.FOUR_COLOR, true, "cards-four-colour.svg", "دستهٔ چهاررنگ — خال‌ها با رنگ متفاوت", credit)

    // ---- backs, plus a stack to check the side thickness
    run {
        val backCols = HokmCardBacks.ALL.size
        val width = 40f * 2 + backCols * (CardSheet.CARD_W + 30f) + CardSheet.CARD_W * 0.6f
        val height = 110f + CardSheet.CARD_H + 40f
        val body = buildString {
            append(CardSheet.heading("سه پشتِ کارتِ اورجینال + لبهٔ ضخامت", credit, width / 2f))
            HokmCardBacks.ALL.forEachIndexed { i, spec ->
                append(CardSheet.back(spec, 40f + i * (CardSheet.CARD_W + 30f), 100f))
            }
            // five stacked backs, offset like a deck on the table
            val sx = 40f + backCols * (CardSheet.CARD_W + 30f)
            repeat(5) { i ->
                append(
                    """<g transform="translate(${sx + i * 3f} ${100f + i * 3f})">""" +
                        CardSheet.back(HokmCardBacks.EMERALD_GIRIH, 0f, 0f, withLabel = false) +
                        "</g>",
                )
            }
        }
        File(outDir, "backs.svg").writeText(CardSheet.svg(body, width, height))
    }

    // ---- magnified detail: the hardest faces at 2.6×, for hand review
    run {
        val detail = listOf("KH", "QH", "JH", "TS", "AC", "9D").map { CardFaceCatalog.plan(it) }
        val scale = 2.6f
        val cellW = CardSheet.CARD_W * scale + 40f
        val width = 40f + detail.size * cellW
        val height = 120f + CardSheet.CARD_H * scale + 40f
        val body = buildString {
            append(
                CardSheet.heading(
                    "بزرگ‌نماییِ وجه‌های سخت (۱۰، آس، فیگورها)",
                    "اینجا ایندکسِ «۱۰»، فیگورهای درباری و برخوردشان با خال‌ها را با چشم می‌شود سنجید",
                    width / 2f,
                ),
            )
            detail.forEachIndexed { i, plan ->
                append(CardSheet.card(plan, 40f + i * cellW, 100f, scale))
            }
        }
        File(outDir, "detail.svg").writeText(CardSheet.svg(body, width, height))
    }

    // ---- the glyph library itself: every outline, plus the four suits at table sizes
    run {
        val cell = 170f
        val cols = 6
        val glyphs = CardGlyphs.ALL
        val rows = (glyphs.size + cols - 1) / cols
        val width = 60f + cols * cell
        val height = 150f + rows * cell + 200f
        val body = buildString {
            append(
                CardSheet.heading(
                    "کتابخانهٔ شکل‌ها (همهٔ بردارهای کارت)",
                    "هر شکل در قابِ مربعِ خودش؛ خطِ اضافه یا سوراخ در هر شکل، همان‌جا باگ است",
                    width / 2f,
                ),
            )
            glyphs.forEachIndexed { i, glyph ->
                val r = i / cols
                val c = i % cols
                val size = 100f
                val x = 50f + c * cell
                val y = 120f + r * cell
                append("""<rect x="$x" y="$y" width="$size" height="$size" fill="none" stroke="#2F6B57"/>""")
                append(CardSheet.unitPath(glyph, x + size / 2f, y + size / 2f, size, CardSheet.FILL))
                append(CardSheet.label(glyph.id, x + size / 2f, y + size + 20f))
            }
            // the four suits at the sizes the table actually uses
            val suitsY = 120f + rows * cell + 30f
            append(CardSheet.label("خال‌ها در اندازه‌های واقعیِ میز (۴۴، ۵۶ و ۷۶ پیکسل)", width / 2f, suitsY - 12f))
            Suit.entries.forEachIndexed { si, suit ->
                listOf(44f, 56f, 76f).forEachIndexed { k, size ->
                    val x = 80f + si * 300f + k * 100f
                    append(CardSheet.unitPath(CardGlyphs.suit(suit), x, suitsY + 60f, size, CardSheet.FILL))
                    append(CardSheet.label("${suit.code} $size", x, suitsY + 60f + size / 2f + 16f))
                }
            }
        }
        File(outDir, "glyphs.svg").writeText(CardSheet.svg(body, width, height))
    }

    println("GX-03 review sheet written to ${outDir.absolutePath}")
    outDir.listFiles()?.sortedBy { it.name }?.forEach { println("  ${it.name}  ${it.length() / 1024} KB") }
}
