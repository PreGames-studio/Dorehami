package ir.dorehami.games.chess.ui.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * OpenGL ES 2.0 renderer of the 3D board. Runs on the GL thread only; the view hands it new
 * [BoardScene]s and touch events through `queueEvent`.
 *
 * Frame: (1) glossy reflections of the pieces in the board (stencil-masked), (2) table and the
 * board's soft drop shadow, (3) board block + lacquered top, (4) contact blobs + planar shadows,
 * (5) square highlights, (6) the pieces, then the fading captured piece.
 */
internal class ChessRenderer(
    private val requestRender: () -> Unit,
    private val onPicked: (Int) -> Unit,
) : GLSurfaceView.Renderer {

    val camera = OrbitCamera()

    private var palette = ChessBoardPalette.Light
    private var surfaceCreated = false
    private val tableRgb = FloatArray(3)
    private val frameRgb = FloatArray(3)
    private val lightPieceRgb = FloatArray(3)
    private val darkPieceRgb = FloatArray(3)
    private val selectedRgb = FloatArray(3)

    init {
        updatePaletteColors()
    }

    // ----------------------------------------------------------------- scene state (GL thread)
    private var scene = BoardScene(IntArray(64))
    private var anim: MoveAnim? = null
    private var animStartMs = 0L
    private var animDurationMs = 1L
    private var lastAnimId = Long.MIN_VALUE
    private var selectLift = 0f
    private var lastFrameMs = 0L
    private var instances: List<Inst> = emptyList()

    /** Applies a theme on the GL thread; move state and the game rules are unaffected. */
    fun setPalette(value: ChessBoardPalette) {
        if (palette == value) return
        palette = value
        updatePaletteColors()
        if (surfaceCreated) {
            applyClearColor()
            updateBoardTexture()
        }
        requestRender()
    }

    /** Animation of the camera side switch etc. is driven by [OrbitCamera.step]. */
    fun setScene(s: BoardScene) {
        val now = SystemClock.uptimeMillis()
        if (s.positionId != scene.positionId) {
            if (s.anim != null && s.positionId != lastAnimId) {
                anim = s.anim
                lastAnimId = s.positionId
                animStartMs = now
                val dx = (s.anim.to and 7) - (s.anim.from and 7)
                val dz = (s.anim.to shr 3) - (s.anim.from shr 3)
                val dist = sqrt((dx * dx + dz * dz).toFloat())
                animDurationMs = (240 + dist * 55).toLong().coerceIn(260, 620)
            } else {
                anim = null
            }
        }
        scene = s
        requestRender()
    }

    /** Screen tap → square (or -1), reported through [onPicked]. */
    fun pick(sx: Float, sy: Float) {
        onPicked(pickSquare(sx, sy))
    }

    // ----------------------------------------------------------------- GL objects
    private var pieceProg: Program? = null
    private var boardProg: Program? = null
    private var flatProg: Program? = null
    private var shadowProg: Program? = null
    private val pieceMeshes = arrayOfNulls<GpuMesh>(7)
    private var boardBlock: GpuMesh? = null
    private var boardTopVbo = 0
    private var quadVbo = 0
    private var boardTexture = 0
    private var hasStencil = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Context (re)created: every GL name from before is gone.
        pieceProg = Program(Shaders.PIECE_VS, Shaders.PIECE_FS)
        boardProg = Program(Shaders.BOARD_VS, Shaders.BOARD_FS)
        flatProg = Program(Shaders.FLAT_VS, Shaders.FLAT_FS)
        shadowProg = Program(Shaders.SHADOW_VS, Shaders.SHADOW_FS)
        for (t in 1..6) pieceMeshes[t] = GpuMesh(PieceMeshes.build(t))
        boardBlock = GpuMesh(MeshBuilder().apply { box(0f, -0.172f, 0f, 9.2f, 0.34f, 9.2f) }.build())
        val h = OrbitCamera.BOARD_HALF
        boardTopVbo = floatVbo(floatArrayOf(-h, 0f, -h, h, 0f, -h, h, 0f, h, -h, 0f, -h, h, 0f, h, -h, 0f, h))
        quadVbo = floatVbo(floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, -1f, 1f, 1f, -1f, 1f))
        boardTexture = 0
        updatePaletteColors()
        boardTexture = createBoardTexture()
        surfaceCreated = true

        val bits = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_STENCIL_BITS, bits, 0)
        hasStencil = bits[0] > 0
        applyClearColor()
        GLES20.glClearStencil(0)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        lastFrameMs = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        camera.resize(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrameMs == 0L) 0.016f else ((now - lastFrameMs) / 1000f).coerceIn(0f, 0.1f)
        lastFrameMs = now
        var more = camera.step(dt)

        // Selection lift eases in/out.
        val liftTarget = if (scene.selected >= 0) SELECT_LIFT else 0f
        selectLift += (liftTarget - selectLift) * min(1f, dt * 14f)
        if (abs(liftTarget - selectLift) > 0.002f) more = true
        if (scene.selected >= 0) more = true // pulse

        val t = animProgress(now)
        if (anim != null && t < 1f + POP_TAIL) more = true
        instances = buildInstances(now, t)

        val vp = camera.viewProj
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_STENCIL_BUFFER_BIT)
        GLES20.glDepthFunc(GLES20.GL_LESS)
        GLES20.glDepthMask(true)

        // (1) Reflections.
        if (hasStencil) {
            GLES20.glEnable(GLES20.GL_STENCIL_TEST)
            GLES20.glStencilFunc(GLES20.GL_ALWAYS, 1, 0xFF)
            GLES20.glStencilOp(GLES20.GL_KEEP, GLES20.GL_KEEP, GLES20.GL_REPLACE)
            GLES20.glColorMask(false, false, false, false)
            GLES20.glDepthMask(false)
            drawFlat(vp, 0f, 0f, 0f, 4f, 4f, SHAPE_SOFT_RECT, 0f, 0f, 0f, 1f)
            GLES20.glColorMask(true, true, true, true)
            GLES20.glDepthMask(true)
            GLES20.glStencilFunc(GLES20.GL_EQUAL, 1, 0xFF)
            GLES20.glStencilOp(GLES20.GL_KEEP, GLES20.GL_KEEP, GLES20.GL_KEEP)
            GLES20.glFrontFace(GLES20.GL_CW)
            drawPieces(vp, instances, mirror = true)
            GLES20.glFrontFace(GLES20.GL_CCW)
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT)
            GLES20.glStencilFunc(GLES20.GL_NOTEQUAL, 1, 0xFF)
        }

        // (2) Table + drop shadow of the board.
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        drawFlat(vp, 0f, -0.345f, 0f, 22f, 22f, SHAPE_TABLE, tableRgb[0], tableRgb[1], tableRgb[2], 1f)
        drawFlat(vp, 0.45f, -0.343f, 0.35f, 5.5f, 5.5f, SHAPE_SOFT_RECT, 0f, 0f, 0f, 0.62f)
        if (hasStencil) GLES20.glDisable(GLES20.GL_STENCIL_TEST)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)

        // (3) Board block and lacquered top.
        drawMesh(vp, boardBlock!!, IDENTITY, frameRgb, spec = 0.25f, shininess = 30f, ao = 0f, alpha = 1f, mirror = false, cull = true)
        if (hasStencil) GLES20.glEnable(GLES20.GL_BLEND)
        drawBoardTop(vp, if (hasStencil) 0.86f else 1f)

        // (4) Contact blobs (no depth write) and planar shadows (depth trick: no double darkening).
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glDepthMask(false)
        for (p in instances) {
            if (p.ghost) continue
            val r = 0.40f * p.scale
            drawFlat(vp, p.x, 0.003f, p.z, r, r, SHAPE_BLOB, 0f, 0f, 0f, 0.42f * p.alpha)
        }
        GLES20.glDepthMask(true)
        drawShadows(vp, instances)
        GLES20.glDepthMask(false)

        // (5) Highlights.
        drawHighlights(vp, now)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)

        // (6) Pieces.
        drawPieces(vp, instances.filter { !it.ghost }, mirror = false)
        val ghosts = instances.filter { it.ghost }
        if (ghosts.isNotEmpty()) {
            GLES20.glEnable(GLES20.GL_BLEND)
            drawPieces(vp, ghosts, mirror = false)
            GLES20.glDisable(GLES20.GL_BLEND)
        }

        if (more) requestRender()
    }

    // ----------------------------------------------------------------- instances & animation
    private class Inst(
        val square: Int,
        val type: Int,
        val white: Boolean,
        val x: Float,
        val y: Float,
        val z: Float,
        val yaw: Float,
        val scale: Float,
        val alpha: Float,
        val glow: Float,
        val ghost: Boolean,
    )

    private fun animProgress(now: Long): Float {
        if (anim == null) return 2f
        return (now - animStartMs).toFloat() / animDurationMs
    }

    private fun ease(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private fun buildInstances(now: Long, t: Float): List<Inst> {
        val out = ArrayList<Inst>(34)
        val a = anim
        val running = a != null && t < 1f
        val pulse = 0.5f + 0.5f * sin(now / 1000f * 5.5f)
        for (sq in 0 until 64) {
            val code = scene.squares[sq]
            if (code == 0) continue
            var type = abs(code)
            val white = code > 0
            var x = BoardScene.squareX(sq)
            var z = BoardScene.squareZ(sq)
            var y = 0f
            var scale = PIECE_SCALE
            if (a != null && running && sq == a.to) {
                val e = ease(t)
                val fx = BoardScene.squareX(a.from)
                val fz = BoardScene.squareZ(a.from)
                x = fx + (x - fx) * e
                z = fz + (z - fz) * e
                val dist = sqrt((x - fx) * (x - fx) + (z - fz) * (z - fz)) / max(e, 0.001f)
                val hop = if (abs(a.moved) == 2) 0.7f else 0.10f + 0.035f * dist
                y = 4f * hop * t * (1f - t)
                type = abs(a.moved) // a promoting pawn stays a pawn until it lands
            } else if (a != null && running && sq == a.rookTo) {
                val e = ease(t)
                val fx = BoardScene.squareX(a.rookFrom)
                x = fx + (x - fx) * e
                y = 4f * 0.18f * t * (1f - t)
            } else if (a != null && sq == a.to && abs(a.moved) != type && t in 1f..(1f + POP_TAIL)) {
                // Promotion pop.
                val k = (t - 1f) / POP_TAIL
                scale *= 1f + 0.28f * sin(k * PI.toFloat())
            }
            var glow = 0f
            if (sq == scene.selected) {
                y += selectLift
                glow = 0.55f + 0.45f * pulse
            }
            out += Inst(sq, type, white, x, y, z, yawFor(type, white, x), scale, 1f, glow, ghost = false)
        }
        if (a != null && running && a.captured != 0 && a.captureSquare >= 0) {
            val fade = ((t - 0.55f) / 0.45f).coerceIn(0f, 1f)
            val x = BoardScene.squareX(a.captureSquare)
            val z = BoardScene.squareZ(a.captureSquare)
            out += Inst(
                a.captureSquare, abs(a.captured), a.captured > 0, x, -0.25f * fade, z,
                yawFor(abs(a.captured), a.captured > 0, x), PIECE_SCALE * (1f - 0.35f * fade), 1f - fade, 0f, ghost = true,
            )
        }
        return out
    }

    /** Knights look towards the centre file; bishops show their slit to their owner. */
    private fun yawFor(type: Int, white: Boolean, x: Float): Float = when (type) {
        2 -> if (x < 0f) 0f else PI.toFloat()
        3 -> if (white) -PI.toFloat() / 2 else PI.toFloat() / 2
        else -> 0f
    }

    // ----------------------------------------------------------------- drawing helpers
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val tmp = FloatArray(16)
    private val shadowMat = FloatArray(16).also {
        Mat4.shadowOnPlaneY(it, -LIGHT[0], -LIGHT[1], -LIGHT[2], 0.0045f)
    }

    private fun drawPieces(vp: FloatArray, list: List<Inst>, mirror: Boolean) {
        for (p in list) {
            if (mirror && p.ghost) continue
            val mesh = pieceMeshes[p.type] ?: continue
            Mat4.trs(model, p.x, p.y, p.z, p.yaw, p.scale, p.scale, p.scale)
            val color = if (p.white) lightPieceRgb else darkPieceRgb
            val glow = p.glow
            selectedRgb.copyInto(emissive)
            for (i in 0..2) emissive[i] *= 0.22f * glow
            drawMesh(
                vp, mesh, model, color,
                spec = if (p.white) 0.32f else 0.55f,
                shininess = if (p.white) 28f else 56f,
                ao = 0.35f, alpha = p.alpha, mirror = mirror, cull = true, emissive = emissive,
            )
        }
    }

    private val emissive = FloatArray(3)
    private val noEmissive = FloatArray(3)

    private fun drawMesh(
        vp: FloatArray, mesh: GpuMesh, modelIn: FloatArray, color: FloatArray,
        spec: Float, shininess: Float, ao: Float, alpha: Float, mirror: Boolean, cull: Boolean,
        emissive: FloatArray = noEmissive,
    ) {
        val prog = pieceProg ?: return
        prog.use()
        val m = if (mirror) {
            modelIn.copyInto(tmp)
            tmp[1] = -tmp[1]; tmp[5] = -tmp[5]; tmp[9] = -tmp[9]; tmp[13] = -tmp[13]
            tmp
        } else {
            modelIn
        }
        Mat4.multiply(mvp, vp, m)
        GLES20.glUniformMatrix4fv(prog.u("uMVP"), 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(prog.u("uModel"), 1, false, m, 0)
        GLES20.glUniform3f(prog.u("uColor"), color[0], color[1], color[2])
        val ey = if (mirror) -camera.eye[1] else camera.eye[1]
        GLES20.glUniform3f(prog.u("uEye"), camera.eye[0], ey, camera.eye[2])
        GLES20.glUniform3f(prog.u("uLight"), LIGHT[0], if (mirror) -LIGHT[1] else LIGHT[1], LIGHT[2])
        GLES20.glUniform1f(prog.u("uSpec"), spec)
        GLES20.glUniform1f(prog.u("uShininess"), shininess)
        GLES20.glUniform3f(prog.u("uEmissive"), emissive[0], emissive[1], emissive[2])
        GLES20.glUniform1f(prog.u("uAlpha"), alpha)
        GLES20.glUniform1f(prog.u("uAo"), ao)
        if (cull) GLES20.glEnable(GLES20.GL_CULL_FACE) else GLES20.glDisable(GLES20.GL_CULL_FACE)
        mesh.draw(prog.a("aPos"), prog.a("aNormal"), prog.a("aShade"))
        GLES20.glDisable(GLES20.GL_CULL_FACE)
    }

    private fun drawShadows(vp: FloatArray, list: List<Inst>) {
        val prog = shadowProg ?: return
        prog.use()
        GLES20.glUniform4f(prog.u("uColor"), 0f, 0f, 0f, 0.30f)
        for (p in list) {
            if (p.ghost) continue
            val mesh = pieceMeshes[p.type] ?: continue
            Mat4.trs(model, p.x, p.y, p.z, p.yaw, p.scale, p.scale, p.scale)
            Mat4.multiply(tmp, shadowMat, model)
            Mat4.multiply(mvp, vp, tmp)
            GLES20.glUniformMatrix4fv(prog.u("uMVP"), 1, false, mvp, 0)
            mesh.draw(prog.a("aPos"), -1, -1)
        }
    }

    private fun drawHighlights(vp: FloatArray, now: Long) {
        val s = scene
        val half = 0.5f
        if (s.lastFrom >= 0) squareFlatColor(vp, s.lastFrom, half, SHAPE_SQUARE, palette.lastMove, 0.32f)
        if (s.lastTo >= 0) squareFlatColor(vp, s.lastTo, half, SHAPE_SQUARE, palette.lastMove, 0.42f)
        if (s.checkSquare >= 0) {
            val k = 0.75f + 0.25f * sin(now / 1000f * 6f)
            squareFlatColor(vp, s.checkSquare, 0.78f, SHAPE_GLOW, palette.check, 0.9f * k)
        }
        if (s.selected >= 0) squareFlatColor(vp, s.selected, half, SHAPE_SQUARE, palette.selected, 0.55f)
        for (sq in s.quietTargets) squareFlatColor(vp, sq, half, SHAPE_DOT, palette.quietMove, 0.75f)
        for (sq in s.captureTargets) squareFlatColor(vp, sq, half, SHAPE_RING, palette.capture, 0.85f)
    }

    private fun squareFlatColor(vp: FloatArray, sq: Int, half: Float, shape: Float, color: Int, alpha: Float) {
        drawFlat(
            vp, BoardScene.squareX(sq), 0.008f, BoardScene.squareZ(sq), half, half, shape,
            red(color), green(color), blue(color), alpha,
        )
    }

    private fun drawFlat(
        vp: FloatArray, cx: Float, cy: Float, cz: Float, hx: Float, hz: Float,
        shape: Float, r: Float, g: Float, b: Float, a: Float,
    ) {
        val prog = flatProg ?: return
        prog.use()
        GLES20.glUniformMatrix4fv(prog.u("uMVP"), 1, false, vp, 0)
        GLES20.glUniform3f(prog.u("uCenter"), cx, cy, cz)
        GLES20.glUniform2f(prog.u("uHalf"), hx, hz)
        GLES20.glUniform1f(prog.u("uShape"), shape)
        GLES20.glUniform4f(prog.u("uColor"), r, g, b, a)
        val loc = prog.a("aPos")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, quadVbo)
        GLES20.glEnableVertexAttribArray(loc)
        GLES20.glVertexAttribPointer(loc, 2, GLES20.GL_FLOAT, false, 8, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(loc)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun drawBoardTop(vp: FloatArray, innerAlpha: Float) {
        val prog = boardProg ?: return
        prog.use()
        GLES20.glUniformMatrix4fv(prog.u("uMVP"), 1, false, vp, 0)
        GLES20.glUniform3f(prog.u("uEye"), camera.eye[0], camera.eye[1], camera.eye[2])
        GLES20.glUniform3f(prog.u("uLight"), LIGHT[0], LIGHT[1], LIGHT[2])
        GLES20.glUniform1f(prog.u("uInnerAlpha"), innerAlpha)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, boardTexture)
        GLES20.glUniform1i(prog.u("uTex"), 0)
        val loc = prog.a("aPos")
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, boardTopVbo)
        GLES20.glEnableVertexAttribArray(loc)
        GLES20.glVertexAttribPointer(loc, 3, GLES20.GL_FLOAT, false, 12, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(loc)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    // ----------------------------------------------------------------- picking
    private fun pickSquare(sx: Float, sy: Float): Int {
        val r = camera.ray(sx, sy)
        val ox = r[0]; val oy = r[1]; val oz = r[2]
        val dx = r[3]; val dy = r[4]; val dz = r[5]
        var best = -1
        var bestT = Float.MAX_VALUE
        for (p in instances) {
            if (p.ghost) continue
            val mesh = pieceMeshes[p.type] ?: continue
            val radius = 0.36f * p.scale
            val top = p.y + mesh.maxY * p.scale
            // Ray vs vertical cylinder in XZ.
            val fx = ox - p.x
            val fz = oz - p.z
            val a = dx * dx + dz * dz
            if (a < 1e-6f) continue
            val b = 2f * (fx * dx + fz * dz)
            val c = fx * fx + fz * fz - radius * radius
            val disc = b * b - 4f * a * c
            if (disc < 0f) continue
            val sq = sqrt(disc)
            val t0 = (-b - sq) / (2f * a)
            val t1 = (-b + sq) / (2f * a)
            val y0 = oy + t0 * dy
            val y1 = oy + t1 * dy
            val lo = min(y0, y1)
            val hi = max(y0, y1)
            if (hi < p.y || lo > top) continue
            val enter = if (y0 > top && dy < 0f) (top - oy) / dy else t0
            if (enter < bestT) {
                bestT = enter
                best = p.square
            }
        }
        if (best >= 0) return best
        if (dy >= -1e-5f) return -1
        val t = -oy / dy
        return BoardScene.squareAt(ox + t * dx, oz + t * dz)
    }

    // ----------------------------------------------------------------- GL utilities
    private fun floatVbo(data: FloatArray): Int {
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        val buf = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(data).position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.size * 4, buf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        return ids[0]
    }

    private fun createBoardTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val texture = ids[0]
        uploadBoardTexture(texture, configure = true)
        return texture
    }

    private fun updateBoardTexture() {
        if (boardTexture != 0) uploadBoardTexture(boardTexture, configure = false)
    }

    private fun uploadBoardTexture(texture: Int, configure: Boolean) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        val bmp = BoardTexture.create(palette)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        if (configure) {
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            val ext = GLES20.glGetString(GLES20.GL_EXTENSIONS) ?: ""
            if ("GL_EXT_texture_filter_anisotropic" in ext) {
                val maxAniso = FloatArray(1)
                GLES20.glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, maxAniso, 0)
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, min(8f, maxAniso[0]))
            }
        }
    }

    private fun updatePaletteColors() {
        fillRgb(tableRgb, palette.tableColor)
        fillRgb(frameRgb, palette.frameStart)
        fillRgb(lightPieceRgb, palette.lightPiece)
        fillRgb(darkPieceRgb, palette.darkPiece)
        fillRgb(selectedRgb, palette.selected)
    }

    private fun applyClearColor() {
        GLES20.glClearColor(
            red(palette.clearColor), green(palette.clearColor), blue(palette.clearColor), 1f,
        )
    }

    private fun fillRgb(target: FloatArray, color: Int) {
        target[0] = red(color)
        target[1] = green(color)
        target[2] = blue(color)
    }

    private fun red(color: Int) = ((color ushr 16) and 0xFF) / 255f
    private fun green(color: Int) = ((color ushr 8) and 0xFF) / 255f
    private fun blue(color: Int) = (color and 0xFF) / 255f

    /** Compiled program with cached attribute/uniform locations. */
    private class Program(vs: String, fs: String) {
        val id: Int = GLES20.glCreateProgram()
        private val attribs = HashMap<String, Int>()
        private val uniforms = HashMap<String, Int>()

        init {
            val v = compile(GLES20.GL_VERTEX_SHADER, vs)
            val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
            GLES20.glAttachShader(id, v)
            GLES20.glAttachShader(id, f)
            GLES20.glLinkProgram(id)
            val ok = IntArray(1)
            GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, ok, 0)
            if (ok[0] == 0) Log.e(TAG, "link failed: " + GLES20.glGetProgramInfoLog(id))
            GLES20.glDeleteShader(v)
            GLES20.glDeleteShader(f)
        }

        fun use() = GLES20.glUseProgram(id)

        fun a(name: String): Int = attribs.getOrPut(name) { GLES20.glGetAttribLocation(id, name) }

        fun u(name: String): Int = uniforms.getOrPut(name) { GLES20.glGetUniformLocation(id, name) }

        private fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src.trimIndent())
            GLES20.glCompileShader(s)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) Log.e(TAG, "shader compile failed: " + GLES20.glGetShaderInfoLog(s))
            return s
        }
    }

    /** Vertex + index buffers of one [MeshData]. */
    private class GpuMesh(data: MeshData) {
        private val vbo: Int
        private val ibo: Int
        private val count = data.indexCount
        val maxY = data.maxY

        init {
            val ids = IntArray(2)
            GLES20.glGenBuffers(2, ids, 0)
            vbo = ids[0]
            ibo = ids[1]
            val vb = ByteBuffer.allocateDirect(data.vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            vb.put(data.vertices).position(0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.vertices.size * 4, vb, GLES20.GL_STATIC_DRAW)
            val ib = ByteBuffer.allocateDirect(data.indices.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
            ib.put(data.indices).position(0)
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
            GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, data.indices.size * 2, ib, GLES20.GL_STATIC_DRAW)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        }

        fun draw(posLoc: Int, normalLoc: Int, shadeLoc: Int) {
            val stride = MeshData.STRIDE * 4
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
            GLES20.glEnableVertexAttribArray(posLoc)
            GLES20.glVertexAttribPointer(posLoc, 3, GLES20.GL_FLOAT, false, stride, 0)
            if (normalLoc >= 0) {
                GLES20.glEnableVertexAttribArray(normalLoc)
                GLES20.glVertexAttribPointer(normalLoc, 3, GLES20.GL_FLOAT, false, stride, 12)
            }
            if (shadeLoc >= 0) {
                GLES20.glEnableVertexAttribArray(shadeLoc)
                GLES20.glVertexAttribPointer(shadeLoc, 1, GLES20.GL_FLOAT, false, stride, 24)
            }
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, count, GLES20.GL_UNSIGNED_SHORT, 0)
            GLES20.glDisableVertexAttribArray(posLoc)
            if (normalLoc >= 0) GLES20.glDisableVertexAttribArray(normalLoc)
            if (shadeLoc >= 0) GLES20.glDisableVertexAttribArray(shadeLoc)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        }
    }

    companion object {
        private const val TAG = "Chess3D"
        private const val PIECE_SCALE = 1.12f
        private const val SELECT_LIFT = 0.14f
        private const val POP_TAIL = 0.6f

        private const val SHAPE_SQUARE = 0f
        private const val SHAPE_DOT = 1f
        private const val SHAPE_RING = 2f
        private const val SHAPE_GLOW = 3f
        private const val SHAPE_BLOB = 4f
        private const val SHAPE_TABLE = 5f
        private const val SHAPE_SOFT_RECT = 6f

        private const val GL_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FE
        private const val GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FF

        /** Direction *towards* the key light (normalised). */
        private val LIGHT = floatArrayOf(-0.42f, 0.84f, 0.34f).let {
            val l = sqrt(it[0] * it[0] + it[1] * it[1] + it[2] * it[2])
            floatArrayOf(it[0] / l, it[1] / l, it[2] / l)
        }
        private val IDENTITY = Mat4.identity()
    }
}
