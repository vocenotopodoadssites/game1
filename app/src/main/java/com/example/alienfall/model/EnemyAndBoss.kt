package com.example.alienfall.model

enum class AlienType(
    val id: String,
    val displayName: String,
    val baseHp: Float,
    val baseSpeed: Float,
    val baseDamage: Float,
    val radius: Float,
    val scale: Float,
    val xpValue: Int,
    val coinChance: Float,
    val bodyColorHex: Long,
    val accentColorHex: Long,
    val glbModelPath: String
) {
    RASTEJADOR(
        "rastejador", "Rastejador",
        baseHp = 26f, baseSpeed = 4.5f, baseDamage = 8f, radius = 0.55f, scale = 0.85f,
        xpValue = 8, coinChance = 0.25f, bodyColorHex = 0xFF3E182E, accentColorHex = 0xFFFF2E4C,
        glbModelPath = "models/aliens/rastejador.glb"
    ),
    BRUTAMONTES(
        "brutamontes", "Brutamontes",
        baseHp = 95f, baseSpeed = 2.2f, baseDamage = 18f, radius = 0.95f, scale = 1.45f,
        xpValue = 22, coinChance = 0.55f, bodyColorHex = 0xFF564B41, accentColorHex = 0xFFFF6B1A,
        glbModelPath = "models/aliens/brutamontes.glb"
    ),
    SALTADOR(
        "saltador", "Saltador",
        baseHp = 34f, baseSpeed = 4.1f, baseDamage = 11f, radius = 0.6f, scale = 0.95f,
        xpValue = 12, coinChance = 0.35f, bodyColorHex = 0xFF4B2859, accentColorHex = 0xFFD946EF,
        glbModelPath = "models/aliens/saltador.glb"
    ),
    CUSPIDOR(
        "cuspidor", "Cuspidor",
        baseHp = 38f, baseSpeed = 2.9f, baseDamage = 12f, radius = 0.65f, scale = 1.0f,
        xpValue = 15, coinChance = 0.40f, bodyColorHex = 0xFF3A4435, accentColorHex = 0xFF76FF03,
        glbModelPath = "models/aliens/cuspidor.glb"
    ),
    PARASITA(
        "parasita", "Parasita",
        baseHp = 16f, baseSpeed = 4.8f, baseDamage = 6f, radius = 0.42f, scale = 0.65f,
        xpValue = 5, coinChance = 0.18f, bodyColorHex = 0xFF7F1D1D, accentColorHex = 0xFFFF4D4D,
        glbModelPath = "models/aliens/parasita.glb"
    ),
    ESCAVADOR(
        "escavador", "Escavador",
        baseHp = 52f, baseSpeed = 3.3f, baseDamage = 14f, radius = 0.7f, scale = 1.1f,
        xpValue = 16, coinChance = 0.45f, bodyColorHex = 0xFF453629, accentColorHex = 0xFFF59E0B,
        glbModelPath = "models/aliens/escavador.glb"
    ),
    SENTINELA(
        "sentinela", "Sentinela",
        baseHp = 72f, baseSpeed = 3.0f, baseDamage = 15f, radius = 0.78f, scale = 1.2f,
        xpValue = 20, coinChance = 0.55f, bodyColorHex = 0xFF5D534A, accentColorHex = 0xFF00E5FF,
        glbModelPath = "models/aliens/sentinela.glb"
    ),
    CACADOR_ALFA(
        "cacador_alfa", "Caçador Alfa",
        baseHp = 84f, baseSpeed = 5.1f, baseDamage = 17f, radius = 0.75f, scale = 1.18f,
        xpValue = 28, coinChance = 0.70f, bodyColorHex = 0xFF1F1629, accentColorHex = 0xFFFFD166,
        glbModelPath = "models/aliens/cacador_alfa.glb"
    )
}

enum class BossType(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val isMajorBoss: Boolean,
    val baseHp: Float,
    val baseSpeed: Float,
    val baseDamage: Float,
    val radius: Float,
    val scale: Float,
    val coinReward: Int,
    val xpReward: Int,
    val bodyColorHex: Long,
    val accentColorHex: Long
) {
    PREDADOR_ALFA(
        "predador_alfa", "Predador Alfa", "Minichefe Caçador",
        isMajorBoss = false, baseHp = 650f, baseSpeed = 4.6f, baseDamage = 24f,
        radius = 1.35f, scale = 2.0f, coinReward = 35, xpReward = 140,
        bodyColorHex = 0xFF4A0D1E, accentColorHex = 0xFFFF2E4C
    ),
    COLOSSO_BLINDADO(
        "colosso_blindado", "Colosso Blindado", "Minichefe Titânico",
        isMajorBoss = false, baseHp = 980f, baseSpeed = 2.4f, baseDamage = 32f,
        radius = 1.65f, scale = 2.5f, coinReward = 50, xpReward = 180,
        bodyColorHex = 0xFF4B4239, accentColorHex = 0xFFFF6B1A
    ),
    RAINHA_PARASITA(
        "rainha_parasita", "Rainha Parasita", "Chefe Principal do Enxame",
        isMajorBoss = true, baseHp = 1900f, baseSpeed = 2.9f, baseDamage = 30f,
        radius = 1.85f, scale = 2.8f, coinReward = 110, xpReward = 320,
        bodyColorHex = 0xFF3E0A2F, accentColorHex = 0xFF76FF03
    ),
    DEVORADOR_COSMICO(
        "devorador_cosmico", "Devorador Cósmico", "Chefe Colossal Multifase",
        isMajorBoss = true, baseHp = 4200f, baseSpeed = 3.2f, baseDamage = 40f,
        radius = 2.3f, scale = 3.4f, coinReward = 260, xpReward = 700,
        bodyColorHex = 0xFF140B29, accentColorHex = 0xFF00E5FF
    )
}

data class BossMilestone(
    val killThreshold: Int,
    val bosses: List<BossType>
)

object BossMilestones {
    val schedule = listOf(
        BossMilestone(50, listOf(BossType.PREDADOR_ALFA)),
        BossMilestone(150, listOf(BossType.COLOSSO_BLINDADO)),
        BossMilestone(300, listOf(BossType.RAINHA_PARASITA)),
        BossMilestone(500, listOf(BossType.PREDADOR_ALFA, BossType.COLOSSO_BLINDADO)),
        BossMilestone(750, listOf(BossType.RAINHA_PARASITA)),
        BossMilestone(1000, listOf(BossType.DEVORADOR_COSMICO))
    )
}

enum class EnemyState {
    ACTIVE,
    BURROWED,
    LEAPING,
    DEAD
}

data class EnemyInstance(
    val uid: Int,
    val type: AlienType,
    var x: Float,
    var z: Float,
    var vx: Float = 0f,
    var vz: Float = 0f,
    var facingAngle: Float = 0f,
    var hp: Float,
    var maxHp: Float,
    var shield: Float = 0f,
    var maxShield: Float = 0f,
    var state: EnemyState = EnemyState.ACTIVE,
    var animTime: Float = 0f,
    var hitFlashTimer: Float = 0f,
    var poisonTimer: Float = 0f,
    var poisonDps: Float = 0f,
    var burnTimer: Float = 0f,
    var burnDps: Float = 0f,
    var slowTimer: Float = 0f,
    var slowFactor: Float = 1f,
    var attackCooldown: Float = 0f,
    var specialTimer: Float = 0f,
    var leapTargetX: Float = 0f,
    var leapTargetZ: Float = 0f,
    var yOffset: Float = 0f
)

data class BossInstance(
    val uid: Int,
    val type: BossType,
    var x: Float,
    var z: Float,
    var facingAngle: Float = 0f,
    var hp: Float,
    var maxHp: Float,
    var phase: Int = 1,
    var entranceTimer: Float = 1.6f,
    var animTime: Float = 0f,
    var hitFlashTimer: Float = 0f,
    var attackTimer: Float = 2.0f,
    var telegraphTimer: Float = 0f,
    var telegraphType: Int = 0, // 0=none, 1=charge lane, 2=radial slam, 3=toxic/void barrage
    var targetX: Float = 0f,
    var targetZ: Float = 0f,
    var chargingTimer: Float = 0f,
    var poisonTimer: Float = 0f,
    var poisonDps: Float = 0f,
    var burnTimer: Float = 0f,
    var burnDps: Float = 0f,
    var slowTimer: Float = 0f,
    var slowFactor: Float = 1f
)

enum class ProjectileKind {
    BULLET,
    ENERGY_BOLT,
    STAR_ARROW,
    ARCANE_ORB,
    CORRUPTION_BLAST,
    DRONE_LASER,
    TURRET_SHOT,
    ALIEN_ACID,
    BOSS_VOID_ORB
}

data class ProjectileInstance(
    var active: Boolean = false,
    var isPlayer: Boolean = true,
    var kind: ProjectileKind = ProjectileKind.BULLET,
    var x: Float = 0f,
    var y: Float = 0.9f,
    var z: Float = 0f,
    var vx: Float = 0f,
    var vz: Float = 0f,
    var damage: Float = 10f,
    var radius: Float = 0.28f,
    var life: Float = 1.5f,
    var pierceLeft: Int = 0,
    var isCrit: Boolean = false,
    var colorHex: Long = 0xFFFF6B1A,
    val hitIds: HashSet<Int> = HashSet()
)

enum class VfxKind {
    SLASH_ARC,
    SHOCKWAVE_RING,
    CHAIN_LIGHTNING,
    PLASMA_EXPLOSION,
    METEOR_WARNING,
    METEOR_IMPACT,
    GRAVITY_FIELD,
    POISON_CLOUD,
    TOXIC_ZONE,
    DASH_TRAIL,
    LEVEL_UP_PILLAR,
    BOSS_LINE_TELEGRAPH,
    BOSS_RING_TELEGRAPH,
    STAR_REBIRTH,
    ECHO_HIT,
    HEAL_PULSE,
    SIPHON_BEAM,
    CRIT_BURST,
    FIRE_BURST,
    CRYO_NOVA,
    RAGE_PULSE,
    CARD_EVOLVE_BURST
}

data class VisualEffectInstance(
    var active: Boolean = false,
    var kind: VfxKind = VfxKind.PLASMA_EXPLOSION,
    var x: Float = 0f,
    var y: Float = 0.1f,
    var z: Float = 0f,
    var x2: Float = 0f,
    var z2: Float = 0f,
    var radius: Float = 1f,
    var angle: Float = 0f,
    var timer: Float = 0f,
    var duration: Float = 0.4f,
    var colorHex: Long = 0xFFFF6B1A
)

enum class PickupKind {
    XP_CRYSTAL,
    COIN,
    MEDKIT,
    SHIELD_ORB,
    BOSS_RELIC
}

data class PickupInstance(
    var active: Boolean = false,
    var kind: PickupKind = PickupKind.XP_CRYSTAL,
    var x: Float = 0f,
    var z: Float = 0f,
    var value: Int = 10,
    var age: Float = 0f
)

data class AllyUnit(
    var uid: Int,
    var isTurret: Boolean,
    var x: Float,
    var z: Float,
    var orbitAngle: Float,
    var fireTimer: Float,
    var lifeTimer: Float, // negative = permanent
    var damage: Float
)

data class FloatingNumber(
    val id: Int,
    val worldX: Float,
    val worldZ: Float,
    val text: String,
    val colorHex: Long,
    var timer: Float,
    val duration: Float = 0.7f
)
