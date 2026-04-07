package com.mapp.app.renderer

import android.opengl.GLES30
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Lightweight unit cube (12 triangles, 12 line segments for outline). Grey fill + black edge lines.
 * No fancy shaders — suitable for mid-range devices.
 */
internal class CubeOutlineRenderer {

    private var program = 0
    private var positionAttrib = 0
    private var colorUniform = 0
    private var mvpUniform = 0

    private val vertexBuffer: FloatBuffer
    private val indexBufferTriangles: java.nio.ShortBuffer
    private val indexBufferLines: java.nio.ShortBuffer

    init {
        val h = 0.5f
        // 8 corners
        val verts = floatArrayOf(
            -h, -h, +h,
            +h, -h, +h,
            +h, +h, +h,
            -h, +h, +h,
            -h, -h, -h,
            +h, -h, -h,
            +h, +h, -h,
            -h, +h, -h,
        )
        vertexBuffer = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(verts)
        vertexBuffer.position(0)

        // 12 triangles (CCW), outward facing
        val triIdx = shortArrayOf(
            0, 1, 2, 0, 2, 3, // front
            4, 5, 6, 4, 6, 7, // back
            0, 4, 7, 0, 7, 3, // left
            1, 5, 6, 1, 6, 2, // right
            3, 2, 6, 3, 6, 7, // top
            0, 1, 5, 0, 5, 4, // bottom
        )
        indexBufferTriangles = ByteBuffer.allocateDirect(triIdx.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(triIdx)
        indexBufferTriangles.position(0)

        // 12 edges as lines
        val lineIdx = shortArrayOf(
            0, 1, 1, 2, 2, 3, 3, 0,
            4, 5, 5, 6, 6, 7, 7, 4,
            0, 4, 1, 5, 2, 6, 3, 7,
        )
        indexBufferLines = ByteBuffer.allocateDirect(lineIdx.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .put(lineIdx)
        indexBufferLines.position(0)
    }

    fun createOnGlThread() {
        val vs = """
            #version 300 es
            uniform mat4 u_MVPMatrix;
            in vec4 a_Position;
            void main() {
              gl_Position = u_MVPMatrix * a_Position;
            }
        """.trimIndent()
        val fs = """
            #version 300 es
            precision mediump float;
            uniform vec4 u_Color;
            out vec4 fragColor;
            void main() {
              fragColor = u_Color;
            }
        """.trimIndent()
        program = ShaderUtil.createProgram(vs, fs)
        positionAttrib = GLES30.glGetAttribLocation(program, "a_Position")
        colorUniform = GLES30.glGetUniformLocation(program, "u_Color")
        mvpUniform = GLES30.glGetUniformLocation(program, "u_MVPMatrix")
    }

    fun drawMvp(mvpMatrix: FloatArray, greyRgb: Float = 0.55f) {
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(mvpUniform, 1, false, mvpMatrix, 0)

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)

        // Fill
        GLES30.glUniform4f(colorUniform, greyRgb, greyRgb, greyRgb, 1f)
        GLES30.glVertexAttribPointer(positionAttrib, 3, GLES30.GL_FLOAT, false, 0, vertexBuffer)
        GLES30.glEnableVertexAttribArray(positionAttrib)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, 36, GLES30.GL_UNSIGNED_SHORT, indexBufferTriangles)

        // Black outline on top
        GLES30.glUniform4f(colorUniform, 0f, 0f, 0f, 1f)
        GLES30.glLineWidth(3f)
        GLES30.glDrawElements(GLES30.GL_LINES, lineIdxCount(), GLES30.GL_UNSIGNED_SHORT, indexBufferLines)

        GLES30.glDisableVertexAttribArray(positionAttrib)
    }

    private fun lineIdxCount(): Int = 24

    companion object {
        fun buildModelViewProjection(
            proj: FloatArray,
            view: FloatArray,
            modelFromPose: FloatArray,
            uniformScale: Float,
            outMvp: FloatArray,
        ) {
            val scale = FloatArray(16)
            Matrix.setIdentityM(scale, 0)
            Matrix.scaleM(scale, 0, uniformScale, uniformScale, uniformScale)

            val modelScaled = FloatArray(16)
            Matrix.multiplyMM(modelScaled, 0, modelFromPose, 0, scale, 0)

            buildMvp(proj, view, modelScaled, outMvp)
        }

        fun buildMvp(
            proj: FloatArray,
            view: FloatArray,
            modelMatrix: FloatArray,
            outMvp: FloatArray,
        ) {
            val mv = FloatArray(16)
            Matrix.multiplyMM(mv, 0, view, 0, modelMatrix, 0)
            Matrix.multiplyMM(outMvp, 0, proj, 0, mv, 0)
        }
    }
}
