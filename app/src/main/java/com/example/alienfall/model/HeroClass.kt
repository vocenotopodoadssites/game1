package com.example.alienfall.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.ArcaneViolet
import com.example.ui.theme.BioVenomGreen
import com.example.ui.theme.CrimsonCore
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.PlasmaOrange
import com.example.ui.theme.ShieldBlue
import com.example.ui.theme.SolarGold

enum class AttackStyle {
    MELEE_TWIN_BLADES,
    MELEE_SWORD_SHIELD,
    MELEE_KATANA,
    PLASMA_DRONE,
    RAIL_RIFLE,
    ARCANE_STAFF,
    CORRUPTION_ORB,
    STAR_BOW,
    WRENCH_CANNON
}

enum class SkinVariant(
    val id: String,
    val displayName: String,
    val cost: Int,
    val accentHex: Long,
    val trimHex: Long
) {
    DEFAULT("default", "Padrão de Combate", 0, 0xFFFF6B1A, 0xFF1F2940),
    CRIMSON_ECLIPSE("crimson", "Eclipse Carmesim", 120, 0xFFFF2E4C, 0xFF2B0E17),
    VOID_COBALT("cobalt", "Cobalto Orbital", 180, 0xFF00E5FF, 0xFF0D253F),
    SOLAR_APEX("solar", "Ápice Dourado", 250, 0xFFFFD166, 0xFF33240B)
}

enum class HeroClass(
    val id: String,
    val title: String,
    val subtitle: String,
    val roleDescription: String,
    val baseHp: Float,
    val baseDamage: Float,
    val moveSpeed: Float,
    val attackInterval: Float,
    val attackRange: Float,
    val attackStyle: AttackStyle,
    val passiveName: String,
    val passiveDesc: String,
    val specialName: String,
    val specialDesc: String,
    val specialCooldown: Float,
    val unlockCost: Int,
    val primaryColor: Color,
    val secondaryColor: Color,
    val glbModelPath: String
) {
    BERSERKER(
        id = "berserker",
        title = "Berserker",
        subtitle = "Destruidor",
        roleDescription = "Guerreiro infernal com chifres flamejantes, ombreira de crânio demoníaco, capa carmesim e machado crescente de magma.",
        baseHp = 150f,
        baseDamage = 18f,
        moveSpeed = 5.8f,
        attackInterval = 0.44f,
        attackRange = 3.2f,
        attackStyle = AttackStyle.MELEE_TWIN_BLADES,
        passiveName = "Sede de Batalha",
        passiveDesc = "+1% de dano a cada 10 inimigos derrotados (máx. 30%).",
        specialName = "Fúria Alienígena",
        specialDesc = "Aumenta velocidade de ataque em +65% e dano em +40% por 6s.",
        specialCooldown = 14f,
        unlockCost = 0,
        primaryColor = PlasmaOrange,
        secondaryColor = CrimsonCore,
        glbModelPath = "models/heroes/berserker.glb"
    ),
    TECNOLOGICO(
        id = "tecnologico",
        title = "Tecnológico",
        subtitle = "Engenheiro de Combate",
        roleDescription = "Soldado futurista com armadura robótica, emissor de plasma e drones autônomos.",
        baseHp = 110f,
        baseDamage = 12f,
        moveSpeed = 5.7f,
        attackInterval = 0.48f,
        attackRange = 9.5f,
        attackStyle = AttackStyle.PLASMA_DRONE,
        passiveName = "Protocolo Drone",
        passiveDesc = "Todos os drones aliados causam +15% de dano.",
        specialName = "Enxame Mecânico",
        specialDesc = "Invoca 3 drones de combate temporários por 10s.",
        specialCooldown = 16f,
        unlockCost = 0,
        primaryColor = CyberCyan,
        secondaryColor = ShieldBlue,
        glbModelPath = "models/heroes/tecnologico.glb"
    ),
    ATIRADOR(
        id = "atirador",
        title = "Atirador",
        subtitle = "Caçador de Elite",
        roleDescription = "Especialista de alta mobilidade equipado com rifle eletromagnético de precisão.",
        baseHp = 90f,
        baseDamage = 20f,
        moveSpeed = 6.7f,
        attackInterval = 0.56f,
        attackRange = 13.5f,
        attackStyle = AttackStyle.RAIL_RIFLE,
        passiveName = "Mira Letal",
        passiveDesc = "+10% de chance de acerto crítico.",
        specialName = "Chuva de Projéteis",
        specialDesc = "Dispara rajada orbital em cone com múltiplos projéteis perfurantes.",
        specialCooldown = 13f,
        unlockCost = 80,
        primaryColor = SolarGold,
        secondaryColor = PlasmaOrange,
        glbModelPath = "models/heroes/atirador.glb"
    ),
    MAGO(
        id = "mago",
        title = "Mago",
        subtitle = "Arcanista Cósmico",
        roleDescription = "Mestre arcano de manto índigo e ouro empunhando cajado de cristal cósmico.",
        baseHp = 85f,
        baseDamage = 16f,
        moveSpeed = 5.7f,
        attackInterval = 0.58f,
        attackRange = 10.5f,
        attackStyle = AttackStyle.ARCANE_STAFF,
        passiveName = "Fluxo Astral",
        passiveDesc = "Habilidades mágicas têm 10% menos tempo de recarga.",
        specialName = "Tempestade Cósmica",
        specialDesc = "Libera uma tempestade de raios e energia ao redor por 5s.",
        specialCooldown = 15f,
        unlockCost = 100,
        primaryColor = ArcaneViolet,
        secondaryColor = CyberCyan,
        glbModelPath = "models/heroes/mago.glb"
    ),
    BRUXO(
        id = "bruxo",
        title = "Bruxo",
        subtitle = "Mestre da Corrupção",
        roleDescription = "Conjurador sombrio que domina venenos alienígenas, maldições e pragas virais.",
        baseHp = 100f,
        baseDamage = 14f,
        moveSpeed = 5.7f,
        attackInterval = 0.54f,
        attackRange = 10.0f,
        attackStyle = AttackStyle.CORRUPTION_ORB,
        passiveName = "Toxina Persistente",
        passiveDesc = "Efeitos de veneno duram +20% mais tempo.",
        specialName = "Praga Alienígena",
        specialDesc = "Infecta inimigos próximos com maldição que explode ao morrer.",
        specialCooldown = 14f,
        unlockCost = 120,
        primaryColor = BioVenomGreen,
        secondaryColor = ArcaneViolet,
        glbModelPath = "models/heroes/bruxo.glb"
    ),
    GUERREIRO(
        id = "guerreiro",
        title = "Guerreiro",
        subtitle = "Guardião da Terra",
        roleDescription = "Combatente blindado com espada energética e escudo holográfico de defesa.",
        baseHp = 140f,
        baseDamage = 15f,
        moveSpeed = 5.8f,
        attackInterval = 0.48f,
        attackRange = 3.5f,
        attackStyle = AttackStyle.MELEE_SWORD_SHIELD,
        passiveName = "Bastião Terrestre",
        passiveDesc = "Reduz em 10% todo o dano recebido.",
        specialName = "Onda de Choque",
        specialDesc = "Impacto sísmico que empurra inimigos e causa dano em área.",
        specialCooldown = 12f,
        unlockCost = 0,
        primaryColor = ShieldBlue,
        secondaryColor = SolarGold,
        glbModelPath = "models/heroes/guerreiro.glb"
    ),
    ARQUEIRO(
        id = "arqueiro",
        title = "Arqueiro",
        subtitle = "Rastreador Estelar",
        roleDescription = "Batedor silencioso com arco estelar capaz de perfurar múltiplas ameaças.",
        baseHp = 95f,
        baseDamage = 17f,
        moveSpeed = 6.6f,
        attackInterval = 0.45f,
        attackRange = 12.0f,
        attackStyle = AttackStyle.STAR_BOW,
        passiveName = "Flechas Fantasmas",
        passiveDesc = "15% de chance de os projéteis atravessarem +1 inimigo.",
        specialName = "Flechas Estelares",
        specialDesc = "Dispara 12 flechas estelares em leque radial de alta velocidade.",
        specialCooldown = 11f,
        unlockCost = 90,
        primaryColor = CyberCyan,
        secondaryColor = BioVenomGreen,
        glbModelPath = "models/heroes/arqueiro.glb"
    ),
    GLADIADOR(
        id = "gladiador",
        title = "Gladiador",
        subtitle = "Campeão de Arena",
        roleDescription = "Guerreiro blindado em bronze e carmesim com manto rubro, gládio e Escudo do Leão.",
        baseHp = 145f,
        baseDamage = 16f,
        moveSpeed = 5.9f,
        attackInterval = 0.46f,
        attackRange = 3.3f,
        attackStyle = AttackStyle.MELEE_SWORD_SHIELD,
        passiveName = "Égide do Leão",
        passiveDesc = "Reduz 15% do dano frontal e reflete choque nos atacantes.",
        specialName = "Rugido da Arena",
        specialDesc = "Investida com escudo que atordoa e aumenta dano por 5s.",
        specialCooldown = 13f,
        unlockCost = 140,
        primaryColor = SolarGold,
        secondaryColor = CrimsonCore,
        glbModelPath = "models/heroes/gladiador.glb"
    ),
    SAMURAI_NEON(
        id = "samurai_neon",
        title = "Samurai Neon",
        subtitle = "Lâmina Cyberpunk",
        roleDescription = "Infiltrador cibernético com visor luminoso e katana de frequência neon.",
        baseHp = 105f,
        baseDamage = 19f,
        moveSpeed = 6.8f,
        attackInterval = 0.39f,
        attackRange = 3.6f,
        attackStyle = AttackStyle.MELEE_KATANA,
        passiveName = "Reflexo Sináptico",
        passiveDesc = "Recarga da esquiva 15% menor; esquiva corta inimigos próximos.",
        specialName = "Corte Dimensional",
        specialDesc = "Desfere múltiplos cortes em arco amplo causando dano crítico.",
        specialCooldown = 12f,
        unlockCost = 160,
        primaryColor = CyberCyan,
        secondaryColor = CrimsonCore,
        glbModelPath = "models/heroes/samurai_neon.glb"
    ),
    INVENTOR(
        id = "inventor",
        title = "Inventor",
        subtitle = "Mestre das Engrenagens",
        roleDescription = "Gnomo steampunk com mochila alquímica brilhante, óculos de latão e chave-canhão.",
        baseHp = 115f,
        baseDamage = 14f,
        moveSpeed = 5.6f,
        attackInterval = 0.50f,
        attackRange = 9.0f,
        attackStyle = AttackStyle.WRENCH_CANNON,
        passiveName = "Reator Alquímico",
        passiveDesc = "+25% raio de coleta e +15% dano de engenhocas e explosões.",
        specialName = "Torreta Alquímica",
        specialDesc = "Implanta uma torreta automática e frascos ácidos por 10s.",
        specialCooldown = 15f,
        unlockCost = 150,
        primaryColor = BioVenomGreen,
        secondaryColor = SolarGold,
        glbModelPath = "models/heroes/inventor.glb"
    );

    companion object {
        fun fromId(id: String): HeroClass = entries.firstOrNull { it.id == id } ?: BERSERKER
    }
}

enum class PermanentUpgradeType(
    val id: String,
    val title: String,
    val description: String,
    val maxLevel: Int,
    val baseCost: Int,
    val costStep: Int,
    val bonusPerLevelText: String
) {
    VITALITY("vitality", "Núcleo Vital", "Aumenta a vida máxima inicial em todas as partidas.", 8, 40, 35, "+15 Vida"),
    MIGHT("might", "Forja de Plasma", "Aumenta o dano base de todos os ataques.", 8, 50, 45, "+8% Dano"),
    AGILITY("agility", "Propulsores Táticos", "Aumenta a velocidade de movimento.", 5, 45, 40, "+5% Velocidade"),
    ARMOR("armor", "Revestimento Titânico", "Reduz o dano recebido de ataques alienígenas.", 5, 55, 50, "+3% Redução"),
    GREED("greed", "Extrator Estelar", "Aumenta o ganho de moedas e raio de coleta.", 6, 40, 35, "+12% Moedas/Raio"),
    COOLDOWN("cooldown", "Capacitor Quântico", "Reduz o tempo de recarga das habilidades especiais.", 5, 60, 55, "-5% Recarga");

    fun costForNext(currentLevel: Int): Int = baseCost + currentLevel * costStep
}

enum class EquipmentItem(
    val id: String,
    val title: String,
    val description: String,
    val cost: Int,
    val bonusTag: String
) {
    NONE("none", "Sem Equipamento Extra", "Equipamento padrão de combate.", 0, "Padrão"),
    PLASMA_CORE("plasma_core", "Núcleo de Plasma Mk-II", "+10% Dano e +10% Velocidade de Ataque.", 220, "+10% Dano/Vel"),
    AEGIS_MODULE("aegis_module", "Módulo Égide Orbital", "+25 Vida Máxima e +15 Escudo Inicial.", 240, "+25 HP / +15 Esc"),
    MAGNET_COIL("magnet_coil", "Bobina Gravitacional", "+40% Raio de Coleta e +15% XP.", 200, "+40% Raio / +15% XP"),
    CHRONO_BOOSTER("chrono_booster", "Propulsor Crono", "+12% Velocidade e -18% Recarga de Esquiva.", 260, "+12% Vel / -18% Esq");

    companion object {
        fun fromId(id: String): EquipmentItem = entries.firstOrNull { it.id == id } ?: NONE
    }
}
