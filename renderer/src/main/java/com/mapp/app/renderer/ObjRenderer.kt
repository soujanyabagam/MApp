package com.mapp.app.renderer

import android.opengl.GLES30
import java.nio.FloatBuffer

/** Solid + edge draw for triangular mesh (same visual language as [CubeOutlineRenderer]). */
internal class ObjRenderer {

    private var program = 0
    private var positionAttrib = 0
    private var colorUniform = 0
    private var mvpUniform = 0

    private var vertexBuffer: FloatBuffer? = null
    private var vertexCount = 0

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

    fun setMesh(buffer: FloatBuffer, count: Int) {
        vertexBuffer = buffer
        vertexCount = count
    }

    fun drawMvp(mvpMatrix: FloatArray, greyRgb: Float = 0.55f) {
        val vb = vertexBuffer ?: return
        if (vertexCount < 3) return

        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(mvpUniform, 1, false, mvpMatrix, 0)

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)

        GLES30.glUniform4f(colorUniform, greyRgb, greyRgb, greyRgb, 1f)
        vb.position(0)
        GLES30.glVertexAttribPointer(positionAttrib, 3, GLES30.GL_FLOAT, false, 0, vb)
        GLES30.glEnableVertexAttribArray(positionAttrib)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, vertexCount)

        GLES30.glDisableVertexAttribArray(positionAttrib)
    }
}
