package ir.dorehami.games.chess.ui.gl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Paints the selected material frame and a high-contrast black-and-white playing surface into a
 * power-of-two bitmap. Row 0 of the bitmap is black's back rank side (z = −4.6), like a diagram
 * seen by white. Labels on the far half are rotated 180° so each player reads the near ones.
 *
 * Procedural grain and frame ornament use a fixed-seed generator, so finishes are repeatable.
 */
internal object BoardTexture {
    const val SIZE = 1024

    /** Board half size incl. frame is 4.6; the playing area is ±4. */
    private const val FRAME_FRACTION = 0.6f / 9.2f

    fun create(palette: ChessBoardPalette): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rng = Lcg(20251010L)
        val frame = SIZE * FRAME_FRACTION
        val sq = (SIZE - 2 * frame) / 8f

        // The frame finish changes materially while the playing squares remain monochrome.
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        framePaint.shader = LinearGradient(
            0f,
            0f,
            SIZE.toFloat(),
            SIZE.toFloat(),
            palette.frameStart,
            palette.frameEnd,
            Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), framePaint)
        val (lines, strength) = when (palette.boardStyle) {
            ir.dorehami.games.chess.ui.ChessBoardStyle.METAL -> 300 to 0.10f
            ir.dorehami.games.chess.ui.ChessBoardStyle.WOOD -> 220 to 0.22f
            ir.dorehami.games.chess.ui.ChessBoardStyle.LUXURY -> 120 to 0.12f
            ir.dorehami.games.chess.ui.ChessBoardStyle.TRADITIONAL -> 170 to 0.17f
            ir.dorehami.games.chess.ui.ChessBoardStyle.PAPER -> 360 to 0.08f
        }
        grain(
            c, rng, 0f, 0f, SIZE.toFloat(), SIZE.toFloat(), horizontal = true,
            base = rgb(palette.frameGrain), lines = lines, strength = strength,
        )
        drawFrameFinish(c, palette, frame)

        // Every set uses the requested black-and-white chess-square pattern.
        val squarePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (rank in 0 until 8) for (file in 0 until 8) {
            val left = frame + file * sq
            val top = frame + (7 - rank) * sq
            val dark = (file + rank) % 2 == 0
            squarePaint.color = if (dark) palette.darkSquare else palette.lightSquare
            c.drawRect(left, top, left + sq, top + sq, squarePaint)
        }

        // Thin material-appropriate inlay around the playing area.
        val inlay = Paint(Paint.ANTI_ALIAS_FLAG)
        inlay.style = Paint.Style.STROKE
        inlay.strokeWidth = SIZE / 300f
        inlay.color = palette.trim
        val o = frame - inlay.strokeWidth * 1.6f
        c.drawRect(o, o, SIZE - o, SIZE - o, inlay)

        // Coordinates.
        val text = Paint(Paint.ANTI_ALIAS_FLAG)
        text.color = palette.coordinates
        text.textSize = frame * 0.52f
        text.typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        text.textAlign = Paint.Align.CENTER
        val baseline = (text.descent() + text.ascent()) / 2
        for (i in 0 until 8) {
            val letter = ('a' + i).toString()
            val digit = ('1' + i).toString()
            val x = frame + (i + 0.5f) * sq
            val y = frame + (7 - i + 0.5f) * sq
            // Near side (white): upright.
            c.drawText(letter, x, SIZE - frame / 2 - baseline, text)
            c.drawText(digit, frame / 2, y - baseline, text)
            // Far side (black): rotated so black reads them upright.
            c.save(); c.rotate(180f, x, frame / 2); c.drawText(letter, x, frame / 2 - baseline, text); c.restore()
            c.save(); c.rotate(180f, SIZE - frame / 2, y); c.drawText(digit, SIZE - frame / 2, y - baseline, text); c.restore()
        }

        // Soft vignette so the board does not look flat-printed.
        val vignette = Paint()
        vignette.shader = RadialGradient(
            SIZE / 2f, SIZE / 2f, SIZE * 0.75f,
            intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(0, 0, 0, 0), Color.argb(70, 0, 0, 0)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), vignette)
        return bmp
    }

    private fun drawFrameFinish(c: Canvas, palette: ChessBoardPalette, frame: Float) {
        val insetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = SIZE / 900f
        }
        val outer = frame * 0.12f
        val inner = frame * 0.86f
        val studCenters = listOf(
            android.graphics.PointF(frame / 2f, frame / 2f),
            android.graphics.PointF(SIZE - frame / 2f, frame / 2f),
            android.graphics.PointF(frame / 2f, SIZE - frame / 2f),
            android.graphics.PointF(SIZE - frame / 2f, SIZE - frame / 2f),
        )
        when (palette.boardStyle) {
            ir.dorehami.games.chess.ui.ChessBoardStyle.METAL -> {
                insetPaint.color = Color.argb(100, 255, 255, 255)
                c.drawRect(outer, outer, SIZE - outer, SIZE - outer, insetPaint)
                insetPaint.color = Color.argb(95, 34, 40, 45)
                c.drawRect(inner, inner, SIZE - inner, SIZE - inner, insetPaint)
                val screw = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.coordinates }
                studCenters.forEach { center ->
                    c.drawCircle(center.x, center.y, frame * 0.055f, screw)
                    c.drawLine(center.x - frame * 0.028f, center.y, center.x + frame * 0.028f, center.y, insetPaint)
                }
            }
            ir.dorehami.games.chess.ui.ChessBoardStyle.WOOD -> {
                insetPaint.color = Color.argb(105, Color.red(palette.trim), Color.green(palette.trim), Color.blue(palette.trim))
                c.drawRect(outer, outer, SIZE - outer, SIZE - outer, insetPaint)
                insetPaint.color = Color.argb(75, Color.red(palette.frameGrain), Color.green(palette.frameGrain), Color.blue(palette.frameGrain))
                c.drawRect(inner, inner, SIZE - inner, SIZE - inner, insetPaint)
            }
            ir.dorehami.games.chess.ui.ChessBoardStyle.LUXURY -> {
                insetPaint.color = palette.trim
                insetPaint.strokeWidth = SIZE / 430f
                c.drawRect(outer, outer, SIZE - outer, SIZE - outer, insetPaint)
                insetPaint.strokeWidth = SIZE / 900f
                c.drawRect(inner, inner, SIZE - inner, SIZE - inner, insetPaint)
                val medallion = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.trim }
                studCenters.forEach { center ->
                    c.drawCircle(center.x, center.y, frame * 0.11f, medallion)
                    medallion.color = palette.frameEnd
                    c.drawCircle(center.x, center.y, frame * 0.055f, medallion)
                    medallion.color = palette.trim
                }
            }
            ir.dorehami.games.chess.ui.ChessBoardStyle.TRADITIONAL -> {
                insetPaint.color = Color.argb(185, Color.red(palette.trim), Color.green(palette.trim), Color.blue(palette.trim))
                insetPaint.strokeWidth = SIZE / 520f
                c.drawRect(outer, outer, SIZE - outer, SIZE - outer, insetPaint)
                insetPaint.strokeWidth = SIZE / 950f
                c.drawRect(inner, inner, SIZE - inner, SIZE - inner, insetPaint)
                studCenters.forEach { center -> drawPersianRosette(c, center.x, center.y, frame * 0.17f, palette.trim) }
            }
            ir.dorehami.games.chess.ui.ChessBoardStyle.PAPER -> {
                insetPaint.color = Color.argb(120, Color.red(palette.trim), Color.green(palette.trim), Color.blue(palette.trim))
                insetPaint.strokeWidth = SIZE / 700f
                c.drawRect(outer, outer, SIZE - outer, SIZE - outer, insetPaint)
                insetPaint.strokeWidth = SIZE / 1200f
                c.drawRect(inner, inner, SIZE - inner, SIZE - inner, insetPaint)
                val crop = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.coordinates
                    strokeWidth = SIZE / 950f
                }
                studCenters.forEach { center ->
                    val d = frame * 0.09f
                    c.drawLine(center.x - d, center.y, center.x + d, center.y, crop)
                    c.drawLine(center.x, center.y - d, center.x, center.y + d, crop)
                }
            }
        }
    }

    private fun drawPersianRosette(c: Canvas, x: Float, y: Float, radius: Float, color: Int) {
        val path = Path().apply {
            moveTo(x, y - radius)
            lineTo(x + radius * 0.36f, y - radius * 0.36f)
            lineTo(x + radius, y)
            lineTo(x + radius * 0.36f, y + radius * 0.36f)
            lineTo(x, y + radius)
            lineTo(x - radius * 0.36f, y + radius * 0.36f)
            lineTo(x - radius, y)
            lineTo(x - radius * 0.36f, y - radius * 0.36f)
            close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = SIZE / 700f
        }
        c.drawPath(path, paint)
        c.drawCircle(x, y, radius * 0.26f, paint)
    }

    private fun rgb(color: Int) = intArrayOf(Color.red(color), Color.green(color), Color.blue(color))

    /** Fine, fixed-seed surface strokes tuned by the selected material finish. */
    private fun grain(
        c: Canvas, rng: Lcg, left: Float, top: Float, w: Float, h: Float,
        horizontal: Boolean, base: IntArray, lines: Int, strength: Float,
    ) {
        c.save()
        c.clipRect(left, top, left + w, top + h)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE
        val path = Path()
        val len = if (horizontal) w else h
        val across = if (horizontal) h else w
        for (i in 0 until lines) {
            val darker = rng.nextFloat() < 0.65f
            val k = if (darker) 1f - strength * (0.4f + rng.nextFloat() * 0.6f) else 1f + strength * 0.5f * rng.nextFloat()
            p.color = Color.argb(
                (40 + rng.nextFloat() * 80).toInt(),
                (base[0] * k).toInt().coerceIn(0, 255),
                (base[1] * k).toInt().coerceIn(0, 255),
                (base[2] * k).toInt().coerceIn(0, 255),
            )
            p.strokeWidth = 0.6f + rng.nextFloat() * across / 60f
            val pos = rng.nextFloat() * across
            val amp = across * (0.004f + rng.nextFloat() * 0.02f)
            val freq = 1f + rng.nextFloat() * 3f
            val phase = rng.nextFloat() * 6.28f
            path.reset()
            val steps = 24
            for (s in 0..steps) {
                val t = s / steps.toFloat()
                val along = t * len
                val off = pos + amp * kotlin.math.sin(phase + t * freq * 6.28f)
                val x = if (horizontal) left + along else left + off
                val y = if (horizontal) top + off else top + along
                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            c.drawPath(path, p)
        }
        c.restore()
    }

    /** Minimal deterministic generator (visual noise only). */
    private class Lcg(seed: Long) {
        private var s = seed

        fun nextFloat(): Float {
            s = s * 6364136223846793005L + 1442695040888963407L
            return ((s ushr 40) and 0xFFFFFF).toFloat() / 0x1000000
        }
    }
}
