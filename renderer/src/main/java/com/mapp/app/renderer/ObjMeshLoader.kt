package com.mapp.app.renderer

import android.content.res.AssetManager
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max

internal object ObjMeshLoader {
    private const val TAG = "ObjMeshLoader"

    data class Mesh(
        val vertexBuffer: FloatBuffer,
        val vertexCount: Int,
        val halfExtents: FloatArray,
    )

    fun loadFromAssets(assets: AssetManager, assetPath: String): Mesh? {
        return try {
            assets.open(assetPath).use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    parse(reader.readLines())
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load OBJ: $assetPath", e)
            null
        }
    }

    private fun parse(lines: List<String>): Mesh? {
        val positions = ArrayList<Triple<Float, Float, Float>>(256)
        val faceCorners = ArrayList<IntArray>()

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val parts = line.split(Regex("\\s+"))
            when (parts.firstOrNull()) {
                "v" -> {
                    if (parts.size >= 4) {
                        positions.add(
                            Triple(
                                parts[1].toFloat(),
                                parts[2].toFloat(),
                                parts[3].toFloat(),
                            ),
                        )
                    }
                }
                "f" -> {
                    if (parts.size >= 4) {
                        val idx = IntArray(parts.size - 1)
                        for (i in 1 until parts.size) {
                            val corner = parts[i]
                            val vPart = corner.substringBefore("/")
                            idx[i - 1] = vPart.toInt()
                        }
                        faceCorners.add(idx)
                    }
                }
            }
        }

        if (positions.isEmpty()) return null

        val verts = FloatArrayList()

        for (face in faceCorners) {
            if (face.size < 3) continue
            val i0 = face[0] - 1
            if (i0 !in positions.indices) continue

            for (k in 1 until face.size - 1) {
                val i1 = face[k] - 1
                val i2 = face[k + 1] - 1
                if (i1 !in positions.indices || i2 !in positions.indices) continue

                val p0 = positions[i0]
                val p1 = positions[i1]
                val p2 = positions[i2]

                verts.add(p0.first, p0.second, p0.third)
                verts.add(p1.first, p1.second, p1.third)
                verts.add(p2.first, p2.second, p2.third)
            }
        }

        if (verts.size < 9) return null

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY

        var i = 0
        while (i < verts.size) {
            val x = verts[i]
            val y = verts[i + 1]
            val z = verts[i + 2]

            minX = minOf(minX, x)
            minY = minOf(minY, y)
            minZ = minOf(minZ, z)
            maxX = maxOf(maxX, x)
            maxY = maxOf(maxY, y)
            maxZ = maxOf(maxZ, z)

            i += 3
        }

        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        val cz = (minZ + maxZ) * 0.5f

        i = 0
        while (i < verts.size) {
            verts[i] = verts[i] - cx
            verts[i + 1] = verts[i + 1] - cy
            verts[i + 2] = verts[i + 2] - cz
            i += 3
        }

        val hx = (maxX - minX) * 0.5f
        val hy = (maxY - minY) * 0.5f
        val hz = (maxZ - minZ) * 0.5f

        val halfExtents = floatArrayOf(
            max(hx, 1e-4f),
            max(hy, 1e-4f),
            max(hz, 1e-4f),
        )

        val bb = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(verts.toFloatArray())

        bb.position(0)

        return Mesh(
            vertexBuffer = bb,
            vertexCount = verts.size / 3,
            halfExtents = halfExtents,
        )
    }

    private class FloatArrayList {
        private var data = FloatArray(1024)
        var size: Int = 0
            private set

        fun add(x: Float, y: Float, z: Float) {
            if (size + 3 > data.size) {
                data = data.copyOf(max(data.size * 2, size + 3))
            }
            data[size] = x
            data[size + 1] = y
            data[size + 2] = z
            size += 3
        }

        // ✅ THIS FIXES YOUR ERROR
        operator fun get(index: Int): Float {
            if (index >= size) throw IndexOutOfBoundsException()
            return data[index]
        }

        // ✅ REQUIRED because you MODIFY verts[i]
        operator fun set(index: Int, value: Float) {
            if (index >= size) throw IndexOutOfBoundsException()
            data[index] = value
        }

        fun toFloatArray(): FloatArray {
            return data.copyOf(size)
        }
    }
}