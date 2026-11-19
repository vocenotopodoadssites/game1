package com.example.alienfall.renderer

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.alienfall.engine.GameEngine
import com.example.alienfall.engine.MapChunkManager
import com.example.alienfall.engine.RunPhase
import com.example.alienfall.model.AlienType
import com.example.alienfall.model.BossType
import com.example.alienfall.model.EnemyState
import com.example.alienfall.model.HeroClass
import com.example.alienfall.model.PickupKind
import com.example.alienfall.model.PropKind
import com.example.alienfall.model.SkinVariant
import com.example.alienfall.model.VfxKind
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

class AlienfallRenderer(
    private val context: Context,
    private val engine: GameEngine?,
    private val showcaseMode: Boolean = false
) : GLSurfaceView.Renderer {

    @Volatile var showcaseHero: HeroClass = HeroClass.BERSERKER
    @Volatile var showcaseSkin: SkinVariant = SkinVariant.DEFAULT
    @Volatile var showcaseDragYaw: Float = 0.45f
    @Volatile var onFrameHudUpdate: (() -> Unit)? = null

    private val modelLoader = ModelAssetLoader(context)
    private val fbxLoader = FbxAnimationLoader(context)
    private lateinit var shader: ShaderProgram

    private val heroRigs = HashMap<HeroClass, ArticulatedRig>()
    private val alienRigs = HashMap<AlienType, ArticulatedRig>()
    private val bossRigs = HashMap<BossType, ArticulatedRig>()
    private val propMeshes = HashMap<PropKind, GpuMesh>()
    private val glbOverrides = HashMap<String, GpuMesh>()

    private lateinit var groundTileMesh: GpuMesh
    private lateinit var shadowDiscMesh: GpuMesh
    private lateinit var ringMesh: GpuMesh
    private lateinit var discMesh: GpuMesh
    private lateinit var slashArcMesh: GpuMesh
    private lateinit var projectileMesh: GpuMesh
    private lateinit var crystalMesh: GpuMesh
    private lateinit var unitBoxMesh: GpuMesh
    private lateinit var droneMesh: GpuMesh
    private lateinit var turretMesh: GpuMesh
    private lateinit var orbitalBladeMesh: GpuMesh

    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val childMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val scratchMatrix = FloatArray(16)

    private var camX = 0f
    private var camZ = 0f
    private var lastTimeNs = System.nanoTime()
    private var showcaseTime = 0f
    private var hudTickAccumulator = 0f
    private var lastSpecialTimer = 0f
    private var battlecryTimer = 0f

    private var glReady = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        runCatching {
            GLES20.glClearColor(0.055f, 0.044f, 0.048f, 1f)
            GLES20.glEnable(GLES20.GL_DEPTH_TEST)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

            val maxBones = ShaderProgram.chooseSupportedMaxBones()
            shader = ShaderProgram(
                ShaderProgram.buildVertexShader(maxBones),
                ShaderProgram.FRAGMENT_SHADER,
                maxBones
            )
            modelLoader.preloadCatalogCheck()
            fbxLoader.preloadAll()

            ringMesh = MeshBuilder.buildRingMesh()
            discMesh = MeshBuilder.buildDiscMesh()

            if (!showcaseMode) {
                groundTileMesh = MeshBuilder.buildGroundTile()
                shadowDiscMesh = MeshBuilder.buildShadowDisc()
                slashArcMesh = MeshBuilder.buildSlashArcMesh()
                projectileMesh = MeshBuilder.buildProjectileMesh()
                crystalMesh = MeshBuilder.buildCrystalMesh()
                unitBoxMesh = MeshBuilder.buildUnitBox()
                droneMesh = MeshBuilder.buildDroneMesh()
                turretMesh = MeshBuilder.buildTurretMesh()
                orbitalBladeMesh = MeshBuilder.buildOrbitalBladeMesh()

                AlienType.entries.forEach { a ->
                    alienRigs[a] = MeshBuilder.buildAlienRig(a)
                    modelLoader.tryLoadGlb(a.glbModelPath)?.let { geom ->
                        glbOverrides[a.glbModelPath] = MeshAccumulator.uploadGlbGeometry(geom)
                    }
                }
                BossType.entries.forEach { b -> bossRigs[b] = MeshBuilder.buildBossRig(b) }
                PropKind.entries.forEach { p -> propMeshes[p] = MeshBuilder.buildPropMesh(p) }
            }
            lastTimeNs = System.nanoTime()
            glReady = true
        }
    }

    private fun getOrBuildHeroRig(heroClass: HeroClass): ArticulatedRig {
        return heroRigs.getOrPut(heroClass) {
            modelLoader.tryLoadGlb(heroClass.glbModelPath)?.let { geom ->
                glbOverrides[heroClass.glbModelPath] = MeshAccumulator.uploadGlbGeometry(geom)
            }
            MeshBuilder.buildHeroRig(heroClass)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
        Matrix.perspectiveM(projMatrix, 0, if (showcaseMode) 36f else 44f, aspect, 0.5f, 120f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (!glReady) return

        val nowNs = System.nanoTime()
        val dt = ((nowNs - lastTimeNs) / 1_000_000_000.0f).coerceIn(0.001f, 0.05f)
        lastTimeNs = nowNs

        shader.use()

        if (showcaseMode) {
            renderShowcase(dt)
            return
        }
        if (battlecryTimer > 0f) {
            battlecryTimer = (battlecryTimer - dt).coerceAtLeast(0f)
        }

        val game = engine ?: return
        if (game.phase == RunPhase.RUNNING) {
            game.step(dt)
        }

        hudTickAccumulator += dt
        if (hudTickAccumulator >= 0.08f) {
            hudTickAccumulator = 0f
            onFrameHudUpdate?.invoke()
        }

        synchronized(game.stateLock) {
            // Smooth isometric follow camera keeping hero centered and pulled slightly further back
            val distToTarget = hypot(game.playerX - camX, game.playerZ - camZ)
            if (distToTarget > 12f) {
                camX = game.playerX
                camZ = game.playerZ
            } else {
                val lerp = (1f - kotlin.math.exp(-14f * dt)).coerceIn(0.08f, 0.55f)
                camX += (game.playerX - camX) * lerp
                camZ += (game.playerZ - camZ) * lerp
            }
            val shakeX = if (game.cameraShake > 0f) (kotlin.math.sin(nowNs * 0.0001f) * game.cameraShake) else 0f
            val shakeZ = if (game.cameraShake > 0f) (kotlin.math.cos(nowNs * 0.00013f) * game.cameraShake) else 0f

            val eyeX = camX + 14.5f + shakeX
            val eyeY = 21.5f
            val eyeZ = camZ + 14.5f + shakeZ
            Matrix.setLookAtM(viewMatrix, 0, eyeX, eyeY, eyeZ, camX, 0.75f, camZ, 0f, 1f, 0f)

            setGlobalUniforms(eyeX, eyeY, eyeZ, fogDensity = 0.011f)

            // 1. Ground tiles around camera (cracked dusty earth + glowing magma fissures)
            val tileSize = MapChunkManager.CHUNK_SIZE
            val baseCx = floor(camX / tileSize).toInt()
            val baseCz = floor(camZ / tileSize).toInt()
            for (dz in -2..2) {
                for (dx in -2..2) {
                    val tx = (baseCx + dx + 0.5f) * tileSize
                    val tz = (baseCz + dz + 0.5f) * tileSize
                    drawMesh(groundTileMesh, tx, 0f, tz, 0f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 0.14f, 0f)
                }
            }

            // 2. Post-apocalyptic terrain props & POIs
            for (prop in game.mapManager.allProps()) {
                if (hypot(prop.x - camX, prop.z - camZ) > 42f) continue
                propMeshes[prop.kind]?.let { mesh ->
                    if (prop.kind != PropKind.CRATER) {
                        drawMesh(shadowDiscMesh, prop.x, 0.01f, prop.z, 0f, prop.scale * 1.4f, 1f, prop.scale * 1.4f, 1f, 1f, 1f, 0.5f, 0f, 0f)
                    }
                    val emissive = if (prop.kind == PropKind.GLOWING_CRYSTAL || prop.kind == PropKind.CONTAMINATED_FLORA) 0.35f else 0.02f
                    drawMesh(mesh, prop.x, 0f, prop.z, prop.rotation, prop.scale, prop.scale, prop.scale, 1f, 1f, 1f, 1f, emissive, 0f)
                }
            }

            for (poi in game.mapManager.allPois()) {
                if (hypot(poi.x - camX, poi.z - camZ) > 42f) continue
                val pulse = 1f + 0.15f * sin(game.animTime * 3.5f)
                val (r, g, b) = if (poi.discovered) Triple(0.3f, 0.5f, 0.6f) else Triple(0.0f, 0.92f, 1.0f)
                drawMesh(ringMesh, poi.x, 0.04f, poi.z, game.animTime, 2.1f * pulse, 1f, 2.1f * pulse, r, g, b, 0.85f, 0.5f, 0f)
                drawMesh(crystalMesh, poi.x, 0.5f + 0.2f * sin(game.animTime * 2f), poi.z, game.animTime * 1.5f, 1.1f, 1.4f, 1.1f, r, g, b, 0.9f, 0.6f, 0f)
            }

            // 3. Pickups
            for (pk in game.pickups) {
                if (!pk.active) continue
                val bob = 0.12f * sin(pk.age * 4f)
                val (r, g, b, s) = when (pk.kind) {
                    PickupKind.XP_CRYSTAL -> listOf(0.0f, 0.90f, 1.0f, 0.72f)
                    PickupKind.COIN -> listOf(1.0f, 0.82f, 0.18f, 0.78f)
                    PickupKind.MEDKIT -> listOf(0.20f, 1.0f, 0.35f, 0.95f)
                    PickupKind.SHIELD_ORB -> listOf(0.25f, 0.70f, 1.0f, 0.92f)
                    PickupKind.BOSS_RELIC -> listOf(0.85f, 0.30f, 1.0f, 1.35f)
                }
                drawMesh(crystalMesh, pk.x, 0.15f + bob, pk.z, pk.age * 2.5f, s, s, s, r, g, b, 1f, 0.55f, 0f)
            }

            // 4. Visual effects (ground rings, fields, lightning, explosions, telegraphs)
            renderVfx(game)

            // 5. Player Aim Indicator + Hero Rig + Card Orbitals + HP Bar
            renderHero(game)

            // 6. Allies (Drones & Turrets)
            for (ally in game.allies) {
                val mesh = if (ally.isTurret) turretMesh else droneMesh
                val y = if (ally.isTurret) 0f else 1.45f + 0.12f * sin(game.animTime * 5f + ally.orbitAngle)
                drawMesh(shadowDiscMesh, ally.x, 0.02f, ally.z, 0f, 0.6f, 1f, 0.6f, 1f, 1f, 1f, 0.5f, 0f, 0f)
                drawMesh(mesh, ally.x, y, ally.z, ally.orbitAngle, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 0.25f, 0f)
            }

            // 7. Enemies
            for (e in game.enemies) {
                if (hypot(e.x - camX, e.z - camZ) > 36f) continue
                if (e.state == EnemyState.BURROWED) {
                    // Underground rumble dirt ring
                    drawMesh(ringMesh, e.x, 0.05f, e.z, game.animTime * 4f, 0.85f, 1f, 0.85f, 0.96f, 0.62f, 0.1f, 0.8f, 0.4f, 0f)
                    continue
                }
                drawMesh(shadowDiscMesh, e.x, 0.02f, e.z, 0f, e.type.scale * 0.9f, 1f, e.type.scale * 0.9f, 1f, 1f, 1f, 0.65f, 0f, 0f)
                val glb = glbOverrides[e.type.glbModelPath]
                val tint = when {
                    e.slowTimer > 0f -> floatArrayOf(0.55f, 0.88f, 1.0f)
                    e.poisonTimer > 0f -> floatArrayOf(0.55f, 1.0f, 0.45f)
                    e.burnTimer > 0f -> floatArrayOf(1.0f, 0.55f, 0.30f)
                    else -> floatArrayOf(1f, 1f, 1f)
                }
                if (glb != null) {
                    drawMesh(glb, e.x, e.yOffset, e.z, e.facingAngle, e.type.scale, e.type.scale, e.type.scale, tint[0], tint[1], tint[2], 1f, 0.05f, e.hitFlashTimer * 6f)
                } else {
                    alienRigs[e.type]?.let { rig ->
                        drawArticulatedCharacter(
                            rig = rig,
                            x = e.x, y = e.yOffset, z = e.z,
                            facingAngle = e.facingAngle,
                            scale = e.type.scale,
                            walkCycle = e.animTime * 9f,
                            isMoving = true,
                            attackProgress = 0f,
                            r = tint[0], g = tint[1], b = tint[2],
                            hitFlash = (e.hitFlashTimer * 7f).coerceIn(0f, 1f),
                            emissive = 0.08f
                        )
                    }
                }
                if (e.slowTimer > 0f) {
                    val cryoR = e.type.radius * 1.35f
                    drawMesh(ringMesh, e.x, 0.06f, e.z, -game.animTime * 3f, cryoR, 1f, cryoR, 0.4f, 0.92f, 1f, 0.85f, 0.7f, 0f)
                }
                if (e.poisonTimer > 0f) {
                    val toxR = e.type.radius * 1.25f
                    drawMesh(ringMesh, e.x, 0.07f, e.z, game.animTime * 2.5f, toxR, 1f, toxR, 0.45f, 1f, 0.1f, 0.8f, 0.7f, 0f)
                }
                if (e.burnTimer > 0f) {
                    val fireR = e.type.radius * (1.2f + 0.15f * sin(game.animTime * 14f))
                    drawMesh(ringMesh, e.x, 0.08f, e.z, game.animTime * 5f, fireR, 1f, fireR, 1f, 0.42f, 0.08f, 0.9f, 0.85f, 0f)
                }
                if (e.hp < e.maxHp || e.shield > 0f) {
                    drawWorldBar(e.x, 1.85f * e.type.scale + e.yOffset, e.z, 1.0f, (e.hp / e.maxHp).coerceIn(0f, 1f), 1f, 0.22f, 0.26f)
                    if (e.shield > 0f && e.maxShield > 0f) {
                        drawWorldBar(e.x, 2.02f * e.type.scale + e.yOffset, e.z, 1.0f, (e.shield / e.maxShield).coerceIn(0f, 1f), 0.1f, 0.85f, 1f)
                    }
                }
            }

            // 8. Bosses
            for (b in game.bosses) {
                drawMesh(shadowDiscMesh, b.x, 0.02f, b.z, 0f, b.type.scale * 1.15f, 1f, b.type.scale * 1.15f, 1f, 1f, 1f, 0.75f, 0f, 0f)
                val scale = b.type.scale * (if (b.entranceTimer > 0f) (1.25f - b.entranceTimer * 0.25f) else 1f)
                bossRigs[b.type]?.let { rig ->
                    drawArticulatedCharacter(
                        rig = rig,
                        x = b.x, y = 0f, z = b.z,
                        facingAngle = b.facingAngle,
                        scale = scale,
                        walkCycle = b.animTime * 6f,
                        isMoving = true,
                        attackProgress = if (b.chargingTimer > 0f) 0.8f else 0f,
                        r = 1f, g = if (b.phase == 2) 0.75f else 1f, b = 1f,
                        hitFlash = (b.hitFlashTimer * 7f).coerceIn(0f, 1f),
                        emissive = if (b.phase == 2) 0.25f else 0.12f
                    )
                }
                drawWorldBar(b.x, 2.25f * scale, b.z, 2.6f, (b.hp / b.maxHp).coerceIn(0f, 1f), 0.95f, 0.15f, 0.35f)
            }

            // 9. Projectiles
            for (p in game.projectiles) {
                if (!p.active) continue
                val (r, g, b) = hexToRgb(p.colorHex)
                val angle = atan2(p.vx, p.vz)
                val s = if (p.isPlayer) 1.0f else 1.35f
                drawMesh(projectileMesh, p.x, p.y, p.z, angle, s, s, s * 1.4f, r, g, b, 1f, 0.75f, 0f)
            }
        }
    }

    private fun renderShowcase(dt: Float) {
        showcaseTime += dt
        val eyeX = 0f
        val eyeY = 2.10f
        val eyeZ = 6.2f
        Matrix.setLookAtM(viewMatrix, 0, eyeX, eyeY, eyeZ, 0f, 0.70f, 0f, 0f, 1f, 0f)
        setGlobalUniforms(eyeX, eyeY, eyeZ, fogDensity = 0.008f)

        val (ar, ag, ab) = hexToRgb(showcaseSkin.accentHex)
        // Pedestal discs
        drawMesh(ringMesh, 0f, 0.03f, 0f, showcaseTime * 0.8f, 1.45f, 1f, 1.45f, ar, ag, ab, 0.9f, 0.6f, 0f)
        drawMesh(discMesh, 0f, 0.02f, 0f, 0f, 1.25f, 1f, 1.25f, 0.12f, 0.16f, 0.26f, 0.95f, 0.15f, 0f)

        val yaw = showcaseDragYaw + sin(showcaseTime * 0.6f) * 0.22f
        val rig = getOrBuildHeroRig(showcaseHero)
        val glb = glbOverrides[showcaseHero.glbModelPath]
        val tint = skinTint(showcaseSkin)
        val showcaseClip = if ((showcaseTime % 5.5f) < 1.8f) HeroMotionClip.BATTLECRY_TAUNT else null
        val showcaseClipProgress = ((showcaseTime % 5.5f) / 1.8f).coerceIn(0f, 1f)
        val showcasePose = fbxLoader.sampleHeroPose(
            activeClip = showcaseClip,
            clipProgress = showcaseClipProgress,
            walkCycle = showcaseTime * 2f,
            isMoving = false,
            isDashing = false
        )
        if (glb != null) {
            drawGlbHeroPose(glb, 0f, 0.05f, 0f, yaw, 1.05f, showcaseClip, showcaseClipProgress, tint[0], tint[1], tint[2], 0.12f, 0f)
        } else {
            drawArticulatedCharacter(
                rig = rig,
                x = 0f, y = 0.05f, z = 0f,
                facingAngle = yaw,
                scale = 1.05f,
                walkCycle = showcaseTime * 2f,
                isMoving = false,
                attackProgress = 0f,
                r = tint[0], g = tint[1], b = tint[2],
                hitFlash = 0f,
                emissive = 0.08f,
                poseOverride = showcasePose
            )
        }
    }

    private fun renderHero(game: GameEngine) {
        val px = game.playerX
        val pz = game.playerZ
        val (ar, ag, ab) = hexToRgb(game.skin.accentHex)

        // Contact shadow & directional aim indicator ring
        drawMesh(shadowDiscMesh, px, 0.02f, pz, 0f, 1.0f, 1f, 1.0f, 1f, 1f, 1f, 0.7f, 0f, 0f)
        drawMesh(ringMesh, px, 0.04f, pz, 0f, 1.05f, 1f, 1.05f, ar, ag, ab, 0.55f, 0.4f, 0f)
        // Aim direction arrow pointer in front of player
        val aimX = px + sin(game.aimAngle) * 1.45f
        val aimZ = pz + cos(game.aimAngle) * 1.45f
        drawMesh(projectileMesh, aimX, 0.10f, aimZ, game.aimAngle, 0.65f, 0.35f, 1.0f, ar, ag, ab, 0.85f, 0.65f, 0f)

        // Card 03: Crystal Magnet subtle pull aura ring
        val magnetLvl = game.acquiredCards[3] ?: 0
        if (magnetLvl > 0) {
            val magR = 1.65f + magnetLvl * 0.22f + 0.08f * sin(game.animTime * 3.5f)
            drawMesh(ringMesh, px, 0.045f, pz, -game.animTime * 1.4f, magR, 1f, magR, 0.0f, 0.92f, 1.0f, 0.35f, 0.55f, 0f)
        }

        // Card 13: Poison Aura swirling multi-ring toxic zone
        val poisonLvl = game.acquiredCards[13] ?: 0
        if (poisonLvl > 0) {
            val r = 3.2f + poisonLvl * 0.45f
            drawMesh(discMesh, px, 0.03f, pz, 0f, r, 1f, r, 0.30f, 0.95f, 0.10f, 0.14f, 0.45f, 0f)
            drawMesh(ringMesh, px, 0.05f, pz, game.animTime * 1.8f, r, 1f, r, 0.45f, 1.0f, 0.12f, 0.65f, 0.75f, 0f)
            drawMesh(ringMesh, px, 0.08f, pz, -game.animTime * 2.4f, r * 0.65f, 1f, r * 0.65f, 0.65f, 1.0f, 0.20f, 0.40f, 0.65f, 0f)
        }

        // Card 17: Regenerative Shield dual holo-dome rings
        if (game.shield > 0f) {
            val pulse = 1.32f + 0.06f * sin(game.animTime * 4.5f)
            drawMesh(ringMesh, px, 0.55f, pz, game.animTime * 1.8f, pulse, 1f, pulse, 0.18f, 0.85f, 1.0f, 0.82f, 0.78f, 0f)
            drawMesh(ringMesh, px, 1.08f, pz, -game.animTime * 2.2f, pulse * 0.86f, 1f, pulse * 0.86f, 0.55f, 0.95f, 1.0f, 0.60f, 0.80f, 0f)
        }

        // Card 20: Spinning Orbital Blades + fiery orbital ring
        val bladesLvl = game.acquiredCards[20] ?: 0
        if (bladesLvl > 0) {
            drawMesh(ringMesh, px, 0.88f, pz, game.animTime * 4.2f, 2.35f, 1f, 2.35f, 1f, 0.45f, 0.10f, 0.30f, 0.75f, 0f)
            val count = bladesLvl + 1
            for (i in 0 until count) {
                val ang = game.animTime * 4.2f + (i * 2f * PI.toFloat() / count)
                val bx = px + cos(ang) * 2.35f
                val bz = pz + sin(ang) * 2.35f
                drawMesh(orbitalBladeMesh, bx, 0.9f, bz, -ang, 1.25f, 1.25f, 1.25f, 1f, 0.55f, 0.12f, 1f, 0.85f, 0f)
            }
        }

        // Card 24: Growing Fury crimson rage aura
        if (game.furyStacksCount > 0) {
            val furyRatio = (game.furyStacksCount / 12f).coerceIn(0.15f, 1f)
            val furyR = 1.15f + furyRatio * 0.55f + 0.08f * sin(game.animTime * 9f)
            drawMesh(ringMesh, px, 0.06f, pz, game.animTime * 3.5f, furyR, 1f, furyR, 1f, 0.18f, 0.25f, 0.45f + furyRatio * 0.4f, 0.85f, 0f)
        }

        // Card 29: Supreme Evolution golden crown halo
        if ((game.acquiredCards[29] ?: 0) > 0) {
            val haloR = 0.85f + 0.05f * sin(game.animTime * 4f)
            drawMesh(ringMesh, px, 2.15f, pz, game.animTime * 2.5f, haloR, 1f, haloR, 1f, 0.84f, 0.25f, 0.85f, 0.90f, 0f)
        }

        // Card 30: Starlight Rebirth guardian star orbiting while ready
        if (game.isRebirthReady) {
            val starAng = game.animTime * 2.6f
            val sx = px + cos(starAng) * 1.15f
            val sz = pz + sin(starAng) * 1.15f
            drawMesh(crystalMesh, sx, 1.65f + 0.15f * sin(game.animTime * 5f), sz, game.animTime * 4f, 0.72f, 0.72f, 0.72f, 1f, 0.85f, 0.30f, 1f, 0.95f, 0f)
        }

        if (game.specialActiveTimer > lastSpecialTimer + 0.5f) {
            battlecryTimer = 0.75f
        }
        lastSpecialTimer = game.specialActiveTimer

        val skinTintArr = skinTint(game.skin)
        val hurtFlash = (game.hurtAnimTimer * 4f).coerceIn(0f, 1f)
        val specialEmissive = if (game.specialActiveTimer > 0f) 0.35f else 0.08f

        val rig = getOrBuildHeroRig(game.hero)
        val glb = glbOverrides[game.hero.glbModelPath]
        val heroFacing = if (game.attackAnimTimer > 0f) game.aimAngle else game.facingAngle
        val atkProg = (1f - (game.attackAnimTimer / 0.22f)).coerceIn(0f, 1f)

        val (activeClip, clipProgress) = when {
            game.hurtAnimTimer > 0f -> HeroMotionClip.HIT_REACT_LEFT to (1f - (game.hurtAnimTimer / 0.25f)).coerceIn(0f, 1f)
            battlecryTimer > 0f -> HeroMotionClip.BATTLECRY_TAUNT to (1f - (battlecryTimer / 0.75f)).coerceIn(0f, 1f)
            game.isDashing -> HeroMotionClip.ASSASSINATION_FINISHER to ((game.animTime * 3.5f) % 1f)
            game.attackAnimTimer > 0f -> HeroMotionClip.MELEE_DOWNWARD to atkProg
            game.isMoving -> (
                if (game.specialActiveTimer > 0f) HeroMotionClip.JOGGING else HeroMotionClip.WALK_WITH_RIFLE
            ) to ((game.animTime * 1.35f) % 1f)
            else -> null to 0f
        }
        val heroPose = fbxLoader.sampleHeroPose(
            activeClip = activeClip,
            clipProgress = clipProgress,
            walkCycle = game.animTime * (if (game.isMoving) 11f else 2.2f),
            isMoving = game.isMoving,
            isDashing = game.isDashing
        )

        if (glb != null) {
            drawGlbHeroPose(
                mesh = glb,
                px = px,
                py = 0f,
                pz = pz,
                facingAngle = heroFacing,
                scale = 1.0f,
                activeClip = activeClip,
                clipProgress = clipProgress,
                r = skinTintArr[0],
                g = skinTintArr[1],
                b = skinTintArr[2],
                emissive = specialEmissive,
                hurtFlash = hurtFlash
            )
        } else {
            drawArticulatedCharacter(
                rig = rig,
                x = px, y = if (game.isDashing) 0.18f else 0f, z = pz,
                facingAngle = heroFacing,
                scale = 1.0f,
                walkCycle = game.animTime * (if (game.isMoving) 11f else 2.2f),
                isMoving = game.isMoving,
                attackProgress = atkProg,
                r = skinTintArr[0], g = skinTintArr[1], b = skinTintArr[2],
                hitFlash = hurtFlash,
                emissive = specialEmissive,
                poseOverride = heroPose
            )
        }

        // World-space Player Health & Shield Bar directly above character
        drawWorldBar(px, 2.35f, pz, 1.45f, (game.hp / game.maxHp).coerceIn(0f, 1f), 0.18f, 0.92f, 0.42f)
        if (game.maxShield > 0f && game.shield > 0f) {
            drawWorldBar(px, 2.52f, pz, 1.45f, (game.shield / game.maxShield).coerceIn(0f, 1f), 0.20f, 0.78f, 1.0f)
        }
    }

    private fun renderVfx(game: GameEngine) {
        for (v in game.vfxList) {
            if (!v.active) continue
            val t = (v.timer / v.duration).coerceIn(0f, 1f)
            val alpha = (1f - t).coerceIn(0f, 1f)
            val (r, g, b) = hexToRgb(v.colorHex)

            when (v.kind) {
                VfxKind.SLASH_ARC -> {
                    val s = v.radius * (0.78f + 0.38f * t)
                    drawMesh(slashArcMesh, v.x, v.y, v.z, v.angle, s, 1.2f, s, r, g, b, alpha, 0.92f, 0f)
                    drawMesh(slashArcMesh, v.x, v.y + 0.04f, v.z, v.angle, s * 0.85f, 1f, s * 0.85f, 1f, 0.97f, 0.88f, alpha * 0.75f, 0.95f, 0f)
                }
                VfxKind.SHOCKWAVE_RING -> {
                    val s = v.radius * (0.25f + 0.88f * t)
                    drawMesh(ringMesh, v.x, v.y, v.z, t * 2f, s, 1f, s, r, g, b, alpha, 0.90f, 0f)
                    drawMesh(ringMesh, v.x, v.y + 0.03f, v.z, -t * 3f, s * 0.76f, 1f, s * 0.76f, 1f, 1f, 1f, alpha * 0.6f, 0.95f, 0f)
                }
                VfxKind.PLASMA_EXPLOSION -> {
                    val s = v.radius * (0.22f + 0.92f * t)
                    drawMesh(ringMesh, v.x, v.y, v.z, t * 4f, s, 1f, s, r, g, b, alpha, 0.92f, 0f)
                    drawMesh(ringMesh, v.x, v.y + 0.12f, v.z, -t * 5f, s * 0.68f, 1f, s * 0.68f, 1f, 0.7f, 0.2f, alpha * 0.85f, 0.95f, 0f)
                    drawMesh(discMesh, v.x, v.y + 0.02f, v.z, 0f, s * 0.82f, 1f, s * 0.82f, r, g, b, alpha * 0.48f, 0.90f, 0f)
                    drawMesh(crystalMesh, v.x, v.y + 0.25f, v.z, t * 9f, s * 0.42f, (1.4f - t) * 1.4f, s * 0.42f, 1f, 0.95f, 0.85f, alpha * 0.85f, 0.95f, 0f)
                }
                VfxKind.STAR_REBIRTH -> {
                    val s = v.radius * (0.18f + 0.95f * t)
                    drawMesh(ringMesh, v.x, v.y, v.z, t * 3f, s, 1f, s, 1f, 0.85f, 0.25f, alpha, 0.95f, 0f)
                    drawMesh(ringMesh, v.x, v.y + 0.2f, v.z, -t * 4f, s * 0.72f, 1f, s * 0.72f, 1f, 0.45f, 0.10f, alpha * 0.9f, 0.95f, 0f)
                    drawMesh(discMesh, v.x, v.y + 0.02f, v.z, 0f, s * 0.88f, 1f, s * 0.88f, 1f, 0.82f, 0.30f, alpha * 0.48f, 0.90f, 0f)
                    // Towering solar beam
                    drawMesh(unitBoxMesh, v.x, 2.4f, v.z, t * 4f, 0.9f * alpha + 0.2f, 5.2f, 0.9f * alpha + 0.2f, 1f, 0.90f, 0.40f, alpha * 0.72f, 0.95f, 0f)
                }
                VfxKind.METEOR_WARNING, VfxKind.BOSS_RING_TELEGRAPH -> {
                    drawMesh(ringMesh, v.x, v.y, v.z, t * 2.5f, v.radius, 1f, v.radius, r, g, b, 0.90f, 0.85f, 0f)
                    drawMesh(discMesh, v.x, v.y + 0.01f, v.z, 0f, v.radius * t, 1f, v.radius * t, r, g, b, 0.50f, 0.80f, 0f)
                    if (v.kind == VfxKind.METEOR_WARNING) {
                        val dropY = (1f - t) * 13f + 0.5f
                        drawMesh(crystalMesh, v.x, dropY, v.z, t * 7f, 1.45f, 1.85f, 1.45f, 1f, 0.52f, 0.12f, 1f, 0.95f, 0f)
                        drawMesh(ringMesh, v.x, dropY + 0.5f, v.z, -t * 8f, 1.2f, 1f, 1.2f, 1f, 0.82f, 0.25f, 0.85f, 0.95f, 0f)
                    }
                }
                VfxKind.METEOR_IMPACT -> {
                    val s = v.radius * (0.35f + 0.78f * t)
                    drawMesh(ringMesh, v.x, v.y, v.z, t * 3f, s, 1f, s, 1f, 0.40f, 0.08f, alpha, 0.95f, 0f)
                    drawMesh(ringMesh, v.x, v.y + 0.18f, v.z, -t * 4f, s * 0.65f, 1f, s * 0.65f, 1f, 0.82f, 0.22f, alpha, 0.95f, 0f)
                    drawMesh(discMesh, v.x, v.y, v.z, 0f, s * 0.9f, 1f, s * 0.9f, 1f, 0.55f, 0.15f, alpha * 0.60f, 0.92f, 0f)
                    // Towering fiery magma pillar
                    drawMesh(unitBoxMesh, v.x, 1.8f, v.z, t * 5f, 1.1f * (1f - t * 0.5f), 4.0f * (1f - t * 0.4f), 1.1f * (1f - t * 0.5f), 1f, 0.55f, 0.10f, alpha * 0.75f, 0.95f, 0f)
                }
                VfxKind.GRAVITY_FIELD -> {
                    drawMesh(discMesh, v.x, v.y, v.z, 0f, v.radius, 1f, v.radius, 0.42f, 0.12f, 0.85f, alpha * 0.42f, 0.65f, 0f)
                    drawMesh(ringMesh, v.x, v.y + 0.02f, v.z, t * 8f, v.radius, 1f, v.radius, r, g, b, alpha * 0.90f, 0.82f, 0f)
                    val innerR = v.radius * (0.7f - 0.5f * ((t * 3f) % 1f))
                    drawMesh(ringMesh, v.x, v.y + 0.06f, v.z, -t * 10f, innerR, 1f, innerR, 0.85f, 0.50f, 1f, alpha * 0.85f, 0.90f, 0f)
                    drawMesh(crystalMesh, v.x, 0.75f, v.z, t * 9f, 0.65f, 0.65f, 0.65f, 0.25f, 0.05f, 0.45f, alpha * 0.95f, 0.85f, 0f)
                }
                VfxKind.TOXIC_ZONE, VfxKind.POISON_CLOUD -> {
                    drawMesh(discMesh, v.x, v.y, v.z, 0f, v.radius, 1f, v.radius, r, g, b, alpha * 0.36f, 0.60f, 0f)
                    drawMesh(ringMesh, v.x, v.y + 0.03f, v.z, t * 5f, v.radius, 1f, v.radius, r, g, b, alpha * 0.88f, 0.80f, 0f)
                    drawMesh(ringMesh, v.x, v.y + 0.28f + t * 0.7f, v.z, -t * 4f, v.radius * 0.65f, 1f, v.radius * 0.65f, 0.6f, 1f, 0.2f, alpha * 0.55f, 0.75f, 0f)
                }
                VfxKind.CHAIN_LIGHTNING -> {
                    val dx = v.x2 - v.x
                    val dz = v.z2 - v.z
                    val fullLen = hypot(dx, dz).coerceAtLeast(0.1f)
                    val perpX = -dz / fullLen
                    val perpZ = dx / fullLen
                    val jitter = 0.38f * sin(t * 28f + v.x)
                    val p1x = v.x + dx * 0.33f + perpX * jitter
                    val p1z = v.z + dz * 0.33f + perpZ * jitter
                    val p2x = v.x + dx * 0.67f - perpX * jitter
                    val p2z = v.z + dz * 0.67f - perpZ * jitter
                    drawLightningSegment(v.x, v.y, v.z, p1x, v.y + 0.14f, p1z, r, g, b, alpha)
                    drawLightningSegment(p1x, v.y + 0.14f, p1z, p2x, v.y - 0.08f, p2z, r, g, b, alpha)
                    drawLightningSegment(p2x, v.y - 0.08f, p2z, v.x2, v.y, v.z2, r, g, b, alpha)
                }
                VfxKind.SIPHON_BEAM -> {
                    val dx = v.x2 - v.x
                    val dz = v.z2 - v.z
                    val len = hypot(dx, dz).coerceAtLeast(0.1f)
                    val ang = atan2(dx, dz)
                    val mx = (v.x + v.x2) * 0.5f
                    val mz = (v.z + v.z2) * 0.5f
                    drawMesh(unitBoxMesh, mx, v.y, mz, ang, 0.12f, 0.10f, len, r, g, b, alpha * 0.8f, 0.92f, 0f)
                    val orbX = v.x + dx * t
                    val orbZ = v.z + dz * t
                    drawMesh(crystalMesh, orbX, v.y + 0.15f, orbZ, t * 8f, 0.45f, 0.45f, 0.45f, r, g, b, alpha, 0.95f, 0f)
                }
                VfxKind.BOSS_LINE_TELEGRAPH -> {
                    val mx = (v.x + v.x2) * 0.5f
                    val mz = (v.z + v.z2) * 0.5f
                    val dx = v.x2 - v.x
                    val dz = v.z2 - v.z
                    val len = hypot(dx, dz).coerceAtLeast(0.1f)
                    val ang = atan2(dx, dz)
                    drawMesh(unitBoxMesh, mx, 0.08f, mz, ang, v.radius, 0.12f, len, r, g, b, alpha, 0.90f, 0f)
                }
                VfxKind.DASH_TRAIL -> {
                    drawMesh(ringMesh, v.x, 0.18f + t * 0.55f, v.z, t * 7f, v.radius * (1f - t * 0.25f), 1f, v.radius, r, g, b, alpha * 0.85f, 0.88f, 0f)
                }
                VfxKind.ECHO_HIT -> {
                    val s = v.radius * (0.75f + 0.45f * t)
                    drawMesh(slashArcMesh, v.x, v.y, v.z, t * 6f, s, 1f, s, r, g, b, alpha, 0.95f, 0f)
                    drawMesh(slashArcMesh, v.x, v.y + 0.1f, v.z, t * 6f + 2.2f, s, 1f, s, 1f, 1f, 1f, alpha * 0.8f, 0.95f, 0f)
                    drawMesh(ringMesh, v.x, 0.2f, v.z, 0f, s, 1f, s, r, g, b, alpha * 0.7f, 0.90f, 0f)
                }
                VfxKind.CRIT_BURST -> {
                    val s = v.radius * (0.6f + 0.7f * t)
                    drawMesh(ringMesh, v.x, 0.25f, v.z, t * 5f, s, 1f, s, 1f, 0.82f, 0.15f, alpha, 0.95f, 0f)
                    drawMesh(slashArcMesh, v.x, v.y, v.z, 0.7f + t * 2f, s * 0.95f, 1.3f, s * 0.95f, 1f, 0.22f, 0.15f, alpha, 0.95f, 0f)
                    drawMesh(crystalMesh, v.x, v.y + 0.55f + t * 0.6f, v.z, t * 9f, 0.6f * alpha, 1.6f * alpha, 0.6f * alpha, 1f, 0.90f, 0.25f, alpha, 0.95f, 0f)
                }
                VfxKind.FIRE_BURST -> {
                    val s = v.radius * (0.4f + 0.8f * t)
                    drawMesh(ringMesh, v.x, 0.18f, v.z, t * 6f, s, 1f, s, 1f, 0.40f, 0.06f, alpha, 0.95f, 0f)
                    drawMesh(unitBoxMesh, v.x, 0.6f + t * 0.9f, v.z, t * 8f, 0.48f * alpha, 1.5f * (1f - t * 0.3f), 0.48f * alpha, 1f, 0.58f, 0.12f, alpha * 0.85f, 0.95f, 0f)
                }
                VfxKind.CRYO_NOVA -> {
                    val s = v.radius * (0.35f + 0.85f * t)
                    drawMesh(ringMesh, v.x, 0.15f, v.z, -t * 5f, s, 1f, s, 0.45f, 0.94f, 1f, alpha, 0.95f, 0f)
                    drawMesh(crystalMesh, v.x, 0.45f + t * 0.4f, v.z, t * 6f, 0.65f * alpha, 1.25f * alpha, 0.65f * alpha, 0.75f, 0.98f, 1f, alpha * 0.9f, 0.95f, 0f)
                }
                VfxKind.HEAL_PULSE -> {
                    val s = v.radius * (0.55f + 0.55f * t)
                    drawMesh(ringMesh, v.x, 0.15f + t * 1.4f, v.z, t * 4f, s, 1f, s, r, g, b, alpha, 0.92f, 0f)
                    drawMesh(crystalMesh, v.x, 0.75f + t * 1.3f, v.z, t * 6f, 0.45f, 0.75f, 0.45f, r, g, b, alpha, 0.95f, 0f)
                }
                VfxKind.RAGE_PULSE -> {
                    val s = v.radius * (0.45f + 0.75f * t)
                    drawMesh(ringMesh, v.x, 0.25f + t * 0.7f, v.z, t * 7f, s, 1f, s, 1f, 0.16f, 0.22f, alpha, 0.95f, 0f)
                    drawMesh(slashArcMesh, v.x, 0.85f, v.z, t * 9f, s * 0.8f, 1f, s * 0.8f, 1f, 0.42f, 0.10f, alpha * 0.85f, 0.95f, 0f)
                }
                VfxKind.LEVEL_UP_PILLAR, VfxKind.CARD_EVOLVE_BURST -> {
                    val beamScale = (1f - t * 0.4f)
                    val ringS = v.radius * (0.25f + 0.95f * t)
                    drawMesh(unitBoxMesh, v.x, 2.2f, v.z, t * 5f, 0.85f * beamScale, 4.6f, 0.85f * beamScale, r, g, b, alpha * 0.68f, 0.95f, 0f)
                    drawMesh(ringMesh, v.x, 0.15f, v.z, t * 4f, ringS, 1f, ringS, r, g, b, alpha, 0.95f, 0f)
                    drawMesh(ringMesh, v.x, 0.35f + t * 2.1f, v.z, -t * 6f, v.radius * 0.55f * (1f - t * 0.2f), 1f, v.radius * 0.55f * (1f - t * 0.2f), 1f, 1f, 1f, alpha * 0.9f, 0.95f, 0f)
                }
            }
        }
    }

    private fun drawLightningSegment(
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        r: Float, g: Float, b: Float, alpha: Float
    ) {
        val mx = (x1 + x2) * 0.5f
        val my = (y1 + y2) * 0.5f
        val mz = (z1 + z2) * 0.5f
        val dx = x2 - x1
        val dz = z2 - z1
        val len = hypot(dx, dz).coerceAtLeast(0.08f)
        val ang = atan2(dx, dz)
        drawMesh(unitBoxMesh, mx, my, mz, ang, 0.22f, 0.14f, len, r, g, b, alpha, 0.95f, 0f)
        drawMesh(unitBoxMesh, mx, my, mz, ang, 0.08f, 0.08f, len, 1f, 1f, 1f, alpha, 0.98f, 0f)
    }

    private fun drawGlbHeroPose(
        mesh: GpuMesh,
        px: Float, py: Float, pz: Float,
        facingAngle: Float,
        scale: Float,
        activeClip: HeroMotionClip?,
        clipProgress: Float,
        r: Float, g: Float, b: Float,
        emissive: Float,
        hurtFlash: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, px, py, pz)
        Matrix.rotateM(modelMatrix, 0, facingAngle * (180f / PI.toFloat()), 0f, 1f, 0f)
        Matrix.scaleM(modelMatrix, 0, scale, scale, scale)
        setTintAndEffects(r, g, b, 1f, emissive, hurtFlash)

        val sData = mesh.skinData
        val useSkinning = sData != null && mesh.skinVbo != 0 && shader.maxSkinBones > 0
        if (useSkinning && sData != null) {
            val jointCount = minOf(sData.jointNames.size, shader.maxSkinBones)
            val boneMatrices = fbxLoader.computeSkinMatrices(sData, activeClip, clipProgress, shader.maxSkinBones)
            if (shader.uBoneMatrices >= 0) {
                GLES20.glUniformMatrix4fv(shader.uBoneMatrices, jointCount, false, boneMatrices, 0)
            }
        }
        submitPart(mesh, modelMatrix, useSkinning)
    }

    private fun drawArticulatedCharacter(
        rig: ArticulatedRig,
        x: Float, y: Float, z: Float,
        facingAngle: Float,
        scale: Float,
        walkCycle: Float,
        isMoving: Boolean,
        attackProgress: Float,
        r: Float, g: Float, b: Float,
        hitFlash: Float,
        emissive: Float,
        poseOverride: HeroPoseSnapshot? = null
    ) {
        val pose = poseOverride ?: run {
            val bob = if (isMoving) kotlin.math.abs(sin(walkCycle)) * 0.07f else sin(walkCycle) * 0.02f
            val swing = if (isMoving) sin(walkCycle) * 32f else sin(walkCycle * 0.5f) * 4f
            val atkSwing = sin(attackProgress * PI.toFloat()) * 78f
            HeroPoseSnapshot(
                rootBob = bob,
                armR = BonePose(rotX = -swing - atkSwing),
                armL = BonePose(rotX = swing - atkSwing * 0.4f),
                legR = BonePose(rotX = swing),
                legL = BonePose(rotX = -swing)
            )
        }

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        Matrix.rotateM(modelMatrix, 0, facingAngle * (180f / PI.toFloat()) + pose.rootYawOffset, 0f, 1f, 0f)
        if (pose.rootPitch != 0f) Matrix.rotateM(modelMatrix, 0, pose.rootPitch, 1f, 0f, 0f)
        if (pose.rootRoll != 0f) Matrix.rotateM(modelMatrix, 0, pose.rootRoll, 0f, 0f, 1f)
        Matrix.scaleM(modelMatrix, 0, scale, scale, scale)

        setTintAndEffects(r, g, b, 1f, emissive, hitFlash)

        // Torso
        Matrix.translateM(childMatrix, 0, modelMatrix, 0, 0f, pose.rootBob, 0f)
        submitPart(rig.torso, childMatrix)

        // Head
        Matrix.translateM(childMatrix, 0, modelMatrix, 0, 0f, 1.48f + pose.rootBob, 0f)
        if (pose.head.rotX != 0f) Matrix.rotateM(childMatrix, 0, pose.head.rotX, 1f, 0f, 0f)
        if (pose.head.rotY != 0f) Matrix.rotateM(childMatrix, 0, pose.head.rotY, 0f, 1f, 0f)
        if (pose.head.rotZ != 0f) Matrix.rotateM(childMatrix, 0, pose.head.rotZ, 0f, 0f, 1f)
        submitPart(rig.head, childMatrix)

        // Right Arm (weapon hand)
        Matrix.translateM(childMatrix, 0, modelMatrix, 0, -0.44f, 1.32f + pose.rootBob, 0f)
        if (pose.armR.rotX != 0f) Matrix.rotateM(childMatrix, 0, pose.armR.rotX, 1f, 0f, 0f)
        if (pose.armR.rotY != 0f) Matrix.rotateM(childMatrix, 0, pose.armR.rotY, 0f, 1f, 0f)
        if (pose.armR.rotZ != 0f) Matrix.rotateM(childMatrix, 0, pose.armR.rotZ, 0f, 0f, 1f)
        submitPart(rig.armRight, childMatrix)

        // Left Arm (off-hand / shield)
        Matrix.translateM(childMatrix, 0, modelMatrix, 0, 0.44f, 1.32f + pose.rootBob, 0f)
        if (pose.armL.rotX != 0f) Matrix.rotateM(childMatrix, 0, pose.armL.rotX, 1f, 0f, 0f)
        if (pose.armL.rotY != 0f) Matrix.rotateM(childMatrix, 0, pose.armL.rotY, 0f, 1f, 0f)
        if (pose.armL.rotZ != 0f) Matrix.rotateM(childMatrix, 0, pose.armL.rotZ, 0f, 0f, 1f)
        submitPart(rig.armLeft, childMatrix)

        // Right Leg
        Matrix.translateM(childMatrix, 0, modelMatrix, 0, -0.19f, 0.62f, 0f)
        if (pose.legR.rotX != 0f) Matrix.rotateM(childMatrix, 0, pose.legR.rotX, 1f, 0f, 0f)
        if (pose.legR.rotZ != 0f) Matrix.rotateM(childMatrix, 0, pose.legR.rotZ, 0f, 0f, 1f)
        submitPart(rig.legRight, childMatrix)

        // Left Leg
        Matrix.translateM(childMatrix, 0, modelMatrix, 0, 0.19f, 0.62f, 0f)
        if (pose.legL.rotX != 0f) Matrix.rotateM(childMatrix, 0, pose.legL.rotX, 1f, 0f, 0f)
        if (pose.legL.rotZ != 0f) Matrix.rotateM(childMatrix, 0, pose.legL.rotZ, 0f, 0f, 1f)
        submitPart(rig.legLeft, childMatrix)
    }

    private fun drawWorldBar(wx: Float, wy: Float, wz: Float, width: Float, ratio: Float, r: Float, g: Float, b: Float) {
        // Dark backing bar
        drawMesh(unitBoxMesh, wx, wy, wz, 0.785f, width + 0.08f, 0.14f, 0.08f, 0.08f, 0.09f, 0.12f, 0.88f, 0.25f, 0f)
        // Fill bar
        val fillW = (width * ratio).coerceAtLeast(0.02f)
        val offset = -(width - fillW) * 0.5f
        val ox = cos(0.785f) * offset
        val oz = -sin(0.785f) * offset
        drawMesh(unitBoxMesh, wx + ox, wy, wz + oz, 0.785f, fillW, 0.11f, 0.10f, r, g, b, 1f, 0.75f, 0f)
    }

    private fun drawMesh(
        mesh: GpuMesh,
        x: Float, y: Float, z: Float,
        rotYRad: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float, a: Float,
        emissive: Float,
        hitFlash: Float
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, x, y, z)
        if (rotYRad != 0f) {
            Matrix.rotateM(modelMatrix, 0, rotYRad * (180f / PI.toFloat()), 0f, 1f, 0f)
        }
        Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
        setTintAndEffects(r, g, b, a, emissive, hitFlash)
        submitPart(mesh, modelMatrix)
    }

    private fun submitPart(mesh: GpuMesh, mat: FloatArray, useSkinning: Boolean = false) {
        Matrix.multiplyMM(scratchMatrix, 0, viewMatrix, 0, mat, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, scratchMatrix, 0)
        GLES20.glUniformMatrix4fv(shader.uModelMatrix, 1, false, mat, 0)
        GLES20.glUniformMatrix4fv(shader.uMvpMatrix, 1, false, mvpMatrix, 0)
        if (shader.uUseSkinning >= 0) {
            GLES20.glUniform1f(shader.uUseSkinning, if (useSkinning) 1f else 0f)
        }
        if (mesh.textureId != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mesh.textureId)
            GLES20.glUniform1i(shader.uTexture, 0)
            GLES20.glUniform1f(shader.uUseTexture, 1f)
        } else {
            GLES20.glUniform1f(shader.uUseTexture, 0f)
        }
        mesh.draw()
    }

    private fun setGlobalUniforms(eyeX: Float, eyeY: Float, eyeZ: Float, fogDensity: Float) {
        GLES20.glUniform3f(shader.uLightDir, 0.48f, 0.85f, 0.35f)
        GLES20.glUniform3f(shader.uCameraPos, eyeX, eyeY, eyeZ)
        GLES20.glUniform3f(shader.uFogColor, 0.068f, 0.052f, 0.056f)
        GLES20.glUniform1f(shader.uFogDensity, fogDensity)
    }

    private fun setTintAndEffects(r: Float, g: Float, b: Float, a: Float, emissive: Float, hitFlash: Float) {
        GLES20.glUniform4f(shader.uTintColor, r, g, b, a)
        GLES20.glUniform1f(shader.uEmissive, emissive)
        GLES20.glUniform1f(shader.uHitFlash, hitFlash)
    }

    private fun skinTint(skin: SkinVariant): FloatArray = when (skin) {
        SkinVariant.DEFAULT -> floatArrayOf(1f, 1f, 1f)
        SkinVariant.CRIMSON_ECLIPSE -> floatArrayOf(1.05f, 0.72f, 0.76f)
        SkinVariant.VOID_COBALT -> floatArrayOf(0.75f, 0.94f, 1.12f)
        SkinVariant.SOLAR_APEX -> floatArrayOf(1.10f, 0.98f, 0.72f)
    }

    private fun hexToRgb(hex: Long): Triple<Float, Float, Float> {
        val r = ((hex shr 16) and 0xFF) / 255f
        val g = ((hex shr 8) and 0xFF) / 255f
        val b = (hex and 0xFF) / 255f
        return Triple(r, g, b)
    }
}
