package ir.dorehami.games.chess.ui.gl

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

/**
 * The 3D chess board as an Android view (embedded in Compose with `AndroidView`).
 *
 * * tap → [onSquareTap] with the square under the finger (pieces are hit-tested as cylinders,
 *   so tapping a tall piece's head selects *its* square, not the one behind it);
 * * one-finger drag → orbit, pinch → zoom.
 *
 * Everything touching GL is forwarded to the GL thread with `queueEvent`; callbacks come back on
 * the main thread.
 */
@SuppressLint("ViewConstructor")
class Chess3DView(context: Context) : GLSurfaceView(context) {

    /** Called on the main thread with the tapped square (`a1 = 0 … h8 = 63`) or -1. */
    var onSquareTap: ((Int) -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())
    private val renderer = ChessRenderer(
        requestRender = { requestRender() },
        onPicked = { sq -> main.post { onSquareTap?.invoke(sq) } },
    )
    private var lastScene: BoardScene? = null
    private var whiteSide = true

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(ConfigChooser())
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    /** Shows [scene]; animates the move in it once per `positionId`. */
    fun setScene(scene: BoardScene) {
        if (scene == lastScene) return
        lastScene = scene
        queueEvent { renderer.setScene(scene) }
        requestRender()
    }

    /** Puts the camera behind white or black. */
    fun setViewSide(white: Boolean, animate: Boolean = true) {
        if (white == whiteSide && animate) return
        whiteSide = white
        queueEvent { renderer.camera.setSide(white, animate) }
        requestRender()
    }

    /** Back to the default angle/zoom behind the current side. */
    fun resetView() {
        val white = whiteSide
        queueEvent { renderer.camera.setSide(white, animate = true) }
        requestRender()
    }

    private val gestures = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val x = e.x
                val y = e.y
                queueEvent { renderer.pick(x, y) }
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (scaling || e2.pointerCount > 1) return false
                queueEvent { renderer.camera.orbitBy(distanceX, -distanceY) }
                requestRender()
                return true
            }
        },
    )

    private var scaling = false
    private val scaler = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                scaling = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val f = detector.scaleFactor
                queueEvent { renderer.camera.zoomBy(f) }
                requestRender()
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                scaling = false
            }
        },
    )

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaler.onTouchEvent(event)
        if (!scaler.isInProgress) gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }

    /**
     * Prefers 4× MSAA with a stencil buffer (needed for the board reflections); falls back step by
     * step down to anything with a depth buffer so very old GPUs still get a picture.
     */
    private class ConfigChooser : EGLConfigChooser {
        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            val attempts = listOf(
                intArrayOf(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_DEPTH_SIZE, 24, EGL10.EGL_STENCIL_SIZE, 8, EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, 4, EGL10.EGL_NONE),
                intArrayOf(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_DEPTH_SIZE, 24, EGL10.EGL_STENCIL_SIZE, 8, EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL10.EGL_NONE),
                intArrayOf(EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_STENCIL_SIZE, 8, EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL10.EGL_NONE),
                intArrayOf(EGL10.EGL_DEPTH_SIZE, 16, EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL10.EGL_NONE),
            )
            for (spec in attempts) {
                val count = IntArray(1)
                if (!egl.eglChooseConfig(display, spec, null, 0, count) || count[0] <= 0) continue
                val configs = arrayOfNulls<EGLConfig>(count[0])
                if (!egl.eglChooseConfig(display, spec, configs, count[0], count)) continue
                // Prefer an exact RGB888 match (some drivers list larger/odd formats first).
                val pick = configs.firstOrNull { c ->
                    c != null && attr(egl, display, c, EGL10.EGL_RED_SIZE) == 8 &&
                        attr(egl, display, c, EGL10.EGL_GREEN_SIZE) == 8 &&
                        attr(egl, display, c, EGL10.EGL_BLUE_SIZE) == 8
                } ?: configs.firstOrNull()
                if (pick != null) return pick
            }
            throw IllegalStateException("No usable EGL config for OpenGL ES 2.0")
        }

        private fun attr(egl: EGL10, d: EGLDisplay, c: EGLConfig, a: Int): Int {
            val v = IntArray(1)
            return if (egl.eglGetConfigAttrib(d, c, a, v)) v[0] else 0
        }

        companion object {
            private const val EGL_OPENGL_ES2_BIT = 4
        }
    }
}
