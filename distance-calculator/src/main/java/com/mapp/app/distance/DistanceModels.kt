package com.mapp.app.distance

import com.google.ar.core.Pose

/**
 * How the current distance / placement was obtained. Plane hits are preferred; intrinsics fallback
 * never depends on Depth API.
 */
enum class DistanceSource {
    /** Ray from screen center hit a trackable (plane, depth point, etc.). */
    PLANE_HIT_CENTER,

    /** User tapped to cast a ray; that ray hit a trackable. */
    PLANE_HIT_TAP,

    /**
     * No reliable hit: distance estimated from texture intrinsics and an assumed real-world
     * width for an object spanning a fraction of the image (weak heuristic; tunable).
     */
    INTRINSICS_ASSUMED_SIZE
}

/**
 * Result used by the renderer for pose, scale, and UI.
 */
data class DistanceEstimate(
    val distanceMeters: Float,
    /** World-space pose for the cube center (may be synthesized along the camera ray for fallback). */
    val objectPose: Pose,
    val source: DistanceSource
)
