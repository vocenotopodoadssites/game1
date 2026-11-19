package com.example.alienfall.model

enum class GraphicsQuality(val label: String, val maxEnemies: Int, val particleBudget: Int, val chunkRadius: Int) {
    LOW("Baixo", 38, 28, 1),
    MEDIUM("Médio", 65, 55, 2),
    HIGH("Alto", 95, 90, 2)
}

enum class PropKind(
    val label: String,
    val blocksMovement: Boolean,
    val collisionRadius: Float
) {
    DESTROYED_BUILDING("Prédio Destruído", true, 2.3f),
    ABANDONED_HOUSE("Casa Abandonada", true, 1.85f),
    CRASHED_SPACESHIP("Nave Acidentada", true, 2.6f),
    DAMAGED_VEHICLE("Veículo Danificado", true, 1.35f),
    MILITARY_BUNKER("Bunker Militar", true, 2.0f),
    DESTROYED_LAB("Laboratório Destruído", true, 2.1f),
    SHIPPING_CONTAINER("Contêiner", true, 1.45f),
    BROKEN_COMM_TOWER("Torre Quebrada", true, 0.9f),
    SATELLITE_DEBRIS("Fragmento de Satélite", true, 1.25f),
    ALIEN_ROCK("Rocha Alienígena", true, 1.15f),
    GLOWING_CRYSTAL("Cristal Luminoso", true, 0.85f),
    CONTAMINATED_FLORA("Vegetação Contaminada", false, 0.0f),
    CRATER("Cratera de Impacto", false, 0.0f)
}

data class WorldProp(
    val id: Int,
    val kind: PropKind,
    val x: Float,
    val z: Float,
    val rotation: Float,
    val scale: Float,
    val accentHex: Long
)

enum class PoiKind(
    val title: String,
    val rewardText: String
) {
    SUPPLY_POD("Cápsula de Suprimentos", "+25 Moedas e +40 XP"),
    ANCIENT_MONOLITH("Monólito Alienígena", "+15% Dano por 20s e +50 XP"),
    MED_STATION("Estação Médica", "Restaura 45% da Vida Máxima"),
    ORBITAL_RELAY("Repetidor Orbital", "+35 Moedas e Recarga Especial Completa")
}

data class PointOfInterest(
    val id: Int,
    val kind: PoiKind,
    val x: Float,
    val z: Float,
    var discovered: Boolean = false
)

data class WorldChunk(
    val cx: Int,
    val cz: Int,
    val props: List<WorldProp>,
    val poi: PointOfInterest?
)

data class GameSettings(
    val graphicsQuality: GraphicsQuality = GraphicsQuality.MEDIUM,
    val musicEnabled: Boolean = true,
    val sfxEnabled: Boolean = true,
    val manualAimDefault: Boolean = false,
    val showDamageNumbers: Boolean = true
)

data class PersistentProgress(
    val coins: Int = 100,
    val selectedHeroId: String = HeroClass.BERSERKER.id,
    val unlockedHeroIds: Set<String> = setOf(
        HeroClass.BERSERKER.id,
        HeroClass.TECNOLOGICO.id,
        HeroClass.GUERREIRO.id
    ),
    val selectedSkins: Map<String, String> = emptyMap(),
    val unlockedSkins: Set<String> = setOf(SkinVariant.DEFAULT.id),
    val permanentUpgrades: Map<String, Int> = emptyMap(),
    val unlockedEquipments: Set<String> = setOf(EquipmentItem.NONE.id),
    val equippedItemId: String = EquipmentItem.NONE.id,
    val totalKills: Int = 0,
    val totalBossesDefeated: Int = 0,
    val totalRuns: Int = 0,
    val bestSurvivalSeconds: Int = 0,
    val bestKillsInRun: Int = 0,
    val highestLevelReached: Int = 1,
    val settings: GameSettings = GameSettings()
)

data class HudSnapshot(
    val hero: HeroClass = HeroClass.BERSERKER,
    val hp: Float = 150f,
    val maxHp: Float = 150f,
    val shield: Float = 0f,
    val maxShield: Float = 0f,
    val xp: Int = 0,
    val xpToNext: Int = 40,
    val level: Int = 1,
    val survivalSeconds: Float = 0f,
    val kills: Int = 0,
    val coinsCollected: Int = 0,
    val dashCooldownRatio: Float = 1f, // 1 = ready
    val specialCooldownRatio: Float = 1f, // 1 = ready
    val specialActiveTimer: Float = 0f,
    val manualAimEnabled: Boolean = false,
    val activeBossName: String? = null,
    val activeBossSubtitle: String? = null,
    val activeBossHp: Float = 0f,
    val activeBossMaxHp: Float = 1f,
    val bossAnnouncement: String? = null,
    val poiAnnouncement: String? = null,
    val acquiredCards: Map<Int, Int> = emptyMap(),
    val damageBonusPercent: Int = 0,
    val moveSpeedBonusPercent: Int = 0,
    val attackSpeedBonusPercent: Int = 0,
    val critChancePercent: Int = 0,
    val damageReductionPercent: Int = 0,
    val minimapPlayerX: Float = 0f,
    val minimapPlayerZ: Float = 0f,
    val minimapEnemies: List<Pair<Float, Float>> = emptyList(),
    val minimapBosses: List<Pair<Float, Float>> = emptyList(),
    val minimapObstacles: List<Pair<Float, Float>> = emptyList(),
    val minimapPois: List<Triple<Float, Float, Boolean>> = emptyList()
)
