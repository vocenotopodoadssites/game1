package com.example.alienfall.renderer

import android.opengl.GLES20

class ShaderProgram(
    vertexSrc: String,
    fragmentSrc: String,
    val maxSkinBones: Int = DEFAULT_MAX_SKIN_BONES
) {
    val programId: Int

    val uMvpMatrix: Int
    val uModelMatrix: Int
    val uLightDir: Int
    val uTintColor: Int
    val uEmissive: Int
    val uHitFlash: Int
    val uCameraPos: Int
    val uFogColor: Int
    val uFogDensity: Int
    val uUseTexture: Int
    val uTexture: Int
    val uUseSkinning: Int
    val uBoneMatrices: Int

    init {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        programId = GLES20.glCreateProgram()
        GLES20.glAttachShader(programId, vs)
        GLES20.glAttachShader(programId, fs)
        GLES20.glBindAttribLocation(programId, 0, "aPosition")
        GLES20.glBindAttribLocation(programId, 1, "aNormal")
        GLES20.glBindAttribLocation(programId, 2, "aColor")
        GLES20.glBindAttribLocation(programId, 3, "aTexCoord")
        GLES20.glBindAttribLocation(programId, 4, "aJoints")
        GLES20.glBindAttribLocation(programId, 5, "aWeights")
        GLES20.glLinkProgram(programId)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(programId)
            GLES20.glDeleteProgram(programId)
            throw RuntimeException("Shader link error: $log")
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)

        uMvpMatrix = GLES20.glGetUniformLocation(programId, "uMvpMatrix")
        uModelMatrix = GLES20.glGetUniformLocation(programId, "uModelMatrix")
        uLightDir = GLES20.glGetUniformLocation(programId, "uLightDir")
        uTintColor = GLES20.glGetUniformLocation(programId, "uTintColor")
        uEmissive = GLES20.glGetUniformLocation(programId, "uEmissive")
        uHitFlash = GLES20.glGetUniformLocation(programId, "uHitFlash")
        uCameraPos = GLES20.glGetUniformLocation(programId, "uCameraPos")
        uFogColor = GLES20.glGetUniformLocation(programId, "uFogColor")
        uFogDensity = GLES20.glGetUniformLocation(programId, "uFogDensity")
        uUseTexture = GLES20.glGetUniformLocation(programId, "uUseTexture")
        uTexture = GLES20.glGetUniformLocation(programId, "uTexture")
        uUseSkinning = GLES20.glGetUniformLocation(programId, "uUseSkinning")
        uBoneMatrices = GLES20.glGetUniformLocation(programId, "uBoneMatrices")
    }

    fun use() {
        GLES20.glUseProgram(programId)
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Shader compile error: $log")
        }
        return shader
    }

    companion object {
        const val DEFAULT_MAX_SKIN_BONES = 68

        fun chooseSupportedMaxBones(): Int {
            val maxVecs = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_VERTEX_UNIFORM_VECTORS, maxVecs, 0)
            return when {
                maxVecs[0] >= 288 -> 68
                maxVecs[0] >= 144 -> 32
                else -> 0
            }
        }

        fun buildVertexShader(maxBones: Int): String = if (maxBones > 0) {
            """
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec4 aColor;
            attribute vec2 aTexCoord;
            attribute vec4 aJoints;
            attribute vec4 aWeights;

            uniform mat4 uMvpMatrix;
            uniform mat4 uModelMatrix;
            uniform float uUseSkinning;
            uniform mat4 uBoneMatrices[$maxBones];

            varying vec3 vWorldPos;
            varying vec3 vWorldNormal;
            varying vec4 vColor;
            varying vec2 vTexCoord;

            void main() {
                vec4 localPos = vec4(aPosition, 1.0);
                vec3 localNormal = aNormal;
                if (uUseSkinning > 0.5) {
                    float wSum = aWeights.x + aWeights.y + aWeights.z + aWeights.w;
                    if (wSum > 0.0001) {
                        int j0 = int(clamp(aJoints.x, 0.0, ${maxBones - 1}.0));
                        int j1 = int(clamp(aJoints.y, 0.0, ${maxBones - 1}.0));
                        int j2 = int(clamp(aJoints.z, 0.0, ${maxBones - 1}.0));
                        int j3 = int(clamp(aJoints.w, 0.0, ${maxBones - 1}.0));
                        mat4 skinMat =
                            uBoneMatrices[j0] * (aWeights.x / wSum) +
                            uBoneMatrices[j1] * (aWeights.y / wSum) +
                            uBoneMatrices[j2] * (aWeights.z / wSum) +
                            uBoneMatrices[j3] * (aWeights.w / wSum);
                        localPos = skinMat * localPos;
                        localNormal = (skinMat * vec4(aNormal, 0.0)).xyz;
                    }
                }
                vec4 worldPos = uModelMatrix * localPos;
                vWorldPos = worldPos.xyz;
                vWorldNormal = normalize((uModelMatrix * vec4(localNormal, 0.0)).xyz);
                vColor = aColor;
                vTexCoord = aTexCoord;
                gl_Position = uMvpMatrix * localPos;
            }
            """
        } else {
            VERTEX_SHADER
        }

        const val VERTEX_SHADER = """
            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec4 aColor;
            attribute vec2 aTexCoord;

            uniform mat4 uMvpMatrix;
            uniform mat4 uModelMatrix;

            varying vec3 vWorldPos;
            varying vec3 vWorldNormal;
            varying vec4 vColor;
            varying vec2 vTexCoord;

            void main() {
                vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
                vWorldPos = worldPos.xyz;
                vWorldNormal = normalize((uModelMatrix * vec4(aNormal, 0.0)).xyz);
                vColor = aColor;
                vTexCoord = aTexCoord;
                gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
            }
        """

        const val FRAGMENT_SHADER = """
            precision mediump float;

            varying vec3 vWorldPos;
            varying vec3 vWorldNormal;
            varying vec4 vColor;
            varying vec2 vTexCoord;

            uniform vec3 uLightDir;
            uniform vec4 uTintColor;
            uniform float uEmissive;
            uniform float uHitFlash;
            uniform vec3 uCameraPos;
            uniform vec3 uFogColor;
            uniform float uFogDensity;
            uniform float uUseTexture;
            uniform sampler2D uTexture;

            void main() {
                vec3 N = normalize(vWorldNormal);
                vec3 L = normalize(uLightDir);
                vec3 V = normalize(uCameraPos - vWorldPos);
                vec3 H = normalize(L + V);

                float diff = max(dot(N, L), 0.0);
                float hemi = 0.5 + 0.5 * N.y;
                vec3 ambient = mix(vec3(0.16, 0.13, 0.20), vec3(0.32, 0.34, 0.42), hemi);
                float spec = pow(max(dot(N, H), 0.0), 24.0) * 0.26;
                float rim = pow(1.0 - max(dot(N, V), 0.0), 3.0) * 0.20;

                vec4 texCol = uUseTexture > 0.5 ? texture2D(uTexture, vTexCoord) : vec4(1.0);
                vec4 baseCol = vColor * texCol * uTintColor;
                vec3 lit = baseCol.rgb * (ambient + diff * 0.92 + uEmissive) + vec3(spec + rim * 0.35);
                lit = mix(lit, vec3(1.0, 0.92, 0.85), clamp(uHitFlash, 0.0, 1.0));

                float dist = length(uCameraPos - vWorldPos);
                float fogFactor = clamp(1.0 - exp(-pow(dist * uFogDensity, 2.0)), 0.0, 0.85);
                vec3 finalRgb = mix(lit, uFogColor, fogFactor);

                gl_FragColor = vec4(finalRgb, baseCol.a);
            }
        """
    }
}
