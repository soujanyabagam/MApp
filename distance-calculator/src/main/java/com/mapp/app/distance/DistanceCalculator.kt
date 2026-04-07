package com.mapp.app.distance

import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import kotlin.math.sqrt

/**
 * Computes distance from the camera to a hit point and provides fallback estimation when no plane is hit.
 *
 * **Primary path:** [Frame.hitTest] from the screen center (or optional tap point) and first
 * [Plane] in [TrackingState.TRACKING]. Distance is Euclidean between camera and hit pose origins
 * in world space.
 *
 * **Fallback path:** If no plane intersects the ray, estimate distance with pinhole geometry:
 * `Z ≈ (assumedRealWidthMeters * fxPixels) / apparentPixelWidth`, where `apparentPixelWidth` is
 * `imageWidth * assumedVisibleFractionOfImageWidth`. This does **not** use Depth API and is only
 * a rough stand-in until planes are found. Adjust [assumedRealWidthMeters] and
 * [assumedVisibleFractionOfImageWidth] for your scene.
 */
class DistanceCalculator(
    /** Real-world width (meters) assumed for the fallback heuristic (e.g. doorway, desk edge). */
    var assumedRealWidthMeters: Float = 1.0f,
    /** Fraction of full texture width the assumed object is thought to occupy (0–1]. */
    var assumedVisibleFractionOfImageWidth: Float = 0.25f
) {

    /**
     * @param viewWidthPx / viewHeightPx size of the GL surface
     * @param xView yView pointer location in **Android view** coordinates (origin top-left)
     */
    fun estimate(
        frame: Frame,
        camera: Camera,
        viewWidthPx: Int,
        viewHeightPx: Int,
        xView: Float,
        yView: Float
    ): DistanceEstimate {
        if (camera.trackingState != TrackingState.TRACKING) {
            val z = fallbackDistanceMeters(frame, camera)
            return DistanceEstimate(z, poseAlongCameraRay(camera, z), DistanceSource.INTRINSICS_ASSUMED_SIZE)
        }

        val xAr = xView
        val yAr = viewHeightPx - yView

        val hit = firstHit(frame.hitTest(xAr, yAr))
        if (hit != null) {
            val dist = distanceMeters(camera.pose, hit.hitPose)
            val source = if (isCenterHit(xView, yView, viewWidthPx, viewHeightPx)) {
                DistanceSource.PLANE_HIT_CENTER
            } else {
                DistanceSource.PLANE_HIT_TAP
            }
            return DistanceEstimate(dist, hit.hitPose, source)
        }

        val z = fallbackDistanceMeters(frame, camera)
        return DistanceEstimate(z, poseAlongCameraRay(camera, z), DistanceSource.INTRINSICS_ASSUMED_SIZE)
    }

    private fun isCenterHit(x: Float, y: Float, w: Int, h: Int): Boolean {
        val cx = w * 0.5f
        val cy = h * 0.5f
        val tol = 24f
        return kotlin.math.abs(x - cx) <= tol && kotlin.math.abs(y - cy) <= tol
    }

    private fun firstHit(hits: List<HitResult>): HitResult? {
        return hits.firstOrNull()
    }

    private fun distanceMeters(cameraPose: Pose, worldPose: Pose): Float {
        val tc = FloatArray(3)
        val tw = FloatArray(3)
        cameraPose.getTranslation(tc, 0)
        worldPose.getTranslation(tw, 0)
        val dx = tw[0] - tc[0]
        val dy = tw[1] - tc[1]
        val dz = tw[2] - tc[2]
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * Pinhole-style depth estimate using horizontal focal length from texture intrinsics.
     */
    private fun fallbackDistanceMeters(@Suppress("UNUSED_PARAMETER") frame: Frame, camera: Camera): Float {
        val intrinsics = camera.textureIntrinsics
        val dims = intrinsics.imageDimensions
        val imageWidth = dims[0].coerceAtLeast(1)
        val fx = intrinsics.focalLength[0].takeIf { it > 1e-4f } ?: (imageWidth * 0.7f)
        val frac = assumedVisibleFractionOfImageWidth.coerceIn(0.05f, 1f)
        val apparentPx = (imageWidth * frac).coerceAtLeast(1f)
        val w = assumedRealWidthMeters.coerceAtLeast(0.01f)
        // Similar triangles: w / Z = apparentPx / fx  =>  Z = w * fx / apparentPx
        val z = (w * fx) / apparentPx
        return z.coerceIn(0.15f, 25f)
    }

    /**
     * Cube anchor along camera -Z (ARCore camera forward) at [distanceMeters].
     */
    private fun poseAlongCameraRay(camera: Camera, distanceMeters: Float): Pose {
        val cam = camera.pose
        // Point in front of the camera in world space.
        return cam.compose(Pose.makeTranslation(0f, 0f, -distanceMeters))
    }
}
