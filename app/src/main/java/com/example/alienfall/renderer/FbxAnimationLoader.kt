package com.example.alienfall.renderer

import android.content.Context
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Inflater
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class HeroMotionClip(val assetPath: String, val displayName: String) {
    WALK_WITH_RIFLE("animations/Walk With Rifle.fbx", "Walk With Rifle"),
    JOGGING("animations/Jogging.fbx", "Jogging"),
    MELEE_DOWNWARD("animations/Standing Melee Attack Downward.fbx", "Standing Melee Attack Downward"),
    BATTLECRY_TAUNT("animations/Standing Taunt Battlecry.fbx", "Standing Taunt Battlecry"),
    HIT_REACT_LEFT("animations/Standing React Large From Left.fbx", "Standing React Large From Left"),
    ASSASSINATION_FINISHER("animations/Brutal Assassination.fbx", "Brutal Assassination"),
    CLIMBING_TO_TOP("animations/Climbing To Top.fbx", "Climbing To Top")
}

enum class HumanoidBone {
    ROOT,
    HEAD,
    RIGHT_ARM,
    LEFT_ARM,
    RIGHT_LEG,
    LEFT_LEG
}

data class BonePose(
    val rotX: Float = 0f,
    val rotY: Float = 0f,
    val rotZ: Float = 0f
)

data class HeroPoseSnapshot(
    val rootBob: Float = 0f,
    val rootPitch: Float = 0f,
    val rootRoll: Float = 0f,
    val rootYawOffset: Float = 0f,
    val head: BonePose = BonePose(),
    val armR: BonePose = BonePose(),
    val armL: BonePose = BonePose(),
    val legR: BonePose = BonePose(),
    val legL: BonePose = BonePose()
)

data class FbxScalarCurve(
    val timesSec: FloatArray,
    val values: FloatArray
) {
    fun sample(t: Float): Float {
        if (timesSec.isEmpty() || values.isEmpty()) return 0f
        if (timesSec.size == 1 || t <= timesSec[0]) return values[0]
        val last = timesSec.lastIndex
        if (t >= timesSec[last]) return values[last]
        for (i in 0 until last) {
            val t0 = timesSec[i]
            val t1 = timesSec[i + 1]
            if (t in t0..t1) {
                val alpha = if (t1 > t0) (t - t0) / (t1 - t0) else 0f
                return values[i] + (values[i + 1] - values[i]) * alpha
            }
        }
        return values[last]
    }
}

data class FbxBoneTrack(
    var rx: FbxScalarCurve? = null,
    var ry: FbxScalarCurve? = null,
    var rz: FbxScalarCurve? = null,
    var tx: FbxScalarCurve? = null,
    var ty: FbxScalarCurve? = null,
    var tz: FbxScalarCurve? = null
) {
    fun sample(t: Float): BonePose = BonePose(
        rotX = rx?.sample(t) ?: 0f,
        rotY = ry?.sample(t) ?: 0f,
        rotZ = rz?.sample(t) ?: 0f
    )
}

data class FbxSkeletonBone(
    val id: Long,
    val name: String,
    val normalizedName: String,
    val parentIndex: Int,
    val lclTranslation: FloatArray,
    val preRotation: FloatArray,
    val lclRotation: FloatArray,
    val lclScaling: FloatArray,
    val track: FbxBoneTrack,
    val bindGlobalMatrix: FloatArray,
    val invBindGlobalMatrix: FloatArray
)

data class ParsedFbxClip(
    val durationSec: Float,
    val tracks: Map<HumanoidBone, FbxBoneTrack>,
    val skeletonBones: List<FbxSkeletonBone> = emptyList(),
    val fbxBindHeight: Float = 170f
) {
    private val boneByNormName: Map<String, FbxSkeletonBone> =
        skeletonBones.associateBy { it.normalizedName }

    fun sample(normalizedProgress: Float): HeroPoseSnapshot {
        val t = (normalizedProgress.coerceIn(0f, 1f) * durationSec.coerceAtLeast(0.1f))
        val root = tracks[HumanoidBone.ROOT]?.sample(t) ?: BonePose()
        val head = tracks[HumanoidBone.HEAD]?.sample(t) ?: BonePose()
        val armR = tracks[HumanoidBone.RIGHT_ARM]?.sample(t) ?: BonePose()
        val armL = tracks[HumanoidBone.LEFT_ARM]?.sample(t) ?: BonePose()
        val legR = tracks[HumanoidBone.RIGHT_LEG]?.sample(t) ?: BonePose()
        val legL = tracks[HumanoidBone.LEFT_LEG]?.sample(t) ?: BonePose()
        val root0 = tracks[HumanoidBone.ROOT]?.sample(0f) ?: BonePose()
        val head0 = tracks[HumanoidBone.HEAD]?.sample(0f) ?: BonePose()
        val armR0 = tracks[HumanoidBone.RIGHT_ARM]?.sample(0f) ?: BonePose()
        val armL0 = tracks[HumanoidBone.LEFT_ARM]?.sample(0f) ?: BonePose()
        val legR0 = tracks[HumanoidBone.RIGHT_LEG]?.sample(0f) ?: BonePose()
        val legL0 = tracks[HumanoidBone.LEFT_LEG]?.sample(0f) ?: BonePose()

        return HeroPoseSnapshot(
            rootPitch = (root.rotX - root0.rotX).coerceIn(-38f, 38f),
            rootYawOffset = (root.rotY - root0.rotY).coerceIn(-45f, 45f),
            rootRoll = (root.rotZ - root0.rotZ).coerceIn(-35f, 35f),
            head = BonePose(
                rotX = (head.rotX - head0.rotX).coerceIn(-35f, 35f),
                rotY = (head.rotY - head0.rotY).coerceIn(-45f, 45f),
                rotZ = (head.rotZ - head0.rotZ).coerceIn(-30f, 30f)
            ),
            armR = BonePose(
                rotX = (armR.rotX - armR0.rotX).coerceIn(-120f, 120f),
                rotY = (armR.rotY - armR0.rotY).coerceIn(-90f, 90f),
                rotZ = (armR.rotZ - armR0.rotZ).coerceIn(-90f, 90f)
            ),
            armL = BonePose(
                rotX = (armL.rotX - armL0.rotX).coerceIn(-120f, 120f),
                rotY = (armL.rotY - armL0.rotY).coerceIn(-90f, 90f),
                rotZ = (armL.rotZ - armL0.rotZ).coerceIn(-90f, 90f)
            ),
            legR = BonePose(
                rotX = (legR.rotX - legR0.rotX).coerceIn(-75f, 75f),
                rotY = (legR.rotY - legR0.rotY).coerceIn(-45f, 45f),
                rotZ = (legR.rotZ - legR0.rotZ).coerceIn(-45f, 45f)
            ),
            legL = BonePose(
                rotX = (legL.rotX - legL0.rotX).coerceIn(-75f, 75f),
                rotY = (legL.rotY - legL0.rotY).coerceIn(-45f, 45f),
                rotZ = (legL.rotZ - legL0.rotZ).coerceIn(-45f, 45f)
            )
        )
    }

    /**
     * Evaluates global bone world positions at [normalizedProgress] for diagnostics/testing.
     */
    fun evaluateSkeletonWorldMatrices(
        normalizedProgress: Float,
        stripRootHorizontalMotion: Boolean = true
    ): List<FloatArray> {
        if (skeletonBones.isEmpty()) return emptyList()
        val t = normalizedProgress.coerceIn(0f, 1f) * durationSec.coerceAtLeast(0.1f)
        val globals = ArrayList<FloatArray>(skeletonBones.size)
        val local = FloatArray(16)
        for (i in skeletonBones.indices) {
            val bone = skeletonBones[i]
            val isHips = bone.normalizedName == "hips"
            val tx = if (stripRootHorizontalMotion && isHips) {
                bone.lclTranslation[0]
            } else {
                bone.track.tx?.sample(t) ?: bone.lclTranslation[0]
            }
            val ty = bone.track.ty?.sample(t) ?: bone.lclTranslation[1]
            val tz = if (stripRootHorizontalMotion && isHips) {
                bone.lclTranslation[2]
            } else {
                bone.track.tz?.sample(t) ?: bone.lclTranslation[2]
            }
            val rx = bone.track.rx?.sample(t) ?: bone.lclRotation[0]
            val ry = bone.track.ry?.sample(t) ?: bone.lclRotation[1]
            val rz = bone.track.rz?.sample(t) ?: bone.lclRotation[2]

            FbxMat4.composeFbxLocal(
                out = local,
                tx = tx, ty = ty, tz = tz,
                preRx = bone.preRotation[0], preRy = bone.preRotation[1], preRz = bone.preRotation[2],
                rx = rx, ry = ry, rz = rz,
                sx = bone.lclScaling[0], sy = bone.lclScaling[1], sz = bone.lclScaling[2]
            )
            val global = FloatArray(16)
            if (bone.parentIndex in globals.indices) {
                FbxMat4.mul(global, globals[bone.parentIndex], local)
            } else {
                System.arraycopy(local, 0, global, 0, 16)
            }
            globals.add(global)
        }
        return globals
    }

    /**
     * Computes column-major 4x4 GPU skinning matrices (`delta = globalAnim * invBindGlobal`)
     * mapped onto the target GLB's joint names and scaled into normalized game space (height 2.15).
     */
    fun computeSkinMatricesForGlb(
        skinData: GlbSkinData,
        normalizedProgress: Float,
        maxBones: Int
    ): FloatArray {
        val count = minOf(skinData.jointNames.size, maxBones)
        val out = FloatArray(count * 16)
        for (j in 0 until count) {
            out[j * 16 + 0] = 1f
            out[j * 16 + 5] = 1f
            out[j * 16 + 10] = 1f
            out[j * 16 + 15] = 1f
        }
        if (skeletonBones.isEmpty()) return out

        val globals = evaluateSkeletonWorldMatrices(normalizedProgress, stripRootHorizontalMotion = true)
        val scaleToGame = 2.15f / fbxBindHeight.coerceAtLeast(1f)
        val delta = FloatArray(16)

        for (j in 0 until count) {
            val normJoint = normalizeBoneKey(skinData.jointNames[j])
            val boneIdx = skeletonBones.indexOfFirst { it.normalizedName == normJoint }
            if (boneIdx >= 0) {
                val bone = skeletonBones[boneIdx]
                FbxMat4.mul(delta, globals[boneIdx], bone.invBindGlobalMatrix)
                delta[12] *= scaleToGame
                delta[13] *= scaleToGame
                delta[14] *= scaleToGame
                System.arraycopy(delta, 0, out, j * 16, 16)
            }
        }
        return out
    }
}

private fun normalizeBoneKey(name: String): String {
    var s = name.lowercase().trim()
    val colonIdx = s.lastIndexOf(':')
    if (colonIdx >= 0) s = s.substring(colonIdx + 1)
    s = s.removePrefix("mixamorig_")
    return s
}

/**
 * Loads binary or ASCII FBX animation clips from assets/animations/ (Mixamo clips)
 * and evaluates full Mixamo bone hierarchies + skinning matrices for GPU-rigged GLBs,
 * as well as legacy BonePose snapshots for procedural articulated heroes.
 */
class FbxAnimationLoader(private val context: Context) {

    private val loadedClips = HashMap<HeroMotionClip, ParsedFbxClip?>()
    val emptyOrMissingClips = mutableListOf<HeroMotionClip>()

    fun preloadAll() {
        emptyOrMissingClips.clear()
        HeroMotionClip.entries.forEach { clip ->
            val parsed = tryLoadFbx(clip.assetPath)
            loadedClips[clip] = parsed
            if (parsed == null) {
                emptyOrMissingClips.add(clip)
            }
        }
    }

    fun hasLoadedClip(clip: HeroMotionClip): Boolean = loadedClips[clip] != null

    fun getLoadedClip(clip: HeroMotionClip): ParsedFbxClip? = loadedClips[clip]

    fun tryLoadFbx(assetPath: String): ParsedFbxClip? {
        val bytes = runCatching {
            context.assets.open(assetPath).use { it.readBytes() }
        }.getOrNull() ?: return null

        if (bytes.size < 32) return null

        return if (isBinaryFbx(bytes)) {
            parseBinaryFbx(bytes)
        } else {
            parseAsciiFbx(String(bytes, Charsets.UTF_8))
        }
    }

    fun computeSkinMatrices(
        skinData: GlbSkinData,
        activeClip: HeroMotionClip?,
        clipProgress: Float,
        maxBones: Int
    ): FloatArray {
        val clip = activeClip?.let { loadedClips[it] }
        if (clip != null) {
            return clip.computeSkinMatricesForGlb(skinData, clipProgress, maxBones)
        }
        val count = minOf(skinData.jointNames.size, maxBones)
        val identity = FloatArray(count * 16)
        for (j in 0 until count) {
            identity[j * 16 + 0] = 1f
            identity[j * 16 + 5] = 1f
            identity[j * 16 + 10] = 1f
            identity[j * 16 + 15] = 1f
        }
        return identity
    }

    fun sampleHeroPose(
        activeClip: HeroMotionClip?,
        clipProgress: Float,
        walkCycle: Float,
        isMoving: Boolean,
        isDashing: Boolean
    ): HeroPoseSnapshot {
        val baseWalkSwing = if (isMoving) sin(walkCycle) * 32f else sin(walkCycle * 0.5f) * 4f
        val baseWalkBob = if (isMoving) abs(sin(walkCycle)) * 0.07f else sin(walkCycle) * 0.02f

        if (activeClip != null) {
            loadedClips[activeClip]?.let { fbx ->
                val sampled = fbx.sample(clipProgress)
                return sampled.copy(rootBob = baseWalkBob)
            }
        }

        return when (activeClip) {
            HeroMotionClip.MELEE_DOWNWARD -> {
                val p = clipProgress.coerceIn(0f, 1f)
                val swing = if (p < 0.3f) {
                    -105f * (p / 0.3f)
                } else {
                    val t = (p - 0.3f) / 0.7f
                    -105f + 150f * sin(t * (PI.toFloat() * 0.5f))
                }
                val bodyPitch = sin(p * PI.toFloat()) * 16f
                val bodyYaw = if (p < 0.3f) -18f * (p / 0.3f) else -18f + 42f * ((p - 0.3f) / 0.7f)
                HeroPoseSnapshot(
                    rootBob = baseWalkBob + sin(p * PI.toFloat()) * 0.05f,
                    rootPitch = bodyPitch,
                    rootYawOffset = bodyYaw,
                    armR = BonePose(rotX = swing - baseWalkSwing * 0.25f, rotZ = -14f),
                    armL = BonePose(rotX = baseWalkSwing * 0.4f + 22f, rotZ = 18f),
                    legR = BonePose(rotX = baseWalkSwing),
                    legL = BonePose(rotX = -baseWalkSwing)
                )
            }
            HeroMotionClip.BATTLECRY_TAUNT -> {
                val s = sin(clipProgress.coerceIn(0f, 1f) * PI.toFloat())
                HeroPoseSnapshot(
                    rootBob = baseWalkBob + s * 0.08f,
                    rootPitch = -12f * s,
                    head = BonePose(rotX = -16f * s),
                    armR = BonePose(rotX = -95f * s, rotZ = -35f * s),
                    armL = BonePose(rotX = -95f * s, rotZ = 35f * s),
                    legR = BonePose(rotX = baseWalkSwing * 0.5f),
                    legL = BonePose(rotX = -baseWalkSwing * 0.5f)
                )
            }
            HeroMotionClip.HIT_REACT_LEFT -> {
                val s = sin(clipProgress.coerceIn(0f, 1f) * PI.toFloat())
                HeroPoseSnapshot(
                    rootBob = baseWalkBob,
                    rootPitch = -14f * s,
                    rootRoll = 24f * s,
                    rootYawOffset = 18f * s,
                    head = BonePose(rotZ = 18f * s),
                    armR = BonePose(rotX = -25f * s, rotZ = -28f * s),
                    armL = BonePose(rotX = 35f * s, rotZ = 22f * s),
                    legR = BonePose(rotX = baseWalkSwing),
                    legL = BonePose(rotX = -baseWalkSwing)
                )
            }
            HeroMotionClip.ASSASSINATION_FINISHER -> {
                val p = clipProgress.coerceIn(0f, 1f)
                val s = sin(p * PI.toFloat())
                HeroPoseSnapshot(
                    rootBob = if (isDashing) 0.14f else baseWalkBob + 0.04f,
                    rootPitch = 22f * (0.4f + 0.6f * s),
                    rootYawOffset = cos(p * 2f * PI.toFloat()) * 16f,
                    armR = BonePose(rotX = -85f * s, rotZ = -22f),
                    armL = BonePose(rotX = 45f * s, rotZ = 24f),
                    legR = BonePose(rotX = baseWalkSwing * 1.25f),
                    legL = BonePose(rotX = -baseWalkSwing * 1.25f)
                )
            }
            HeroMotionClip.JOGGING, HeroMotionClip.WALK_WITH_RIFLE -> {
                val mult = if (activeClip == HeroMotionClip.JOGGING) 1.25f else 0.95f
                HeroPoseSnapshot(
                    rootBob = baseWalkBob,
                    rootPitch = 8f * mult,
                    armR = BonePose(rotX = -baseWalkSwing * mult),
                    armL = BonePose(rotX = baseWalkSwing * mult),
                    legR = BonePose(rotX = baseWalkSwing * mult),
                    legL = BonePose(rotX = -baseWalkSwing * mult)
                )
            }
            HeroMotionClip.CLIMBING_TO_TOP, null -> {
                HeroPoseSnapshot(
                    rootBob = baseWalkBob,
                    rootPitch = if (isMoving) 6f else 0f,
                    armR = BonePose(rotX = -baseWalkSwing),
                    armL = BonePose(rotX = baseWalkSwing),
                    legR = BonePose(rotX = baseWalkSwing),
                    legL = BonePose(rotX = -baseWalkSwing)
                )
            }
        }
    }

    private fun isBinaryFbx(bytes: ByteArray): Boolean {
        val header = "Kaydara FBX Binary"
        if (bytes.size < header.length) return false
        for (i in header.indices) {
            if (bytes[i] != header[i].code.toByte()) return false
        }
        return true
    }

    private fun mapBoneName(name: String): HumanoidBone? {
        val lower = normalizeBoneKey(name)
        return when {
            lower == "head" -> HumanoidBone.HEAD
            lower == "rightarm" || lower == "r_arm" -> HumanoidBone.RIGHT_ARM
            lower == "leftarm" || lower == "l_arm" -> HumanoidBone.LEFT_ARM
            lower == "rightupleg" || lower == "r_leg" -> HumanoidBone.RIGHT_LEG
            lower == "leftupleg" || lower == "l_leg" -> HumanoidBone.LEFT_LEG
            lower == "hips" || lower == "pelvis" -> HumanoidBone.ROOT
            else -> null
        }
    }

    private fun parseAsciiFbx(text: String): ParsedFbxClip? {
        val modelBones = HashMap<Long, HumanoidBone>()
        val curveNodes = HashMap<Long, String>()
        val curves = HashMap<Long, FbxScalarCurve>()

        val modelRegex = Regex("""Model:\s*(\d+),\s*"Model::([^"]+)"""")
        modelRegex.findAll(text).forEach { m ->
            val id = m.groupValues[1].toLongOrNull() ?: return@forEach
            mapBoneName(m.groupValues[2])?.let { modelBones[id] = it }
        }

        val cNodeRegex = Regex("""AnimationCurveNode:\s*(\d+),\s*"AnimCurveNode::([^"]+)"""")
        cNodeRegex.findAll(text).forEach { m ->
            val id = m.groupValues[1].toLongOrNull() ?: return@forEach
            curveNodes[id] = m.groupValues[2]
        }

        val curveBlockRegex = Regex(
            """AnimationCurve:\s*(\d+),\s*"AnimCurve::[^"]*"[^{]*\{.*?KeyTime:\s*\*\d+\s*\{\s*a:\s*([^}]+)\}.*?KeyValueFloat:\s*\*\d+\s*\{\s*a:\s*([^}]+)\}""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        curveBlockRegex.findAll(text).forEach { m ->
            val id = m.groupValues[1].toLongOrNull() ?: return@forEach
            val times = m.groupValues[2].split(',').mapNotNull { it.trim().toLongOrNull() }
            val vals = m.groupValues[3].split(',').mapNotNull { it.trim().toFloatOrNull() }
            val n = minOf(times.size, vals.size)
            if (n > 0) {
                val t0 = times[0]
                val secArr = FloatArray(n) { i -> ((times[i] - t0) / 46186158000.0).toFloat() }
                val valArr = FloatArray(n) { i -> vals[i] }
                curves[id] = FbxScalarCurve(secArr, valArr)
            }
        }

        if (curves.isEmpty()) return null

        val nodeToBone = HashMap<Long, HumanoidBone>()
        val opRegex = Regex("""C:\s*"OP",\s*(\d+),\s*(\d+),\s*"([^"]+)"""")
        val opMatches = opRegex.findAll(text).toList()
        opMatches.forEach { m ->
            val src = m.groupValues[1].toLongOrNull() ?: return@forEach
            val dst = m.groupValues[2].toLongOrNull() ?: return@forEach
            val prop = m.groupValues[3]
            if (curveNodes.containsKey(src) && modelBones.containsKey(dst) && prop.contains("Rotation")) {
                nodeToBone[src] = modelBones[dst]!!
            }
        }

        val boneTracks = HashMap<HumanoidBone, FbxBoneTrack>()
        opMatches.forEach { m ->
            val srcCurve = m.groupValues[1].toLongOrNull() ?: return@forEach
            val dstNode = m.groupValues[2].toLongOrNull() ?: return@forEach
            val prop = m.groupValues[3]
            val curve = curves[srcCurve] ?: return@forEach
            val bone = nodeToBone[dstNode] ?: return@forEach
            val track = boneTracks.getOrPut(bone) { FbxBoneTrack() }
            when {
                prop.endsWith("|X") -> track.rx = curve
                prop.endsWith("|Y") -> track.ry = curve
                prop.endsWith("|Z") -> track.rz = curve
            }
        }

        if (boneTracks.isEmpty()) return null
        val maxDur = curves.values.maxOfOrNull { it.timesSec.lastOrNull() ?: 0f } ?: 1f
        return ParsedFbxClip(maxDur.coerceAtLeast(0.1f), boneTracks)
    }

    private data class RawFbxModel(
        val id: Long,
        val name: String,
        val lclTranslation: FloatArray = floatArrayOf(0f, 0f, 0f),
        val preRotation: FloatArray = floatArrayOf(0f, 0f, 0f),
        val lclRotation: FloatArray = floatArrayOf(0f, 0f, 0f),
        val lclScaling: FloatArray = floatArrayOf(1f, 1f, 1f)
    )

    private fun parseBinaryFbx(bytes: ByteArray): ParsedFbxClip? {
        return runCatching {
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (bb.remaining() < 27) return null
            bb.position(23)
            val version = bb.int
            val is64 = version >= 7500

            val rawModels = LinkedHashMap<Long, RawFbxModel>()
            val curveNodes = HashMap<Long, String>()
            val curves = HashMap<Long, FbxScalarCurve>()
            val ooConns = ArrayList<Pair<Long, Long>>()
            val opConns = ArrayList<Triple<Long, Long, String>>()

            fun readNodeHeader(buf: ByteBuffer): Triple<Int, Int, Int>? {
                val endOff = if (is64) buf.long.toInt() else buf.int
                val numProps = if (is64) buf.long.toInt() else buf.int
                val propLen = if (is64) buf.long.toInt() else buf.int
                if (endOff == 0) return null
                return Triple(endOff, numProps, propLen)
            }

            fun parseProperties70(endOffset: Int, model: RawFbxModel) {
                val hdrSize = if (is64) 25 else 13
                while (bb.position() < endOffset - hdrSize) {
                    val cHdr = readNodeHeader(bb) ?: break
                    val cEnd = cHdr.first
                    val cNumProps = cHdr.second
                    val cPropLen = cHdr.third
                    val cNameLen = bb.get().toInt() and 0xFF
                    val cNameBytes = ByteArray(cNameLen)
                    bb.get(cNameBytes)
                    val cName = String(cNameBytes, Charsets.UTF_8)
                    val cPropEnd = (bb.position() + cPropLen).coerceAtMost(bb.limit())
                    if (cName == "P" && cNumProps >= 7) {
                        val pList = ArrayList<Any?>(cNumProps)
                        for (i in 0 until cNumProps) {
                            if (bb.position() >= cPropEnd) break
                            pList.add(readBinaryProp(bb))
                        }
                        val pName = pList.getOrNull(0) as? String
                        val vx = (pList.getOrNull(4) as? Number)?.toFloat()
                        val vy = (pList.getOrNull(5) as? Number)?.toFloat()
                        val vz = (pList.getOrNull(6) as? Number)?.toFloat()
                        if (pName != null && vx != null && vy != null && vz != null) {
                            when (pName) {
                                "Lcl Translation" -> {
                                    model.lclTranslation[0] = vx
                                    model.lclTranslation[1] = vy
                                    model.lclTranslation[2] = vz
                                }
                                "PreRotation" -> {
                                    model.preRotation[0] = vx
                                    model.preRotation[1] = vy
                                    model.preRotation[2] = vz
                                }
                                "Lcl Rotation" -> {
                                    model.lclRotation[0] = vx
                                    model.lclRotation[1] = vy
                                    model.lclRotation[2] = vz
                                }
                                "Lcl Scaling" -> {
                                    model.lclScaling[0] = vx
                                    model.lclScaling[1] = vy
                                    model.lclScaling[2] = vz
                                }
                            }
                        }
                    }
                    bb.position(cEnd.coerceIn(cPropEnd, bb.limit()))
                }
            }

            fun readNode(): Boolean {
                val endOffset = if (is64) bb.long.toInt() else bb.int
                val numProps = if (is64) bb.long.toInt() else bb.int
                val propListLen = if (is64) bb.long.toInt() else bb.int
                val nameLen = bb.get().toInt() and 0xFF
                if (endOffset == 0) return false
                val nameBytes = ByteArray(nameLen)
                bb.get(nameBytes)
                val nodeName = String(nameBytes, Charsets.UTF_8)
                val propsEnd = (bb.position() + propListLen).coerceAtMost(bb.limit())

                val props = ArrayList<Any?>(numProps)
                for (p in 0 until numProps) {
                    if (bb.position() >= propsEnd) break
                    props.add(readBinaryProp(bb))
                }
                bb.position(propsEnd)

                val hdrSize = if (is64) 25 else 13
                when (nodeName) {
                    "Model" -> {
                        val id = (props.getOrNull(0) as? Number)?.toLong()
                        val rawLabel = props.getOrNull(1) as? String
                        val subType = props.getOrNull(2) as? String
                        val cleanLabel = rawLabel?.substringBefore('\u0000')
                        if (id != null && cleanLabel != null && (subType == "LimbNode" || cleanLabel.lowercase().contains("mixamorig"))) {
                            val model = RawFbxModel(id, cleanLabel)
                            while (bb.position() < endOffset - hdrSize) {
                                val cHdr = readNodeHeader(bb) ?: break
                                val cEnd = cHdr.first
                                val cPropLen = cHdr.third
                                val cNameLen = bb.get().toInt() and 0xFF
                                val cNameB = ByteArray(cNameLen)
                                bb.get(cNameB)
                                val cName = String(cNameB, Charsets.UTF_8)
                                val cPropEnd = (bb.position() + cPropLen).coerceAtMost(bb.limit())
                                bb.position(cPropEnd)
                                if (cName == "Properties70") {
                                    parseProperties70(cEnd, model)
                                }
                                bb.position(cEnd.coerceIn(cPropEnd, bb.limit()))
                            }
                            rawModels[id] = model
                        }
                    }
                    "AnimationCurveNode" -> {
                        val id = (props.getOrNull(0) as? Number)?.toLong()
                        val label = (props.getOrNull(1) as? String)?.substringBefore('\u0000')
                        if (id != null && label != null) {
                            curveNodes[id] = label
                        }
                    }
                    "AnimationCurve" -> {
                        val id = (props.getOrNull(0) as? Number)?.toLong()
                        var keyTimes: LongArray? = null
                        var keyVals: FloatArray? = null
                        while (bb.position() < endOffset - hdrSize) {
                            val childEnd = if (is64) bb.long.toInt() else bb.int
                            val cNumProps = if (is64) bb.long.toInt() else bb.int
                            val cPropLen = if (is64) bb.long.toInt() else bb.int
                            val cNameLen = bb.get().toInt() and 0xFF
                            if (childEnd == 0) break
                            val cNameB = ByteArray(cNameLen)
                            bb.get(cNameB)
                            val cName = String(cNameB, Charsets.UTF_8)
                            val cEndProps = (bb.position() + cPropLen).coerceAtMost(bb.limit())
                            if (cName == "KeyTime" && cNumProps > 0) {
                                keyTimes = readBinaryProp(bb) as? LongArray
                            } else if (cName == "KeyValueFloat" && cNumProps > 0) {
                                keyVals = readBinaryProp(bb) as? FloatArray
                            }
                            bb.position(childEnd.coerceIn(cEndProps, bb.limit()))
                        }
                        if (id != null && keyTimes != null && keyVals != null) {
                            val n = minOf(keyTimes.size, keyVals.size)
                            if (n > 0) {
                                val t0 = keyTimes[0]
                                val secArr = FloatArray(n) { i -> ((keyTimes[i] - t0) / 46186158000.0).toFloat() }
                                val valArr = FloatArray(n) { i -> keyVals[i] }
                                curves[id] = FbxScalarCurve(secArr, valArr)
                            }
                        }
                    }
                    "C" -> {
                        val kind = props.getOrNull(0) as? String
                        val src = (props.getOrNull(1) as? Number)?.toLong()
                        val dst = (props.getOrNull(2) as? Number)?.toLong()
                        val attr = props.getOrNull(3) as? String
                        if (src != null && dst != null) {
                            if (kind == "OO") ooConns.add(src to dst)
                            else if (kind == "OP" && attr != null) opConns.add(Triple(src, dst, attr))
                        }
                    }
                    "Objects", "Connections" -> {
                        while (bb.position() < endOffset - hdrSize) {
                            if (!readNode()) break
                        }
                    }
                }
                bb.position(endOffset.coerceIn(bb.position(), bb.limit()))
                return true
            }

            while (bb.remaining() > (if (is64) 25 else 13)) {
                if (!readNode()) break
            }

            if (curves.isEmpty() || rawModels.isEmpty()) return@runCatching null

            // Build parent relationships between skeleton Model nodes via OO connections
            val modelParentId = HashMap<Long, Long>()
            for ((src, dst) in ooConns) {
                if (rawModels.containsKey(src) && rawModels.containsKey(dst)) {
                    modelParentId[src] = dst
                }
            }

            // Map AnimationCurveNode -> (modelId, attrName) via OP connections!
            val curveNodeTarget = HashMap<Long, Pair<Long, String>>()
            for ((src, dst, attr) in opConns) {
                if (curveNodes.containsKey(src) && rawModels.containsKey(dst)) {
                    curveNodeTarget[src] = dst to attr
                }
            }

            val modelTracks = HashMap<Long, FbxBoneTrack>()
            val legacyBoneTracks = HashMap<HumanoidBone, FbxBoneTrack>()
            for ((srcCurve, dstNode, prop) in opConns) {
                val curve = curves[srcCurve] ?: continue
                val (modelId, attrName) = curveNodeTarget[dstNode] ?: continue
                val mTrack = modelTracks.getOrPut(modelId) { FbxBoneTrack() }
                val isRot = attrName.contains("Rotation")
                val isTrans = attrName.contains("Translation")
                if (isRot) {
                    when {
                        prop.endsWith("|X") -> mTrack.rx = curve
                        prop.endsWith("|Y") -> mTrack.ry = curve
                        prop.endsWith("|Z") -> mTrack.rz = curve
                    }
                    mapBoneName(rawModels[modelId]?.name ?: "")?.let { hBone ->
                        val hTrack = legacyBoneTracks.getOrPut(hBone) { FbxBoneTrack() }
                        when {
                            prop.endsWith("|X") -> hTrack.rx = curve
                            prop.endsWith("|Y") -> hTrack.ry = curve
                            prop.endsWith("|Z") -> hTrack.rz = curve
                        }
                    }
                } else if (isTrans) {
                    when {
                        prop.endsWith("|X") -> mTrack.tx = curve
                        prop.endsWith("|Y") -> mTrack.ty = curve
                        prop.endsWith("|Z") -> mTrack.tz = curve
                    }
                }
            }

            if (modelTracks.isEmpty()) return@runCatching null

            // Topological sort so parents always precede children
            val orderedIds = ArrayList<Long>(rawModels.size)
            val visited = HashSet<Long>(rawModels.size)
            fun visit(id: Long) {
                if (!visited.add(id)) return
                val p = modelParentId[id]
                if (p != null && rawModels.containsKey(p)) visit(p)
                orderedIds.add(id)
            }
            rawModels.keys.forEach { visit(it) }

            val idToIndex = HashMap<Long, Int>(orderedIds.size)
            orderedIds.forEachIndexed { idx, id -> idToIndex[id] = idx }

            val skeletonBones = ArrayList<FbxSkeletonBone>(orderedIds.size)
            var minBindY = Float.MAX_VALUE
            var maxBindY = -Float.MAX_VALUE
            val localBind = FloatArray(16)

            for (id in orderedIds) {
                val raw = rawModels[id]!!
                val parentIdx = modelParentId[id]?.let { idToIndex[it] } ?: -1
                FbxMat4.composeFbxLocal(
                    out = localBind,
                    tx = raw.lclTranslation[0], ty = raw.lclTranslation[1], tz = raw.lclTranslation[2],
                    preRx = raw.preRotation[0], preRy = raw.preRotation[1], preRz = raw.preRotation[2],
                    rx = raw.lclRotation[0], ry = raw.lclRotation[1], rz = raw.lclRotation[2],
                    sx = raw.lclScaling[0], sy = raw.lclScaling[1], sz = raw.lclScaling[2]
                )
                val bindGlobal = FloatArray(16)
                if (parentIdx >= 0) {
                    FbxMat4.mul(bindGlobal, skeletonBones[parentIdx].bindGlobalMatrix, localBind)
                } else {
                    System.arraycopy(localBind, 0, bindGlobal, 0, 16)
                }
                val invBind = FloatArray(16)
                FbxMat4.invert(invBind, bindGlobal)

                val by = bindGlobal[13]
                if (by < minBindY) minBindY = by
                if (by > maxBindY) maxBindY = by

                skeletonBones.add(
                    FbxSkeletonBone(
                        id = id,
                        name = raw.name,
                        normalizedName = normalizeBoneKey(raw.name),
                        parentIndex = parentIdx,
                        lclTranslation = raw.lclTranslation,
                        preRotation = raw.preRotation,
                        lclRotation = raw.lclRotation,
                        lclScaling = raw.lclScaling,
                        track = modelTracks[id] ?: FbxBoneTrack(),
                        bindGlobalMatrix = bindGlobal,
                        invBindGlobalMatrix = invBind
                    )
                )
            }

            val bindHeight = (maxBindY - minBindY).let { if (it > 10f) it * 1.08f else 170f }
            val maxDur = curves.values.maxOfOrNull { it.timesSec.lastOrNull() ?: 0f } ?: 1f
            ParsedFbxClip(
                durationSec = maxDur.coerceAtLeast(0.1f),
                tracks = legacyBoneTracks,
                skeletonBones = skeletonBones,
                fbxBindHeight = bindHeight
            )
        }.getOrNull()
    }

    private fun readBinaryProp(bb: ByteBuffer): Any? {
        if (!bb.hasRemaining()) return null
        val typeCode = bb.get().toInt().toChar()
        return when (typeCode) {
            'Y' -> bb.short
            'C' -> bb.get().toInt() != 0
            'I' -> bb.int
            'F' -> bb.float
            'D' -> bb.double
            'L' -> bb.long
            'S', 'R' -> {
                val len = bb.int.coerceAtLeast(0).coerceAtMost(bb.remaining())
                val arr = ByteArray(len)
                bb.get(arr)
                if (typeCode == 'S') String(arr, Charsets.UTF_8) else arr
            }
            'f', 'd', 'l', 'i', 'b' -> {
                val arrLen = bb.int
                val encoding = bb.int
                val compLen = bb.int.coerceAtLeast(0).coerceAtMost(bb.remaining())
                val raw = ByteArray(compLen)
                bb.get(raw)
                val decoded = if (encoding == 1) inflateBytes(raw) else raw
                val dbb = ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN)
                when (typeCode) {
                    'f' -> FloatArray(arrLen.coerceAtMost(decoded.size / 4)) { dbb.float }
                    'l' -> LongArray(arrLen.coerceAtMost(decoded.size / 8)) { dbb.long }
                    'i' -> IntArray(arrLen.coerceAtMost(decoded.size / 4)) { dbb.int }
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun inflateBytes(compressed: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(compressed)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!inflater.finished()) {
            val count = inflater.inflate(buf)
            if (count == 0) break
            out.write(buf, 0, count)
        }
        inflater.end()
        return out.toByteArray()
    }
}

internal object FbxMat4 {
    private const val DEG2RAD = (PI / 180.0).toFloat()

    fun setIdentity(m: FloatArray) {
        for (i in 0 until 16) m[i] = 0f
        m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f
    }

    fun mul(out: FloatArray, a: FloatArray, b: FloatArray) {
        for (col in 0 until 4) {
            val b0 = b[col * 4 + 0]
            val b1 = b[col * 4 + 1]
            val b2 = b[col * 4 + 2]
            val b3 = b[col * 4 + 3]
            out[col * 4 + 0] = a[0] * b0 + a[4] * b1 + a[8] * b2 + a[12] * b3
            out[col * 4 + 1] = a[1] * b0 + a[5] * b1 + a[9] * b2 + a[13] * b3
            out[col * 4 + 2] = a[2] * b0 + a[6] * b1 + a[10] * b2 + a[14] * b3
            out[col * 4 + 3] = a[3] * b0 + a[7] * b1 + a[11] * b2 + a[15] * b3
        }
    }

    fun setEulerXYZ(m: FloatArray, rxDeg: Float, ryDeg: Float, rzDeg: Float) {
        val rx = rxDeg * DEG2RAD
        val ry = ryDeg * DEG2RAD
        val rz = rzDeg * DEG2RAD
        val cx = cos(rx); val sx = sin(rx)
        val cy = cos(ry); val sy = sin(ry)
        val cz = cos(rz); val sz = sin(rz)
        // R = Rz * Ry * Rx (column-major)
        m[0] = cz * cy
        m[1] = sz * cy
        m[2] = -sy
        m[3] = 0f

        m[4] = cz * sy * sx - sz * cx
        m[5] = sz * sy * sx + cz * cx
        m[6] = cy * sx
        m[7] = 0f

        m[8] = cz * sy * cx + sz * sx
        m[9] = sz * sy * cx - cz * sx
        m[10] = cy * cx
        m[11] = 0f

        m[12] = 0f; m[13] = 0f; m[14] = 0f; m[15] = 1f
    }

    fun composeFbxLocal(
        out: FloatArray,
        tx: Float, ty: Float, tz: Float,
        preRx: Float, preRy: Float, preRz: Float,
        rx: Float, ry: Float, rz: Float,
        sx: Float, sy: Float, sz: Float
    ) {
        val rPre = FloatArray(16)
        val rLcl = FloatArray(16)
        val rRot = FloatArray(16)
        setEulerXYZ(rPre, preRx, preRy, preRz)
        setEulerXYZ(rLcl, rx, ry, rz)
        mul(rRot, rPre, rLcl)

        // Apply scaling on columns 0..2 and translation in column 3
        out[0] = rRot[0] * sx; out[1] = rRot[1] * sx; out[2] = rRot[2] * sx; out[3] = 0f
        out[4] = rRot[4] * sy; out[5] = rRot[5] * sy; out[6] = rRot[6] * sy; out[7] = 0f
        out[8] = rRot[8] * sz; out[9] = rRot[9] * sz; out[10] = rRot[10] * sz; out[11] = 0f
        out[12] = tx; out[13] = ty; out[14] = tz; out[15] = 1f
    }

    fun invert(out: FloatArray, m: FloatArray): Boolean {
        val inv = FloatArray(16)
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] +
            m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] -
            m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] +
            m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] -
            m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]

        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] -
            m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] +
            m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] -
            m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] +
            m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]

        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] +
            m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] -
            m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] +
            m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] -
            m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]

        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] -
            m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] +
            m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] -
            m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] +
            m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]

        val det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12]
        if (abs(det) < 1e-10f) {
            setIdentity(out)
            return false
        }
        val invDet = 1f / det
        for (i in 0 until 16) out[i] = inv[i] * invDet
        return true
    }
}
