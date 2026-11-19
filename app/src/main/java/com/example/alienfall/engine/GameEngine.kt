package com.example.alienfall.engine

import com.example.alienfall.audio.MusicMode
import com.example.alienfall.audio.SfxType
import com.example.alienfall.audio.SoundEngine
import com.example.alienfall.model.AlienType
import com.example.alienfall.model.AllyUnit
import com.example.alienfall.model.AttackStyle
import com.example.alienfall.model.BossInstance
import com.example.alienfall.model.BossMilestones
import com.example.alienfall.model.BossType
import com.example.alienfall.model.EnemyInstance
import com.example.alienfall.model.EnemyState
import com.example.alienfall.model.EquipmentItem
import com.example.alienfall.model.EvolutionCardCatalog
import com.example.alienfall.model.EvolutionCardDef
import com.example.alienfall.model.FloatingNumber
import com.example.alienfall.model.HeroClass
import com.example.alienfall.model.HudSnapshot
import com.example.alienfall.model.PermanentUpgradeType
import com.example.alienfall.model.PersistentProgress
import com.example.alienfall.model.PickupInstance
import com.example.alienfall.model.PickupKind
import com.example.alienfall.model.ProjectileInstance
import com.example.alienfall.model.ProjectileKind
import com.example.alienfall.model.SkinVariant
import com.example.alienfall.model.VfxKind
import com.example.alienfall.model.VisualEffectInstance
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class RunPhase {
    RUNNING,
    PAUSED,
    CARD_CHOICE,
    GAME_OVER,
    VICTORY
}

class GameEngine(
    private val soundEngine: SoundEngine
) {
    val mapManager = MapChunkManager()
    val stateLock = Any()

    // Pools
    val projectiles = Array(180) { ProjectileInstance() }
    val vfxList = Array(220) { VisualEffectInstance() }
    val pickups = Array(160) { PickupInstance() }

    val enemies = ArrayList<EnemyInstance>(110)
    val bosses = ArrayList<BossInstance>(4)
    val allies = ArrayList<AllyUnit>(12)
    val floatingNumbers = CopyOnWriteArrayList<FloatingNumber>()

    // Pending delayed effects (Echo hits, Meteors)
    private data class DelayedEcho(val targetUid: Int, val isBoss: Boolean, var timer: Float, val damage: Float)
    private data class PendingMeteor(val x: Float, val z: Float, var timer: Float, val damage: Float)
    private data class GroundHazard(val x: Float, val z: Float, val radius: Float, var timer: Float, val isToxicBoss: Boolean, val slowFactor: Float)

    private val delayedEchoes = ArrayList<DelayedEcho>()
    private val pendingMeteors = ArrayList<PendingMeteor>()
    private val groundHazards = ArrayList<GroundHazard>()

    // Player & Run State
    @Volatile var phase: RunPhase = RunPhase.RUNNING
    @Volatile var hero: HeroClass = HeroClass.BERSERKER
    @Volatile var skin: SkinVariant = SkinVariant.DEFAULT
    private var progress: PersistentProgress = PersistentProgress()

    var playerX = 0f
    var playerZ = 0f
    var facingAngle = 0f
    var aimAngle = 0f
    var isMoving = false
    var isDashing = false
    var animTime = 0f
    var attackAnimTimer = 0f
    var hurtAnimTimer = 0f
    var cameraShake = 0f

    var hp = 150f
    var maxHp = 150f
    var shield = 0f
    var maxShield = 0f
    private var shieldOutOfCombatTimer = 0f

    var level = 1
    var xp = 0
    var xpToNext = 40
    var kills = 0
    var coinsCollected = 0
    var bossesDefeated = 0
    var survivalSeconds = 0f

    private var dashTimer = 0f
    private var dashCooldownTimer = 0f
    private var dashDirX = 0f
    private var dashDirZ = 1f

    private var attackTimer = 0f
    private var specialCooldownTimer = 0f
    var specialActiveTimer = 0f

    private var spawnTimer = 0.6f
    private var meteorTimer = 5.0f
    private var repulsorTimer = 4.5f
    private var gravityTimer = 7.0f
    private var poisonAuraTickTimer = 0.4f
    private var regenVfxTimer = 0f
    private var speedTrailTimer = 0f
    private var magnetSparkTimer = 0f
    private var reactiveArmorCooldown = 0f
    private var vampirismRateTimer = 0f
    private var killFuryStacks = 0
    private var killFuryDecayTimer = 0f
    private var poiBuffDamageTimer = 0f
    private var rebirthUsed = false

    val furyStacksCount: Int get() = killFuryStacks
    val isRebirthReady: Boolean get() = (acquiredCards[30] ?: 0) > 0 && !rebirthUsed

    private var nextUid = 1
    private val triggeredMilestones = HashSet<Int>()
    private val pendingLevelQueue = ArrayDeque<Boolean>() // true = boss high-rarity choice

    var currentCardChoices: List<EvolutionCardDef> = emptyList()
        private set

    val acquiredCards = LinkedHashMap<Int, Int>()

    // Input state
    @Volatile var moveInputX = 0f
    @Volatile var moveInputZ = 0f
    @Volatile var manualAimActive = false
    @Volatile var manualAimX = 0f
    @Volatile var manualAimZ = 0f
    @Volatile var manualAimEnabled = false

    private var bossBannerText: String? = null
    private var bossBannerTimer = 0f
    private var poiBannerText: String? = null
    private var poiBannerTimer = 0f

    fun startNewRun(heroClass: HeroClass, skinVariant: SkinVariant, persistentProgress: PersistentProgress) {
        synchronized(stateLock) {
            progress = persistentProgress
            hero = heroClass
            skin = skinVariant
            manualAimEnabled = persistentProgress.settings.manualAimDefault

            mapManager.clear()
            mapManager.updateAround(0f, 0f, persistentProgress.settings.graphicsQuality.chunkRadius)

            enemies.clear()
            bosses.clear()
            allies.clear()
            floatingNumbers.clear()
            delayedEchoes.clear()
            pendingMeteors.clear()
            groundHazards.clear()
            acquiredCards.clear()
            triggeredMilestones.clear()
            pendingLevelQueue.clear()
            currentCardChoices = emptyList()

            for (p in projectiles) p.active = false
            for (v in vfxList) v.active = false
            for (pk in pickups) pk.active = false

            playerX = 0f
            playerZ = 0f
            facingAngle = 0f
            aimAngle = 0f
            isMoving = false
            isDashing = false
            animTime = 0f
            attackAnimTimer = 0f
            hurtAnimTimer = 0f
            cameraShake = 0f

            val vitalLvl = persistentProgress.permanentUpgrades[PermanentUpgradeType.VITALITY.id] ?: 0
            val equip = EquipmentItem.fromId(persistentProgress.equippedItemId)
            val equipHp = if (equip == EquipmentItem.AEGIS_MODULE) 25f else 0f
            maxHp = heroClass.baseHp + vitalLvl * 15f + equipHp
            hp = maxHp

            maxShield = if (equip == EquipmentItem.AEGIS_MODULE) 15f else 0f
            shield = maxShield
            shieldOutOfCombatTimer = 0f

            level = 1
            xp = 0
            xpToNext = 40
            kills = 0
            coinsCollected = 0
            bossesDefeated = 0
            survivalSeconds = 0f

            dashTimer = 0f
            dashCooldownTimer = 0f
            attackTimer = 0.1f
            specialCooldownTimer = 0f
            specialActiveTimer = 0f
            spawnTimer = 0.5f
            meteorTimer = 4.5f
            repulsorTimer = 4.0f
            gravityTimer = 6.0f
            poisonAuraTickTimer = 0.35f
            regenVfxTimer = 0f
            speedTrailTimer = 0f
            magnetSparkTimer = 0f
            reactiveArmorCooldown = 0f
            vampirismRateTimer = 0f
            killFuryStacks = 0
            killFuryDecayTimer = 0f
            poiBuffDamageTimer = 0f
            rebirthUsed = false
            bossBannerText = null
            bossBannerTimer = 0f
            poiBannerText = null
            poiBannerTimer = 0f

            // Tecnologico starts with 1 baseline combat drone
            if (heroClass == HeroClass.TECNOLOGICO) {
                allies.add(AllyUnit(nextUid++, isTurret = false, x = 1.6f, z = 0f, orbitAngle = 0f, fireTimer = 0.2f, lifeTimer = -1f, damage = 9f))
            }

            phase = RunPhase.RUNNING
            soundEngine.musicMode = MusicMode.EXPLORE
        }
    }

    fun triggerDash() {
        synchronized(stateLock) {
            if (phase != RunPhase.RUNNING || isDashing || dashCooldownTimer > 0f) return
            val dx: Float
            val dz: Float
            val mag = hypot(moveInputX, moveInputZ)
            if (mag > 0.15f) {
                dx = moveInputX / mag
                dz = moveInputZ / mag
            } else {
                dx = sin(facingAngle)
                dz = cos(facingAngle)
            }
            dashDirX = dx
            dashDirZ = dz
            isDashing = true
            dashTimer = 0.18f

            val equip = EquipmentItem.fromId(progress.equippedItemId)
            var cd = 1.85f
            if (hero == HeroClass.SAMURAI_NEON) cd *= 0.85f
            if (equip == EquipmentItem.CHRONO_BOOSTER) cd *= 0.82f
            dashCooldownTimer = cd

            spawnVfx(VfxKind.DASH_TRAIL, playerX, 0.5f, playerZ, radius = 1.0f, duration = 0.35f, colorHex = skin.accentHex)
            soundEngine.playSfx(SfxType.DASH)

            // Samurai Neon passive: dash slash damages nearby enemies
            if (hero == HeroClass.SAMURAI_NEON) {
                val skillMult = 1f + (acquiredCards[29] ?: 0) * 0.15f
                val dmg = computePlayerDamage() * 1.1f * skillMult
                damageEnemiesInRadius(playerX, playerZ, 2.8f, dmg, knockback = 2.0f)
                spawnVfx(VfxKind.SLASH_ARC, playerX, 0.9f, playerZ, radius = 2.8f, angle = facingAngle, duration = 0.25f, colorHex = 0xFF00E5FF)
            }
        }
    }

    fun triggerSpecial() {
        synchronized(stateLock) {
            if (phase != RunPhase.RUNNING || specialCooldownTimer > 0f) return
            val cdReduction = computeCooldownReduction()
            specialCooldownTimer = (hero.specialCooldown * (1f - cdReduction)).coerceAtLeast(3.5f)
            soundEngine.playSfx(SfxType.SPECIAL)
            val skillMult = 1f + (acquiredCards[29] ?: 0) * 0.15f

            when (hero) {
                HeroClass.BERSERKER -> {
                    specialActiveTimer = 6.0f
                    spawnVfx(VfxKind.SHOCKWAVE_RING, playerX, 0.2f, playerZ, radius = 3.8f, duration = 0.45f, colorHex = 0xFFFF6B1A)
                }
                HeroClass.TECNOLOGICO -> {
                    for (i in 0 until 3) {
                        val a = (i * 2f * PI.toFloat() / 3f)
                        allies.add(
                            AllyUnit(
                                uid = nextUid++,
                                isTurret = false,
                                x = playerX + cos(a) * 2.2f,
                                z = playerZ + sin(a) * 2.2f,
                                orbitAngle = a,
                                fireTimer = 0.1f * i,
                                lifeTimer = 10f,
                                damage = 11f * skillMult
                            )
                        )
                    }
                    spawnVfx(VfxKind.SHOCKWAVE_RING, playerX, 0.3f, playerZ, radius = 3.0f, duration = 0.4f, colorHex = 0xFF00E5FF)
                }
                HeroClass.ATIRADOR -> {
                    val baseAngle = aimAngle
                    val count = 11
                    val dmg = computePlayerDamage() * 1.15f * skillMult
                    for (i in 0 until count) {
                        val offset = (i - count / 2) * 0.11f
                        val a = baseAngle + offset
                        spawnProjectile(
                            isPlayer = true, kind = ProjectileKind.BULLET,
                            x = playerX, z = playerZ,
                            vx = sin(a) * 22f, vz = cos(a) * 22f,
                            damage = dmg, pierce = 2 + (acquiredCards[12] ?: 0),
                            colorHex = 0xFFFFD166
                        )
                    }
                }
                HeroClass.MAGO -> {
                    specialActiveTimer = 5.0f
                    val dmg = computePlayerDamage() * 1.9f * skillMult
                    damageEnemiesInRadius(playerX, playerZ, 5.8f, dmg, knockback = 2.5f)
                    spawnVfx(VfxKind.SHOCKWAVE_RING, playerX, 0.3f, playerZ, radius = 5.8f, duration = 0.6f, colorHex = 0xFFC084FC)
                }
                HeroClass.BRUXO -> {
                    val dmg = computePlayerDamage() * 1.2f * skillMult
                    val durBonus = 1.2f
                    for (e in enemies) {
                        if (hypot(e.x - playerX, e.z - playerZ) <= 7.5f) {
                            e.poisonTimer = 5.0f * durBonus
                            e.poisonDps = 8f * skillMult
                            applyDamageToEnemy(e, dmg, isCrit = false)
                        }
                    }
                    for (b in bosses) {
                        if (hypot(b.x - playerX, b.z - playerZ) <= 8.5f) {
                            b.poisonTimer = 5.0f * durBonus
                            b.poisonDps = 12f * skillMult
                            applyDamageToBoss(b, dmg, isCrit = false)
                        }
                    }
                    spawnVfx(VfxKind.POISON_CLOUD, playerX, 0.3f, playerZ, radius = 7.5f, duration = 0.8f, colorHex = 0xFF76FF03)
                }
                HeroClass.GUERREIRO -> {
                    val dmg = computePlayerDamage() * 2.2f * skillMult
                    damageEnemiesInRadius(playerX, playerZ, 5.2f, dmg, knockback = 6.5f)
                    spawnVfx(VfxKind.SHOCKWAVE_RING, playerX, 0.25f, playerZ, radius = 5.2f, duration = 0.55f, colorHex = 0xFF38BDF8)
                    cameraShake = 0.25f
                }
                HeroClass.ARQUEIRO -> {
                    val arrows = 12
                    val dmg = computePlayerDamage() * 1.25f * skillMult
                    for (i in 0 until arrows) {
                        val a = (i * 2f * PI.toFloat() / arrows)
                        spawnProjectile(
                            isPlayer = true, kind = ProjectileKind.STAR_ARROW,
                            x = playerX, z = playerZ,
                            vx = sin(a) * 19f, vz = cos(a) * 19f,
                            damage = dmg, pierce = 2 + (acquiredCards[12] ?: 0),
                            colorHex = 0xFF00E5FF
                        )
                    }
                }
                HeroClass.GLADIADOR -> {
                    specialActiveTimer = 5.0f
                    val dmg = computePlayerDamage() * 2.0f * skillMult
                    damageEnemiesInRadius(playerX, playerZ, 4.6f, dmg, knockback = 5.5f)
                    shield = (shield + 25f).coerceAtMost(maxShield + 25f)
                    spawnVfx(VfxKind.SHOCKWAVE_RING, playerX, 0.25f, playerZ, radius = 4.6f, duration = 0.5f, colorHex = 0xFFFFD166)
                }
                HeroClass.SAMURAI_NEON -> {
                    val dmg = computePlayerDamage() * 2.4f * skillMult
                    damageEnemiesInRadius(playerX, playerZ, 5.0f, dmg, knockback = 3.0f, forceCrit = true)
                    spawnVfx(VfxKind.SLASH_ARC, playerX, 0.9f, playerZ, radius = 5.0f, angle = facingAngle, duration = 0.35f, colorHex = 0xFF00E5FF)
                    spawnVfx(VfxKind.SLASH_ARC, playerX, 0.9f, playerZ, radius = 5.0f, angle = facingAngle + 2.1f, duration = 0.35f, colorHex = 0xFFFF2E4C)
                }
                HeroClass.INVENTOR -> {
                    allies.add(
                        AllyUnit(
                            uid = nextUid++,
                            isTurret = true,
                            x = playerX + sin(facingAngle) * 1.5f,
                            z = playerZ + cos(facingAngle) * 1.5f,
                            orbitAngle = 0f,
                            fireTimer = 0.1f,
                            lifeTimer = 10f,
                            damage = 14f * skillMult
                        )
                    )
                    spawnVfx(VfxKind.POISON_CLOUD, playerX, 0.25f, playerZ, radius = 3.5f, duration = 0.5f, colorHex = 0xFF76FF03)
                }
            }
        }
    }

    fun selectEvolutionCard(card: EvolutionCardDef?) {
        synchronized(stateLock) {
            if (phase != RunPhase.CARD_CHOICE) return
            if (card != null) {
                val nextLvl = ((acquiredCards[card.id] ?: 0) + 1).coerceAtMost(card.maxLevel)
                acquiredCards[card.id] = nextLvl
                onCardApplied(card, nextLvl)
                soundEngine.playSfx(SfxType.CARD_SELECT)
            } else {
                // Alternative reward when all 30 cards are maxed
                coinsCollected += 50
                hp = (hp + maxHp * 0.35f).coerceAtMost(maxHp)
                spawnVfx(VfxKind.HEAL_PULSE, playerX, 0.2f, playerZ, radius = 2.8f, duration = 0.6f, colorHex = 0xFF22FF88)
                soundEngine.playSfx(SfxType.PICKUP_COIN)
            }

            if (pendingLevelQueue.isNotEmpty()) {
                val preferHigh = pendingLevelQueue.removeFirst()
                val choices = EvolutionCardCatalog.drawChoices(acquiredCards, 3, preferHigh)
                currentCardChoices = choices
                phase = RunPhase.CARD_CHOICE
            } else {
                currentCardChoices = emptyList()
                phase = RunPhase.RUNNING
                soundEngine.musicMode = if (bosses.isNotEmpty()) MusicMode.BOSS else MusicMode.COMBAT
            }
        }
    }

    internal fun applyCardForTest(cardId: Int) {
        val card = EvolutionCardCatalog.byId(cardId) ?: return
        synchronized(stateLock) {
            val nextLvl = ((acquiredCards[card.id] ?: 0) + 1).coerceAtMost(card.maxLevel)
            acquiredCards[card.id] = nextLvl
            onCardApplied(card, nextLvl)
        }
    }

    private fun onCardApplied(card: EvolutionCardDef, level: Int) {
        val cardColor = cardPowerColor(card.id)
        // Dramatic power burst immediately visible upon returning to combat
        spawnVfx(VfxKind.CARD_EVOLVE_BURST, playerX, 0.15f, playerZ, radius = 3.6f, duration = 0.78f, colorHex = cardColor)
        spawnVfx(VfxKind.SHOCKWAVE_RING, playerX, 0.22f, playerZ, radius = 4.2f, duration = 0.55f, colorHex = cardColor)
        spawnFloatingText(playerX, playerZ, "${card.name} Nv.$level!", cardColor)

        when (card.id) {
            6 -> {
                maxHp += 20f
                hp = (hp + 20f).coerceAtMost(maxHp)
                spawnVfx(VfxKind.HEAL_PULSE, playerX, 0.2f, playerZ, radius = 2.6f, duration = 0.65f, colorHex = 0xFF22FF88)
                spawnFloatingText(playerX, playerZ - 0.5f, "+20 HP MÁX!", 0xFF22FF88)
            }
            17 -> {
                val equip = EquipmentItem.fromId(progress.equippedItemId)
                val aegisBonus = if (equip == EquipmentItem.AEGIS_MODULE) 15f else 0f
                maxShield = aegisBonus + 20f + level * 15f
                shield = maxShield
                spawnVfx(VfxKind.SHOCKWAVE_RING, playerX, 0.6f, playerZ, radius = 2.4f, duration = 0.5f, colorHex = 0xFF38BDF8)
            }
            19 -> {
                val dmg = 10f * (1f + (acquiredCards[29] ?: 0) * 0.15f)
                allies.add(
                    AllyUnit(
                        uid = nextUid++,
                        isTurret = false,
                        x = playerX + 1.5f,
                        z = playerZ,
                        orbitAngle = allies.size * 1.4f,
                        fireTimer = 0.2f,
                        lifeTimer = -1f,
                        damage = dmg
                    )
                )
            }
        }
    }

    private fun cardPowerColor(cardId: Int): Long = when (cardId) {
        1, 6, 27 -> 0xFF22FF88 // Bio / Vitality emerald
        2, 9, 24 -> 0xFFFF2E4C // Crimson / Rage / Brute
        3, 8, 12, 17, 19, 22 -> 0xFF00E5FF // Cyber / Lightning / Shield / Drone
        4, 10, 29, 30 -> 0xFFFFD166 // Gold / Crit / Supreme / Solar
        5 -> 0xFFCBD5E1 // Titanium steel
        7, 11, 25 -> 0xFFC084FC // Speed / Multi / Temporal Echo
        13, 23 -> 0xFF76FF03 // Toxic Venom / Viral
        14, 20, 21 -> 0xFFFF6B1A // Flames / Blades / Meteor
        15, 28 -> 0xFF38BDF8 // Electric Chain / Reactive
        16, 26 -> 0xFFD946EF // Plasma / Gravity Vortex
        18 -> 0xFF67E8F9 // Cryo Frost
        else -> 0xFF00E5FF
    }

    fun setPaused(paused: Boolean) {
        synchronized(stateLock) {
            if (phase == RunPhase.RUNNING && paused) {
                phase = RunPhase.PAUSED
            } else if (phase == RunPhase.PAUSED && !paused) {
                phase = RunPhase.RUNNING
            }
        }
    }

    fun continueEndlessAfterVictory() {
        synchronized(stateLock) {
            if (phase == RunPhase.VICTORY) {
                phase = RunPhase.RUNNING
                soundEngine.musicMode = MusicMode.COMBAT
            }
        }
    }

    fun step(rawDt: Float) {
        val dt = rawDt.coerceIn(0.001f, 0.05f)
        synchronized(stateLock) {
            if (phase != RunPhase.RUNNING) return

            survivalSeconds += dt
            animTime += dt
            if (attackAnimTimer > 0f) attackAnimTimer = (attackAnimTimer - dt).coerceAtLeast(0f)
            if (hurtAnimTimer > 0f) hurtAnimTimer = (hurtAnimTimer - dt).coerceAtLeast(0f)
            if (cameraShake > 0f) cameraShake = (cameraShake - dt * 2.2f).coerceAtLeast(0f)
            if (reactiveArmorCooldown > 0f) reactiveArmorCooldown = (reactiveArmorCooldown - dt).coerceAtLeast(0f)
            if (vampirismRateTimer > 0f) vampirismRateTimer = (vampirismRateTimer - dt).coerceAtLeast(0f)
            if (poiBuffDamageTimer > 0f) poiBuffDamageTimer = (poiBuffDamageTimer - dt).coerceAtLeast(0f)
            if (bossBannerTimer > 0f) {
                bossBannerTimer -= dt
                if (bossBannerTimer <= 0f) bossBannerText = null
            }
            if (poiBannerTimer > 0f) {
                poiBannerTimer -= dt
                if (poiBannerTimer <= 0f) poiBannerText = null
            }

            if (killFuryDecayTimer > 0f) {
                killFuryDecayTimer -= dt
                if (killFuryDecayTimer <= 0f) killFuryStacks = 0
            }

            // Cooldowns
            if (dashCooldownTimer > 0f) dashCooldownTimer = (dashCooldownTimer - dt).coerceAtLeast(0f)
            if (specialCooldownTimer > 0f) specialCooldownTimer = (specialCooldownTimer - dt).coerceAtLeast(0f)
            if (specialActiveTimer > 0f) {
                specialActiveTimer = (specialActiveTimer - dt).coerceAtLeast(0f)
                // Mage Cosmic Storm periodic pulse
                if (hero == HeroClass.MAGO && ((animTime * 4f).toInt() != ((animTime - dt) * 4f).toInt())) {
                    val skillMult = 1f + (acquiredCards[29] ?: 0) * 0.15f
                    val dmg = computePlayerDamage() * 0.7f * skillMult
                    damageEnemiesInRadius(playerX, playerZ, 5.2f, dmg, knockback = 0.5f)
                    spawnVfx(VfxKind.CHAIN_LIGHTNING, playerX, 0.8f, playerZ, x2 = playerX + (Random.nextFloat() - 0.5f) * 6f, z2 = playerZ + (Random.nextFloat() - 0.5f) * 6f, duration = 0.18f, colorHex = 0xFFC084FC)
                }
            }

            updatePlayerMovement(dt)
            mapManager.updateAround(playerX, playerZ, progress.settings.graphicsQuality.chunkRadius)
            checkPois()
            updatePassiveCardSystems(dt)
            updatePlayerAttack(dt)
            updateAllies(dt)
            updateProjectiles(dt)
            updateSpawning(dt)
            updateEnemies(dt)
            updateBosses(dt)
            updateGroundHazards(dt)
            updatePickups(dt)
            updateVfxAndText(dt)

            // Dynamic music mode
            soundEngine.musicMode = when {
                bosses.isNotEmpty() -> MusicMode.BOSS
                enemies.size >= 5 -> MusicMode.COMBAT
                else -> MusicMode.EXPLORE
            }
        }
    }

    private fun updatePlayerMovement(dt: Float) {
        val speed = computeMoveSpeed()
        if (isDashing) {
            dashTimer -= dt
            val dashSpeed = speed * 3.1f
            val (nx, nz) = mapManager.resolveMovementCollision(
                playerX + dashDirX * dashSpeed * dt,
                playerZ + dashDirZ * dashSpeed * dt,
                0.55f
            )
            playerX = nx
            playerZ = nz
            isMoving = true
            if (dashTimer <= 0f) isDashing = false
        } else {
            val mag = hypot(moveInputX, moveInputZ)
            if (mag > 0.08f) {
                val nxDir = moveInputX / mag.coerceAtLeast(1f)
                val nzDir = moveInputZ / mag.coerceAtLeast(1f)
                val (nx, nz) = mapManager.resolveMovementCollision(
                    playerX + nxDir * speed * dt,
                    playerZ + nzDir * speed * dt,
                    0.55f
                )
                playerX = nx
                playerZ = nz
                facingAngle = atan2(nxDir, nzDir)
                isMoving = true
                // Card 07: Supernatural Speed afterimage trail VFX
                val speedCard = acquiredCards[7] ?: 0
                if (speedCard > 0) {
                    speedTrailTimer -= dt
                    if (speedTrailTimer <= 0f) {
                        speedTrailTimer = (0.28f - speedCard * 0.03f).coerceAtLeast(0.14f)
                        spawnVfx(VfxKind.DASH_TRAIL, playerX, 0.35f, playerZ, radius = 0.75f + speedCard * 0.1f, duration = 0.28f, colorHex = 0xFFC084FC)
                    }
                }
            } else {
                isMoving = false
            }
        }

        // Update aim angle
        if (manualAimEnabled && manualAimActive && hypot(manualAimX, manualAimZ) > 0.15f) {
            aimAngle = atan2(manualAimX, manualAimZ)
        } else {
            val nearest = findNearestTarget(playerX, playerZ, hero.attackRange + 5f)
            aimAngle = if (nearest != null) {
                atan2(nearest.first - playerX, nearest.second - playerZ)
            } else {
                facingAngle
            }
        }
    }

    private fun checkPois() {
        for (poi in mapManager.allPois()) {
            if (!poi.discovered && hypot(poi.x - playerX, poi.z - playerZ) < 2.6f) {
                mapManager.markPoiDiscovered(poi.id)
                poiBannerText = "${poi.kind.title}: ${poi.kind.rewardText}"
                poiBannerTimer = 3.5f
                soundEngine.playSfx(SfxType.LEVEL_UP)
                when (poi.kind) {
                    com.example.alienfall.model.PoiKind.SUPPLY_POD -> {
                        coinsCollected += 25
                        grantXp(40)
                    }
                    com.example.alienfall.model.PoiKind.ANCIENT_MONOLITH -> {
                        poiBuffDamageTimer = 20f
                        grantXp(50)
                    }
                    com.example.alienfall.model.PoiKind.MED_STATION -> {
                        hp = (hp + maxHp * 0.45f).coerceAtMost(maxHp)
                    }
                    com.example.alienfall.model.PoiKind.ORBITAL_RELAY -> {
                        coinsCollected += 35
                        specialCooldownTimer = 0f
                    }
                }
            }
        }
    }

    private fun updatePassiveCardSystems(dt: Float) {
        val skillMult = 1f + (acquiredCards[29] ?: 0) * 0.15f
        if (magnetSparkTimer > 0f) magnetSparkTimer = (magnetSparkTimer - dt).coerceAtLeast(0f)

        // Card 01: Cellular Regeneration
        val regenLvl = acquiredCards[1] ?: 0
        if (regenLvl > 0 && hp < maxHp) {
            val regenPerSec = 2f + (regenLvl - 1) * 1f
            hp = (hp + regenPerSec * dt).coerceAtMost(maxHp)
            regenVfxTimer -= dt
            if (regenVfxTimer <= 0f) {
                regenVfxTimer = 1.15f
                spawnVfx(VfxKind.HEAL_PULSE, playerX, 0.2f, playerZ, radius = 1.6f, duration = 0.5f, colorHex = 0xFF22FF88)
                spawnFloatingText(playerX, playerZ, "+${regenPerSec.toInt()} HP", 0xFF22FF88)
            }
        }

        // Card 17: Regenerative Shield
        if (maxShield > 0f) {
            shieldOutOfCombatTimer += dt
            if (shieldOutOfCombatTimer >= 4.0f && shield < maxShield) {
                val wasLow = shield < maxShield * 0.99f
                shield = (shield + 12f * dt).coerceAtMost(maxShield)
                if (wasLow && shield >= maxShield) {
                    spawnVfx(VfxKind.SHOCKWAVE_RING, playerX, 0.65f, playerZ, radius = 2.0f, duration = 0.45f, colorHex = 0xFF38BDF8)
                }
            }
        }

        // Card 13: Poison Aura
        val poisonAuraLvl = acquiredCards[13] ?: 0
        if (poisonAuraLvl > 0) {
            poisonAuraTickTimer -= dt
            if (poisonAuraTickTimer <= 0f) {
                poisonAuraTickTimer = 0.55f
                val auraR = 3.2f + poisonAuraLvl * 0.45f
                val dps = (4f + poisonAuraLvl * 3f) * skillMult
                val durMult = if (hero == HeroClass.BRUXO) 1.2f else 1f
                var hitAny = false
                for (e in enemies) {
                    if (hypot(e.x - playerX, e.z - playerZ) <= auraR) {
                        e.poisonTimer = 3.0f * durMult
                        e.poisonDps = maxOf(e.poisonDps, dps)
                        hitAny = true
                    }
                }
                for (b in bosses) {
                    if (hypot(b.x - playerX, b.z - playerZ) <= auraR + b.type.radius) {
                        b.poisonTimer = 3.0f * durMult
                        b.poisonDps = maxOf(b.poisonDps, dps)
                        hitAny = true
                    }
                }
                if (hitAny) {
                    spawnVfx(VfxKind.POISON_CLOUD, playerX, 0.14f, playerZ, radius = auraR, duration = 0.45f, colorHex = 0xFF76FF03)
                }
            }
        }

        // Card 20: Orbital Blades
        val bladesLvl = acquiredCards[20] ?: 0
        if (bladesLvl > 0) {
            val bladeCount = bladesLvl + 1
            val orbitR = 2.35f
            val dmg = computePlayerDamage() * (0.55f + bladesLvl * 0.15f) * skillMult
            for (i in 0 until bladeCount) {
                val angle = animTime * 4.2f + (i * 2f * PI.toFloat() / bladeCount)
                val bx = playerX + cos(angle) * orbitR
                val bz = playerZ + sin(angle) * orbitR
                for (e in enemies) {
                    if (e.state != EnemyState.BURROWED && hypot(e.x - bx, e.z - bz) < e.type.radius + 0.55f && e.hitFlashTimer <= 0f) {
                        applyDamageToEnemy(e, dmg, isCrit = false)
                        spawnVfx(VfxKind.SLASH_ARC, bx, 0.85f, bz, radius = 1.25f, angle = -angle, duration = 0.18f, colorHex = 0xFFFF6B1A)
                    }
                }
                for (b in bosses) {
                    if (hypot(b.x - bx, b.z - bz) < b.type.radius + 0.6f && b.hitFlashTimer <= 0f) {
                        applyDamageToBoss(b, dmg, isCrit = false)
                        spawnVfx(VfxKind.SLASH_ARC, bx, 0.85f, bz, radius = 1.4f, angle = -angle, duration = 0.18f, colorHex = 0xFFFF6B1A)
                    }
                }
            }
        }

        // Card 21: Arcane Meteor (targets densest enemy cluster)
        val meteorLvl = acquiredCards[21] ?: 0
        if (meteorLvl > 0) {
            meteorTimer -= dt
            if (meteorTimer <= 0f) {
                meteorTimer = (5.5f - meteorLvl * 0.6f).coerceAtLeast(2.8f)
                val target = findDensestTarget(playerX, playerZ, 12.5f) ?: findNearestTarget(playerX, playerZ, 12.5f)
                if (target != null) {
                    val dmg = (40f + meteorLvl * 20f) * skillMult
                    pendingMeteors.add(PendingMeteor(target.first, target.second, 0.75f, dmg))
                    spawnVfx(VfxKind.METEOR_WARNING, target.first, 0.08f, target.second, radius = 3.2f, duration = 0.75f, colorHex = 0xFFFF6B1A)
                }
            }
        }

        val metIter = pendingMeteors.iterator()
        while (metIter.hasNext()) {
            val m = metIter.next()
            m.timer -= dt
            if (m.timer <= 0f) {
                metIter.remove()
                damageEnemiesInRadius(m.x, m.z, 3.4f, m.damage, knockback = 4.0f)
                spawnVfx(VfxKind.METEOR_IMPACT, m.x, 0.2f, m.z, radius = 3.8f, duration = 0.6f, colorHex = 0xFFFF6B1A)
                spawnVfx(VfxKind.SHOCKWAVE_RING, m.x, 0.25f, m.z, radius = 4.4f, duration = 0.45f, colorHex = 0xFFFFD166)
                soundEngine.playSfx(SfxType.EXPLOSION)
                cameraShake = maxOf(cameraShake, 0.24f)
            }
        }

        // Card 22: Repulsor Wave
        val repulsorLvl = acquiredCards[22] ?: 0
        if (repulsorLvl > 0) {
            repulsorTimer -= dt
            if (repulsorTimer <= 0f) {
                repulsorTimer = (6.0f - repulsorLvl * 0.7f).coerceAtLeast(2.6f)
                val dmg = (16f + repulsorLvl * 9f) * skillMult
                damageEnemiesInRadius(playerX, playerZ, 4.6f, dmg, knockback = 6.0f)
                spawnVfx(VfxKind.SHOCKWAVE_RING, playerX, 0.22f, playerZ, radius = 4.6f, duration = 0.45f, colorHex = 0xFF38BDF8)
                spawnVfx(VfxKind.SHOCKWAVE_RING, playerX, 0.45f, playerZ, radius = 3.2f, duration = 0.32f, colorHex = 0xFFFFFFFF)
            }
        }

        // Card 26: Gravity Field
        val gravLvl = acquiredCards[26] ?: 0
        if (gravLvl > 0) {
            gravityTimer -= dt
            if (gravityTimer <= 0f) {
                gravityTimer = 7.5f
                val target = findDensestTarget(playerX, playerZ, 9.5f) ?: findNearestTarget(playerX, playerZ, 9.5f)
                val gx = target?.first ?: playerX
                val gz = target?.second ?: playerZ
                val slow = (0.30f + gravLvl * 0.10f).coerceAtMost(0.75f)
                groundHazards.add(GroundHazard(gx, gz, 3.6f, 4.0f, isToxicBoss = false, slowFactor = slow))
                spawnVfx(VfxKind.GRAVITY_FIELD, gx, 0.08f, gz, radius = 3.6f, duration = 4.0f, colorHex = 0xFF9D4EDD)
            }
        }

        // Delayed Echoes (Card 25)
        val echoIter = delayedEchoes.iterator()
        while (echoIter.hasNext()) {
            val echo = echoIter.next()
            echo.timer -= dt
            if (echo.timer <= 0f) {
                echoIter.remove()
                if (echo.isBoss) {
                    bosses.firstOrNull { it.uid == echo.targetUid }?.let { b ->
                        applyDamageToBoss(b, echo.damage, isCrit = false, triggerProcs = false)
                        spawnVfx(VfxKind.ECHO_HIT, b.x, 1.1f, b.z, radius = 1.45f, duration = 0.30f, colorHex = 0xFFC084FC)
                    }
                } else {
                    enemies.firstOrNull { it.uid == echo.targetUid }?.let { e ->
                        applyDamageToEnemy(e, echo.damage, isCrit = false, triggerProcs = false)
                        spawnVfx(VfxKind.ECHO_HIT, e.x, 0.9f, e.z, radius = 1.1f, duration = 0.28f, colorHex = 0xFF00E5FF)
                    }
                }
            }
        }
    }

    private fun updatePlayerAttack(dt: Float) {
        attackTimer -= dt
        if (attackTimer > 0f) return

        val nearest = if (!manualAimEnabled) findNearestTarget(playerX, playerZ, hero.attackRange + 0.8f) else null
        if (!manualAimEnabled && nearest == null) return

        val interval = computeAttackInterval()
        attackTimer = interval
        attackAnimTimer = 0.22f

        val baseDmg = computePlayerDamage()
        val critChance = computeCritChance()
        val extraCount = acquiredCards[11] ?: 0
        val brutalLvl = acquiredCards[9] ?: 0
        val accelLvl = acquiredCards[8] ?: 0
        val extraPierce = (acquiredCards[12] ?: 0) + if (hero == HeroClass.ARQUEIRO && Random.nextFloat() < 0.15f) 1 else 0

        if (accelLvl >= 2) {
            spawnVfx(VfxKind.SHOCKWAVE_RING, playerX, 0.12f, playerZ, radius = 1.25f, duration = 0.16f, colorHex = 0xFF38BDF8)
        }

        when (hero.attackStyle) {
            AttackStyle.MELEE_TWIN_BLADES,
            AttackStyle.MELEE_SWORD_SHIELD,
            AttackStyle.MELEE_KATANA -> {
                soundEngine.playSfx(SfxType.ATTACK_MELEE)
                val arcRadius = hero.attackRange + extraCount * 0.40f + brutalLvl * 0.12f
                val isCrit = Random.nextFloat() < critChance
                val dmg = if (isCrit) baseDmg * 2f else baseDmg
                cleaveArc(playerX, playerZ, aimAngle, arcRadius, 1.35f, dmg, isCrit)
                val slashColor = if (brutalLvl >= 2) 0xFFFF6B1A else skin.accentHex
                spawnVfx(VfxKind.SLASH_ARC, playerX, 0.95f, playerZ, radius = arcRadius, angle = aimAngle, duration = 0.24f, colorHex = slashColor)
                if (extraCount > 0) {
                    // Card 11: +1 forward energy wave per level + second sweeping slash visual
                    spawnVfx(VfxKind.SLASH_ARC, playerX, 1.15f, playerZ, radius = arcRadius * 1.1f, angle = aimAngle + 0.18f, duration = 0.26f, colorHex = 0xFFC084FC)
                    for (i in 0 until extraCount) {
                        val spread = if (extraCount == 1) 0f else (i - (extraCount - 1) * 0.5f) * 0.20f
                        val a = aimAngle + spread
                        spawnProjectile(
                            isPlayer = true, kind = ProjectileKind.ENERGY_BOLT,
                            x = playerX + sin(a) * 0.6f, z = playerZ + cos(a) * 0.6f,
                            vx = sin(a) * 15.5f, vz = cos(a) * 15.5f,
                            damage = baseDmg * 0.75f, pierce = 1 + extraPierce,
                            isCrit = isCrit, colorHex = 0xFFC084FC
                        )
                    }
                }
            }
            AttackStyle.PLASMA_DRONE,
            AttackStyle.RAIL_RIFLE,
            AttackStyle.WRENCH_CANNON -> {
                soundEngine.playSfx(SfxType.ATTACK_LASER)
                val totalShots = 1 + extraCount
                val kind = if (hero.attackStyle == AttackStyle.RAIL_RIFLE) ProjectileKind.BULLET else ProjectileKind.ENERGY_BOLT
                for (i in 0 until totalShots) {
                    val spread = if (totalShots == 1) 0f else (i - (totalShots - 1) * 0.5f) * 0.14f
                    val a = aimAngle + spread
                    val isCrit = Random.nextFloat() < critChance
                    spawnProjectile(
                        isPlayer = true, kind = kind,
                        x = playerX + sin(a) * 0.7f, z = playerZ + cos(a) * 0.7f,
                        vx = sin(a) * 19f, vz = cos(a) * 19f,
                        damage = if (isCrit) baseDmg * 2f else baseDmg,
                        pierce = extraPierce, isCrit = isCrit, colorHex = skin.accentHex
                    )
                }
            }
            AttackStyle.STAR_BOW -> {
                soundEngine.playSfx(SfxType.ATTACK_BOW)
                val totalShots = 1 + extraCount
                for (i in 0 until totalShots) {
                    val spread = if (totalShots == 1) 0f else (i - (totalShots - 1) * 0.5f) * 0.12f
                    val a = aimAngle + spread
                    val isCrit = Random.nextFloat() < critChance
                    spawnProjectile(
                        isPlayer = true, kind = ProjectileKind.STAR_ARROW,
                        x = playerX + sin(a) * 0.6f, z = playerZ + cos(a) * 0.6f,
                        vx = sin(a) * 20f, vz = cos(a) * 20f,
                        damage = if (isCrit) baseDmg * 2f else baseDmg,
                        pierce = extraPierce, isCrit = isCrit, colorHex = 0xFF00E5FF
                    )
                }
            }
            AttackStyle.ARCANE_STAFF,
            AttackStyle.CORRUPTION_ORB -> {
                soundEngine.playSfx(SfxType.ATTACK_ARCANE)
                val totalShots = 1 + extraCount
                val kind = if (hero.attackStyle == AttackStyle.ARCANE_STAFF) ProjectileKind.ARCANE_ORB else ProjectileKind.CORRUPTION_BLAST
                val col = if (hero.attackStyle == AttackStyle.ARCANE_STAFF) 0xFFC084FC else 0xFF76FF03
                for (i in 0 until totalShots) {
                    val spread = if (totalShots == 1) 0f else (i - (totalShots - 1) * 0.5f) * 0.18f
                    val a = aimAngle + spread
                    val isCrit = Random.nextFloat() < critChance
                    spawnProjectile(
                        isPlayer = true, kind = kind,
                        x = playerX + sin(a) * 0.7f, z = playerZ + cos(a) * 0.7f,
                        vx = sin(a) * 15f, vz = cos(a) * 15f,
                        damage = if (isCrit) baseDmg * 2f else baseDmg,
                        pierce = extraPierce, isCrit = isCrit, colorHex = col
                    )
                }
            }
        }
    }

    private fun updateAllies(dt: Float) {
        val droneBonus = if (hero == HeroClass.TECNOLOGICO) 1.15f else 1.0f
        val inventorBonus = if (hero == HeroClass.INVENTOR) 1.15f else 1.0f
        val skillMult = 1f + (acquiredCards[29] ?: 0) * 0.15f
        val iter = allies.iterator()
        while (iter.hasNext()) {
            val ally = iter.next()
            if (ally.lifeTimer > 0f) {
                ally.lifeTimer -= dt
                if (ally.lifeTimer <= 0f) {
                    iter.remove()
                    continue
                }
            }

            if (!ally.isTurret) {
                ally.orbitAngle += dt * 2.1f
                ally.x = playerX + cos(ally.orbitAngle) * 2.1f
                ally.z = playerZ + sin(ally.orbitAngle) * 2.1f
            }

            ally.fireTimer -= dt
            if (ally.fireTimer <= 0f) {
                val target = findNearestTarget(ally.x, ally.z, 10.5f)
                if (target != null) {
                    ally.fireTimer = if (ally.isTurret) 0.42f else 0.65f
                    val a = atan2(target.first - ally.x, target.second - ally.z)
                    val mult = (if (ally.isTurret) inventorBonus else droneBonus) * skillMult
                    spawnProjectile(
                        isPlayer = true,
                        kind = if (ally.isTurret) ProjectileKind.TURRET_SHOT else ProjectileKind.DRONE_LASER,
                        x = ally.x, z = ally.z,
                        vx = sin(a) * 18f, vz = cos(a) * 18f,
                        damage = ally.damage * mult,
                        pierce = 0,
                        colorHex = if (ally.isTurret) 0xFF76FF03 else 0xFF00E5FF
                    )
                    spawnVfx(
                        VfxKind.ECHO_HIT,
                        ally.x, if (ally.isTurret) 0.7f else 1.45f, ally.z,
                        radius = 0.55f, duration = 0.15f,
                        colorHex = if (ally.isTurret) 0xFF76FF03 else 0xFF00E5FF
                    )
                } else {
                    ally.fireTimer = 0.2f
                }
            }
        }
    }

    private fun updateProjectiles(dt: Float) {
        for (p in projectiles) {
            if (!p.active) continue
            p.x += p.vx * dt
            p.z += p.vz * dt
            p.life -= dt
            if (p.life <= 0f) {
                p.active = false
                continue
            }

            // Check obstacle hit
            val (rx, rz) = mapManager.resolveMovementCollision(p.x, p.z, p.radius)
            if (absDiff(rx, p.x) > 0.02f || absDiff(rz, p.z) > 0.02f) {
                p.active = false
                continue
            }

            if (p.isPlayer) {
                // Hit enemies
                for (e in enemies) {
                    if (e.state == EnemyState.BURROWED || e.state == EnemyState.DEAD) continue
                    if (p.hitIds.contains(e.uid)) continue
                    val hitR = e.type.radius + p.radius
                    if (hypot(e.x - p.x, e.z - p.z) <= hitR) {
                        p.hitIds.add(e.uid)
                        applyDamageToEnemy(e, p.damage, p.isCrit)
                        if (p.pierceLeft > 0) {
                            p.pierceLeft--
                            spawnVfx(VfxKind.ECHO_HIT, e.x, 0.9f, e.z, radius = 0.85f, duration = 0.18f, colorHex = 0xFF00E5FF)
                        } else {
                            p.active = false
                            break
                        }
                    }
                }
                if (!p.active) continue

                // Hit bosses
                for (b in bosses) {
                    if (p.hitIds.contains(b.uid)) continue
                    val hitR = b.type.radius + p.radius
                    if (hypot(b.x - p.x, b.z - p.z) <= hitR) {
                        p.hitIds.add(b.uid)
                        applyDamageToBoss(b, p.damage, p.isCrit)
                        if (p.pierceLeft > 0) {
                            p.pierceLeft--
                            spawnVfx(VfxKind.ECHO_HIT, b.x, 1.1f, b.z, radius = 1.15f, duration = 0.2f, colorHex = 0xFF00E5FF)
                        } else {
                            p.active = false
                            break
                        }
                    }
                }
            } else {
                // Enemy/Boss projectile hitting player
                if (!isDashing && hypot(playerX - p.x, playerZ - p.z) <= 0.65f + p.radius) {
                    applyDamageToPlayer(p.damage)
                    p.active = false
                }
            }
        }
    }

    private fun updateSpawning(dt: Float) {
        val maxActive = progress.settings.graphicsQuality.maxEnemies
        if (enemies.size >= maxActive) return

        spawnTimer -= dt
        if (spawnTimer > 0f) return

        val difficulty = 1f + survivalSeconds / 55f
        spawnTimer = (1.15f / difficulty).coerceAtLeast(0.28f)

        val batchSize = (1 + (survivalSeconds / 40f).toInt()).coerceAtMost(4)
        val availableSlots = (maxActive - enemies.size).coerceAtLeast(0)
        val count = min(batchSize, availableSlots)

        for (i in 0 until count) {
            val type = pickAlienTypeForTime(survivalSeconds)
            if (type == AlienType.PARASITA) {
                // Spawn pack of 3
                val baseAngle = Random.nextFloat() * 2f * PI.toFloat()
                val dist = 14.5f + Random.nextFloat() * 4f
                for (k in 0 until 3) {
                    if (enemies.size >= maxActive) break
                    val a = baseAngle + (k - 1) * 0.16f
                    spawnEnemy(AlienType.PARASITA, playerX + cos(a) * dist, playerZ + sin(a) * dist, difficulty)
                }
            } else {
                val angle = Random.nextFloat() * 2f * PI.toFloat()
                val dist = 14.5f + Random.nextFloat() * 4.5f
                spawnEnemy(type, playerX + cos(angle) * dist, playerZ + sin(angle) * dist, difficulty)
            }
        }
    }

    private fun pickAlienTypeForTime(sec: Float): AlienType {
        val roll = Random.nextFloat()
        return when {
            sec < 25f -> if (roll < 0.75f) AlienType.RASTEJADOR else AlienType.PARASITA
            sec < 60f -> when {
                roll < 0.35f -> AlienType.RASTEJADOR
                roll < 0.58f -> AlienType.PARASITA
                roll < 0.80f -> AlienType.SALTADOR
                else -> AlienType.BRUTAMONTES
            }
            sec < 110f -> when {
                roll < 0.22f -> AlienType.RASTEJADOR
                roll < 0.38f -> AlienType.PARASITA
                roll < 0.54f -> AlienType.SALTADOR
                roll < 0.70f -> AlienType.BRUTAMONTES
                roll < 0.86f -> AlienType.CUSPIDOR
                else -> AlienType.ESCAVADOR
            }
            else -> when {
                roll < 0.16f -> AlienType.RASTEJADOR
                roll < 0.30f -> AlienType.PARASITA
                roll < 0.44f -> AlienType.SALTADOR
                roll < 0.58f -> AlienType.BRUTAMONTES
                roll < 0.72f -> AlienType.CUSPIDOR
                roll < 0.82f -> AlienType.ESCAVADOR
                roll < 0.93f -> AlienType.SENTINELA
                else -> AlienType.CACADOR_ALFA
            }
        }
    }

    private fun spawnEnemy(type: AlienType, x: Float, z: Float, difficulty: Float) {
        val hpScale = 1f + (difficulty - 1f) * 0.45f
        val shield = if (type == AlienType.SENTINELA) 45f * hpScale else 0f
        enemies.add(
            EnemyInstance(
                uid = nextUid++,
                type = type,
                x = x,
                z = z,
                hp = type.baseHp * hpScale,
                maxHp = type.baseHp * hpScale,
                shield = shield,
                maxShield = shield,
                state = if (type == AlienType.ESCAVADOR) EnemyState.BURROWED else EnemyState.ACTIVE,
                specialTimer = if (type == AlienType.ESCAVADOR) 1.4f else 2.2f + Random.nextFloat() * 1.5f
            )
        )
    }

    private fun updateEnemies(dt: Float) {
        val durPoisonMult = if (hero == HeroClass.BRUXO) 1.2f else 1f
        val iter = enemies.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            e.animTime += dt
            if (e.hitFlashTimer > 0f) e.hitFlashTimer = (e.hitFlashTimer - dt).coerceAtLeast(0f)
            if (e.attackCooldown > 0f) e.attackCooldown = (e.attackCooldown - dt).coerceAtLeast(0f)

            // DoTs
            if (e.poisonTimer > 0f) {
                e.poisonTimer -= dt
                e.hp -= e.poisonDps * dt
            }
            if (e.burnTimer > 0f) {
                e.burnTimer -= dt
                e.hp -= e.burnDps * dt
            }
            if (e.slowTimer > 0f) {
                e.slowTimer -= dt
                if (e.slowTimer <= 0f) e.slowFactor = 1f
            }

            if (e.hp <= 0f) {
                handleEnemyDeath(e, durPoisonMult)
                iter.remove()
                continue
            }

            // Hazard slow + gravity well pull check (Card 26)
            var envSlow = 1f
            for (h in groundHazards) {
                val hdx = h.x - e.x
                val hdz = h.z - e.z
                val hDist = hypot(hdx, hdz)
                if (!h.isToxicBoss && hDist <= h.radius) {
                    envSlow = min(envSlow, 1f - h.slowFactor)
                    if (hDist > 0.45f) {
                        val pull = 2.4f * dt
                        e.x += (hdx / hDist) * pull
                        e.z += (hdz / hDist) * pull
                    }
                }
            }

            val dx = playerX - e.x
            val dz = playerZ - e.z
            val dist = hypot(dx, dz).coerceAtLeast(0.001f)
            val dirX = dx / dist
            val dirZ = dz / dist
            e.facingAngle = atan2(dirX, dirZ)

            val speed = e.type.baseSpeed * e.slowFactor * envSlow

            when (e.type) {
                AlienType.ESCAVADOR -> {
                    e.specialTimer -= dt
                    if (e.state == EnemyState.BURROWED) {
                        // Move fast underground toward player, emerge near player
                        e.x += dirX * speed * 1.5f * dt
                        e.z += dirZ * speed * 1.5f * dt
                        e.yOffset = -0.65f
                        if (dist < 4.5f || e.specialTimer <= 0f) {
                            e.state = EnemyState.ACTIVE
                            e.yOffset = 0f
                            e.specialTimer = 5.0f
                            spawnVfx(VfxKind.SHOCKWAVE_RING, e.x, 0.1f, e.z, radius = 1.6f, duration = 0.35f, colorHex = 0xFFF59E0B)
                        }
                    } else {
                        moveEnemyToward(e, dirX, dirZ, speed, dt)
                        if (e.specialTimer <= 0f && dist > 6f) {
                            e.state = EnemyState.BURROWED
                            e.specialTimer = 2.0f
                        }
                    }
                }
                AlienType.SALTADOR -> {
                    e.specialTimer -= dt
                    if (e.state == EnemyState.LEAPING) {
                        val lx = e.leapTargetX - e.x
                        val lz = e.leapTargetZ - e.z
                        val lDist = hypot(lx, lz)
                        e.yOffset = 0.8f * sin((e.specialTimer.coerceIn(0f, 0.45f) / 0.45f) * PI.toFloat())
                        if (lDist < 0.4f || e.specialTimer <= 0f) {
                            e.state = EnemyState.ACTIVE
                            e.yOffset = 0f
                            e.specialTimer = 2.8f
                        } else {
                            e.x += (lx / lDist) * 11.5f * dt
                            e.z += (lz / lDist) * 11.5f * dt
                        }
                    } else {
                        moveEnemyToward(e, dirX, dirZ, speed, dt)
                        if (e.specialTimer <= 0f && dist in 3.2f..7.5f) {
                            e.state = EnemyState.LEAPING
                            e.leapTargetX = playerX
                            e.leapTargetZ = playerZ
                            e.specialTimer = 0.45f
                        }
                    }
                }
                AlienType.CUSPIDOR -> {
                    if (dist > 6.8f) {
                        moveEnemyToward(e, dirX, dirZ, speed, dt)
                    } else if (dist < 4.0f) {
                        moveEnemyToward(e, -dirX, -dirZ, speed * 0.7f, dt)
                    }
                    e.specialTimer -= dt
                    if (e.specialTimer <= 0f && dist <= 11f) {
                        e.specialTimer = 2.3f
                        spawnProjectile(
                            isPlayer = false, kind = ProjectileKind.ALIEN_ACID,
                            x = e.x, z = e.z,
                            vx = dirX * 9.5f, vz = dirZ * 9.5f,
                            damage = e.type.baseDamage, pierce = 0, colorHex = 0xFF76FF03
                        )
                    }
                }
                else -> {
                    moveEnemyToward(e, dirX, dirZ, speed, dt)
                }
            }

            // Despawn if ultra far (>34m) to recycle budget
            if (dist > 34f) {
                iter.remove()
                continue
            }

            // Melee contact damage
            if (e.state != EnemyState.BURROWED && dist <= e.type.radius + 0.58f && e.attackCooldown <= 0f) {
                e.attackCooldown = 0.85f
                if (!isDashing) {
                    applyDamageToPlayer(e.type.baseDamage)
                }
            }
        }
    }

    private fun moveEnemyToward(e: EnemyInstance, dirX: Float, dirZ: Float, speed: Float, dt: Float) {
        val (steerX, steerZ) = mapManager.computeAvoidanceSteering(e.x, e.z, dirX, dirZ)
        var mx = dirX + steerX
        var mz = dirZ + steerZ
        val mLen = hypot(mx, mz).coerceAtLeast(0.001f)
        mx /= mLen
        mz /= mLen
        val (nx, nz) = mapManager.resolveMovementCollision(
            e.x + mx * speed * dt,
            e.z + mz * speed * dt,
            e.type.radius
        )
        e.x = nx
        e.z = nz
    }

    private fun updateBosses(dt: Float) {
        val iter = bosses.iterator()
        while (iter.hasNext()) {
            val b = iter.next()
            b.animTime += dt
            if (b.hitFlashTimer > 0f) b.hitFlashTimer = (b.hitFlashTimer - dt).coerceAtLeast(0f)

            if (b.poisonTimer > 0f) {
                b.poisonTimer -= dt
                b.hp -= b.poisonDps * dt
            }
            if (b.burnTimer > 0f) {
                b.burnTimer -= dt
                b.hp -= b.burnDps * dt
            }
            if (b.slowTimer > 0f) {
                b.slowTimer -= dt
                if (b.slowTimer <= 0f) b.slowFactor = 1f
            }

            if (b.hp <= 0f) {
                handleBossDeath(b)
                iter.remove()
                continue
            }

            if (b.entranceTimer > 0f) {
                b.entranceTimer -= dt
                continue
            }

            // Phase 2 transition for Devorador Cosmico
            if (b.type == BossType.DEVORADOR_COSMICO && b.phase == 1 && b.hp <= b.maxHp * 0.5f) {
                b.phase = 2
                bossBannerText = "DEVORADOR CÓSMICO: FASE 2!"
                bossBannerTimer = 3.0f
                soundEngine.playSfx(SfxType.BOSS_ALERT)
                spawnVfx(VfxKind.SHOCKWAVE_RING, b.x, 0.3f, b.z, radius = 6.5f, duration = 0.7f, colorHex = 0xFF00E5FF)
            }

            val dx = playerX - b.x
            val dz = playerZ - b.z
            val dist = hypot(dx, dz).coerceAtLeast(0.001f)
            val dirX = dx / dist
            val dirZ = dz / dist
            b.facingAngle = atan2(dirX, dirZ)

            if (b.chargingTimer > 0f) {
                b.chargingTimer -= dt
                val tx = b.targetX - b.x
                val tz = b.targetZ - b.z
                val tDist = hypot(tx, tz)
                if (tDist > 0.3f) {
                    val cSpeed = b.type.baseSpeed * 3.1f
                    val (nx, nz) = mapManager.resolveMovementCollision(b.x + (tx / tDist) * cSpeed * dt, b.z + (tz / tDist) * cSpeed * dt, b.type.radius)
                    b.x = nx
                    b.z = nz
                }
                if (!isDashing && hypot(playerX - b.x, playerZ - b.z) <= b.type.radius + 0.7f) {
                    applyDamageToPlayer(b.type.baseDamage * 1.25f)
                    b.chargingTimer = 0f
                }
                continue
            }

            if (b.telegraphTimer > 0f) {
                b.telegraphTimer -= dt
                if (b.telegraphTimer <= 0f) {
                    executeBossSpecialAttack(b)
                }
                continue
            }

            // Move toward player
            val spd = b.type.baseSpeed * b.slowFactor * (if (b.phase == 2) 1.25f else 1f)
            val (nx, nz) = mapManager.resolveMovementCollision(b.x + dirX * spd * dt, b.z + dirZ * spd * dt, b.type.radius)
            b.x = nx
            b.z = nz

            if (!isDashing && dist <= b.type.radius + 0.65f && ((b.animTime * 2f).toInt() != ((b.animTime - dt) * 2f).toInt())) {
                applyDamageToPlayer(b.type.baseDamage * 0.7f)
            }

            b.attackTimer -= dt
            if (b.attackTimer <= 0f) {
                b.attackTimer = if (b.phase == 2) 2.6f else 3.8f
                when (b.type) {
                    BossType.PREDADOR_ALFA -> {
                        b.telegraphType = 1
                        b.telegraphTimer = 0.85f
                        b.targetX = playerX + dirX * 2f
                        b.targetZ = playerZ + dirZ * 2f
                        spawnVfx(VfxKind.BOSS_LINE_TELEGRAPH, b.x, 0.1f, b.z, x2 = b.targetX, z2 = b.targetZ, radius = 1.6f, duration = 0.85f, colorHex = 0xFFFF2E4C)
                    }
                    BossType.COLOSSO_BLINDADO -> {
                        b.telegraphType = 2
                        b.telegraphTimer = 1.0f
                        spawnVfx(VfxKind.BOSS_RING_TELEGRAPH, b.x, 0.1f, b.z, radius = 5.5f, duration = 1.0f, colorHex = 0xFFFF6B1A)
                    }
                    BossType.RAINHA_PARASITA -> {
                        b.telegraphType = 3
                        b.telegraphTimer = 0.9f
                        spawnVfx(VfxKind.BOSS_RING_TELEGRAPH, b.x, 0.1f, b.z, radius = 4.2f, duration = 0.9f, colorHex = 0xFF76FF03)
                    }
                    BossType.DEVORADOR_COSMICO -> {
                        b.telegraphType = if (Random.nextBoolean()) 2 else 3
                        b.telegraphTimer = 1.0f
                        spawnVfx(VfxKind.BOSS_RING_TELEGRAPH, b.x, 0.1f, b.z, radius = 6.2f, duration = 1.0f, colorHex = 0xFF00E5FF)
                    }
                }
            }
        }
    }

    private fun executeBossSpecialAttack(b: BossInstance) {
        when (b.telegraphType) {
            1 -> {
                b.chargingTimer = 0.55f
                soundEngine.playSfx(SfxType.DASH)
            }
            2 -> {
                val radius = if (b.type == BossType.DEVORADOR_COSMICO) 6.2f else 5.5f
                spawnVfx(VfxKind.SHOCKWAVE_RING, b.x, 0.25f, b.z, radius = radius, duration = 0.5f, colorHex = b.type.accentColorHex)
                soundEngine.playSfx(SfxType.EXPLOSION)
                cameraShake = 0.35f
                if (!isDashing && hypot(playerX - b.x, playerZ - b.z) <= radius) {
                    applyDamageToPlayer(b.type.baseDamage)
                }
            }
            3 -> {
                val orbCount = if (b.type == BossType.DEVORADOR_COSMICO) (if (b.phase == 2) 14 else 10) else 8
                for (i in 0 until orbCount) {
                    val a = b.facingAngle + (i * 2f * PI.toFloat() / orbCount)
                    spawnProjectile(
                        isPlayer = false, kind = ProjectileKind.BOSS_VOID_ORB,
                        x = b.x, z = b.z,
                        vx = sin(a) * 9.5f, vz = cos(a) * 9.5f,
                        damage = b.type.baseDamage * 0.75f, pierce = 0, colorHex = b.type.accentColorHex
                    )
                }
                if (b.type == BossType.RAINHA_PARASITA) {
                    // Summon 3 parasites + toxic hazard
                    for (k in 0 until 3) {
                        val a = k * 2.1f
                        spawnEnemy(AlienType.PARASITA, b.x + cos(a) * 2.5f, b.z + sin(a) * 2.5f, 1.4f)
                    }
                    groundHazards.add(GroundHazard(playerX, playerZ, 2.8f, 4.5f, isToxicBoss = true, slowFactor = 0f))
                    spawnVfx(VfxKind.TOXIC_ZONE, playerX, 0.08f, playerZ, radius = 2.8f, duration = 4.5f, colorHex = 0xFF76FF03)
                }
            }
        }
        b.telegraphType = 0
    }

    private fun updateGroundHazards(dt: Float) {
        val iter = groundHazards.iterator()
        while (iter.hasNext()) {
            val h = iter.next()
            h.timer -= dt
            if (h.timer <= 0f) {
                iter.remove()
                continue
            }
            if (h.isToxicBoss && !isDashing && hypot(playerX - h.x, playerZ - h.z) <= h.radius) {
                applyDamageToPlayer(10f * dt)
            }
        }
    }

    private fun updatePickups(dt: Float) {
        val magnetLvl = acquiredCards[3] ?: 0
        val greedLvl = progress.permanentUpgrades[PermanentUpgradeType.GREED.id] ?: 0
        val equip = EquipmentItem.fromId(progress.equippedItemId)
        var pickupRadius = 2.8f * (1f + magnetLvl * 0.35f + greedLvl * 0.12f)
        if (hero == HeroClass.INVENTOR) pickupRadius *= 1.25f
        if (equip == EquipmentItem.MAGNET_COIL) pickupRadius *= 1.40f

        for (pk in pickups) {
            if (!pk.active) continue
            pk.age += dt
            val dx = playerX - pk.x
            val dz = playerZ - pk.z
            val dist = hypot(dx, dz)

            if (dist < pickupRadius && dist > 0.01f) {
                val pullSpeed = 12f + magnetLvl * 2.5f
                pk.x += (dx / dist) * pullSpeed * dt
                pk.z += (dz / dist) * pullSpeed * dt
                if (magnetLvl > 0 && magnetSparkTimer <= 0f) {
                    magnetSparkTimer = 0.25f
                    spawnVfx(VfxKind.SIPHON_BEAM, pk.x, 0.35f, pk.z, x2 = playerX, z2 = playerZ, duration = 0.16f, colorHex = 0xFF00E5FF)
                }
            }

            if (dist <= 0.85f) {
                pk.active = false
                when (pk.kind) {
                    PickupKind.XP_CRYSTAL -> {
                        val xpBonus = if (equip == EquipmentItem.MAGNET_COIL) 1.15f else 1f
                        grantXp((pk.value * xpBonus).toInt().coerceAtLeast(1))
                        soundEngine.playSfx(SfxType.PICKUP_XP)
                    }
                    PickupKind.COIN -> {
                        val fortLvl = acquiredCards[4] ?: 0
                        val mult = 1f + fortLvl * 0.20f + greedLvl * 0.12f
                        val gained = (pk.value * mult).toInt().coerceAtLeast(1)
                        coinsCollected += gained
                        if (fortLvl > 0) {
                            spawnVfx(VfxKind.SHOCKWAVE_RING, playerX, 0.25f, playerZ, radius = 1.35f, duration = 0.26f, colorHex = 0xFFFFD166)
                            spawnFloatingText(playerX, playerZ, "+$gained$", 0xFFFFD166)
                        }
                        soundEngine.playSfx(SfxType.PICKUP_COIN)
                    }
                    PickupKind.MEDKIT -> {
                        hp = (hp + pk.value).coerceAtMost(maxHp)
                        spawnVfx(VfxKind.HEAL_PULSE, playerX, 0.2f, playerZ, radius = 1.8f, duration = 0.45f, colorHex = 0xFF22FF88)
                        soundEngine.playSfx(SfxType.CARD_SELECT)
                    }
                    PickupKind.SHIELD_ORB -> {
                        shield = (shield + pk.value).coerceAtMost(maxShield.coerceAtLeast(30f))
                        spawnVfx(VfxKind.SHOCKWAVE_RING, playerX, 0.55f, playerZ, radius = 1.8f, duration = 0.4f, colorHex = 0xFF38BDF8)
                        soundEngine.playSfx(SfxType.CARD_SELECT)
                    }
                    PickupKind.BOSS_RELIC -> {
                        val fortLvl = acquiredCards[4] ?: 0
                        val coinGain = (pk.value * (1f + fortLvl * 0.20f)).toInt().coerceAtLeast(pk.value)
                        coinsCollected += coinGain
                        grantXp(pk.value * 2)
                        spawnVfx(VfxKind.CARD_EVOLVE_BURST, playerX, 0.2f, playerZ, radius = 3.2f, duration = 0.65f, colorHex = 0xFFFFD166)
                        soundEngine.playSfx(SfxType.LEVEL_UP)
                    }
                }
            }
        }
    }

    private fun updateVfxAndText(dt: Float) {
        for (v in vfxList) {
            if (!v.active) continue
            v.timer += dt
            if (v.timer >= v.duration) v.active = false
        }
        for (fn in floatingNumbers) {
            fn.timer += dt
            if (fn.timer >= fn.duration) floatingNumbers.remove(fn)
        }
    }

    private fun applyDamageToEnemy(e: EnemyInstance, rawDamage: Float, isCrit: Boolean, triggerProcs: Boolean = true) {
        if (e.state == EnemyState.DEAD) return
        var dmg = rawDamage
        if (e.shield > 0f) {
            val absorbed = min(e.shield, dmg)
            e.shield -= absorbed
            dmg -= absorbed
        }
        e.hp -= dmg
        e.hitFlashTimer = 0.12f
        soundEngine.playSfx(SfxType.HIT)

        if (isCrit) {
            spawnVfx(VfxKind.CRIT_BURST, e.x, 0.9f, e.z, radius = 1.45f, duration = 0.28f, colorHex = 0xFFFFD166)
        }

        if (progress.settings.showDamageNumbers) {
            val txt = if (isCrit) "${rawDamage.toInt()}!" else rawDamage.toInt().toString()
            spawnFloatingText(e.x, e.z, txt, if (isCrit) 0xFFFFD166 else 0xFFFFFFFF)
        }

        if (triggerProcs) applyOnHitCardProcs(e.uid, e.x, e.z, rawDamage, targetEnemy = e)
    }

    private fun applyDamageToBoss(b: BossInstance, rawDamage: Float, isCrit: Boolean, triggerProcs: Boolean = true) {
        b.hp -= rawDamage
        b.hitFlashTimer = 0.10f
        soundEngine.playSfx(SfxType.HIT)
        if (isCrit) {
            spawnVfx(VfxKind.CRIT_BURST, b.x, 1.3f, b.z, radius = 2.0f, duration = 0.30f, colorHex = 0xFFFFD166)
        }
        if (progress.settings.showDamageNumbers) {
            val txt = if (isCrit) "${rawDamage.toInt()}!" else rawDamage.toInt().toString()
            spawnFloatingText(b.x, b.z, txt, if (isCrit) 0xFFFFD166 else 0xFFFF6B1A)
        }
        if (triggerProcs) applyOnHitCardProcs(b.uid, b.x, b.z, rawDamage, targetBoss = b)
    }

    private fun applyOnHitCardProcs(
        uid: Int,
        x: Float,
        z: Float,
        hitDamage: Float,
        targetEnemy: EnemyInstance? = null,
        targetBoss: BossInstance? = null
    ) {
        val skillMult = 1f + (acquiredCards[29] ?: 0) * 0.15f

        // Card 14: Cosmic Flames (Burn)
        val burnLvl = acquiredCards[14] ?: 0
        if (burnLvl > 0 && Random.nextFloat() < (0.15f + burnLvl * 0.12f)) {
            targetEnemy?.let { it.burnTimer = 3.0f; it.burnDps = 6f * burnLvl * skillMult }
            targetBoss?.let { it.burnTimer = 3.0f; it.burnDps = 9f * burnLvl * skillMult }
            spawnVfx(VfxKind.FIRE_BURST, x, 0.35f, z, radius = 1.45f, duration = 0.34f, colorHex = 0xFFFF6B1A)
        }

        // Card 18: Temporal Freeze (Slow)
        val freezeLvl = acquiredCards[18] ?: 0
        if (freezeLvl > 0 && Random.nextFloat() < (0.25f + freezeLvl * 0.10f)) {
            targetEnemy?.let { it.slowTimer = 1.5f + freezeLvl * 0.4f; it.slowFactor = 0.52f }
            targetBoss?.let { it.slowTimer = 1.2f + freezeLvl * 0.3f; it.slowFactor = 0.72f }
            spawnVfx(VfxKind.CRYO_NOVA, x, 0.25f, z, radius = 1.5f, duration = 0.36f, colorHex = 0xFF67E8F9)
        }

        // Card 15: Chain Lightning (jumps to up to 3 nearest enemies/bosses)
        val chainLvl = acquiredCards[15] ?: 0
        if (chainLvl > 0 && Random.nextFloat() < (0.20f + chainLvl * 0.10f)) {
            soundEngine.playSfx(SfxType.LIGHTNING)
            var prevX = x
            var prevZ = z
            val hitSet = hashSetOf(uid)
            val chainDmg = hitDamage * (0.45f + chainLvl * 0.10f) * skillMult
            for (step in 0 until 3) {
                val nextEnemy = enemies
                    .filter { it.state != EnemyState.BURROWED && it.state != EnemyState.DEAD && !hitSet.contains(it.uid) }
                    .map { it to hypot(it.x - prevX, it.z - prevZ) }
                    .filter { it.second <= 6.2f }
                    .minByOrNull { it.second }?.first
                if (nextEnemy != null) {
                    hitSet.add(nextEnemy.uid)
                    nextEnemy.hp -= chainDmg
                    nextEnemy.hitFlashTimer = 0.14f
                    if (progress.settings.showDamageNumbers) {
                        spawnFloatingText(nextEnemy.x, nextEnemy.z, "${chainDmg.toInt()}", 0xFF38BDF8)
                    }
                    spawnVfx(VfxKind.CHAIN_LIGHTNING, prevX, 0.95f, prevZ, x2 = nextEnemy.x, z2 = nextEnemy.z, duration = 0.26f, colorHex = 0xFF00E5FF)
                    spawnVfx(VfxKind.ECHO_HIT, nextEnemy.x, 0.85f, nextEnemy.z, radius = 0.85f, duration = 0.20f, colorHex = 0xFF38BDF8)
                    prevX = nextEnemy.x
                    prevZ = nextEnemy.z
                } else {
                    val nextBoss = bosses
                        .filter { !hitSet.contains(it.uid) && hypot(it.x - prevX, it.z - prevZ) <= 7.0f }
                        .minByOrNull { hypot(it.x - prevX, it.z - prevZ) } ?: break
                    hitSet.add(nextBoss.uid)
                    nextBoss.hp -= chainDmg
                    nextBoss.hitFlashTimer = 0.12f
                    if (progress.settings.showDamageNumbers) {
                        spawnFloatingText(nextBoss.x, nextBoss.z, "${chainDmg.toInt()}", 0xFF38BDF8)
                    }
                    spawnVfx(VfxKind.CHAIN_LIGHTNING, prevX, 0.95f, prevZ, x2 = nextBoss.x, z2 = nextBoss.z, duration = 0.26f, colorHex = 0xFF00E5FF)
                    prevX = nextBoss.x
                    prevZ = nextBoss.z
                }
            }
        }

        // Card 25: Attack Echo
        val echoLvl = acquiredCards[25] ?: 0
        if (echoLvl > 0 && Random.nextFloat() < (0.20f + echoLvl * 0.12f)) {
            val echoDmg = hitDamage * (0.4f + echoLvl * 0.1f)
            if (targetEnemy != null) {
                delayedEchoes.add(DelayedEcho(uid, isBoss = false, timer = 0.25f, damage = echoDmg))
            } else if (targetBoss != null) {
                delayedEchoes.add(DelayedEcho(uid, isBoss = true, timer = 0.25f, damage = echoDmg))
            }
        }
    }

    private fun handleEnemyDeath(e: EnemyInstance, durPoisonMult: Float) {
        e.state = EnemyState.DEAD
        kills++
        soundEngine.playSfx(SfxType.ENEMY_DEATH)

        // Card 02: Alien Vampirism (rate-limited max 2/s)
        val vampLvl = acquiredCards[2] ?: 0
        if (vampLvl > 0 && vampirismRateTimer <= 0f) {
            vampirismRateTimer = 0.5f
            val heal = vampLvl * 2f
            hp = (hp + heal).coerceAtMost(maxHp)
            spawnVfx(VfxKind.SIPHON_BEAM, e.x, 0.7f, e.z, x2 = playerX, z2 = playerZ, duration = 0.26f, colorHex = 0xFFFF2E4C)
            spawnVfx(VfxKind.HEAL_PULSE, playerX, 0.2f, playerZ, radius = 1.45f, duration = 0.38f, colorHex = 0xFFFF2E4C)
            spawnFloatingText(playerX, playerZ, "+${heal.toInt()}", 0xFFFF4D6D)
        }

        // Card 27: Soul Harvest (every 12 kills)
        val soulLvl = acquiredCards[27] ?: 0
        if (soulLvl > 0 && kills % 12 == 0) {
            val heal = 6f + soulLvl * 4f
            hp = (hp + heal).coerceAtMost(maxHp)
            spawnVfx(VfxKind.LEVEL_UP_PILLAR, playerX, 0.15f, playerZ, radius = 2.4f, duration = 0.65f, colorHex = 0xFFC084FC)
            spawnVfx(VfxKind.HEAL_PULSE, playerX, 0.25f, playerZ, radius = 2.2f, duration = 0.55f, colorHex = 0xFF22FF88)
            spawnFloatingText(playerX, playerZ, "+${heal.toInt()} ALMAS", 0xFF22FF88)
        }

        // Card 24: Growing Fury
        if ((acquiredCards[24] ?: 0) > 0) {
            killFuryStacks = (killFuryStacks + 1).coerceAtMost(12)
            killFuryDecayTimer = 4.0f
            spawnVfx(VfxKind.RAGE_PULSE, playerX, 0.22f, playerZ, radius = 1.35f + killFuryStacks * 0.08f, duration = 0.30f, colorHex = 0xFFFF2E4C)
        }

        // Card 16: Plasma Explosion on death
        val plasmaLvl = acquiredCards[16] ?: 0
        if (plasmaLvl > 0 && Random.nextFloat() < (0.25f + plasmaLvl * 0.12f)) {
            val dmg = (20f + plasmaLvl * 10f) * (1f + (acquiredCards[29] ?: 0) * 0.15f)
            damageEnemiesInRadius(e.x, e.z, 3.2f, dmg, knockback = 1.8f)
            spawnVfx(VfxKind.PLASMA_EXPLOSION, e.x, 0.35f, e.z, radius = 3.4f, duration = 0.44f, colorHex = 0xFFD946EF)
            spawnVfx(VfxKind.SHOCKWAVE_RING, e.x, 0.25f, e.z, radius = 3.6f, duration = 0.36f, colorHex = 0xFFFF6B1A)
            soundEngine.playSfx(SfxType.EXPLOSION)
        }

        // Card 23: Viral Infection (spread poison on death)
        val viralLvl = acquiredCards[23] ?: 0
        if (viralLvl > 0 && e.poisonTimer > 0f) {
            val range = 3.2f + viralLvl * 0.8f
            for (other in enemies) {
                if (other.uid != e.uid && hypot(other.x - e.x, other.z - e.z) <= range) {
                    other.poisonTimer = 3.5f * durPoisonMult
                    other.poisonDps = maxOf(other.poisonDps, e.poisonDps)
                    spawnVfx(VfxKind.SIPHON_BEAM, e.x, 0.65f, e.z, x2 = other.x, z2 = other.z, duration = 0.28f, colorHex = 0xFF76FF03)
                }
            }
            spawnVfx(VfxKind.POISON_CLOUD, e.x, 0.25f, e.z, radius = range, duration = 0.5f, colorHex = 0xFF76FF03)
        }

        // Drops
        spawnPickup(PickupKind.XP_CRYSTAL, e.x, e.z, e.type.xpValue)
        if (Random.nextFloat() < e.type.coinChance) {
            spawnPickup(PickupKind.COIN, e.x + 0.35f, e.z - 0.25f, Random.nextInt(1, 4))
        }
        val extraDrop = Random.nextFloat()
        if (extraDrop < 0.03f) {
            spawnPickup(PickupKind.MEDKIT, e.x - 0.3f, e.z + 0.3f, 22)
        } else if (extraDrop < 0.045f) {
            spawnPickup(PickupKind.SHIELD_ORB, e.x + 0.25f, e.z + 0.35f, 20)
        }

        checkBossMilestones()
    }

    private fun handleBossDeath(b: BossInstance) {
        bossesDefeated++
        kills++
        soundEngine.playSfx(SfxType.EXPLOSION)
        spawnVfx(VfxKind.PLASMA_EXPLOSION, b.x, 0.8f, b.z, radius = 5.5f, duration = 0.7f, colorHex = b.type.accentColorHex)
        cameraShake = 0.45f

        coinsCollected += b.type.coinReward
        spawnPickup(PickupKind.BOSS_RELIC, b.x, b.z, b.type.coinReward / 2)
        grantXp(b.type.xpReward)

        if (b.type.isMajorBoss) {
            // Major bosses grant a bonus Special Card Choice!
            queueCardChoice(preferHighRarity = true)
        }

        if (b.type == BossType.DEVORADOR_COSMICO) {
            phase = RunPhase.VICTORY
            soundEngine.musicMode = MusicMode.MENU
        }
    }

    private fun checkBossMilestones() {
        for (m in BossMilestones.schedule) {
            if (kills >= m.killThreshold && !triggeredMilestones.contains(m.killThreshold)) {
                triggeredMilestones.add(m.killThreshold)
                soundEngine.playSfx(SfxType.BOSS_ALERT)
                val names = m.bosses.joinToString(" & ") { it.displayName }
                bossBannerText = "ALERTA DE CHEFE: $names!"
                bossBannerTimer = 3.6f

                m.bosses.forEachIndexed { idx, bossType ->
                    val angle = idx * 2.4f + Random.nextFloat() * 1.5f
                    val bx = playerX + cos(angle) * 14f
                    val bz = playerZ + sin(angle) * 14f
                    val scaleHp = bossType.baseHp * (1f + survivalSeconds / 240f)
                    bosses.add(
                        BossInstance(
                            uid = nextUid++,
                            type = bossType,
                            x = bx,
                            z = bz,
                            hp = scaleHp,
                            maxHp = scaleHp
                        )
                    )
                }
                // Spawn only 1 milestone per check to prevent simultaneous stacking
                break
            }
        }
    }

    private fun applyDamageToPlayer(rawDamage: Float) {
        if (phase != RunPhase.RUNNING || isDashing) return
        shieldOutOfCombatTimer = 0f

        var reduction = (acquiredCards[5] ?: 0) * 0.08f +
            (progress.permanentUpgrades[PermanentUpgradeType.ARMOR.id] ?: 0) * 0.03f
        if (hero == HeroClass.GUERREIRO) reduction += 0.10f
        if (hero == HeroClass.GLADIADOR) reduction += 0.15f
        reduction = reduction.coerceAtMost(0.68f)

        var dmg = rawDamage * (1f - reduction)
        if ((acquiredCards[5] ?: 0) > 0) {
            spawnVfx(VfxKind.SHOCKWAVE_RING, playerX, 0.55f, playerZ, radius = 1.45f, duration = 0.24f, colorHex = 0xFFCBD5E1)
        }
        if (shield > 0f) {
            val absorbed = min(shield, dmg)
            shield -= absorbed
            dmg -= absorbed
            spawnVfx(VfxKind.SHOCKWAVE_RING, playerX, 0.70f, playerZ, radius = 1.75f, duration = 0.28f, colorHex = 0xFF38BDF8)
        }

        if (dmg > 0f) {
            hp -= dmg
            hurtAnimTimer = 0.25f
            cameraShake = maxOf(cameraShake, 0.18f)
        }

        val skillMult = 1f + (acquiredCards[29] ?: 0) * 0.15f

        // Card 28: Reactive Armor electric discharge
        val reactLvl = acquiredCards[28] ?: 0
        if (reactLvl > 0 && reactiveArmorCooldown <= 0f) {
            reactiveArmorCooldown = 2.5f
            val shockDmg = (22f + reactLvl * 12f) * skillMult
            damageEnemiesInRadius(playerX, playerZ, 4.4f, shockDmg, knockback = 3.2f)
            spawnVfx(VfxKind.SHOCKWAVE_RING, playerX, 0.3f, playerZ, radius = 4.4f, duration = 0.38f, colorHex = 0xFF00E5FF)
            var zaps = 0
            for (e in enemies) {
                if (e.state != EnemyState.BURROWED && hypot(e.x - playerX, e.z - playerZ) <= 4.8f) {
                    spawnVfx(VfxKind.CHAIN_LIGHTNING, playerX, 0.9f, playerZ, x2 = e.x, z2 = e.z, duration = 0.26f, colorHex = 0xFF00E5FF)
                    zaps++
                    if (zaps >= 4) break
                }
            }
            soundEngine.playSfx(SfxType.LIGHTNING)
        }

        if (hp <= 0f) {
            // Card 30: Starlight Rebirth
            if ((acquiredCards[30] ?: 0) > 0 && !rebirthUsed) {
                rebirthUsed = true
                hp = maxHp * 0.6f
                shield = maxShield
                damageEnemiesInRadius(playerX, playerZ, 7.8f, 120f * skillMult, knockback = 8.5f)
                spawnVfx(VfxKind.STAR_REBIRTH, playerX, 0.35f, playerZ, radius = 7.8f, duration = 0.95f, colorHex = 0xFFFFD166)
                spawnVfx(VfxKind.LEVEL_UP_PILLAR, playerX, 0.1f, playerZ, radius = 3.5f, duration = 0.95f, colorHex = 0xFFFF6B1A)
                cameraShake = 0.45f
                soundEngine.playSfx(SfxType.LEVEL_UP)
                poiBannerText = "RENASCIMENTO ESTELAR ATIVADO!"
                poiBannerTimer = 3.0f
            } else {
                hp = 0f
                phase = RunPhase.GAME_OVER
                soundEngine.musicMode = MusicMode.MENU
            }
        }
    }

    private fun grantXp(amount: Int) {
        xp += amount
        while (xp >= xpToNext) {
            xp -= xpToNext
            level++
            xpToNext = 40 + level * 24 + level * level * 3
            spawnVfx(VfxKind.LEVEL_UP_PILLAR, playerX, 0.1f, playerZ, radius = 2.5f, duration = 0.7f, colorHex = 0xFF00E5FF)
            soundEngine.playSfx(SfxType.LEVEL_UP)
            queueCardChoice(preferHighRarity = false)
        }
    }

    private fun queueCardChoice(preferHighRarity: Boolean) {
        if (phase == RunPhase.CARD_CHOICE) {
            pendingLevelQueue.addLast(preferHighRarity)
        } else {
            currentCardChoices = EvolutionCardCatalog.drawChoices(acquiredCards, 3, preferHighRarity)
            phase = RunPhase.CARD_CHOICE
        }
    }

    private fun cleaveArc(cx: Float, cz: Float, angle: Float, radius: Float, halfConeRad: Float, dmg: Float, isCrit: Boolean): Int {
        var hits = 0
        for (e in enemies) {
            if (e.state == EnemyState.BURROWED) continue
            val dx = e.x - cx
            val dz = e.z - cz
            val dist = hypot(dx, dz)
            if (dist <= radius + e.type.radius) {
                val eAngle = atan2(dx, dz)
                val diff = absAngleDiff(angle, eAngle)
                if (diff <= halfConeRad || dist < 1.3f) {
                    applyDamageToEnemy(e, dmg, isCrit)
                    if (dist > 0.01f) {
                        e.x += (dx / dist) * 0.45f
                        e.z += (dz / dist) * 0.45f
                    }
                    hits++
                }
            }
        }
        for (b in bosses) {
            val dx = b.x - cx
            val dz = b.z - cz
            val dist = hypot(dx, dz)
            if (dist <= radius + b.type.radius) {
                applyDamageToBoss(b, dmg, isCrit)
                hits++
            }
        }
        return hits
    }

    private fun damageEnemiesInRadius(cx: Float, cz: Float, radius: Float, dmg: Float, knockback: Float, forceCrit: Boolean = false) {
        for (e in enemies) {
            if (e.state == EnemyState.BURROWED) continue
            val dx = e.x - cx
            val dz = e.z - cz
            val dist = hypot(dx, dz)
            if (dist <= radius + e.type.radius) {
                applyDamageToEnemy(e, dmg, forceCrit)
                if (knockback > 0f && dist > 0.01f) {
                    e.x += (dx / dist) * knockback * 0.35f
                    e.z += (dz / dist) * knockback * 0.35f
                }
            }
        }
        for (b in bosses) {
            if (hypot(b.x - cx, b.z - cz) <= radius + b.type.radius) {
                applyDamageToBoss(b, dmg, forceCrit)
            }
        }
    }

    private fun findNearestTarget(fromX: Float, fromZ: Float, maxRange: Float): Pair<Float, Float>? {
        var bestDist = maxRange * maxRange
        var best: Pair<Float, Float>? = null
        for (e in enemies) {
            if (e.state == EnemyState.BURROWED || e.state == EnemyState.DEAD) continue
            val dx = e.x - fromX
            val dz = e.z - fromZ
            val d2 = dx * dx + dz * dz
            if (d2 < bestDist) {
                bestDist = d2
                best = e.x to e.z
            }
        }
        for (b in bosses) {
            val dx = b.x - fromX
            val dz = b.z - fromZ
            val d2 = dx * dx + dz * dz
            if (d2 < bestDist) {
                bestDist = d2
                best = b.x to b.z
            }
        }
        return best
    }

    private fun findDensestTarget(fromX: Float, fromZ: Float, maxRange: Float): Pair<Float, Float>? {
        val maxR2 = maxRange * maxRange
        var bestScore = -1
        var bestPos: Pair<Float, Float>? = null
        for (e in enemies) {
            if (e.state == EnemyState.BURROWED || e.state == EnemyState.DEAD) continue
            val dx = e.x - fromX
            val dz = e.z - fromZ
            if (dx * dx + dz * dz > maxR2) continue
            var neighbors = 0
            for (other in enemies) {
                if (other.state != EnemyState.DEAD && hypot(other.x - e.x, other.z - e.z) <= 3.4f) {
                    neighbors++
                }
            }
            if (neighbors > bestScore) {
                bestScore = neighbors
                bestPos = e.x to e.z
            }
        }
        if (bestPos == null && bosses.isNotEmpty()) {
            val b = bosses.first()
            if (hypot(b.x - fromX, b.z - fromZ) <= maxRange + 2f) {
                return b.x to b.z
            }
        }
        return bestPos
    }

    private fun spawnProjectile(
        isPlayer: Boolean,
        kind: ProjectileKind,
        x: Float,
        z: Float,
        vx: Float,
        vz: Float,
        damage: Float,
        pierce: Int,
        isCrit: Boolean = false,
        colorHex: Long
    ) {
        for (p in projectiles) {
            if (!p.active) {
                p.active = true
                p.isPlayer = isPlayer
                p.kind = kind
                p.x = x
                p.y = 0.9f
                p.z = z
                p.vx = vx
                p.vz = vz
                p.damage = damage
                p.radius = if (kind == ProjectileKind.BOSS_VOID_ORB) 0.42f else 0.28f
                p.life = if (isPlayer) 1.35f else 2.2f
                p.pierceLeft = pierce
                p.isCrit = isCrit
                p.colorHex = colorHex
                p.hitIds.clear()
                return
            }
        }
    }

    private fun spawnVfx(
        kind: VfxKind,
        x: Float,
        y: Float,
        z: Float,
        x2: Float = x,
        z2: Float = z,
        radius: Float = 1f,
        angle: Float = 0f,
        duration: Float = 0.35f,
        colorHex: Long
    ) {
        val budget = progress.settings.graphicsQuality.particleBudget
        var activeCount = 0
        var freeSlot: VisualEffectInstance? = null
        for (v in vfxList) {
            if (v.active) activeCount++ else if (freeSlot == null) freeSlot = v
        }
        if (activeCount >= budget || freeSlot == null) return
        freeSlot.active = true
        freeSlot.kind = kind
        freeSlot.x = x
        freeSlot.y = y
        freeSlot.z = z
        freeSlot.x2 = x2
        freeSlot.z2 = z2
        freeSlot.radius = radius
        freeSlot.angle = angle
        freeSlot.timer = 0f
        freeSlot.duration = duration
        freeSlot.colorHex = colorHex
    }

    private fun spawnPickup(kind: PickupKind, x: Float, z: Float, value: Int) {
        for (pk in pickups) {
            if (!pk.active) {
                pk.active = true
                pk.kind = kind
                pk.x = x
                pk.z = z
                pk.value = value
                pk.age = 0f
                return
            }
        }
    }

    private fun spawnFloatingText(wx: Float, wz: Float, text: String, colorHex: Long) {
        if (floatingNumbers.size > 24) return
        floatingNumbers.add(FloatingNumber(nextUid++, wx, wz, text, colorHex, 0f))
    }

    fun computePlayerDamage(): Float {
        val mightLvl = progress.permanentUpgrades[PermanentUpgradeType.MIGHT.id] ?: 0
        val brutalLvl = acquiredCards[9] ?: 0
        val furyLvl = acquiredCards[24] ?: 0
        val equip = EquipmentItem.fromId(progress.equippedItemId)

        var mult = 1f + mightLvl * 0.08f + brutalLvl * 0.12f
        if (hero == HeroClass.BERSERKER) {
            val passive = ((kills / 10) * 0.01f).coerceAtMost(0.30f)
            mult += passive
            if (specialActiveTimer > 0f) mult += 0.40f
        }
        if (hero == HeroClass.GLADIADOR && specialActiveTimer > 0f) mult += 0.30f
        if (equip == EquipmentItem.PLASMA_CORE) mult += 0.10f
        if (furyLvl > 0 && killFuryStacks > 0) {
            val cap = 0.18f + furyLvl * 0.08f
            mult += (killFuryStacks * 0.03f).coerceAtMost(cap)
        }
        if (poiBuffDamageTimer > 0f) mult += 0.15f
        return hero.baseDamage * mult
    }

    fun computeMoveSpeed(): Float {
        val agilLvl = progress.permanentUpgrades[PermanentUpgradeType.AGILITY.id] ?: 0
        val speedCard = acquiredCards[7] ?: 0
        val equip = EquipmentItem.fromId(progress.equippedItemId)
        var mult = 1f + agilLvl * 0.05f + speedCard * 0.10f
        if (equip == EquipmentItem.CHRONO_BOOSTER) mult += 0.12f
        return hero.moveSpeed * mult
    }

    fun computeAttackInterval(): Float {
        val accelLvl = acquiredCards[8] ?: 0
        val equip = EquipmentItem.fromId(progress.equippedItemId)
        var speedMult = 1f + accelLvl * 0.08f
        if (hero == HeroClass.BERSERKER && specialActiveTimer > 0f) speedMult += 0.65f
        if (equip == EquipmentItem.PLASMA_CORE) speedMult += 0.10f
        return (hero.attackInterval / speedMult).coerceAtLeast(0.14f)
    }

    fun computeCritChance(): Float {
        val precLvl = acquiredCards[10] ?: 0
        var c = 0.05f + precLvl * 0.05f
        if (hero == HeroClass.ATIRADOR) c += 0.10f
        return c.coerceAtMost(0.75f)
    }

    private fun computeCooldownReduction(): Float {
        val permCd = (progress.permanentUpgrades[PermanentUpgradeType.COOLDOWN.id] ?: 0) * 0.05f
        val mageBonus = if (hero == HeroClass.MAGO) 0.10f else 0f
        return (permCd + mageBonus).coerceAtMost(0.45f)
    }

    fun snapshotHud(): HudSnapshot {
        synchronized(stateLock) {
            val primaryBoss = bosses.firstOrNull()
            val reduction = (((acquiredCards[5] ?: 0) * 8) +
                ((progress.permanentUpgrades[PermanentUpgradeType.ARMOR.id] ?: 0) * 3) +
                (if (hero == HeroClass.GUERREIRO) 10 else if (hero == HeroClass.GLADIADOR) 15 else 0)).coerceAtMost(68)

            val dmgPct = (((computePlayerDamage() / hero.baseDamage) - 1f) * 100f).toInt().coerceAtLeast(0)
            val spdPct = (((computeMoveSpeed() / hero.moveSpeed) - 1f) * 100f).toInt().coerceAtLeast(0)
            val atkPct = (((hero.attackInterval / computeAttackInterval()) - 1f) * 100f).toInt().coerceAtLeast(0)

            return HudSnapshot(
                hero = hero,
                hp = hp,
                maxHp = maxHp,
                shield = shield,
                maxShield = maxShield,
                xp = xp,
                xpToNext = xpToNext,
                level = level,
                survivalSeconds = survivalSeconds,
                kills = kills,
                coinsCollected = coinsCollected,
                dashCooldownRatio = (1f - (dashCooldownTimer / 1.85f)).coerceIn(0f, 1f),
                specialCooldownRatio = (1f - (specialCooldownTimer / hero.specialCooldown)).coerceIn(0f, 1f),
                specialActiveTimer = specialActiveTimer,
                manualAimEnabled = manualAimEnabled,
                activeBossName = primaryBoss?.type?.displayName,
                activeBossSubtitle = primaryBoss?.type?.subtitle,
                activeBossHp = primaryBoss?.hp ?: 0f,
                activeBossMaxHp = primaryBoss?.maxHp ?: 1f,
                bossAnnouncement = bossBannerText,
                poiAnnouncement = poiBannerText,
                acquiredCards = LinkedHashMap(acquiredCards),
                damageBonusPercent = dmgPct,
                moveSpeedBonusPercent = spdPct,
                attackSpeedBonusPercent = atkPct,
                critChancePercent = (computeCritChance() * 100f).toInt(),
                damageReductionPercent = reduction,
                minimapPlayerX = playerX,
                minimapPlayerZ = playerZ,
                minimapEnemies = enemies.take(55).map { (it.x - playerX) to (it.z - playerZ) },
                minimapBosses = bosses.map { (it.x - playerX) to (it.z - playerZ) },
                minimapObstacles = mapManager.allProps()
                    .filter { it.kind.blocksMovement && hypot(it.x - playerX, it.z - playerZ) < 28f }
                    .take(40)
                    .map { (it.x - playerX) to (it.z - playerZ) },
                minimapPois = mapManager.allPois()
                    .filter { hypot(it.x - playerX, it.z - playerZ) < 42f }
                    .map { Triple(it.x - playerX, it.z - playerZ, it.discovered) }
            )
        }
    }

    private fun absAngleDiff(a: Float, b: Float): Float {
        var d = (a - b) % (2f * PI.toFloat())
        if (d > PI.toFloat()) d -= 2f * PI.toFloat()
        if (d < -PI.toFloat()) d += 2f * PI.toFloat()
        return kotlin.math.abs(d)
    }

    private fun absDiff(a: Float, b: Float): Float = kotlin.math.abs(a - b)
}
