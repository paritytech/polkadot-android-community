package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.coins

import android.opengl.GLES20

object CoinageGlProgram {
    fun link(vertexSource: String, fragmentSource: String): Int {
        val program = GLES20.glCreateProgram()

        GLES20.glAttachShader(program, compile(GLES20.GL_VERTEX_SHADER, vertexSource))
        GLES20.glAttachShader(program, compile(GLES20.GL_FRAGMENT_SHADER, fragmentSource))
        GLES20.glLinkProgram(program)

        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)

        check(status[0] != 0) { "coin program did not link: ${GLES20.glGetProgramInfoLog(program)}" }

        return program
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)

        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)

        check(status[0] != 0) { "coin shader did not compile: ${GLES20.glGetShaderInfoLog(shader)}" }

        return shader
    }
}
