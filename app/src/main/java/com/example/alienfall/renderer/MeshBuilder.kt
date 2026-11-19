package com.example.alienfall.renderer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import com.example.alienfall.model.AlienType
import com.example.alienfall.model.BossType
import com.example.alienfall.model.HeroClass
import com.example.alienfall.model.PropKind
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

class GpuMesh(
    val vbo: Int,
    val ibo: Int,
    val indexCount: Int,
    val textureId: Int = 0,
    val skinVbo: Int = 0,
    val skinData: GlbSkinData? = null
) {
    fun draw(mode: Int = GLES20.GL_TRIANGLES) {
        val stride = 12 * 4
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glEnableVertexAttribArray(1)
        GLES20.glVertexAttribPointer(1, 3, GLES20.GL_FLOAT, false, stride, 3 * 4)
        GLES20.glEnableVertexAttribArray(2)
        GLES20.glVertexAttribPointer(2, 4, GLES20.GL_FLOAT, false, stride, 6 * 4)
        GLES20.glEnableVertexAttribArray(3)
        GLES20.glVertexAttribPointer(3, 2, GLES20.GL_FLOAT, false, stride, 10 * 4)

        if (skinVbo != 0) {
            val skinStride = 8 * 4
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, skinVbo)
            GLES20.glEnableVertexAttribArray(4)
            GLES20.glVertexAttribPointer(4, 4, GLES20.GL_FLOAT, false, skinStride, 0)
            GLES20.glEnableVertexAttribArray(5)
            GLES20.glVertexAttribPointer(5, 4, GLES20.GL_FLOAT, false, skinStride, 4 * 4)
        } else {
            GLES20.glDisableVertexAttribArray(4)
            GLES20.glDisableVertexAttribArray(5)
            GLES20.glVertexAttrib4f(4, 0f, 0f, 0f, 0f)
            GLES20.glVertexAttrib4f(5, 1f, 0f, 0f, 0f)
        }

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glDrawElements(mode, indexCount, GLES20.GL_UNSIGNED_SHORT, 0)

        if (skinVbo != 0) {
            GLES20.glDisableVertexAttribArray(4)
            GLES20.glDisableVertexAttribArray(5)
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    fun release() {
        GLES20.glDeleteBuffers(2, intArrayOf(vbo, ibo), 0)
        if (skinVbo != 0) {
            GLES20.glDeleteBuffers(1, intArrayOf(skinVbo), 0)
        }
        if (textureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        }
    }
}

class MeshAccumulator {
    private val verts = ArrayList<Float>(1024)
    private val indices = ArrayList<Short>(512)

    fun addBox(
        cx: Float, cy: Float, cz: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float, a: Float = 1f,
        rotY: Float = 0f, rotX: Float = 0f, rotZ: Float = 0f
    ) {
        val hx = sx * 0.5f
        val hy = sy * 0.5f
        val hz = sz * 0.5f

        val faces = arrayOf(
            // +Z front
            Pair(floatArrayOf(0f, 0f, 1f), arrayOf(floatArrayOf(-hx, -hy, hz), floatArrayOf(hx, -hy, hz), floatArrayOf(hx, hy, hz), floatArrayOf(-hx, hy, hz))),
            // -Z back
            Pair(floatArrayOf(0f, 0f, -1f), arrayOf(floatArrayOf(hx, -hy, -hz), floatArrayOf(-hx, -hy, -hz), floatArrayOf(-hx, hy, -hz), floatArrayOf(hx, hy, -hz))),
            // +X right
            Pair(floatArrayOf(1f, 0f, 0f), arrayOf(floatArrayOf(hx, -hy, hz), floatArrayOf(hx, -hy, -hz), floatArrayOf(hx, hy, -hz), floatArrayOf(hx, hy, hz))),
            // -X left
            Pair(floatArrayOf(-1f, 0f, 0f), arrayOf(floatArrayOf(-hx, -hy, -hz), floatArrayOf(-hx, -hy, hz), floatArrayOf(-hx, hy, hz), floatArrayOf(-hx, hy, -hz))),
            // +Y top
            Pair(floatArrayOf(0f, 1f, 0f), arrayOf(floatArrayOf(-hx, hy, hz), floatArrayOf(hx, hy, hz), floatArrayOf(hx, hy, -hz), floatArrayOf(-hx, hy, -hz))),
            // -Y bottom
            Pair(floatArrayOf(0f, -1f, 0f), arrayOf(floatArrayOf(-hx, -hy, -hz), floatArrayOf(hx, -hy, -hz), floatArrayOf(hx, -hy, hz), floatArrayOf(-hx, -hy, hz)))
        )

        for ((norm, corners) in faces) {
            val baseIdx = (verts.size / 12).toShort()
            val rn = rotateVec(norm[0], norm[1], norm[2], rotX, rotY, rotZ)
            for (c in corners) {
                val rc = rotateVec(c[0], c[1], c[2], rotX, rotY, rotZ)
                pushVertex(cx + rc[0], cy + rc[1], cz + rc[2], rn[0], rn[1], rn[2], r, g, b, a)
            }
            indices.add(baseIdx)
            indices.add((baseIdx + 1).toShort())
            indices.add((baseIdx + 2).toShort())
            indices.add(baseIdx)
            indices.add((baseIdx + 2).toShort())
            indices.add((baseIdx + 3).toShort())
        }
    }

    fun addTaperedPrism(
        cx: Float, cy: Float, cz: Float,
        bottomRadius: Float, topRadius: Float, height: Float,
        segments: Int,
        r: Float, g: Float, b: Float, a: Float = 1f,
        rotX: Float = 0f, rotY: Float = 0f, rotZ: Float = 0f
    ) {
        val hy = height * 0.5f
        for (i in 0 until segments) {
            val a0 = (i * 2f * PI.toFloat()) / segments
            val a1 = ((i + 1) * 2f * PI.toFloat()) / segments
            val c0 = cos(a0); val s0 = sin(a0)
            val c1 = cos(a1); val s1 = sin(a1)
            val am = (a0 + a1) * 0.5f
            val nx = cos(am); val nz = sin(am)
            val slope = (bottomRadius - topRadius) / height.coerceAtLeast(0.01f)
            val ny = slope
            val nLen = hypot(hypot(nx, ny), nz).coerceAtLeast(0.001f)
            val rn = rotateVec(nx / nLen, ny / nLen, nz / nLen, rotX, rotY, rotZ)

            val p0 = rotateVec(c0 * bottomRadius, -hy, s0 * bottomRadius, rotX, rotY, rotZ)
            val p1 = rotateVec(c1 * bottomRadius, -hy, s1 * bottomRadius, rotX, rotY, rotZ)
            val p2 = rotateVec(c1 * topRadius, hy, s1 * topRadius, rotX, rotY, rotZ)
            val p3 = rotateVec(c0 * topRadius, hy, s0 * topRadius, rotX, rotY, rotZ)

            val base = (verts.size / 12).toShort()
            pushVertex(cx + p0[0], cy + p0[1], cz + p0[2], rn[0], rn[1], rn[2], r, g, b, a)
            pushVertex(cx + p1[0], cy + p1[1], cz + p1[2], rn[0], rn[1], rn[2], r, g, b, a)
            pushVertex(cx + p2[0], cy + p2[1], cz + p2[2], rn[0], rn[1], rn[2], r, g, b, a)
            pushVertex(cx + p3[0], cy + p3[1], cz + p3[2], rn[0], rn[1], rn[2], r, g, b, a)

            indices.add(base); indices.add((base + 1).toShort()); indices.add((base + 2).toShort())
            indices.add(base); indices.add((base + 2).toShort()); indices.add((base + 3).toShort())

            // Top cap
            if (topRadius > 0.01f) {
                val tBase = (verts.size / 12).toShort()
                val tn = rotateVec(0f, 1f, 0f, rotX, rotY, rotZ)
                val tc = rotateVec(0f, hy, 0f, rotX, rotY, rotZ)
                pushVertex(cx + tc[0], cy + tc[1], cz + tc[2], tn[0], tn[1], tn[2], r, g, b, a)
                pushVertex(cx + p3[0], cy + p3[1], cz + p3[2], tn[0], tn[1], tn[2], r, g, b, a)
                pushVertex(cx + p2[0], cy + p2[1], cz + p2[2], tn[0], tn[1], tn[2], r, g, b, a)
                indices.add(tBase); indices.add((tBase + 1).toShort()); indices.add((tBase + 2).toShort())
            }
        }
    }

    fun addOctahedron(
        cx: Float, cy: Float, cz: Float,
        rx: Float, ry: Float, rz: Float,
        r: Float, g: Float, b: Float, a: Float = 1f
    ) {
        val top = floatArrayOf(0f, ry, 0f)
        val bot = floatArrayOf(0f, -ry, 0f)
        val ring = arrayOf(
            floatArrayOf(rx, 0f, 0f),
            floatArrayOf(0f, 0f, rz),
            floatArrayOf(-rx, 0f, 0f),
            floatArrayOf(0f, 0f, -rz)
        )
        for (i in 0 until 4) {
            val aP = ring[i]
            val bP = ring[(i + 1) % 4]
            addTri(cx, cy, cz, top, aP, bP, r, g, b, a)
            addTri(cx, cy, cz, bot, bP, aP, r * 0.8f, g * 0.8f, b * 0.8f, a)
        }
    }

    fun addRing(
        cx: Float, cy: Float, cz: Float,
        innerR: Float, outerR: Float,
        segments: Int,
        r: Float, g: Float, b: Float, a: Float = 1f
    ) {
        for (i in 0 until segments) {
            val a0 = (i * 2f * PI.toFloat()) / segments
            val a1 = ((i + 1) * 2f * PI.toFloat()) / segments
            val c0 = cos(a0); val s0 = sin(a0)
            val c1 = cos(a1); val s1 = sin(a1)
            val base = (verts.size / 12).toShort()
            pushVertex(cx + c0 * innerR, cy, cz + s0 * innerR, 0f, 1f, 0f, r, g, b, a)
            pushVertex(cx + c0 * outerR, cy, cz + s0 * outerR, 0f, 1f, 0f, r, g, b, a)
            pushVertex(cx + c1 * outerR, cy, cz + s1 * outerR, 0f, 1f, 0f, r, g, b, a)
            pushVertex(cx + c1 * innerR, cy, cz + s1 * innerR, 0f, 1f, 0f, r, g, b, a)
            indices.add(base); indices.add((base + 1).toShort()); indices.add((base + 2).toShort())
            indices.add(base); indices.add((base + 2).toShort()); indices.add((base + 3).toShort())
        }
    }

    fun addDisc(
        cx: Float, cy: Float, cz: Float,
        radius: Float, segments: Int,
        r: Float, g: Float, b: Float, a: Float = 1f
    ) {
        val centerIdx = (verts.size / 12).toShort()
        pushVertex(cx, cy, cz, 0f, 1f, 0f, r, g, b, a)
        for (i in 0..segments) {
            val ang = (i * 2f * PI.toFloat()) / segments
            pushVertex(cx + cos(ang) * radius, cy, cz + sin(ang) * radius, 0f, 1f, 0f, r, g, b, a)
        }
        for (i in 1..segments) {
            indices.add(centerIdx)
            indices.add((centerIdx + i).toShort())
            indices.add((centerIdx + i + 1).toShort())
        }
    }

    private fun addTri(
        cx: Float, cy: Float, cz: Float,
        p0: FloatArray, p1: FloatArray, p2: FloatArray,
        r: Float, g: Float, b: Float, a: Float
    ) {
        val ux = p1[0] - p0[0]; val uy = p1[1] - p0[1]; val uz = p1[2] - p0[2]
        val vx = p2[0] - p0[0]; val vy = p2[1] - p0[1]; val vz = p2[2] - p0[2]
        val nx = uy * vz - uz * vy
        val ny = uz * vx - ux * vz
        val nz = ux * vy - uy * vx
        val len = hypot(hypot(nx, ny), nz).coerceAtLeast(0.001f)
        val base = (verts.size / 12).toShort()
        pushVertex(cx + p0[0], cy + p0[1], cz + p0[2], nx / len, ny / len, nz / len, r, g, b, a)
        pushVertex(cx + p1[0], cy + p1[1], cz + p1[2], nx / len, ny / len, nz / len, r, g, b, a)
        pushVertex(cx + p2[0], cy + p2[1], cz + p2[2], nx / len, ny / len, nz / len, r, g, b, a)
        indices.add(base); indices.add((base + 1).toShort()); indices.add((base + 2).toShort())
    }

    fun addTexturedTopQuad(
        cx: Float, y: Float, cz: Float,
        width: Float, depth: Float,
        uMax: Float = 1f, vMax: Float = 1f,
        r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f
    ) {
        val hx = width * 0.5f
        val hz = depth * 0.5f
        val base = (verts.size / 12).toShort()
        pushVertexUv(cx - hx, y, cz + hz, 0f, 1f, 0f, r, g, b, a, 0f, vMax)
        pushVertexUv(cx + hx, y, cz + hz, 0f, 1f, 0f, r, g, b, a, uMax, vMax)
        pushVertexUv(cx + hx, y, cz - hz, 0f, 1f, 0f, r, g, b, a, uMax, 0f)
        pushVertexUv(cx - hx, y, cz - hz, 0f, 1f, 0f, r, g, b, a, 0f, 0f)
        indices.add(base)
        indices.add((base + 1).toShort())
        indices.add((base + 2).toShort())
        indices.add(base)
        indices.add((base + 2).toShort())
        indices.add((base + 3).toShort())
    }

    private fun pushVertex(
        x: Float, y: Float, z: Float,
        nx: Float, ny: Float, nz: Float,
        r: Float, g: Float, b: Float, a: Float
    ) {
        pushVertexUv(x, y, z, nx, ny, nz, r, g, b, a, 0f, 0f)
    }

    private fun pushVertexUv(
        x: Float, y: Float, z: Float,
        nx: Float, ny: Float, nz: Float,
        r: Float, g: Float, b: Float, a: Float,
        u: Float, v: Float
    ) {
        verts.add(x); verts.add(y); verts.add(z)
        verts.add(nx); verts.add(ny); verts.add(nz)
        verts.add(r); verts.add(g); verts.add(b); verts.add(a)
        verts.add(u); verts.add(v)
    }

    fun buildWithTexture(textureId: Int): GpuMesh {
        val vArr = verts.toFloatArray()
        val iArr = indices.toShortArray()
        return uploadMesh(vArr, iArr, textureId)
    }

    private fun rotateVec(x: Float, y: Float, z: Float, rx: Float, ry: Float, rz: Float): FloatArray {
        var vx = x; var vy = y; var vz = z
        if (rx != 0f) {
            val c = cos(rx); val s = sin(rx)
            val ny = vy * c - vz * s
            val nz = vy * s + vz * c
            vy = ny; vz = nz
        }
        if (ry != 0f) {
            val c = cos(ry); val s = sin(ry)
            val nx = vx * c + vz * s
            val nz = -vx * s + vz * c
            vx = nx; vz = nz
        }
        if (rz != 0f) {
            val c = cos(rz); val s = sin(rz)
            val nx = vx * c - vy * s
            val ny = vx * s + vy * c
            vx = nx; vy = ny
        }
        return floatArrayOf(vx, vy, vz)
    }

    fun build(): GpuMesh {
        val vArr = verts.toFloatArray()
        val iArr = indices.toShortArray()
        return uploadMesh(vArr, iArr)
    }

    companion object {
        fun uploadGlbGeometry(geom: LoadedGlbGeometry): GpuMesh {
            var texId = 0
            val imgBytes = geom.baseColorImageBytes
            if (imgBytes != null && imgBytes.isNotEmpty()) {
                val bmp = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size)
                if (bmp != null) {
                    val texIds = IntArray(1)
                    GLES20.glGenTextures(1, texIds, 0)
                    texId = texIds[0]
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
                    bmp.recycle()
                }
            }
            var skinVbo = 0
            val sData = geom.skinData
            if (sData != null && sData.vertexJointsAndWeights.isNotEmpty()) {
                val sBufs = IntArray(1)
                GLES20.glGenBuffers(1, sBufs, 0)
                skinVbo = sBufs[0]
                val sArr = sData.vertexJointsAndWeights
                val sBuf = ByteBuffer.allocateDirect(sArr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
                sBuf.put(sArr).position(0)
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, skinVbo)
                GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, sArr.size * 4, sBuf, GLES20.GL_STATIC_DRAW)
                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
            }
            return uploadMesh(geom.vertices, geom.indices, texId, skinVbo, sData)
        }

        fun uploadMesh(
            vArr: FloatArray,
            iArr: ShortArray,
            textureId: Int = 0,
            skinVbo: Int = 0,
            skinData: GlbSkinData? = null
        ): GpuMesh {
            val bufs = IntArray(2)
            GLES20.glGenBuffers(2, bufs, 0)

            val vBuf = ByteBuffer.allocateDirect(vArr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            vBuf.put(vArr).position(0)

            val iBuf = ByteBuffer.allocateDirect(iArr.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
            iBuf.put(iArr).position(0)

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bufs[0])
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vArr.size * 4, vBuf, GLES20.GL_STATIC_DRAW)

            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, bufs[1])
            GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, iArr.size * 2, iBuf, GLES20.GL_STATIC_DRAW)

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
            return GpuMesh(bufs[0], bufs[1], iArr.size, textureId, skinVbo, skinData)
        }
    }
}

data class ArticulatedRig(
    val torso: GpuMesh,
    val head: GpuMesh,
    val armRight: GpuMesh,
    val armLeft: GpuMesh,
    val legRight: GpuMesh,
    val legLeft: GpuMesh
) {
    fun release() {
        torso.release()
        head.release()
        armRight.release()
        armLeft.release()
        legRight.release()
        legLeft.release()
    }
}

object MeshBuilder {

    fun buildGroundTile(): GpuMesh {
        val texSize = 512
        val pixels = buildArenaGroundTexturePixels(texSize)
        val bmp = Bitmap.createBitmap(pixels, texSize, texSize, Bitmap.Config.ARGB_8888)
        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        val texId = texIds[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        bmp.recycle()

        val acc = MeshAccumulator()
        // Rich cracked wasteland ground tile (seamless 2x2 repeat per 24m sector)
        acc.addTexturedTopQuad(0f, 0f, 0f, 24f, 24f, uMax = 2f, vMax = 2f, r = 1f, g = 1f, b = 1f, a = 1f)
        return acc.buildWithTexture(texId)
    }

    /**
     * Generates a seamless 512x512 wasteland battle-arena texture inspired by the attached
     * Alien Battle Arena artwork: warm cracked dusty earth & basalt plates, glowing amber-orange
     * magma fissures along fractured seams, reflective slate-blue water puddles, pebbles,
     * and subtle crimson flora/mineral flecks.
     */
    fun buildArenaGroundTexturePixels(size: Int = 512): IntArray {
        val pixels = IntArray(size * size)
        // Deterministic periodic Voronoi sites in normalized [0..1) space
        val grid = 6
        val siteX = FloatArray(grid * grid)
        val siteY = FloatArray(grid * grid)
        for (gy in 0 until grid) {
            for (gx in 0 until grid) {
                val idx = gy * grid + gx
                val h1 = hash2d(gx, gy, 17)
                val h2 = hash2d(gx, gy, 53)
                siteX[idx] = (gx + 0.16f + h1 * 0.68f) / grid.toFloat()
                siteY[idx] = (gy + 0.16f + h2 * 0.68f) / grid.toFloat()
            }
        }

        val puddleCenters = arrayOf(
            floatArrayOf(0.24f, 0.71f, 0.085f),
            floatArrayOf(0.76f, 0.28f, 0.070f)
        )

        val invSize = 1f / size.toFloat()
        for (py in 0 until size) {
            val v = py * invSize
            val cellY = floor(v * grid).toInt()
            for (px in 0 until size) {
                val u = px * invSize
                val cellX = floor(u * grid).toInt()

                // Organic multi-octave periodic value noise for dusty earth grain
                val nCoarse = periodicNoise(u * 8f, v * 8f, 8)
                val nMed = periodicNoise(u * 24f, v * 24f, 24)
                val nFine = periodicNoise(u * 64f, v * 64f, 64)
                val grit = hash2d(px, py, 91)

                // Domain-warped coordinates so Voronoi cracks look jagged & natural
                val warpU = u + (nMed - 0.5f) * 0.022f + (nFine - 0.5f) * 0.007f
                val warpV = v + (periodicNoise(u * 24f + 3.7f, v * 24f + 1.9f, 24) - 0.5f) * 0.022f

                var d1 = 99f
                var d2 = 99f
                var id1 = 0
                var id2 = 0
                for (oy in -1..1) {
                    val ny = (cellY + oy + grid) % grid
                    for (ox in -1..1) {
                        val nx = (cellX + ox + grid) % grid
                        val idx = ny * grid + nx
                        var dx = abs(warpU - siteX[idx])
                        var dy = abs(warpV - siteY[idx])
                        if (dx > 0.5f) dx = 1f - dx
                        if (dy > 0.5f) dy = 1f - dy
                        val dist = sqrt(dx * dx + dy * dy)
                        if (dist < d1) {
                            d2 = d1; id2 = id1
                            d1 = dist; id1 = idx
                        } else if (dist < d2) {
                            d2 = dist; id2 = idx
                        }
                    }
                }

                // Slab tint variation per stone plate (warm dusty earth-brown & dark basalt)
                val slabTone = hash2d(id1, id1 * 3, 7)
                var r = 0.25f + slabTone * 0.11f + (nCoarse - 0.5f) * 0.10f + (nMed - 0.5f) * 0.06f + (nFine - 0.5f) * 0.04f
                var g = 0.19f + slabTone * 0.08f + (nCoarse - 0.5f) * 0.07f + (nMed - 0.5f) * 0.05f + (nFine - 0.5f) * 0.03f
                var b = 0.16f + slabTone * 0.06f + (nCoarse - 0.5f) * 0.05f + (nMed - 0.5f) * 0.04f + (nFine - 0.5f) * 0.03f

                // Scattered tiny pebble dark/light speckles
                if (grit > 0.965f) {
                    r *= 0.68f; g *= 0.68f; b *= 0.72f
                } else if (grit < 0.014f) {
                    r += 0.09f; g += 0.07f; b += 0.06f
                }

                // Shallow reflective blue-slate puddles (matching arena image puddles)
                for (pud in puddleCenters) {
                    var pdx = abs(u - pud[0])
                    var pdy = abs(v - pud[1])
                    if (pdx > 0.5f) pdx = 1f - pdx
                    if (pdy > 0.5f) pdy = 1f - pdy
                    val pDist = sqrt(pdx * pdx + pdy * pdy) + (nMed - 0.5f) * 0.036f + (nFine - 0.5f) * 0.012f
                    if (pDist < pud[2]) {
                        val depth = ((pud[2] - pDist) / pud[2]).coerceIn(0f, 1f)
                        val skyRefl = (nCoarse * 0.45f + nMed * 0.35f + 0.20f)
                        val pr = 0.15f + skyRefl * 0.16f
                        val pg = 0.21f + skyRefl * 0.20f
                        val pb = 0.29f + skyRefl * 0.27f
                        val blend = (depth * 2.4f).coerceIn(0f, 0.92f)
                        r = r * (1f - blend) + pr * blend
                        g = g * (1f - blend) + pg * blend
                        b = b * (1f - blend) + pb * blend
                    }
                }

                // Cracked ground seams + glowing orange-amber magma fissures along selected edges
                val edgeDist = d2 - d1
                val pairHash = hash2d(minOf(id1, id2), maxOf(id1, id2), 31)
                val hasLavaVein = pairHash > 0.48f

                if (edgeDist < 0.028f) {
                    val seam = (1f - edgeDist / 0.028f).coerceIn(0f, 1f)
                    // Darken crack borders
                    val darken = 1f - seam * 0.72f
                    r *= darken; g *= darken; b *= darken

                    if (hasLavaVein && nMed > 0.24f) {
                        val pulse = ((nMed - 0.24f) / 0.76f).coerceIn(0f, 1f)
                        val core = (1f - edgeDist / 0.010f).coerceIn(0f, 1f)
                        val halo = (1f - edgeDist / 0.026f).coerceIn(0f, 1f) * 0.45f * pulse
                        // Emissive orange-amber glow bleeding onto crack walls, white-gold hot center
                        r = (r + halo * 0.95f + core * 1.0f * pulse).coerceAtMost(1f)
                        g = (g + halo * 0.38f + core * 0.62f * pulse).coerceAtMost(1f)
                        b = (b + halo * 0.05f + core * 0.14f * pulse).coerceAtMost(1f)
                    }
                }

                // Subtle crimson alien moss flecks along dark soil pockets
                if (nCoarse > 0.76f && nMed > 0.68f && edgeDist > 0.02f && grit > 0.70f) {
                    r = (r * 0.45f + 0.52f).coerceAtMost(1f)
                    g = (g * 0.45f + 0.06f).coerceAtMost(1f)
                    b = (b * 0.45f + 0.10f).coerceAtMost(1f)
                }

                val ri = (r.coerceIn(0f, 1f) * 255f).toInt()
                val gi = (g.coerceIn(0f, 1f) * 255f).toInt()
                val bi = (b.coerceIn(0f, 1f) * 255f).toInt()
                pixels[py * size + px] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
            }
        }
        return pixels
    }

    private fun hash2d(x: Int, y: Int, seed: Int): Float {
        var h = x * 374761393 + y * 668265263 + seed * 1442695041
        h = (h xor (h shr 13)) * 1274126177
        h = h xor (h shr 16)
        return ((h and 0x7FFFFFFF).toFloat() / 0x7FFFFFFF.toFloat())
    }

    private fun periodicNoise(x: Float, y: Float, period: Int): Float {
        val ix0 = floor(x).toInt()
        val iy0 = floor(y).toInt()
        val fx = x - ix0
        val fy = y - iy0
        val sx = fx * fx * (3f - 2f * fx)
        val sy = fy * fy * (3f - 2f * fy)

        val x0 = ((ix0 % period) + period) % period
        val x1 = (x0 + 1) % period
        val y0 = ((iy0 % period) + period) % period
        val y1 = (y0 + 1) % period

        val n00 = hash2d(x0, y0, period)
        val n10 = hash2d(x1, y0, period)
        val n01 = hash2d(x0, y1, period)
        val n11 = hash2d(x1, y1, period)

        val nx0 = n00 + (n10 - n00) * sx
        val nx1 = n01 + (n11 - n01) * sx
        return nx0 + (nx1 - nx0) * sy
    }

    fun buildShadowDisc(): GpuMesh = MeshAccumulator().apply {
        addDisc(0f, 0.02f, 0f, 0.72f, 16, 0.02f, 0.02f, 0.04f, 0.55f)
    }.build()

    fun buildRingMesh(): GpuMesh = MeshAccumulator().apply {
        addRing(0f, 0.05f, 0f, 0.85f, 1.0f, 28, 1f, 1f, 1f, 0.85f)
    }.build()

    fun buildDiscMesh(): GpuMesh = MeshAccumulator().apply {
        addDisc(0f, 0.05f, 0f, 1.0f, 24, 1f, 1f, 1f, 0.38f)
    }.build()

    fun buildSlashArcMesh(): GpuMesh = MeshAccumulator().apply {
        // Curved forward energy crescent blade
        for (i in -4..4) {
            val ang = i * 0.22f
            val x = sin(ang) * 1.0f
            val z = cos(ang) * 1.0f
            accBoxArc(this, x, 0f, z, ang)
        }
    }.build()

    private fun accBoxArc(acc: MeshAccumulator, x: Float, y: Float, z: Float, rotY: Float) {
        acc.addBox(x, y, z, 0.34f, 0.08f, 0.22f, 1f, 0.95f, 0.8f, 0.9f, rotY = rotY)
    }

    fun buildProjectileMesh(): GpuMesh = MeshAccumulator().apply {
        addOctahedron(0f, 0f, 0f, 0.2f, 0.2f, 0.45f, 1f, 1f, 1f, 1f)
    }.build()

    fun buildCrystalMesh(): GpuMesh = MeshAccumulator().apply {
        addOctahedron(0f, 0.35f, 0f, 0.22f, 0.38f, 0.22f, 1f, 1f, 1f, 1f)
    }.build()

    fun buildUnitBox(): GpuMesh = MeshAccumulator().apply {
        addBox(0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f, 1f)
    }.build()

    fun buildDroneMesh(): GpuMesh = MeshAccumulator().apply {
        addOctahedron(0f, 0f, 0f, 0.35f, 0.18f, 0.35f, 0.2f, 0.28f, 0.38f)
        addBox(0f, 0.08f, 0.2f, 0.18f, 0.12f, 0.24f, 0f, 0.9f, 1f)
        addBox(-0.34f, 0f, 0f, 0.24f, 0.06f, 0.32f, 0.4f, 0.48f, 0.6f)
        addBox(0.34f, 0f, 0f, 0.24f, 0.06f, 0.32f, 0.4f, 0.48f, 0.6f)
    }.build()

    fun buildTurretMesh(): GpuMesh = MeshAccumulator().apply {
        addTaperedPrism(0f, 0.3f, 0f, 0.45f, 0.28f, 0.6f, 6, 0.55f, 0.42f, 0.22f)
        addBox(0f, 0.68f, 0.15f, 0.36f, 0.28f, 0.55f, 0.3f, 0.35f, 0.38f)
        addTaperedPrism(0f, 0.7f, 0.55f, 0.1f, 0.08f, 0.45f, 6, 0.22f, 1f, 0.15f, rotX = (PI / 2).toFloat())
    }.build()

    fun buildOrbitalBladeMesh(): GpuMesh = MeshAccumulator().apply {
        addBox(0f, 0f, 0f, 0.16f, 0.06f, 0.85f, 1f, 0.45f, 0.1f)
        addOctahedron(0f, 0f, 0.45f, 0.12f, 0.04f, 0.25f, 1f, 0.85f, 0.3f)
    }.build()

    fun buildHeroRig(heroClass: HeroClass): ArticulatedRig {
        return when (heroClass) {
            HeroClass.BERSERKER -> buildBerserkerRig()
            HeroClass.MAGO -> buildElderMageRig()
            HeroClass.GLADIADOR -> buildRomanGladiatorRig()
            HeroClass.INVENTOR -> buildSteampunkInventorRig()
            HeroClass.SAMURAI_NEON -> buildNeonSamuraiRig()
            HeroClass.TECNOLOGICO -> buildTechnoRig()
            HeroClass.ATIRADOR -> buildSniperRig()
            HeroClass.BRUXO -> buildWarlockRig()
            HeroClass.GUERREIRO -> buildWarriorRig()
            HeroClass.ARQUEIRO -> buildArcherRig()
        }
    }

    private fun buildBerserkerRig(): ArticulatedRig {
        // Matches Infernal Demonic Berserker Concept Sheet:
        // Glowing fiery eyes/scars, twin curved fiery horns, mohawk, bare muscular torso with magma cracks,
        // crossed leather harness with glowing rune diamond, crimson neck scarf & fur collar, tattered crimson back cape
        // with fiery sigil, demon-skull right shoulder pauldron, skull belt buckle, magma-cracked greaves,
        // and massive Infernal Double-Edged Crescent Flaming Battle Axe.
        val torso = MeshAccumulator().apply {
            // Bare muscular upper chest & sculpted abs
            addBox(0f, 1.13f, 0f, 0.70f, 0.42f, 0.42f, 0.76f, 0.50f, 0.40f)
            addBox(0f, 0.84f, 0f, 0.54f, 0.32f, 0.36f, 0.72f, 0.47f, 0.37f)
            // Glowing magma lava fissures on bare chest & ribs
            addBox(-0.18f, 1.14f, 0.215f, 0.16f, 0.03f, 0.02f, 1.0f, 0.30f, 0.04f, rotZ = 0.35f)
            addBox(0.18f, 1.04f, 0.215f, 0.14f, 0.03f, 0.02f, 1.0f, 0.30f, 0.04f, rotZ = -0.35f)
            addBox(-0.10f, 0.88f, 0.185f, 0.12f, 0.025f, 0.02f, 1.0f, 0.26f, 0.04f, rotZ = -0.45f)

            // Crossed dark leather harness straps + glowing fiery rune diamond
            addBox(0f, 1.06f, 0.22f, 0.66f, 0.08f, 0.04f, 0.16f, 0.11f, 0.10f, rotZ = 0.58f)
            addBox(0f, 1.06f, 0.22f, 0.66f, 0.08f, 0.04f, 0.16f, 0.11f, 0.10f, rotZ = -0.58f)
            addBox(0f, 1.06f, -0.21f, 0.64f, 0.08f, 0.04f, 0.16f, 0.11f, 0.10f, rotZ = 0.58f)
            addBox(0f, 1.06f, -0.21f, 0.64f, 0.08f, 0.04f, 0.16f, 0.11f, 0.10f, rotZ = -0.58f)
            addOctahedron(0f, 1.08f, 0.24f, 0.11f, 0.11f, 0.05f, 0.18f, 0.15f, 0.16f) // iron frame
            addOctahedron(0f, 1.08f, 0.26f, 0.07f, 0.07f, 0.04f, 1.0f, 0.24f, 0.04f) // glowing chest rune

            // Crimson neck scarf & dark wolf fur shoulder collar
            addTaperedPrism(0f, 1.36f, 0.03f, 0.34f, 0.26f, 0.16f, 8, 0.62f, 0.07f, 0.10f)
            addBox(0f, 1.24f, 0.22f, 0.36f, 0.16f, 0.08f, 0.58f, 0.06f, 0.09f)
            addTaperedPrism(0f, 1.44f, -0.04f, 0.46f, 0.36f, 0.20f, 8, 0.42f, 0.35f, 0.30f)

            // Tattered crimson back cape with glowing fiery rune sigil
            addBox(0f, 0.96f, -0.28f, 0.64f, 0.88f, 0.05f, 0.58f, 0.07f, 0.10f, rotX = 0.16f)
            addBox(-0.20f, 0.46f, -0.36f, 0.18f, 0.42f, 0.04f, 0.52f, 0.06f, 0.09f, rotX = 0.18f, rotZ = 0.08f)
            addBox(0f, 0.42f, -0.37f, 0.20f, 0.48f, 0.04f, 0.58f, 0.07f, 0.10f, rotX = 0.18f)
            addBox(0.20f, 0.46f, -0.36f, 0.18f, 0.42f, 0.04f, 0.52f, 0.06f, 0.09f, rotX = 0.18f, rotZ = -0.08f)
            // Glowing sigil down back of cape
            addBox(0f, 0.94f, -0.31f, 0.04f, 0.56f, 0.02f, 1.0f, 0.28f, 0.04f, rotX = 0.16f)
            addBox(0f, 1.06f, -0.29f, 0.22f, 0.04f, 0.02f, 1.0f, 0.28f, 0.04f, rotX = 0.16f)

            // Heavy belt, demon skull buckle, crimson tattered loincloth & dark-iron tassets
            addBox(0f, 0.68f, 0f, 0.62f, 0.15f, 0.44f, 0.18f, 0.14f, 0.14f)
            addBox(0f, 0.68f, 0.24f, 0.18f, 0.18f, 0.10f, 0.84f, 0.78f, 0.68f) // ivory skull buckle
            addBox(-0.045f, 0.70f, 0.295f, 0.04f, 0.04f, 0.02f, 1.0f, 0.14f, 0.04f) // skull eye R
            addBox(0.045f, 0.70f, 0.295f, 0.04f, 0.04f, 0.02f, 1.0f, 0.14f, 0.04f) // skull eye L
            addBox(0f, 0.44f, 0.22f, 0.24f, 0.44f, 0.05f, 0.62f, 0.07f, 0.10f) // crimson loincloth
            addBox(-0.33f, 0.54f, 0f, 0.18f, 0.34f, 0.38f, 0.18f, 0.16f, 0.19f, rotZ = 0.14f) // right tasset
            addBox(0.33f, 0.54f, 0f, 0.18f, 0.34f, 0.38f, 0.18f, 0.16f, 0.19f, rotZ = -0.14f) // left tasset
            addBox(-0.36f, 0.54f, 0.18f, 0.10f, 0.03f, 0.04f, 1.0f, 0.30f, 0.04f)
            addBox(0.36f, 0.54f, 0.18f, 0.10f, 0.03f, 0.04f, 1.0f, 0.30f, 0.04f)

            // Giant Horned Demon-Skull Right Shoulder Pauldron (character's right = -X)
            addBox(-0.50f, 1.36f, 0.02f, 0.34f, 0.28f, 0.36f, 0.20f, 0.17f, 0.19f, rotZ = 0.18f)
            addBox(-0.56f, 1.34f, 0.18f, 0.24f, 0.18f, 0.12f, 0.28f, 0.22f, 0.22f, rotZ = 0.18f) // skull brow/snout
            addBox(-0.58f, 1.38f, 0.24f, 0.07f, 0.05f, 0.04f, 1.0f, 0.16f, 0.04f) // glowing skull eye
            addBox(-0.46f, 1.37f, 0.24f, 0.06f, 0.05f, 0.04f, 1.0f, 0.16f, 0.04f)
            addTaperedPrism(-0.58f, 1.22f, 0.20f, 0.04f, 0.01f, 0.14f, 4, 0.84f, 0.78f, 0.68f, rotX = 3.14f) // fang
            addTaperedPrism(-0.48f, 1.22f, 0.20f, 0.04f, 0.01f, 0.14f, 4, 0.84f, 0.78f, 0.68f, rotX = 3.14f) // fang
            addTaperedPrism(-0.54f, 1.56f, 0.04f, 0.06f, 0.01f, 0.22f, 5, 0.22f, 0.18f, 0.20f, rotZ = 0.25f) // pauldron horn
            addBox(-0.52f, 1.44f, 0.12f, 0.18f, 0.03f, 0.18f, 1.0f, 0.28f, 0.04f) // magma crack

            // Spiked Dark-Iron Left Shoulder Pauldron (+X)
            addBox(0.46f, 1.34f, 0f, 0.26f, 0.22f, 0.30f, 0.18f, 0.16f, 0.19f, rotZ = -0.22f)
            addTaperedPrism(0.50f, 1.50f, 0f, 0.05f, 0.01f, 0.18f, 4, 0.22f, 0.18f, 0.20f, rotZ = -0.35f)
            addBox(0.48f, 1.36f, 0.14f, 0.14f, 0.03f, 0.04f, 1.0f, 0.28f, 0.04f)
        }.build()

        val head = MeshAccumulator().apply {
            // Rugged warrior face & shaved sides
            addBox(0f, 0.21f, 0f, 0.34f, 0.38f, 0.34f, 0.78f, 0.52f, 0.42f)
            // Thick dark beard & jawline
            addBox(0f, 0.08f, 0.12f, 0.33f, 0.18f, 0.18f, 0.10f, 0.08f, 0.09f)
            // Swept-back dark mohawk crest
            addBox(0f, 0.42f, -0.02f, 0.14f, 0.16f, 0.40f, 0.10f, 0.08f, 0.09f, rotX = -0.18f)
            addBox(0f, 0.48f, -0.08f, 0.10f, 0.12f, 0.28f, 0.22f, 0.08f, 0.08f, rotX = -0.22f)
            // Glowing fiery red eyes & forehead battle scars
            addBox(-0.08f, 0.24f, 0.175f, 0.07f, 0.035f, 0.02f, 1.0f, 0.14f, 0.04f)
            addBox(0.08f, 0.24f, 0.175f, 0.07f, 0.035f, 0.02f, 1.0f, 0.14f, 0.04f)
            addBox(-0.07f, 0.31f, 0.175f, 0.025f, 0.10f, 0.02f, 1.0f, 0.24f, 0.04f, rotZ = 0.2f)
            addBox(0.08f, 0.30f, 0.175f, 0.025f, 0.09f, 0.02f, 1.0f, 0.24f, 0.04f, rotZ = -0.15f)

            // Twin Curved Glowing Molten Demon Horns (crescent arc sweeping out & up)
            // Right horn (-X)
            addTaperedPrism(-0.20f, 0.34f, -0.02f, 0.06f, 0.05f, 0.16f, 6, 0.22f, 0.08f, 0.08f, rotZ = 0.95f)
            addTaperedPrism(-0.31f, 0.45f, -0.03f, 0.055f, 0.045f, 0.18f, 6, 1.0f, 0.24f, 0.04f, rotZ = 0.42f)
            addTaperedPrism(-0.33f, 0.60f, -0.02f, 0.045f, 0.03f, 0.18f, 6, 1.0f, 0.36f, 0.05f, rotZ = -0.25f)
            addTaperedPrism(-0.26f, 0.72f, 0f, 0.03f, 0.01f, 0.14f, 5, 1.0f, 0.55f, 0.10f, rotZ = -0.75f)
            // Left horn (+X)
            addTaperedPrism(0.20f, 0.34f, -0.02f, 0.06f, 0.05f, 0.16f, 6, 0.22f, 0.08f, 0.08f, rotZ = -0.95f)
            addTaperedPrism(0.31f, 0.45f, -0.03f, 0.055f, 0.045f, 0.18f, 6, 1.0f, 0.24f, 0.04f, rotZ = -0.42f)
            addTaperedPrism(0.33f, 0.60f, -0.02f, 0.045f, 0.03f, 0.18f, 6, 1.0f, 0.36f, 0.05f, rotZ = 0.25f)
            addTaperedPrism(0.26f, 0.72f, 0f, 0.03f, 0.01f, 0.14f, 5, 1.0f, 0.55f, 0.10f, rotZ = 0.75f)
        }.build()

        val armR = MeshAccumulator().apply {
            // Bare muscular upper arm with glowing magma cracks
            addBox(0f, -0.22f, 0f, 0.23f, 0.44f, 0.23f, 0.76f, 0.50f, 0.40f)
            addBox(-0.12f, -0.20f, 0.02f, 0.02f, 0.22f, 0.04f, 1.0f, 0.28f, 0.04f, rotX = 0.4f)
            // Spiked dark-iron gauntlet with molten veins
            addBox(0f, -0.46f, 0.02f, 0.26f, 0.28f, 0.26f, 0.18f, 0.16f, 0.19f)
            addBox(-0.135f, -0.46f, 0.04f, 0.02f, 0.18f, 0.10f, 1.0f, 0.30f, 0.04f)
            addTaperedPrism(-0.14f, -0.34f, -0.08f, 0.04f, 0.01f, 0.14f, 4, 0.24f, 0.20f, 0.22f, rotX = -0.6f)

            // Massive Infernal Double-Edged Crescent Flaming Battle Axe
            // Long spiked dark-iron haft with glowing crimson rune studs
            addTaperedPrism(0f, -0.56f, 0.40f, 0.055f, 0.06f, 1.56f, 6, 0.18f, 0.15f, 0.17f, rotX = (PI / 2).toFloat())
            addBox(0f, -0.56f, -0.38f, 0.12f, 0.12f, 0.10f, 0.26f, 0.22f, 0.24f) // pommel
            addOctahedron(0f, -0.56f, 0.62f, 0.06f, 0.06f, 0.06f, 1.0f, 0.22f, 0.04f) // rune diamond
            addOctahedron(0f, -0.56f, 0.88f, 0.065f, 0.065f, 0.065f, 1.0f, 0.22f, 0.04f)

            // Axe head dark volcanic iron socket
            addBox(0f, -0.56f, 1.04f, 0.12f, 0.44f, 0.34f, 0.18f, 0.14f, 0.16f)
            addBox(0f, -0.56f, 1.04f, 0.13f, 0.18f, 0.18f, 1.0f, 0.26f, 0.04f) // molten core cracks

            // Giant Sweeping Crescent Molten Axe Blade (upper & lower horned wings)
            // Center crescent plate
            addBox(0f, -0.56f, 1.24f, 0.08f, 0.56f, 0.28f, 0.24f, 0.12f, 0.10f)
            addBox(0f, -0.56f, 1.38f, 0.045f, 0.50f, 0.12f, 1.0f, 0.42f, 0.04f) // glowing cutting edge
            // Upper crescent wing
            addBox(0f, -0.24f, 1.18f, 0.07f, 0.36f, 0.28f, 0.22f, 0.12f, 0.10f, rotX = -0.55f)
            addBox(0f, -0.18f, 1.32f, 0.04f, 0.34f, 0.14f, 1.0f, 0.56f, 0.08f, rotX = -0.65f)
            addTaperedPrism(0f, -0.02f, 1.22f, 0.07f, 0.01f, 0.24f, 4, 1.0f, 0.72f, 0.14f, rotX = -0.85f)
            // Lower crescent wing
            addBox(0f, -0.88f, 1.18f, 0.07f, 0.36f, 0.28f, 0.22f, 0.12f, 0.10f, rotX = 0.55f)
            addBox(0f, -0.94f, 1.32f, 0.04f, 0.34f, 0.14f, 1.0f, 0.56f, 0.08f, rotX = 0.65f)
            addTaperedPrism(0f, -1.10f, 1.22f, 0.07f, 0.01f, 0.24f, 4, 1.0f, 0.72f, 0.14f, rotX = 3.99f)
        }.build()

        val armL = MeshAccumulator().apply {
            // Bare muscular upper arm with magma veins + crimson wrap + spiked iron gauntlet
            addBox(0f, -0.22f, 0f, 0.23f, 0.44f, 0.23f, 0.76f, 0.50f, 0.40f)
            addBox(0.12f, -0.20f, 0.02f, 0.02f, 0.20f, 0.04f, 1.0f, 0.28f, 0.04f, rotX = -0.3f)
            addBox(0f, -0.34f, 0f, 0.24f, 0.10f, 0.24f, 0.60f, 0.07f, 0.10f) // crimson cuff
            addBox(0f, -0.47f, 0.02f, 0.26f, 0.28f, 0.26f, 0.18f, 0.16f, 0.19f) // gauntlet
            addBox(0.135f, -0.47f, 0.04f, 0.02f, 0.16f, 0.08f, 1.0f, 0.26f, 0.04f) // rune glow
            addBox(0f, -0.60f, 0.04f, 0.20f, 0.14f, 0.20f, 0.20f, 0.16f, 0.18f) // clenched armored fist
        }.build()

        val legR = MeshAccumulator().apply {
            addBox(0f, -0.22f, 0f, 0.25f, 0.36f, 0.27f, 0.15f, 0.13f, 0.14f) // dark thigh
            addBox(0f, -0.34f, 0.14f, 0.22f, 0.16f, 0.08f, 0.20f, 0.18f, 0.21f) // knee plate
            addBox(0f, -0.34f, 0.185f, 0.10f, 0.04f, 0.02f, 1.0f, 0.28f, 0.04f) // knee lava glow
            addBox(0f, -0.52f, 0.04f, 0.27f, 0.30f, 0.35f, 0.17f, 0.15f, 0.18f) // armored greave & boot
            addBox(0f, -0.50f, 0.22f, 0.04f, 0.18f, 0.02f, 1.0f, 0.26f, 0.04f) // boot magma fissure
        }.build()

        val legL = MeshAccumulator().apply {
            addBox(0f, -0.22f, 0f, 0.25f, 0.36f, 0.27f, 0.15f, 0.13f, 0.14f)
            addBox(0f, -0.34f, 0.14f, 0.22f, 0.16f, 0.08f, 0.20f, 0.18f, 0.21f)
            addBox(0f, -0.34f, 0.185f, 0.10f, 0.04f, 0.02f, 1.0f, 0.28f, 0.04f)
            addBox(0f, -0.52f, 0.04f, 0.27f, 0.30f, 0.35f, 0.17f, 0.15f, 0.18f)
            addBox(0f, -0.50f, 0.22f, 0.04f, 0.18f, 0.02f, 1.0f, 0.26f, 0.04f)
        }.build()

        return ArticulatedRig(torso, head, armR, armL, legR, legL)
    }

    private fun buildElderMageRig(): ArticulatedRig {
        // Matches Elder Mage with Arcane Staff: Purple/navy gold-trimmed robes, hooded sage, glowing purple crystal staff
        val torso = MeshAccumulator().apply {
            addTaperedPrism(0f, 0.75f, 0f, 0.56f, 0.36f, 1.15f, 8, 0.18f, 0.13f, 0.42f) // flowing indigo/purple robe
            addBox(0f, 1.15f, 0.22f, 0.26f, 0.45f, 0.06f, 0.88f, 0.70f, 0.22f) // gold front trim
            addOctahedron(0f, 1.22f, 0.26f, 0.09f, 0.13f, 0.06f, 0.75f, 0.20f, 1.0f) // purple chest gem
            addBox(-0.42f, 1.28f, 0f, 0.30f, 0.18f, 0.36f, 0.32f, 0.14f, 0.52f, rotZ = 0.2f) // left mantle
            addBox(0.42f, 1.28f, 0f, 0.30f, 0.18f, 0.36f, 0.32f, 0.14f, 0.52f, rotZ = -0.2f) // right mantle
        }.build()

        val head = MeshAccumulator().apply {
            addBox(0f, 0.22f, 0f, 0.34f, 0.36f, 0.34f, 0.78f, 0.62f, 0.55f)
            addBox(0f, 0.08f, 0.14f, 0.30f, 0.24f, 0.16f, 0.85f, 0.85f, 0.88f) // white beard
            // Peaked purple & gold wizard hood
            addTaperedPrism(0f, 0.36f, -0.04f, 0.36f, 0.08f, 0.42f, 6, 0.36f, 0.14f, 0.58f)
        }.build()

        val armR = MeshAccumulator().apply {
            addBox(0f, -0.24f, 0f, 0.22f, 0.48f, 0.24f, 0.28f, 0.13f, 0.48f)
            // Ornate arcane crystal staff
            addTaperedPrism(0f, -0.15f, 0.22f, 0.05f, 0.05f, 1.65f, 6, 0.55f, 0.40f, 0.20f)
            addOctahedron(0f, 0.78f, 0.22f, 0.18f, 0.28f, 0.18f, 0.82f, 0.25f, 1.0f) // glowing violet crystal
            addRing(0f, 0.78f, 0.22f, 0.22f, 0.26f, 12, 0.92f, 0.75f, 0.25f)
        }.build()

        val armL = MeshAccumulator().apply {
            addBox(0f, -0.24f, 0f, 0.22f, 0.48f, 0.24f, 0.28f, 0.13f, 0.48f)
            addOctahedron(0f, -0.48f, 0.22f, 0.14f, 0.14f, 0.14f, 0.78f, 0.30f, 1.0f) // arcane orb in hand
        }.build()

        val legR = MeshAccumulator().apply {
            addBox(0f, -0.35f, 0f, 0.22f, 0.46f, 0.26f, 0.24f, 0.18f, 0.14f)
        }.build()
        val legL = MeshAccumulator().apply {
            addBox(0f, -0.35f, 0f, 0.22f, 0.46f, 0.26f, 0.24f, 0.18f, 0.14f)
        }.build()
        return ArticulatedRig(torso, head, armR, armL, legR, legL)
    }

    private fun buildRomanGladiatorRig(): ArticulatedRig {
        // Matches Armored Roman Gladiator with Lion Shield: Bronze & red armor, red cape, gladius, round Lion Shield
        val torso = MeshAccumulator().apply {
            addBox(0f, 1.05f, 0f, 0.68f, 0.76f, 0.44f, 0.62f, 0.44f, 0.22f) // bronze lorica chest
            addOctahedron(0f, 1.12f, 0.24f, 0.14f, 0.14f, 0.06f, 0.85f, 0.68f, 0.24f) // lion chest crest
            addBox(0f, 0.65f, 0f, 0.66f, 0.28f, 0.46f, 0.55f, 0.14f, 0.14f) // crimson pteruges skirt
            addBox(0f, 0.92f, -0.28f, 0.64f, 1.10f, 0.07f, 0.72f, 0.10f, 0.12f, rotX = 0.12f) // flowing crimson cape
        }.build()

        val head = MeshAccumulator().apply {
            addBox(0f, 0.22f, 0f, 0.34f, 0.38f, 0.34f, 0.78f, 0.58f, 0.48f)
            addBox(0f, 0.40f, 0f, 0.36f, 0.14f, 0.36f, 0.16f, 0.12f, 0.10f) // tousled dark hair
        }.build()

        val armR = MeshAccumulator().apply {
            addBox(0f, -0.24f, 0f, 0.20f, 0.48f, 0.20f, 0.78f, 0.56f, 0.46f)
            addBox(0f, -0.42f, 0f, 0.23f, 0.20f, 0.23f, 0.60f, 0.42f, 0.20f) // bronze vambrace
            // Gladius short sword
            addBox(0f, -0.52f, 0.44f, 0.07f, 0.12f, 0.85f, 0.78f, 0.80f, 0.85f)
        }.build()

        val armL = MeshAccumulator().apply {
            addBox(0f, -0.24f, 0f, 0.20f, 0.48f, 0.20f, 0.78f, 0.56f, 0.46f)
            // Circular Bronze & Crimson Lion Shield
            addTaperedPrism(0.14f, -0.34f, 0.18f, 0.52f, 0.52f, 0.10f, 12, 0.62f, 0.15f, 0.15f, rotZ = (PI / 2).toFloat())
            addOctahedron(0.20f, -0.34f, 0.18f, 0.08f, 0.22f, 0.22f, 0.88f, 0.70f, 0.24f) // golden lion emblem
        }.build()

        val legR = MeshAccumulator().apply {
            addBox(0f, -0.22f, 0f, 0.22f, 0.34f, 0.22f, 0.78f, 0.56f, 0.46f)
            addBox(0f, -0.48f, 0.03f, 0.24f, 0.34f, 0.26f, 0.62f, 0.44f, 0.22f) // bronze greave
        }.build()
        val legL = MeshAccumulator().apply {
            addBox(0f, -0.22f, 0f, 0.22f, 0.34f, 0.22f, 0.78f, 0.56f, 0.46f)
            addBox(0f, -0.48f, 0.03f, 0.24f, 0.34f, 0.26f, 0.62f, 0.44f, 0.22f)
        }.build()
        return ArticulatedRig(torso, head, armR, armL, legR, legL)
    }

    private fun buildSteampunkInventorRig(): ArticulatedRig {
        // Matches Steampunk Gnome Inventor with Glowing Gearpack: compact stocky build, green coat, brass armor, glowing green tank pack, wrench
        val torso = MeshAccumulator().apply {
            addBox(0f, 0.90f, 0f, 0.68f, 0.66f, 0.48f, 0.20f, 0.34f, 0.22f) // green steampunk coat
            addBox(-0.42f, 1.15f, 0f, 0.26f, 0.24f, 0.34f, 0.76f, 0.52f, 0.22f) // brass shoulder L
            addBox(0.42f, 1.15f, 0f, 0.26f, 0.24f, 0.34f, 0.76f, 0.52f, 0.22f) // brass shoulder R
            // Glowing green alchemy gearpack
            addBox(0f, 1.02f, -0.34f, 0.52f, 0.56f, 0.28f, 0.45f, 0.28f, 0.16f)
            addTaperedPrism(-0.14f, 1.24f, -0.36f, 0.12f, 0.12f, 0.36f, 8, 0.22f, 1.0f, 0.20f) // glowing green flask
            addRing(0.18f, 1.02f, -0.49f, 0.08f, 0.18f, 8, 0.88f, 0.68f, 0.24f) // gear wheel
        }.build()

        val head = MeshAccumulator().apply {
            addBox(0f, 0.14f, 0f, 0.36f, 0.34f, 0.34f, 0.84f, 0.64f, 0.54f)
            addBox(0f, 0.02f, 0.16f, 0.38f, 0.24f, 0.18f, 0.90f, 0.88f, 0.84f) // bushy mustache/beard
            // Brass goggles on forehead with green lenses
            addBox(-0.10f, 0.28f, 0.18f, 0.14f, 0.12f, 0.08f, 0.25f, 1.0f, 0.45f)
            addBox(0.10f, 0.28f, 0.18f, 0.14f, 0.12f, 0.08f, 0.25f, 1.0f, 0.45f)
        }.build()

        val armR = MeshAccumulator().apply {
            addBox(0f, -0.20f, 0f, 0.22f, 0.40f, 0.22f, 0.76f, 0.52f, 0.22f)
            // Heavy Brass Wrench-Cannon
            addBox(0f, -0.34f, 0.35f, 0.12f, 0.14f, 0.68f, 0.78f, 0.56f, 0.24f)
            addBox(0f, -0.34f, 0.70f, 0.26f, 0.16f, 0.18f, 0.24f, 1.0f, 0.20f)
        }.build()

        val armL = MeshAccumulator().apply {
            addBox(0f, -0.20f, 0f, 0.22f, 0.40f, 0.22f, 0.76f, 0.52f, 0.22f)
        }.build()

        val legR = MeshAccumulator().apply {
            addBox(0f, -0.22f, 0f, 0.24f, 0.42f, 0.26f, 0.52f, 0.34f, 0.18f)
        }.build()
        val legL = MeshAccumulator().apply {
            addBox(0f, -0.22f, 0f, 0.24f, 0.42f, 0.26f, 0.52f, 0.34f, 0.18f)
        }.build()
        return ArticulatedRig(torso, head, armR, armL, legR, legL)
    }

    private fun buildNeonSamuraiRig(): ArticulatedRig {
        // Matches Neon Cyberpunk Warrior with Katana: Sleek dark armor, glowing cyan & crimson neon visor/trim, glowing katana
        val torso = MeshAccumulator().apply {
            addBox(0f, 1.05f, 0f, 0.60f, 0.76f, 0.38f, 0.10f, 0.12f, 0.18f)
            addBox(0f, 1.15f, 0.20f, 0.46f, 0.10f, 0.04f, 0.0f, 0.92f, 1.0f) // cyan neon chest stripe
            addBox(0f, 0.90f, -0.22f, 0.42f, 0.68f, 0.06f, 1.0f, 0.16f, 0.35f, rotX = 0.18f) // crimson neon scarf
            addBox(-0.38f, 1.30f, 0f, 0.22f, 0.20f, 0.28f, 0.16f, 0.20f, 0.28f, rotZ = 0.2f)
            addBox(0.38f, 1.30f, 0f, 0.22f, 0.20f, 0.28f, 0.16f, 0.20f, 0.28f, rotZ = -0.2f)
        }.build()

        val head = MeshAccumulator().apply {
            addBox(0f, 0.22f, 0f, 0.34f, 0.36f, 0.36f, 0.12f, 0.14f, 0.20f)
            addBox(0f, 0.24f, 0.18f, 0.32f, 0.10f, 0.05f, 0.0f, 0.95f, 1.0f) // neon visor
        }.build()

        val armR = MeshAccumulator().apply {
            addBox(0f, -0.24f, 0f, 0.18f, 0.46f, 0.18f, 0.14f, 0.18f, 0.26f)
            // Long glowing Neon Katana
            addBox(0f, -0.46f, 0.56f, 0.04f, 0.09f, 1.15f, 0.0f, 0.95f, 1.0f)
        }.build()

        val armL = MeshAccumulator().apply {
            addBox(0f, -0.24f, 0f, 0.18f, 0.46f, 0.18f, 0.14f, 0.18f, 0.26f)
        }.build()

        val legR = MeshAccumulator().apply {
            addBox(0f, -0.30f, 0f, 0.20f, 0.56f, 0.22f, 0.12f, 0.14f, 0.20f)
        }.build()
        val legL = MeshAccumulator().apply {
            addBox(0f, -0.30f, 0f, 0.20f, 0.56f, 0.22f, 0.12f, 0.14f, 0.20f)
        }.build()
        return ArticulatedRig(torso, head, armR, armL, legR, legL)
    }

    private fun buildTechnoRig(): ArticulatedRig = buildStandardClassRig(
        armorR = 0.18f, armorG = 0.24f, armorB = 0.34f,
        trimR = 0.0f, trimG = 0.90f, trimB = 1.0f,
        weaponType = 1
    )

    private fun buildSniperRig(): ArticulatedRig = buildStandardClassRig(
        armorR = 0.22f, armorG = 0.24f, armorB = 0.28f,
        trimR = 1.0f, trimG = 0.80f, trimB = 0.25f,
        weaponType = 2
    )

    private fun buildWarlockRig(): ArticulatedRig = buildStandardClassRig(
        armorR = 0.18f, armorG = 0.12f, armorB = 0.24f,
        trimR = 0.35f, trimG = 1.0f, trimB = 0.15f,
        weaponType = 3
    )

    private fun buildWarriorRig(): ArticulatedRig = buildStandardClassRig(
        armorR = 0.20f, armorG = 0.30f, armorB = 0.48f,
        trimR = 0.25f, trimG = 0.78f, trimB = 1.0f,
        weaponType = 4
    )

    private fun buildArcherRig(): ArticulatedRig = buildStandardClassRig(
        armorR = 0.14f, armorG = 0.28f, armorB = 0.28f,
        trimR = 0.0f, trimG = 0.95f, trimB = 0.85f,
        weaponType = 5
    )

    private fun buildStandardClassRig(
        armorR: Float, armorG: Float, armorB: Float,
        trimR: Float, trimG: Float, trimB: Float,
        weaponType: Int
    ): ArticulatedRig {
        val torso = MeshAccumulator().apply {
            addBox(0f, 1.05f, 0f, 0.64f, 0.76f, 0.40f, armorR, armorG, armorB)
            addBox(0f, 1.15f, 0.21f, 0.36f, 0.18f, 0.05f, trimR, trimG, trimB)
            addBox(-0.40f, 1.30f, 0f, 0.24f, 0.22f, 0.28f, armorR * 1.2f, armorG * 1.2f, armorB * 1.2f)
            addBox(0.40f, 1.30f, 0f, 0.24f, 0.22f, 0.28f, armorR * 1.2f, armorG * 1.2f, armorB * 1.2f)
            addBox(0f, 1.05f, -0.26f, 0.42f, 0.52f, 0.18f, armorR * 0.8f, armorG * 0.8f, armorB * 0.8f) // tech pack
        }.build()

        val head = MeshAccumulator().apply {
            addBox(0f, 0.22f, 0f, 0.34f, 0.36f, 0.34f, armorR * 1.1f, armorG * 1.1f, armorB * 1.1f)
            addBox(0f, 0.24f, 0.18f, 0.28f, 0.10f, 0.04f, trimR, trimG, trimB)
        }.build()

        val armR = MeshAccumulator().apply {
            addBox(0f, -0.24f, 0f, 0.19f, 0.46f, 0.19f, armorR, armorG, armorB)
            when (weaponType) {
                1, 2 -> {
                    // Plasma / Rail rifle
                    addBox(0f, -0.38f, 0.46f, 0.10f, 0.16f, 0.88f, 0.22f, 0.24f, 0.28f)
                    addBox(0f, -0.36f, 0.76f, 0.06f, 0.08f, 0.40f, trimR, trimG, trimB)
                }
                3 -> {
                    // Corruption scythe/staff
                    addTaperedPrism(0f, -0.20f, 0.24f, 0.04f, 0.04f, 1.5f, 6, 0.28f, 0.20f, 0.32f)
                    addOctahedron(0f, 0.62f, 0.24f, 0.18f, 0.18f, 0.18f, trimR, trimG, trimB)
                }
                4 -> {
                    // Energy broadsword
                    addBox(0f, -0.48f, 0.56f, 0.06f, 0.12f, 0.98f, trimR, trimG, trimB)
                }
                else -> {
                    // Starlight bow
                    addBox(0f, -0.36f, 0.35f, 0.06f, 0.90f, 0.12f, trimR, trimG, trimB)
                }
            }
        }.build()

        val armL = MeshAccumulator().apply {
            addBox(0f, -0.24f, 0f, 0.19f, 0.46f, 0.19f, armorR, armorG, armorB)
            if (weaponType == 4) {
                // Holo shield
                addBox(0.14f, -0.32f, 0.18f, 0.08f, 0.72f, 0.52f, trimR, trimG, trimB, 0.85f)
            }
        }.build()

        val legR = MeshAccumulator().apply {
            addBox(0f, -0.30f, 0f, 0.21f, 0.54f, 0.23f, armorR * 0.9f, armorG * 0.9f, armorB * 0.9f)
        }.build()
        val legL = MeshAccumulator().apply {
            addBox(0f, -0.30f, 0f, 0.21f, 0.54f, 0.23f, armorR * 0.9f, armorG * 0.9f, armorB * 0.9f)
        }.build()
        return ArticulatedRig(torso, head, armR, armL, legR, legL)
    }

    fun buildAlienRig(type: AlienType): ArticulatedRig {
        return when (type) {
            AlienType.RASTEJADOR -> {
                // Fast crimson quadruped with dorsal spikes & glowing red eyes
                val torso = MeshAccumulator().apply {
                    addBox(0f, 0.56f, 0f, 0.54f, 0.38f, 0.96f, 0.28f, 0.10f, 0.18f)
                    addOctahedron(0f, 0.84f, 0.1f, 0.08f, 0.22f, 0.12f, 0.85f, 0.12f, 0.20f)
                    addOctahedron(0f, 0.82f, -0.22f, 0.07f, 0.20f, 0.12f, 0.85f, 0.12f, 0.20f)
                    addBox(0f, 0.50f, -0.72f, 0.14f, 0.14f, 0.62f, 0.22f, 0.08f, 0.14f, rotX = -0.3f)
                }.build()
                val head = MeshAccumulator().apply {
                    addBox(0f, 0.05f, 0.18f, 0.38f, 0.30f, 0.44f, 0.34f, 0.11f, 0.20f)
                    addBox(-0.12f, 0.10f, 0.41f, 0.08f, 0.06f, 0.04f, 1.0f, 0.12f, 0.15f)
                    addBox(0.12f, 0.10f, 0.41f, 0.08f, 0.06f, 0.04f, 1.0f, 0.12f, 0.15f)
                }.build()
                val limb = MeshAccumulator().apply {
                    addBox(0f, -0.22f, 0f, 0.14f, 0.44f, 0.16f, 0.24f, 0.09f, 0.16f)
                }.build()
                val limb2 = MeshAccumulator().apply {
                    addBox(0f, -0.22f, 0f, 0.14f, 0.44f, 0.16f, 0.24f, 0.09f, 0.16f)
                }.build()
                val limb3 = MeshAccumulator().apply {
                    addBox(0f, -0.22f, 0f, 0.14f, 0.44f, 0.16f, 0.24f, 0.09f, 0.16f)
                }.build()
                val limb4 = MeshAccumulator().apply {
                    addBox(0f, -0.22f, 0f, 0.14f, 0.44f, 0.16f, 0.24f, 0.09f, 0.16f)
                }.build()
                ArticulatedRig(torso, head, limb, limb2, limb3, limb4)
            }
            AlienType.BRUTAMONTES -> {
                // Massive rocky-armored brute with molten orange cracks & tusks
                val torso = MeshAccumulator().apply {
                    addBox(0f, 1.05f, 0f, 1.05f, 0.92f, 0.72f, 0.35f, 0.30f, 0.26f) // rock armor
                    addBox(0f, 1.08f, 0.37f, 0.58f, 0.14f, 0.05f, 1.0f, 0.45f, 0.05f) // molten magma crack
                    addBox(-0.62f, 1.32f, 0f, 0.44f, 0.40f, 0.48f, 0.42f, 0.36f, 0.30f)
                    addBox(0.62f, 1.32f, 0f, 0.44f, 0.40f, 0.48f, 0.42f, 0.36f, 0.30f)
                }.build()
                val head = MeshAccumulator().apply {
                    addBox(0f, 0.16f, 0.08f, 0.46f, 0.40f, 0.42f, 0.32f, 0.27f, 0.24f)
                    addOctahedron(-0.16f, 0.08f, 0.32f, 0.06f, 0.16f, 0.06f, 0.92f, 0.84f, 0.65f) // tusk L
                    addOctahedron(0.16f, 0.08f, 0.32f, 0.06f, 0.16f, 0.06f, 0.92f, 0.84f, 0.65f) // tusk R
                    addBox(0f, 0.22f, 0.30f, 0.26f, 0.07f, 0.04f, 1.0f, 0.45f, 0.05f)
                }.build()
                val armR = MeshAccumulator().apply {
                    addBox(0f, -0.35f, 0f, 0.34f, 0.70f, 0.34f, 0.38f, 0.32f, 0.28f)
                    addBox(0f, -0.66f, 0.05f, 0.40f, 0.28f, 0.40f, 0.95f, 0.42f, 0.08f)
                }.build()
                val armL = MeshAccumulator().apply {
                    addBox(0f, -0.35f, 0f, 0.34f, 0.70f, 0.34f, 0.38f, 0.32f, 0.28f)
                    addBox(0f, -0.66f, 0.05f, 0.40f, 0.28f, 0.40f, 0.95f, 0.42f, 0.08f)
                }.build()
                val legR = MeshAccumulator().apply {
                    addBox(0f, -0.30f, 0f, 0.34f, 0.56f, 0.36f, 0.32f, 0.28f, 0.25f)
                }.build()
                val legL = MeshAccumulator().apply {
                    addBox(0f, -0.30f, 0f, 0.34f, 0.56f, 0.36f, 0.32f, 0.28f, 0.25f)
                }.build()
                ArticulatedRig(torso, head, armR, armL, legR, legL)
            }
            AlienType.SALTADOR -> {
                // Purple agile reptilian leaper with curved crescent head crest
                val torso = MeshAccumulator().apply {
                    addBox(0f, 0.84f, 0f, 0.44f, 0.56f, 0.62f, 0.32f, 0.16f, 0.42f, rotX = 0.3f)
                    addBox(0f, 0.62f, -0.58f, 0.16f, 0.16f, 0.76f, 0.26f, 0.12f, 0.34f, rotX = -0.2f)
                }.build()
                val head = MeshAccumulator().apply {
                    addBox(0f, 0.12f, 0.16f, 0.28f, 0.26f, 0.42f, 0.40f, 0.18f, 0.50f)
                    addBox(0f, 0.26f, -0.18f, 0.10f, 0.16f, 0.54f, 0.85f, 0.25f, 0.95f, rotX = 0.25f) // swept crest
                }.build()
                val armR = MeshAccumulator().apply { addBox(0f, -0.22f, 0.1f, 0.14f, 0.44f, 0.14f, 0.36f, 0.18f, 0.46f) }.build()
                val armL = MeshAccumulator().apply { addBox(0f, -0.22f, 0.1f, 0.14f, 0.44f, 0.14f, 0.36f, 0.18f, 0.46f) }.build()
                val legR = MeshAccumulator().apply { addBox(0f, -0.28f, 0f, 0.18f, 0.56f, 0.20f, 0.28f, 0.14f, 0.38f) }.build()
                val legL = MeshAccumulator().apply { addBox(0f, -0.28f, 0f, 0.18f, 0.56f, 0.20f, 0.28f, 0.14f, 0.38f) }.build()
                ArticulatedRig(torso, head, armR, armL, legR, legL)
            }
            AlienType.CUSPIDOR -> {
                // Hunchback spitter with glowing neon green acid sacs on back
                val torso = MeshAccumulator().apply {
                    addBox(0f, 0.86f, 0f, 0.58f, 0.62f, 0.52f, 0.24f, 0.28f, 0.22f)
                    addOctahedron(-0.18f, 1.24f, -0.16f, 0.18f, 0.20f, 0.18f, 0.48f, 1.0f, 0.05f)
                    addOctahedron(0.18f, 1.28f, -0.10f, 0.20f, 0.22f, 0.20f, 0.48f, 1.0f, 0.05f)
                    addOctahedron(0f, 1.10f, -0.32f, 0.16f, 0.18f, 0.16f, 0.40f, 0.95f, 0.05f)
                }.build()
                val head = MeshAccumulator().apply {
                    addBox(0f, 0.12f, 0.15f, 0.34f, 0.30f, 0.38f, 0.26f, 0.32f, 0.24f)
                    addBox(0f, 0.02f, 0.34f, 0.22f, 0.10f, 0.08f, 0.50f, 1.0f, 0.05f) // toxic jaw
                }.build()
                val armR = MeshAccumulator().apply { addBox(0f, -0.22f, 0f, 0.16f, 0.44f, 0.16f, 0.24f, 0.28f, 0.22f) }.build()
                val armL = MeshAccumulator().apply { addBox(0f, -0.22f, 0f, 0.16f, 0.44f, 0.16f, 0.24f, 0.28f, 0.22f) }.build()
                val legR = MeshAccumulator().apply { addBox(0f, -0.24f, 0f, 0.20f, 0.46f, 0.20f, 0.24f, 0.28f, 0.22f) }.build()
                val legL = MeshAccumulator().apply { addBox(0f, -0.24f, 0f, 0.20f, 0.46f, 0.20f, 0.24f, 0.28f, 0.22f) }.build()
                ArticulatedRig(torso, head, armR, armL, legR, legL)
            }
            AlienType.PARASITA -> {
                // Crimson multi-legged arachnid swarmer
                val torso = MeshAccumulator().apply {
                    addOctahedron(0f, 0.42f, 0f, 0.44f, 0.24f, 0.52f, 0.58f, 0.12f, 0.14f)
                    addOctahedron(0f, 0.46f, -0.34f, 0.26f, 0.20f, 0.26f, 0.45f, 0.10f, 0.12f)
                }.build()
                val head = MeshAccumulator().apply {
                    addBox(0f, 0.04f, 0.12f, 0.26f, 0.18f, 0.22f, 0.65f, 0.14f, 0.16f)
                    addBox(0f, 0.06f, 0.24f, 0.20f, 0.06f, 0.04f, 1.0f, 0.20f, 0.15f)
                }.build()
                val leg1 = MeshAccumulator().apply { addBox(0.16f, -0.16f, 0f, 0.34f, 0.09f, 0.10f, 0.46f, 0.10f, 0.12f, rotZ = -0.5f) }.build()
                val leg2 = MeshAccumulator().apply { addBox(-0.16f, -0.16f, 0f, 0.34f, 0.09f, 0.10f, 0.46f, 0.10f, 0.12f, rotZ = 0.5f) }.build()
                val leg3 = MeshAccumulator().apply { addBox(0.16f, -0.16f, -0.18f, 0.34f, 0.09f, 0.10f, 0.46f, 0.10f, 0.12f, rotZ = -0.5f) }.build()
                val leg4 = MeshAccumulator().apply { addBox(-0.16f, -0.16f, -0.18f, 0.34f, 0.09f, 0.10f, 0.46f, 0.10f, 0.12f, rotZ = 0.5f) }.build()
                ArticulatedRig(torso, head, leg1, leg2, leg3, leg4)
            }
            AlienType.SENTINELA -> {
                // Bronze/gunmetal cyber-alien mech-warrior with cyan core, energy blade & holographic blue shield
                val torso = MeshAccumulator().apply {
                    addBox(0f, 1.05f, 0f, 0.68f, 0.78f, 0.44f, 0.38f, 0.34f, 0.30f)
                    addOctahedron(0f, 1.15f, 0.24f, 0.12f, 0.16f, 0.06f, 0.0f, 0.90f, 1.0f) // glowing cyan core
                    addBox(-0.44f, 1.32f, 0f, 0.28f, 0.26f, 0.36f, 0.45f, 0.40f, 0.34f)
                    addBox(0.44f, 1.32f, 0f, 0.28f, 0.26f, 0.36f, 0.45f, 0.40f, 0.34f)
                }.build()
                val head = MeshAccumulator().apply {
                    addOctahedron(0f, 0.20f, 0f, 0.22f, 0.28f, 0.26f, 0.38f, 0.34f, 0.30f)
                    addBox(0f, 0.20f, 0.20f, 0.14f, 0.10f, 0.05f, 0.0f, 0.90f, 1.0f)
                }.build()
                val armR = MeshAccumulator().apply {
                    addBox(0f, -0.24f, 0f, 0.20f, 0.48f, 0.20f, 0.38f, 0.34f, 0.30f)
                    addOctahedron(0f, -0.46f, 0.46f, 0.06f, 0.10f, 0.50f, 1.0f, 0.60f, 0.15f) // plasma arm blade
                }.build()
                val armL = MeshAccumulator().apply {
                    addBox(0f, -0.24f, 0f, 0.20f, 0.48f, 0.20f, 0.38f, 0.34f, 0.30f)
                    // Translucent glowing blue energy shield
                    addBox(0.18f, -0.24f, 0.24f, 0.08f, 0.92f, 0.68f, 0.15f, 0.72f, 1.0f, 0.75f)
                }.build()
                val legR = MeshAccumulator().apply { addBox(0f, -0.30f, 0f, 0.22f, 0.56f, 0.24f, 0.35f, 0.30f, 0.28f) }.build()
                val legL = MeshAccumulator().apply { addBox(0f, -0.30f, 0f, 0.22f, 0.56f, 0.24f, 0.35f, 0.30f, 0.28f) }.build()
                ArticulatedRig(torso, head, armR, armL, legR, legL)
            }
            AlienType.ESCAVADOR, AlienType.CACADOR_ALFA -> {
                val isAlpha = type == AlienType.CACADOR_ALFA
                val cr = if (isAlpha) 0.16f else 0.34f
                val cg = if (isAlpha) 0.12f else 0.26f
                val cb = if (isAlpha) 0.22f else 0.20f
                val ar = 1.0f
                val ag = if (isAlpha) 0.80f else 0.60f
                val ab = 0.10f
                val torso = MeshAccumulator().apply {
                    addBox(0f, 0.82f, 0f, 0.62f, 0.62f, 0.52f, cr, cg, cb)
                    addOctahedron(0f, 1.22f, -0.10f, 0.10f, 0.26f, 0.26f, ar, ag, ab)
                }.build()
                val head = MeshAccumulator().apply {
                    addOctahedron(0f, 0.14f, 0.14f, 0.24f, 0.22f, 0.34f, cr * 1.2f, cg * 1.2f, cb * 1.2f)
                    addBox(0f, 0.18f, 0.34f, 0.20f, 0.07f, 0.04f, ar, ag, ab)
                }.build()
                val armR = MeshAccumulator().apply { addBox(0f, -0.24f, 0f, 0.20f, 0.48f, 0.20f, cr, cg, cb) }.build()
                val armL = MeshAccumulator().apply { addBox(0f, -0.24f, 0f, 0.20f, 0.48f, 0.20f, cr, cg, cb) }.build()
                val legR = MeshAccumulator().apply { addBox(0f, -0.28f, 0f, 0.22f, 0.48f, 0.22f, cr, cg, cb) }.build()
                val legL = MeshAccumulator().apply { addBox(0f, -0.28f, 0f, 0.22f, 0.48f, 0.22f, cr, cg, cb) }.build()
                ArticulatedRig(torso, head, armR, armL, legR, legL)
            }
        }
    }

    fun buildBossRig(type: BossType): ArticulatedRig {
        val cols = when (type) {
            BossType.PREDADOR_ALFA -> floatArrayOf(0.32f, 0.08f, 0.14f, 1.0f, 0.18f, 0.30f)
            BossType.COLOSSO_BLINDADO -> floatArrayOf(0.34f, 0.30f, 0.26f, 1.0f, 0.42f, 0.10f)
            BossType.RAINHA_PARASITA -> floatArrayOf(0.28f, 0.07f, 0.24f, 0.45f, 1.0f, 0.05f)
            BossType.DEVORADOR_COSMICO -> floatArrayOf(0.10f, 0.06f, 0.20f, 0.0f, 0.90f, 1.0f)
        }
        val r = cols[0]; val g = cols[1]; val b = cols[2]
        val ar = cols[3]; val ag = cols[4]; val ab = cols[5]
        val torso = MeshAccumulator().apply {
            addBox(0f, 1.12f, 0f, 1.05f, 1.05f, 0.72f, r, g, b)
            addOctahedron(0f, 1.15f, 0.38f, 0.22f, 0.28f, 0.10f, ar, ag, ab)
            addOctahedron(-0.55f, 1.65f, -0.1f, 0.18f, 0.42f, 0.24f, ar, ag, ab)
            addOctahedron(0.55f, 1.65f, -0.1f, 0.18f, 0.42f, 0.24f, ar, ag, ab)
            if (type == BossType.DEVORADOR_COSMICO || type == BossType.RAINHA_PARASITA) {
                addBox(-0.85f, 1.35f, -0.3f, 0.65f, 0.85f, 0.08f, ar * 0.7f, ag * 0.7f, ab * 0.7f, 0.85f, rotY = -0.35f)
                addBox(0.85f, 1.35f, -0.3f, 0.65f, 0.85f, 0.08f, ar * 0.7f, ag * 0.7f, ab * 0.7f, 0.85f, rotY = 0.35f)
            }
        }.build()
        val head = MeshAccumulator().apply {
            addBox(0f, 0.24f, 0.10f, 0.50f, 0.44f, 0.48f, r * 1.15f, g * 1.15f, b * 1.15f)
            addBox(0f, 0.28f, 0.35f, 0.34f, 0.10f, 0.05f, ar, ag, ab)
            addOctahedron(-0.24f, 0.50f, 0f, 0.08f, 0.26f, 0.08f, ar, ag, ab)
            addOctahedron(0.24f, 0.50f, 0f, 0.08f, 0.26f, 0.08f, ar, ag, ab)
        }.build()
        val armR = MeshAccumulator().apply {
            addBox(0f, -0.34f, 0f, 0.32f, 0.72f, 0.32f, r, g, b)
            addOctahedron(0f, -0.68f, 0.18f, 0.18f, 0.20f, 0.35f, ar, ag, ab)
        }.build()
        val armL = MeshAccumulator().apply {
            addBox(0f, -0.34f, 0f, 0.32f, 0.72f, 0.32f, r, g, b)
            addOctahedron(0f, -0.68f, 0.18f, 0.18f, 0.20f, 0.35f, ar, ag, ab)
        }.build()
        val legR = MeshAccumulator().apply { addBox(0f, -0.32f, 0f, 0.34f, 0.60f, 0.34f, r, g, b) }.build()
        val legL = MeshAccumulator().apply { addBox(0f, -0.32f, 0f, 0.34f, 0.60f, 0.34f, r, g, b) }.build()
        return ArticulatedRig(torso, head, armR, armL, legR, legL)
    }

    fun buildPropMesh(kind: PropKind): GpuMesh {
        val acc = MeshAccumulator()
        when (kind) {
            PropKind.DESTROYED_BUILDING -> {
                acc.addBox(0f, 1.9f, 0f, 3.2f, 3.8f, 3.0f, 0.22f, 0.24f, 0.29f)
                acc.addBox(-0.6f, 4.1f, 0.4f, 1.5f, 1.4f, 1.6f, 0.19f, 0.21f, 0.25f)
                acc.addBox(0.8f, 1.8f, 1.52f, 0.6f, 0.35f, 0.06f, 0.95f, 0.45f, 0.15f) // broken lit window
            }
            PropKind.ABANDONED_HOUSE -> {
                acc.addBox(0f, 1.1f, 0f, 2.8f, 2.2f, 2.5f, 0.30f, 0.27f, 0.25f)
                acc.addBox(0f, 2.4f, 0f, 3.1f, 0.4f, 2.8f, 0.42f, 0.20f, 0.16f, rotZ = 0.14f)
            }
            PropKind.CRASHED_SPACESHIP -> {
                acc.addBox(0f, 0.95f, 0f, 2.2f, 1.3f, 4.6f, 0.32f, 0.36f, 0.42f, rotX = 0.24f, rotZ = 0.28f)
                acc.addBox(-1.6f, 0.8f, -0.4f, 1.8f, 0.25f, 1.6f, 0.26f, 0.28f, 0.34f, rotZ = 0.35f)
                acc.addBox(1.5f, 1.1f, -0.2f, 1.5f, 0.25f, 1.4f, 0.26f, 0.28f, 0.34f, rotZ = -0.2f)
                acc.addOctahedron(0f, 0.6f, -2.2f, 0.45f, 0.45f, 0.55f, 1.0f, 0.42f, 0.08f) // glowing engine core
            }
            PropKind.DAMAGED_VEHICLE -> {
                acc.addBox(0f, 0.6f, 0f, 1.6f, 0.85f, 2.8f, 0.28f, 0.32f, 0.36f, rotZ = 0.15f)
                acc.addBox(0f, 1.15f, -0.2f, 1.2f, 0.5f, 1.4f, 0.20f, 0.24f, 0.28f, rotZ = 0.15f)
            }
            PropKind.MILITARY_BUNKER -> {
                acc.addTaperedPrism(0f, 1.0f, 0f, 2.4f, 1.8f, 2.0f, 6, 0.26f, 0.29f, 0.26f)
                acc.addBox(0f, 1.4f, 1.75f, 1.5f, 0.28f, 0.25f, 0.10f, 0.12f, 0.12f)
            }
            PropKind.DESTROYED_LAB -> {
                acc.addBox(0f, 1.2f, 0f, 3.0f, 2.4f, 2.8f, 0.24f, 0.28f, 0.34f)
                acc.addTaperedPrism(0f, 2.5f, 0f, 1.2f, 0.6f, 0.8f, 8, 0.15f, 0.82f, 0.90f, 0.7f)
            }
            PropKind.SHIPPING_CONTAINER -> {
                // Dark armored cyber-crate with glowing cyan energy strip lights (matches arena artwork)
                acc.addBox(0f, 0.9f, 0f, 1.7f, 1.8f, 3.2f, 0.20f, 0.22f, 0.27f, rotZ = 0.04f)
                acc.addBox(0.87f, 0.9f, 0f, 0.06f, 1.15f, 0.14f, 0.0f, 0.92f, 1.0f, rotZ = 0.04f)
                acc.addBox(-0.87f, 0.9f, 0f, 0.06f, 1.15f, 0.14f, 0.0f, 0.92f, 1.0f, rotZ = 0.04f)
            }
            PropKind.BROKEN_COMM_TOWER -> {
                acc.addTaperedPrism(0f, 2.2f, 0f, 0.7f, 0.22f, 4.4f, 4, 0.34f, 0.32f, 0.34f, rotZ = 0.18f)
                acc.addOctahedron(0.35f, 4.2f, 0f, 0.25f, 0.25f, 0.25f, 1.0f, 0.35f, 0.15f)
            }
            PropKind.SATELLITE_DEBRIS -> {
                acc.addBox(0f, 0.65f, 0f, 1.4f, 0.9f, 1.4f, 0.42f, 0.38f, 0.32f, rotX = 0.4f)
                acc.addBox(1.2f, 0.5f, 0f, 1.4f, 0.10f, 0.9f, 0.14f, 0.24f, 0.52f, rotZ = -0.35f) // solar wing
            }
            PropKind.ALIEN_ROCK -> {
                // Rugged weathered basalt boulders matching the wasteland arena
                acc.addOctahedron(0f, 0.65f, 0f, 1.18f, 1.05f, 1.08f, 0.26f, 0.23f, 0.22f)
                acc.addOctahedron(0.75f, 0.42f, 0.45f, 0.68f, 0.65f, 0.62f, 0.22f, 0.19f, 0.18f)
            }
            PropKind.GLOWING_CRYSTAL -> {
                acc.addOctahedron(0f, 0.9f, 0f, 0.42f, 1.25f, 0.42f, 0.15f, 0.92f, 1.0f)
                acc.addOctahedron(0.45f, 0.55f, 0.25f, 0.28f, 0.75f, 0.28f, 0.72f, 0.25f, 1.0f)
            }
            PropKind.CONTAMINATED_FLORA -> {
                // Vibrant crimson spiky alien grass/flora matching the battle arena image
                acc.addTaperedPrism(0f, 0.42f, 0f, 0.16f, 0.04f, 0.90f, 5, 0.78f, 0.10f, 0.18f, rotX = 0.28f, rotZ = 0.18f)
                acc.addTaperedPrism(0.18f, 0.38f, 0.12f, 0.14f, 0.03f, 0.80f, 5, 0.92f, 0.14f, 0.24f, rotX = -0.32f, rotZ = -0.25f)
                acc.addTaperedPrism(-0.18f, 0.40f, -0.10f, 0.15f, 0.03f, 0.85f, 5, 0.85f, 0.12f, 0.20f, rotX = 0.22f, rotZ = -0.30f)
            }
            PropKind.CRATER -> {
                // Scorched rock crater with glowing orange magma fissure center
                acc.addRing(0f, 0.06f, 0f, 1.45f, 2.35f, 12, 0.24f, 0.19f, 0.17f)
                acc.addDisc(0f, 0.03f, 0f, 1.48f, 12, 0.14f, 0.09f, 0.08f)
                acc.addDisc(0f, 0.04f, 0f, 0.72f, 10, 1.0f, 0.42f, 0.08f, 0.85f)
            }
        }
        return acc.build()
    }
}
