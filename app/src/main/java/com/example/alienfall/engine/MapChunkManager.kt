package com.example.alienfall.engine

import com.example.alienfall.model.PoiKind
import com.example.alienfall.model.PointOfInterest
import com.example.alienfall.model.PropKind
import com.example.alienfall.model.WorldChunk
import com.example.alienfall.model.WorldProp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Streams 24x24m sectors around the player, generating post-apocalyptic structures,
 * crashed starships, bunkers, crystals, contaminated flora, craters, and POIs,
 * while unloading distant chunks for steady frame rates.
 */
class MapChunkManager {

    companion object {
        const val CHUNK_SIZE = 24f
    }

    private val loadedChunks = HashMap<Long, WorldChunk>()
    private val discoveredPoiIds = HashSet<Int>()
    private var nextPropId = 1

    fun clear() {
        loadedChunks.clear()
        discoveredPoiIds.clear()
        nextPropId = 1
    }

    fun updateAround(playerX: Float, playerZ: Float, chunkRadius: Int) {
        val pcx = floor(playerX / CHUNK_SIZE).toInt()
        val pcz = floor(playerZ / CHUNK_SIZE).toInt()

        val activeKeys = HashSet<Long>()
        for (dz in -chunkRadius..chunkRadius) {
            for (dx in -chunkRadius..chunkRadius) {
                val cx = pcx + dx
                val cz = pcz + dz
                val key = chunkKey(cx, cz)
                activeKeys.add(key)
                if (!loadedChunks.containsKey(key)) {
                    loadedChunks[key] = generateChunk(cx, cz)
                }
            }
        }

        val iterator = loadedChunks.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!activeKeys.contains(entry.key)) {
                iterator.remove()
            }
        }
    }

    fun allProps(): List<WorldProp> {
        val list = ArrayList<WorldProp>(loadedChunks.size * 10)
        for (chunk in loadedChunks.values) {
            list.addAll(chunk.props)
        }
        return list
    }

    fun allPois(): List<PointOfInterest> {
        val list = ArrayList<PointOfInterest>()
        for (chunk in loadedChunks.values) {
            chunk.poi?.let { list.add(it) }
        }
        return list
    }

    fun markPoiDiscovered(poiId: Int) {
        discoveredPoiIds.add(poiId)
        for (chunk in loadedChunks.values) {
            if (chunk.poi?.id == poiId) {
                chunk.poi.discovered = true
            }
        }
    }

    /**
     * Resolves circle vs obstacle collision and returns adjusted (x, z).
     */
    fun resolveMovementCollision(x: Float, z: Float, radius: Float): Pair<Float, Float> {
        var rx = x
        var rz = z
        for (chunk in loadedChunks.values) {
            // Quick AABB rejection
            val minX = chunk.cx * CHUNK_SIZE - 4f
            val maxX = (chunk.cx + 1) * CHUNK_SIZE + 4f
            val minZ = chunk.cz * CHUNK_SIZE - 4f
            val maxZ = (chunk.cz + 1) * CHUNK_SIZE + 4f
            if (rx < minX || rx > maxX || rz < minZ || rz > maxZ) continue

            for (prop in chunk.props) {
                if (!prop.kind.blocksMovement) continue
                val minDist = radius + prop.kind.collisionRadius * prop.scale
                val dx = rx - prop.x
                val dz = rz - prop.z
                val distSq = dx * dx + dz * dz
                if (distSq < minDist * minDist) {
                    val dist = hypot(dx, dz)
                    if (dist > 0.0001f) {
                        val push = minDist - dist
                        rx += (dx / dist) * push
                        rz += (dz / dist) * push
                    } else {
                        rx += minDist
                    }
                }
            }
        }
        return rx to rz
    }

    /**
     * Computes obstacle-avoidance steering vector for AI enemies so they smoothly skirt ruins.
     */
    fun computeAvoidanceSteering(x: Float, z: Float, dirX: Float, dirZ: Float): Pair<Float, Float> {
        var steerX = 0f
        var steerZ = 0f
        val lookAhead = 2.6f
        val probeX = x + dirX * lookAhead
        val probeZ = z + dirZ * lookAhead

        for (chunk in loadedChunks.values) {
            val minX = chunk.cx * CHUNK_SIZE - 3f
            val maxX = (chunk.cx + 1) * CHUNK_SIZE + 3f
            val minZ = chunk.cz * CHUNK_SIZE - 3f
            val maxZ = (chunk.cz + 1) * CHUNK_SIZE + 3f
            if (x < minX || x > maxX || z < minZ || z > maxZ) continue

            for (prop in chunk.props) {
                if (!prop.kind.blocksMovement) continue
                val avoidR = prop.kind.collisionRadius * prop.scale + 1.15f
                val dx = probeX - prop.x
                val dz = probeZ - prop.z
                val dSq = dx * dx + dz * dz
                if (dSq < avoidR * avoidR && dSq > 0.001f) {
                    val d = kotlin.math.sqrt(dSq)
                    val strength = (avoidR - d) / avoidR
                    steerX += (dx / d) * strength * 1.8f
                    steerZ += (dz / d) * strength * 1.8f
                }
            }
        }
        return steerX to steerZ
    }

    private fun generateChunk(cx: Int, cz: Int): WorldChunk {
        val seed = (cx.toLong() * 73856093L) xor (cz.toLong() * 19349663L) xor 0x5F3759DFL
        val rng = Random(seed)
        val originX = cx * CHUNK_SIZE
        val originZ = cz * CHUNK_SIZE
        val props = mutableListOf<WorldProp>()

        // Keep spawn origin (0,0) clear of big blocking ruins
        val isSpawnChunk = cx == 0 && cz == 0
        val count = if (isSpawnChunk) 6 else rng.nextInt(8, 13)

        val propTypes = PropKind.entries
        for (i in 0 until count) {
            val px = originX + 2.5f + rng.nextFloat() * (CHUNK_SIZE - 5f)
            val pz = originZ + 2.5f + rng.nextFloat() * (CHUNK_SIZE - 5f)
            if (isSpawnChunk && hypot(px, pz) < 6.5f) continue

            // Avoid overlapping props inside same chunk
            if (props.any { hypot(it.x - px, it.z - pz) < 3.8f }) continue

            val kind = propTypes[rng.nextInt(propTypes.size)]
            val scale = when (kind) {
                PropKind.DESTROYED_BUILDING, PropKind.CRASHED_SPACESHIP, PropKind.DESTROYED_LAB -> 0.85f + rng.nextFloat() * 0.35f
                PropKind.GLOWING_CRYSTAL, PropKind.CONTAMINATED_FLORA -> 0.8f + rng.nextFloat() * 0.6f
                else -> 0.85f + rng.nextFloat() * 0.3f
            }
            val accent = when (kind) {
                PropKind.GLOWING_CRYSTAL -> if (rng.nextBoolean()) 0xFF00E5FF else 0xFFC084FC
                PropKind.CONTAMINATED_FLORA -> 0xFFE11D48
                PropKind.CRASHED_SPACESHIP, PropKind.DESTROYED_LAB -> 0xFFFF6B1A
                else -> 0xFF64748B
            }
            props.add(
                WorldProp(
                    id = nextPropId++,
                    kind = kind,
                    x = px,
                    z = pz,
                    rotation = rng.nextFloat() * (2f * PI.toFloat()),
                    scale = scale,
                    accentHex = accent
                )
            )
        }

        // Spawn discoverable Points of Interest in non-origin chunks (~30% chance)
        val poi = if (!isSpawnChunk && (abs(cx * 3 + cz * 5) % 3 == 0)) {
            val poiId = ((cx + 500) shl 12) xor (cz + 500)
            val poiKind = PoiKind.entries[abs(cx + cz * 7) % PoiKind.entries.size]
            val px = originX + CHUNK_SIZE * 0.5f + (rng.nextFloat() - 0.5f) * 6f
            val pz = originZ + CHUNK_SIZE * 0.5f + (rng.nextFloat() - 0.5f) * 6f
            PointOfInterest(
                id = poiId,
                kind = poiKind,
                x = px,
                z = pz,
                discovered = discoveredPoiIds.contains(poiId)
            )
        } else null

        return WorldChunk(cx, cz, props, poi)
    }

    private fun chunkKey(cx: Int, cz: Int): Long = (cx.toLong() shl 32) xor (cz.toLong() and 0xFFFFFFFFL)
}
