package com.mapp.app.renderer

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.mapp.app.distance.DistanceCalculator
import com.mapp.app.distance.DistanceSource
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max
import kotlin.math.min

/**
 * GLES3 renderer: ARCore camera background, distance sampling, dynamically scaled grey cube with black edges.
 *
 * Tuning: [distanceToScaleK] implements `scale = clamp(k * distanceMeters, min, max)`.
 */
class ArSceneGlRenderer(
    private val distanceCalculator: DistanceCalculator,
) : GLSurfaceView.Renderer {

    var session: Session? = null

    /** Pixels of the GL surface (set in [onSurfaceChanged]). */
    private var surfaceWidth = 1
    private var surfaceHeight = 1

    /** Center hit-test in view coordinates (top-left origin), updated from UI thread. */
    @Volatile
    var hitXView: Float = 0f

    @Volatile
    var hitYView: Float = 0f

    var distanceToScaleK: Float = 0.35f
    var minObjectScale: Float = 0.08f
    var maxObjectScale: Float = 1.6f

    var listener: ArRenderListener? = null

    private val backgroundRenderer = BackgroundRenderer()
    private val cubeRenderer = CubeOutlineRenderer()

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvp = FloatArray(16)

    private var viewportSetSession: (() -> Unit)? = null

    fun setViewportSessionHook(hook: () -> Unit) {
        viewportSetSession = hook
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        backgroundRenderer.createOnGlThread()
        cubeRenderer.createOnGlThread()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = max(1, width)
        surfaceHeight = max(1, height)
        GLES30.glViewport(0, 0, surfaceWidth, surfaceHeight)
        viewportSetSession?.invoke()

        if (hitXView == 0f && hitYView == 0f) {
            hitXView = surfaceWidth * 0.5f
            hitYView = surfaceHeight * 0.5f
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        val sess = session
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        if (sess == null) {
            listener?.onFrameStats(Float.NaN, DistanceSource.INTRINSICS_ASSUMED_SIZE, false)
            return
        }

        backgroundRenderer.bindSession(sess)

        val frame = try {
            sess.update()
        } catch (_: Throwable) {
            listener?.onFrameStats(Float.NaN, DistanceSource.INTRINSICS_ASSUMED_SIZE, false)
            return
        }

        // Camera feed should keep rendering even when tracking is recovering (common on mid-range devices).
        backgroundRenderer.draw(frame)

        val camera = frame.camera
        val trackingOk = camera.trackingState == TrackingState.TRACKING

        if (!trackingOk) {
            listener?.onFrameStats(Float.NaN, DistanceSource.INTRINSICS_ASSUMED_SIZE, false)
            return
        }

        val estimate = distanceCalculator.estimate(
            frame,
            camera,
            surfaceWidth,
            surfaceHeight,
            hitXView,
            hitYView,
        )

        val dist = estimate.distanceMeters
        val scale = clampScale(distanceToScaleK * dist)

        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)
        camera.getViewMatrix(viewMatrix, 0)
        estimate.objectPose.toMatrix(modelMatrix, 0)

        CubeOutlineRenderer.buildModelViewProjection(
            projectionMatrix,
            viewMatrix,
            modelMatrix,
            scale,
            mvp,
        )

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(true)
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)

        cubeRenderer.drawMvp(mvp)

        listener?.onFrameStats(dist, estimate.source, true)
    }

    private fun clampScale(raw: Float): Float {
        return min(maxObjectScale, max(minObjectScale, raw))
    }

    fun interface ArRenderListener {
        fun onFrameStats(distanceMeters: Float, source: DistanceSource, trackingOk: Boolean)
    }
}
