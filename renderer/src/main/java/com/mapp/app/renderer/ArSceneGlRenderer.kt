package com.mapp.app.renderer

import android.content.res.AssetManager
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.mapp.app.distance.DistanceCalculator
import com.mapp.app.distance.DistanceSource
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max
import kotlin.math.min

/**
 * GLES3 renderer: ARCore background, distance sampling, OBJ dissector (or fallback cube).
 *
 * **Dissector orientation (locked to device, not plane):** model **+X** = shorter leg → camera forward (⊥ screen);
 * **+Y** = longer leg → camera up (parallel to phone vertical edge / preview Y). Rotation is recomputed each frame from
 * the view matrix so the mesh does not freely rotate with the hit pose.
 *
 * **Scale:** at distance 0, apparent scale is [objectBaseScale]; as distance increases, scale is divided by `(1 + [distanceToScaleK] * distance)` (clamped).
 */
class ArSceneGlRenderer(
    private val assetManager: AssetManager,
    private val distanceCalculator: DistanceCalculator,
) : GLSurfaceView.Renderer {

    var session: Session? = null

    /** Path under `assets/` (e.g. `models/des01671317.obj`). Null or missing file → draw unit cube instead. */
    var objAssetPath: String? = "models/des01671317.obj"

    /**
     * World scale applied at **zero** distance (after mesh centering). Tune so the model matches real size on device.
     */
    var objectBaseScale: Float = 0.4f

    private var surfaceWidth = 1
    private var surfaceHeight = 1

    @Volatile
    var hitXView: Float = 0f

    @Volatile
    var hitYView: Float = 0f

    /** Multiplier `k` in `scale = base / (1 + k * distanceMeters)`. Larger → faster shrink with distance. */
    var distanceToScaleK: Float = 0.35f

    var minObjectScale: Float = 0.06f
    var maxObjectScale: Float = 2f

    var listener: ArRenderListener? = null

    private val backgroundRenderer = BackgroundRenderer()
    private val cubeRenderer = CubeOutlineRenderer()
    private val objRenderer = ObjRenderer()

    private var objLoaded = false

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val poseMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val rotationMatrix = FloatArray(16)
    private val scaleMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)
    private val translateScratch = FloatArray(16)
    private val mvp = FloatArray(16)

    private var viewportSetSession: (() -> Unit)? = null

    fun setViewportSessionHook(hook: () -> Unit) {
        viewportSetSession = hook
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        backgroundRenderer.createOnGlThread()
        cubeRenderer.createOnGlThread()
        objRenderer.createOnGlThread()

        val path = objAssetPath
        if (path != null) {
            val mesh = ObjMeshLoader.loadFromAssets(assetManager, path)
            if (mesh != null) {
                objRenderer.setMesh(mesh.vertexBuffer, mesh.vertexCount)
                objLoaded = true
            } else {
                objLoaded = false
            }
        } else {
            objLoaded = false
        }
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
        val distanceScale = scaleFromDistance(dist)
        val uniformScale = (objectBaseScale * distanceScale).let { clampScale(it) }

        camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100f)
        camera.getViewMatrix(viewMatrix, 0)

        estimate.objectPose.toMatrix(poseMatrix, 0)

        buildPhoneLockedModelMatrix(poseMatrix, viewMatrix, uniformScale, modelMatrix)

        CubeOutlineRenderer.buildMvp(projectionMatrix, viewMatrix, modelMatrix, mvp)

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(true)
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)

        if (objLoaded) {
            objRenderer.drawMvp(mvp)
        } else {
            cubeRenderer.drawMvp(mvp)
        }

        listener?.onFrameStats(dist, estimate.source, true)
    }

    /**
     * `1 / (1 + k * d)` so scale is full at d = 0 and decreases as distance grows.
     */
    private fun scaleFromDistance(distanceMeters: Float): Float {
        val d = if (distanceMeters.isNaN() || distanceMeters < 0f) 0f else distanceMeters
        val denom = 1f + distanceToScaleK * d
        return if (denom > 1e-6f) 1f / denom else maxObjectScale
    }

    private fun clampScale(raw: Float): Float {
        return min(maxObjectScale, max(minObjectScale, raw))
    }

    /**
     * Translation from plane / ray hit; rotation from camera basis (shorter leg → forward, longer → up).
     */
    private fun buildPhoneLockedModelMatrix(
        poseFromEstimate: FloatArray,
        view16: FloatArray,
        uniformScale: Float,
        outModel: FloatArray,
    ) {
        val tx = poseFromEstimate[12]
        val ty = poseFromEstimate[13]
        val tz = poseFromEstimate[14]

        val forward = CameraBasis.forwardWorldFromView(view16)
        val upRaw = CameraBasis.upWorldFromView(view16)
        val side = CameraBasis.normalize(CameraBasis.cross(forward, upRaw))
        val up = CameraBasis.normalize(CameraBasis.cross(side, forward))

        Matrix.setIdentityM(rotationMatrix, 0)
        // Column 0: model +X → shorter leg → screen normal / forward into scene
        rotationMatrix[0] = forward[0]
        rotationMatrix[1] = forward[1]
        rotationMatrix[2] = forward[2]
        rotationMatrix[3] = 0f
        // Column 1: model +Y → longer leg → phone vertical / preview up
        rotationMatrix[4] = up[0]
        rotationMatrix[5] = up[1]
        rotationMatrix[6] = up[2]
        rotationMatrix[7] = 0f
        // Column 2: completes right-handed basis
        rotationMatrix[8] = side[0]
        rotationMatrix[9] = side[1]
        rotationMatrix[10] = side[2]
        rotationMatrix[11] = 0f
        rotationMatrix[12] = 0f
        rotationMatrix[13] = 0f
        rotationMatrix[14] = 0f
        rotationMatrix[15] = 1f

        Matrix.setIdentityM(scaleMatrix, 0)
        Matrix.scaleM(scaleMatrix, 0, uniformScale, uniformScale, uniformScale)

        Matrix.multiplyMM(tempMatrix, 0, rotationMatrix, 0, scaleMatrix, 0)

        Matrix.setIdentityM(translateScratch, 0)
        Matrix.translateM(translateScratch, 0, tx, ty, tz)
        Matrix.multiplyMM(outModel, 0, translateScratch, 0, tempMatrix, 0)
    }

    fun interface ArRenderListener {
        fun onFrameStats(distanceMeters: Float, source: DistanceSource, trackingOk: Boolean)
    }
}
