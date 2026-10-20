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
 * Paints the board top (inlaid maple/walnut squares + walnut frame with coordinates) into a
 * power-of-two bitmap. Row 0 of the bitmap is black's back rank side (z = −4.6), like a diagram
 * seen by white. Labels on the far half are rotated 180° so each player reads the near ones.
 *
 * The wood grain uses a tiny fixed-seed generator: the board looks identical on every launch.
 */
internal object BoardTexture {
    const val SIZE = 1024

    /** Board half size incl. frame is 4.6; the playing area is ±4. */
    private const val FRAME_FRACTION = 0.6f / 9.2f

    private val LIGHT = intArrayOf(0xEC, 0xCF, 0xA2)
    private val DARK = intArrayOf(0x8E, 0x5B, 0x35)

    fun create(): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rng = Lcg(20251010L)
        val frame = SIZE * FRAME_FRACTION
        val sq = (SIZE - 2 * frame) / 8f

        // Frame: dark walnut with long grain.
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        framePaint.shader = LinearGradient(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), Color.rgb(0x4A, 0x2A, 0x17), Color.rgb(0x2E, 0x18, 0x0C), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), framePaint)
        grain(c, rng, 0f, 0f, SIZE.toFloat(), SIZE.toFloat(), horizontal = true, base = intArrayOf(0x3A, 0x20, 0x10), lines = 220, strength = 0.22f)

        // Squares.
        for (rank in 0 until 8) for (file in 0 until 8) {
            val left = frame + file * sq
            val top = frame + (7 - rank) * sq
            val dark = (file + rank) % 2 == 0
            val base = if (dark) DARK else LIGHT
            val p = Paint()
            val tint = rng.nextFloat() * 0.06f - 0.03f
            p.color = Color.rgb(shade(base[0], tint), shade(base[1], tint), shade(base[2], tint))
            c.drawRect(left, top, left + sq, top + sq, p)
            grain(c, rng, left, top, sq, sq, horizontal = (file + rank) % 2 == 1, base = base, lines = 26, strength = if (dark) 0.30f else 0.20f)
        }

        // Thin brass inlay around the playing area.
        val inlay = Paint(Paint.ANTI_ALIAS_FLAG)
        inlay.style = Paint.Style.STROKE
        inlay.strokeWidth = SIZE / 300f
        inlay.color = Color.rgb(0xC9, 0x9A, 0x4B)
        val o = frame - inlay.strokeWidth * 1.6f
        c.drawRect(o, o, SIZE - o, SIZE - o, inlay)

        // Coordinates.
        val text = Paint(Paint.ANTI_ALIAS_FLAG)
        text.color = Color.rgb(0xE6, 0xC2, 0x86)
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

    private fun shade(v: Int, t: Float): Int = (v * (1f + t)).toInt().coerceIn(0, 255)

    /** Wavy translucent streaks imitating wood grain inside the given rectangle. */
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
