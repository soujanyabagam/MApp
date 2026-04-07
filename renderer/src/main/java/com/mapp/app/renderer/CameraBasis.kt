package com.mapp.app.renderer

import android.opengl.Matrix
import kotlin.math.sqrt

/**
 * Derives camera **up** (parallel to phone vertical / preview Y) and **forward** (into scene, ⊥ screen)
 * in world space from the ARCore view matrix, so the dissector can stay locked to the device.
 */
internal object CameraBasis {

    fun upWorldFromView(viewMatrix16: FloatArray): FloatArray {
        return axisWorldFromView(viewMatrix16, 0f, 1f, 0f)
    }

    fun forwardWorldFromView(viewMatrix16: FloatArray): FloatArray {
        return axisWorldFromView(viewMatrix16, 0f, 0f, -1f)
    }

    private fun axisWorldFromView(viewMatrix16: FloatArray, ax: Float, ay: Float, az: Float): FloatArray {
        val inv = FloatArray(16)
        if (!Matrix.invertM(inv, 0, viewMatrix16, 0)) {
            return floatArrayOf(ax, ay, az)
        }
        val dir = floatArrayOf(ax, ay, az, 0f)
        val out = FloatArray(4)
        Matrix.multiplyMV(out, 0, inv, 0, dir, 0)
        val len = sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2])
        if (len < 1e-6f) return floatArrayOf(ax, ay, az)
        return floatArrayOf(out[0] / len, out[1] / len, out[2] / len)
    }

    fun cross(a: FloatArray, b: FloatArray): FloatArray {
        return floatArrayOf(
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0],
        )
    }

    fun normalize(v: FloatArray): FloatArray {
        val len = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        if (len < 1e-6f) return floatArrayOf(0f, 0f, 1f)
        return floatArrayOf(v[0] / len, v[1] / len, v[2] / len)
    }
}
