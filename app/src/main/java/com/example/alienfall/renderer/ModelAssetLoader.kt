package com.example.alienfall.renderer

import android.content.Context
import com.example.alienfall.model.AlienType
import com.example.alienfall.model.HeroClass
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class GlbSkinData(
    val jointNames: List<String>,
    val jointParentIndices: IntArray,
    val inverseBindMatrices: FloatArray, // 16 floats per joint (column-major)
    val vertexJointsAndWeights: FloatArray, // 8 floats per vertex: j0..j3, w0..w3
    val normScale: Float,
    val centerX: Float,
    val minY: Float,
    val centerZ: Float
)

data class LoadedGlbGeometry(
    val vertices: FloatArray, // interleaved (12 floats): x, y, z, nx, ny, nz, r, g, b, a, u, v
    val indices: ShortArray,
    val baseColorImageBytes: ByteArray? = null,
    val skinData: GlbSkinData? = null
) {
    val hasSkeleton: Boolean get() = skinData != null && skinData.jointNames.isNotEmpty()
    val hasSkinning: Boolean get() = skinData != null && skinData.vertexJointsAndWeights.isNotEmpty()
    val boneCount: Int get() = skinData?.jointNames?.size ?: 0
}

/**
 * Loads binary glTF (.glb) 3D models from assets/models/heroes/ and assets/models/aliens/.
 * Supports embedded baseColorTexture, TEXCOORD_0, normals, glTF skin/joints/weights, and
 * auto-normalization to game scale.
 */
class ModelAssetLoader(private val context: Context) {

    private val cachedModels = HashMap<String, LoadedGlbGeometry?>()
    val missingOptionalGlbPaths = mutableListOf<String>()

    fun preloadCatalogCheck() {
        missingOptionalGlbPaths.clear()
        HeroClass.entries.forEach { hero ->
            if (!assetExists(hero.glbModelPath)) {
                missingOptionalGlbPaths.add(hero.glbModelPath)
            }
        }
        AlienType.entries.forEach { alien ->
            if (!assetExists(alien.glbModelPath)) {
                missingOptionalGlbPaths.add(alien.glbModelPath)
            }
        }
    }

    fun tryLoadGlb(assetPath: String): LoadedGlbGeometry? {
        if (cachedModels.containsKey(assetPath)) return cachedModels[assetPath]
        val loaded = runCatching {
            context.assets.open(assetPath).use { stream ->
                parseGlb(stream.readBytes())
            }
        }.getOrNull()
        cachedModels[assetPath] = loaded
        return loaded
    }

    private fun assetExists(path: String): Boolean {
        return runCatching {
            context.assets.open(path).close()
            true
        }.getOrDefault(false)
    }

    private fun parseGlb(bytes: ByteArray): LoadedGlbGeometry? {
        if (bytes.size < 20) return null
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = bb.int
        if (magic != 0x46546C67) return null // "glTF"
        bb.int // version
        bb.int // totalLength

        var jsonText: String? = null
        var binChunk: ByteArray? = null

        while (bb.remaining() >= 8) {
            val chunkLen = bb.int
            val chunkType = bb.int
            if (chunkLen < 0 || chunkLen > bb.remaining()) break
            val chunkBytes = ByteArray(chunkLen)
            bb.get(chunkBytes)
            when (chunkType) {
                0x4E4F534A -> jsonText = String(chunkBytes, Charsets.UTF_8) // JSON
                0x004E4942 -> binChunk = chunkBytes // BIN
            }
        }

        if (jsonText == null || binChunk == null) return null
        val root = JSONObject(jsonText)
        val meshes = root.optJSONArray("meshes") ?: return null
        if (meshes.length() == 0) return null
        val prim = meshes.getJSONObject(0).getJSONArray("primitives").getJSONObject(0)
        val attrs = prim.getJSONObject("attributes")
        val posAccIdx = attrs.optInt("POSITION", -1)
        if (posAccIdx < 0) return null
        val normAccIdx = attrs.optInt("NORMAL", -1)
        val texCoordAccIdx = attrs.optInt("TEXCOORD_0", -1)
        val jointsAccIdx = attrs.optInt("JOINTS_0", -1)
        val weightsAccIdx = attrs.optInt("WEIGHTS_0", -1)
        val idxAccIdx = prim.optInt("indices", -1)

        val accessors = root.getJSONArray("accessors")
        val bufferViews = root.getJSONArray("bufferViews")

        val posAcc = accessors.getJSONObject(posAccIdx)
        val posCount = posAcc.getInt("count")
        val posView = bufferViews.getJSONObject(posAcc.getInt("bufferView"))
        val posOffset = posView.optInt("byteOffset", 0) + posAcc.optInt("byteOffset", 0)
        val posStride = posView.optInt("byteStride", 12).coerceAtLeast(12)

        val binBb = ByteBuffer.wrap(binChunk).order(ByteOrder.LITTLE_ENDIAN)

        // Compute bounding box to center XZ and ground Y=0 at standard hero height (2.15 units)
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        for (i in 0 until posCount) {
            val pBase = posOffset + i * posStride
            val x = binBb.getFloat(pBase)
            val y = binBb.getFloat(pBase + 4)
            val z = binBb.getFloat(pBase + 8)
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
            if (z < minZ) minZ = z
            if (z > maxZ) maxZ = z
        }
        val centerX = (minX + maxX) * 0.5f
        val centerZ = (minZ + maxZ) * 0.5f
        val height = (maxY - minY).coerceAtLeast(0.01f)
        val normScale = 2.15f / height

        // Extract optional embedded baseColorTexture JPEG/PNG bytes
        var baseColorBytes: ByteArray? = null
        val matIdx = prim.optInt("material", -1)
        val imagesArr = root.optJSONArray("images")
        val texturesArr = root.optJSONArray("textures")
        val materialsArr = root.optJSONArray("materials")
        var imgIndex = -1
        if (matIdx >= 0 && materialsArr != null && matIdx < materialsArr.length()) {
            val pbr = materialsArr.getJSONObject(matIdx).optJSONObject("pbrMetallicRoughness")
            val baseTex = pbr?.optJSONObject("baseColorTexture")
            val texIdx = baseTex?.optInt("index", -1) ?: -1
            if (texIdx >= 0 && texturesArr != null && texIdx < texturesArr.length()) {
                imgIndex = texturesArr.getJSONObject(texIdx).optInt("source", -1)
            }
        }
        if (imgIndex < 0 && imagesArr != null && imagesArr.length() > 0) {
            imgIndex = 0
        }
        if (imgIndex >= 0 && imagesArr != null && imgIndex < imagesArr.length()) {
            val imgObj = imagesArr.getJSONObject(imgIndex)
            val imgBvIdx = imgObj.optInt("bufferView", -1)
            if (imgBvIdx >= 0 && imgBvIdx < bufferViews.length()) {
                val imgBv = bufferViews.getJSONObject(imgBvIdx)
                val imgOff = imgBv.optInt("byteOffset", 0)
                val imgLen = imgBv.getInt("byteLength")
                if (imgOff >= 0 && imgLen > 0 && imgOff + imgLen <= binChunk.size) {
                    baseColorBytes = binChunk.copyOfRange(imgOff, imgOff + imgLen)
                }
            }
        }

        val hasTex = baseColorBytes != null && texCoordAccIdx >= 0
        val baseColorR = if (hasTex) 1f else 0.85f
        val baseColorG = if (hasTex) 1f else 0.85f
        val baseColorB = if (hasTex) 1f else 0.90f

        val strideFloats = 12
        val outVerts = FloatArray(posCount * strideFloats)
        for (i in 0 until posCount) {
            val pBase = posOffset + i * posStride
            val o = i * strideFloats
            outVerts[o + 0] = (binBb.getFloat(pBase) - centerX) * normScale
            outVerts[o + 1] = (binBb.getFloat(pBase + 4) - minY) * normScale
            outVerts[o + 2] = (binBb.getFloat(pBase + 8) - centerZ) * normScale
            outVerts[o + 3] = 0f
            outVerts[o + 4] = 1f
            outVerts[o + 5] = 0f
            outVerts[o + 6] = baseColorR
            outVerts[o + 7] = baseColorG
            outVerts[o + 8] = baseColorB
            outVerts[o + 9] = 1f
            outVerts[o + 10] = 0f
            outVerts[o + 11] = 0f
        }

        if (normAccIdx >= 0) {
            val normAcc = accessors.getJSONObject(normAccIdx)
            val normView = bufferViews.getJSONObject(normAcc.getInt("bufferView"))
            val normOffset = normView.optInt("byteOffset", 0) + normAcc.optInt("byteOffset", 0)
            val normStride = normView.optInt("byteStride", 12).coerceAtLeast(12)
            for (i in 0 until posCount) {
                val nBase = normOffset + i * normStride
                val o = i * strideFloats
                outVerts[o + 3] = binBb.getFloat(nBase)
                outVerts[o + 4] = binBb.getFloat(nBase + 4)
                outVerts[o + 5] = binBb.getFloat(nBase + 8)
            }
        }

        if (texCoordAccIdx >= 0) {
            val texAcc = accessors.getJSONObject(texCoordAccIdx)
            val texView = bufferViews.getJSONObject(texAcc.getInt("bufferView"))
            val texOffset = texView.optInt("byteOffset", 0) + texAcc.optInt("byteOffset", 0)
            val texStride = texView.optInt("byteStride", 8).coerceAtLeast(8)
            for (i in 0 until posCount) {
                val tBase = texOffset + i * texStride
                val o = i * strideFloats
                outVerts[o + 10] = binBb.getFloat(tBase)
                outVerts[o + 11] = binBb.getFloat(tBase + 4)
            }
        }

        val outIndices: ShortArray = if (idxAccIdx >= 0) {
            val idxAcc = accessors.getJSONObject(idxAccIdx)
            val idxCount = idxAcc.getInt("count")
            val compType = idxAcc.getInt("componentType")
            val idxView = bufferViews.getJSONObject(idxAcc.getInt("bufferView"))
            val idxOffset = idxView.optInt("byteOffset", 0) + idxAcc.optInt("byteOffset", 0)
            ShortArray(idxCount) { i ->
                when (compType) {
                    5123 -> binBb.getShort(idxOffset + i * 2) // UNSIGNED_SHORT
                    5125 -> binBb.getInt(idxOffset + i * 4).toShort() // UNSIGNED_INT
                    else -> (binBb.get(idxOffset + i).toInt() and 0xFF).toShort()
                }
            }
        } else {
            ShortArray(posCount) { it.toShort() }
        }

        val skinData = parseGlbSkinData(
            root = root,
            accessors = accessors,
            bufferViews = bufferViews,
            binBb = binBb,
            posCount = posCount,
            jointsAccIdx = jointsAccIdx,
            weightsAccIdx = weightsAccIdx,
            normScale = normScale,
            centerX = centerX,
            minY = minY,
            centerZ = centerZ
        )

        return LoadedGlbGeometry(outVerts, outIndices, baseColorBytes, skinData)
    }

    private fun parseGlbSkinData(
        root: JSONObject,
        accessors: JSONArray,
        bufferViews: JSONArray,
        binBb: ByteBuffer,
        posCount: Int,
        jointsAccIdx: Int,
        weightsAccIdx: Int,
        normScale: Float,
        centerX: Float,
        minY: Float,
        centerZ: Float
    ): GlbSkinData? {
        val skins = root.optJSONArray("skins") ?: return null
        val nodes = root.optJSONArray("nodes") ?: return null
        if (skins.length() == 0 || jointsAccIdx < 0 || weightsAccIdx < 0) return null

        val skinObj = skins.getJSONObject(0)
        val jointsJson = skinObj.optJSONArray("joints") ?: return null
        val jointCount = jointsJson.length()
        if (jointCount == 0) return null

        val nodeToJointIdx = HashMap<Int, Int>(jointCount)
        val jointNodeIndices = IntArray(jointCount)
        val jointNames = ArrayList<String>(jointCount)
        for (j in 0 until jointCount) {
            val nodeIdx = jointsJson.getInt(j)
            jointNodeIndices[j] = nodeIdx
            nodeToJointIdx[nodeIdx] = j
            val nodeName = nodes.optJSONObject(nodeIdx)?.optString("name", "joint_$j") ?: "joint_$j"
            jointNames.add(nodeName)
        }

        val nodeParent = IntArray(nodes.length()) { -1 }
        for (nIdx in 0 until nodes.length()) {
            val children = nodes.optJSONObject(nIdx)?.optJSONArray("children") ?: continue
            for (c in 0 until children.length()) {
                val childIdx = children.getInt(c)
                if (childIdx in nodeParent.indices) nodeParent[childIdx] = nIdx
            }
        }
        val jointParents = IntArray(jointCount) { j ->
            var p = nodeParent.getOrElse(jointNodeIndices[j]) { -1 }
            while (p >= 0 && !nodeToJointIdx.containsKey(p)) {
                p = nodeParent.getOrElse(p) { -1 }
            }
            if (p >= 0) nodeToJointIdx[p]!! else -1
        }

        val ibm = FloatArray(jointCount * 16)
        for (j in 0 until jointCount) {
            ibm[j * 16 + 0] = 1f
            ibm[j * 16 + 5] = 1f
            ibm[j * 16 + 10] = 1f
            ibm[j * 16 + 15] = 1f
        }
        val ibmAccIdx = skinObj.optInt("inverseBindMatrices", -1)
        if (ibmAccIdx >= 0) {
            val ibmAcc = accessors.getJSONObject(ibmAccIdx)
            val ibmView = bufferViews.getJSONObject(ibmAcc.getInt("bufferView"))
            val ibmOff = ibmView.optInt("byteOffset", 0) + ibmAcc.optInt("byteOffset", 0)
            val ibmStride = ibmView.optInt("byteStride", 64).coerceAtLeast(64)
            for (j in 0 until minOf(jointCount, ibmAcc.getInt("count"))) {
                val base = ibmOff + j * ibmStride
                for (k in 0 until 16) {
                    ibm[j * 16 + k] = binBb.getFloat(base + k * 4)
                }
            }
        }

        val jAcc = accessors.getJSONObject(jointsAccIdx)
        val jComp = jAcc.getInt("componentType")
        val jView = bufferViews.getJSONObject(jAcc.getInt("bufferView"))
        val jOff = jView.optInt("byteOffset", 0) + jAcc.optInt("byteOffset", 0)
        val jDefaultStride = if (jComp == 5123) 8 else 4
        val jStride = jView.optInt("byteStride", jDefaultStride).coerceAtLeast(jDefaultStride)

        val wAcc = accessors.getJSONObject(weightsAccIdx)
        val wComp = wAcc.getInt("componentType")
        val wView = bufferViews.getJSONObject(wAcc.getInt("bufferView"))
        val wOff = wView.optInt("byteOffset", 0) + wAcc.optInt("byteOffset", 0)
        val wDefaultStride = when (wComp) {
            5126 -> 16
            5123 -> 8
            else -> 4
        }
        val wStride = wView.optInt("byteStride", wDefaultStride).coerceAtLeast(wDefaultStride)

        val jw = FloatArray(posCount * 8)
        for (i in 0 until posCount) {
            val jb = jOff + i * jStride
            val wb = wOff + i * wStride
            val o = i * 8
            for (k in 0 until 4) {
                jw[o + k] = when (jComp) {
                    5123 -> (binBb.getShort(jb + k * 2).toInt() and 0xFFFF).toFloat()
                    else -> (binBb.get(jb + k).toInt() and 0xFF).toFloat()
                }
                jw[o + 4 + k] = when (wComp) {
                    5126 -> binBb.getFloat(wb + k * 4)
                    5123 -> (binBb.getShort(wb + k * 2).toInt() and 0xFFFF) / 65535f
                    else -> (binBb.get(wb + k).toInt() and 0xFF) / 255f
                }
            }
        }

        return GlbSkinData(
            jointNames = jointNames,
            jointParentIndices = jointParents,
            inverseBindMatrices = ibm,
            vertexJointsAndWeights = jw,
            normScale = normScale,
            centerX = centerX,
            minY = minY,
            centerZ = centerZ
        )
    }
}
