package com.mapp.app.renderer

import android.opengl.GLES11Ext
import android.opengl.GLES30
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Session
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Draws the ARCore camera feed as a full-screen quad using an external OES texture.
 * Uses [Frame.transformCoordinates2d] so the preview stays correct under display rotation
 * without extra CPU work.
 */
internal class BackgroundRenderer {

    private var program = 0
    private var positionAttrib = 0
    private var texCoordAttrib = 0
    private var textureUniform = 0

    private val quadCoords2d = floatArrayOf(
        -1f, -1f,
        -1f, +1f,
        +1f, -1f,
        +1f, +1f,
    )
    private val quadTexCoords = FloatArray(quadCoords2d.size)

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var texBuffer: FloatBuffer

    var textureId = -1
        private set

    fun createOnGlThread() {
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        textureId = textures[0]
        val target = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
        GLES30.glBindTexture(target, textureId)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)

        val vertexShader = """
            #version 300 es
            in vec2 a_Position;
            in vec2 a_TexCoord;
            out vec2 v_TexCoord;
            void main() {
              gl_Position = vec4(a_Position, 0.0, 1.0);
              v_TexCoord = a_TexCoord;
            }
        """.trimIndent()

        val fragmentShader = """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;
            in vec2 v_TexCoord;
            uniform samplerExternalOES sTexture;
            out vec4 fragColor;
            void main() {
              fragColor = texture(sTexture, v_TexCoord);
            }
        """.trimIndent()

        program = ShaderUtil.createProgram(vertexShader, fragmentShader)
        positionAttrib = GLES30.glGetAttribLocation(program, "a_Position")
        texCoordAttrib = GLES30.glGetAttribLocation(program, "a_TexCoord")
        textureUniform = GLES30.glGetUniformLocation(program, "sTexture")

        vertexBuffer = ByteBuffer.allocateDirect(quadCoords2d.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(quadCoords2d)
        vertexBuffer.position(0)

        texBuffer = ByteBuffer.allocateDirect(quadTexCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        texBuffer.position(0)
    }

    fun bindSession(session: Session) {
        session.setCameraTextureName(textureId)
    }

    fun draw(frame: Frame) {
        if (program == 0) return
        frame.transformCoordinates2d(
            Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
            quadCoords2d.clone(),
            Coordinates2d.TEXTURE_NORMALIZED,
            quadTexCoords,
        )
        texBuffer.clear()
        texBuffer.put(quadTexCoords)
        texBuffer.position(0)

        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)

        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES30.glUseProgram(program)
        GLES30.glUniform1i(textureUniform, 0)

        GLES30.glVertexAttribPointer(positionAttrib, 2, GLES30.GL_FLOAT, false, 0, vertexBuffer)
        GLES30.glVertexAttribPointer(texCoordAttrib, 2, GLES30.GL_FLOAT, false, 0, texBuffer)
        GLES30.glEnableVertexAttribArray(positionAttrib)
        GLES30.glEnableVertexAttribArray(texCoordAttrib)

        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        GLES30.glDisableVertexAttribArray(positionAttrib)
        GLES30.glDisableVertexAttribArray(texCoordAttrib)
        GLES30.glDepthMask(true)
    }
}
