package com.example.alienfall.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.RarityCommon
import com.example.ui.theme.RarityEpic
import com.example.ui.theme.RarityLegendary
import com.example.ui.theme.RarityRare
import kotlin.random.Random

enum class CardRarity(
    val label: String,
    val weight: Float,
    val color: Color
) {
    COMMON("Comum", 0.55f, RarityCommon),
    RARE("Rara", 0.28f, RarityRare),
    EPIC("Épica", 0.13f, RarityEpic),
    LEGENDARY("Lendária", 0.04f, RarityLegendary)
}

enum class CardCategory(val label: String) {
    COMBAT("Combate"),
    COSMIC("Cósmica"),
    BIO("Sobrevivência & Bio")
}

data class EvolutionCardDef(
    val id: Int,
    val code: String,
    val name: String,
    val description: String,
    val rarity: CardRarity,
    val category: CardCategory,
    val maxLevel: Int,
    val synergyTags: List<String>,
    val requiredCardId: Int? = null,
    val levelPreview: (Int) -> String
)

object EvolutionCardCatalog {

    val allCards: List<EvolutionCardDef> = listOf(
        EvolutionCardDef(
            id = 1, code = "01", name = "Regeneração Celular",
            description = "Restaura 2 pontos de vida por segundo. Cada nível aumenta a regeneração em 1 ponto.",
            rarity = CardRarity.COMMON, category = CardCategory.BIO, maxLevel = 5,
            synergyTags = listOf("Sustentação", "Bio"),
            levelPreview = { lvl -> "Regen: ${1 + lvl} -> ${2 + lvl} HP/s" }
        ),
        EvolutionCardDef(
            id = 2, code = "02", name = "Vampirismo Alienígena",
            description = "Recupera vida ao derrotar inimigos (máximo 2 ativações por segundo).",
            rarity = CardRarity.RARE, category = CardCategory.BIO, maxLevel = 4,
            synergyTags = listOf("Abate", "Bio"),
            levelPreview = { lvl -> "Cura/abate: ${lvl * 2} -> ${(lvl + 1) * 2} HP" }
        ),
        EvolutionCardDef(
            id = 3, code = "03", name = "Ímã de Cristais",
            description = "Aumenta em 35% o raio de coleta de experiência e moedas.",
            rarity = CardRarity.COMMON, category = CardCategory.COSMIC, maxLevel = 4,
            synergyTags = listOf("Coleta", "Economia"),
            levelPreview = { lvl -> "Raio: +${lvl * 35}% -> +${(lvl + 1) * 35}%" }
        ),
        EvolutionCardDef(
            id = 4, code = "04", name = "Fortuna Galáctica",
            description = "Aumenta em 20% a quantidade de moedas recebidas.",
            rarity = CardRarity.COMMON, category = CardCategory.COSMIC, maxLevel = 5,
            synergyTags = listOf("Economia", "Coleta"),
            levelPreview = { lvl -> "Moedas: +${lvl * 20}% -> +${(lvl + 1) * 20}%" }
        ),
        EvolutionCardDef(
            id = 5, code = "05", name = "Pele de Titânio",
            description = "Reduz em 8% o dano recebido, respeitando limite máximo de redução.",
            rarity = CardRarity.COMMON, category = CardCategory.COMBAT, maxLevel = 5,
            synergyTags = listOf("Defesa", "Blindagem"),
            levelPreview = { lvl -> "Redução: ${lvl * 8}% -> ${(lvl + 1) * 8}%" }
        ),
        EvolutionCardDef(
            id = 6, code = "06", name = "Coração de Aço",
            description = "Aumenta a vida máxima em 20 pontos e restaura a mesma quantidade.",
            rarity = CardRarity.COMMON, category = CardCategory.BIO, maxLevel = 6,
            synergyTags = listOf("Defesa", "Vitalidade"),
            levelPreview = { lvl -> "Bônus Vida: +${lvl * 20} -> +${(lvl + 1) * 20}" }
        ),
        EvolutionCardDef(
            id = 7, code = "07", name = "Velocidade Sobrenatural",
            description = "Aumenta em 10% a velocidade de movimentação.",
            rarity = CardRarity.COMMON, category = CardCategory.COMBAT, maxLevel = 4,
            synergyTags = listOf("Mobilidade"),
            levelPreview = { lvl -> "Velocidade: +${lvl * 10}% -> +${(lvl + 1) * 10}%" }
        ),
        EvolutionCardDef(
            id = 8, code = "08", name = "Ataque Acelerado",
            description = "Reduz em 8% o intervalo entre ataques.",
            rarity = CardRarity.RARE, category = CardCategory.COMBAT, maxLevel = 5,
            synergyTags = listOf("Cadência", "Dano"),
            levelPreview = { lvl -> "Cadência: +${lvl * 8}% -> +${(lvl + 1) * 8}%" }
        ),
        EvolutionCardDef(
            id = 9, code = "09", name = "Força Brutal",
            description = "Aumenta em 12% o dano causado em todos os ataques.",
            rarity = CardRarity.COMMON, category = CardCategory.COMBAT, maxLevel = 6,
            synergyTags = listOf("Dano", "Físico"),
            levelPreview = { lvl -> "Dano: +${lvl * 12}% -> +${(lvl + 1) * 12}%" }
        ),
        EvolutionCardDef(
            id = 10, code = "10", name = "Precisão Mortal",
            description = "Aumenta em 5% a chance de ataque crítico (2x de dano).",
            rarity = CardRarity.RARE, category = CardCategory.COMBAT, maxLevel = 5,
            synergyTags = listOf("Crítico", "Dano"),
            levelPreview = { lvl -> "Crítico: +${lvl * 5}% -> +${(lvl + 1) * 5}%" }
        ),
        EvolutionCardDef(
            id = 11, code = "11", name = "Projéteis Duplos",
            description = "Adiciona +1 projétil ou onda adicional aos ataques compatíveis.",
            rarity = CardRarity.EPIC, category = CardCategory.COMBAT, maxLevel = 3,
            synergyTags = listOf("Projétil", "Multi"),
            levelPreview = { lvl -> "Extras: +$lvl -> +${lvl + 1}" }
        ),
        EvolutionCardDef(
            id = 12, code = "12", name = "Munição Perfurante",
            description = "Projéteis atravessam +1 inimigo adicional.",
            rarity = CardRarity.RARE, category = CardCategory.COMBAT, maxLevel = 3,
            synergyTags = listOf("Projétil", "Perfuração"),
            levelPreview = { lvl -> "Perfuração: +$lvl -> +${lvl + 1}" }
        ),
        EvolutionCardDef(
            id = 13, code = "13", name = "Aura Venenosa",
            description = "Cria uma aura tóxica que aplica veneno contínuo aos alienígenas próximos.",
            rarity = CardRarity.RARE, category = CardCategory.BIO, maxLevel = 4,
            synergyTags = listOf("Veneno", "Aura"),
            levelPreview = { lvl -> "Veneno/s: ${4 + lvl * 3} -> ${4 + (lvl + 1) * 3}" }
        ),
        EvolutionCardDef(
            id = 14, code = "14", name = "Chamas Cósmicas",
            description = "Ataques têm chance de incendiar inimigos, causando dano por segundo.",
            rarity = CardRarity.RARE, category = CardCategory.COSMIC, maxLevel = 4,
            synergyTags = listOf("Fogo", "Elemental"),
            levelPreview = { lvl -> "Queimadura: ${15 + lvl * 12}% chance" }
        ),
        EvolutionCardDef(
            id = 15, code = "15", name = "Raio Encadeado",
            description = "Ataques podem liberar eletricidade que salta entre até 3 inimigos.",
            rarity = CardRarity.EPIC, category = CardCategory.COSMIC, maxLevel = 4,
            synergyTags = listOf("Eletricidade", "Cadeia"),
            levelPreview = { lvl -> "Chance: ${20 + lvl * 10}% -> ${20 + (lvl + 1) * 10}%" }
        ),
        EvolutionCardDef(
            id = 16, code = "16", name = "Explosão de Plasma",
            description = "Inimigos derrotados podem provocar explosão de energia em área.",
            rarity = CardRarity.RARE, category = CardCategory.COSMIC, maxLevel = 4,
            synergyTags = listOf("Explosão", "Área"),
            levelPreview = { lvl -> "Chance: ${25 + lvl * 12}% -> ${25 + (lvl + 1) * 12}%" }
        ),
        EvolutionCardDef(
            id = 17, code = "17", name = "Escudo Regenerativo",
            description = "Gera um escudo energético que se regenera após 4s sem sofrer dano.",
            rarity = CardRarity.RARE, category = CardCategory.COSMIC, maxLevel = 4,
            synergyTags = listOf("Escudo", "Defesa"),
            levelPreview = { lvl -> "Escudo: ${20 + lvl * 15} -> ${20 + (lvl + 1) * 15}" }
        ),
        EvolutionCardDef(
            id = 18, code = "18", name = "Congelamento Temporal",
            description = "Ataques têm chance de reduzir temporariamente a velocidade dos inimigos.",
            rarity = CardRarity.RARE, category = CardCategory.COSMIC, maxLevel = 4,
            synergyTags = listOf("Controle", "Temporal"),
            levelPreview = { lvl -> "Lentidão: ${25 + lvl * 10}% por ${1.5f + lvl * 0.4f}s" }
        ),
        EvolutionCardDef(
            id = 19, code = "19", name = "Drone Protetor",
            description = "Invoca um drone orbital que dispara plasma automaticamente nos inimigos.",
            rarity = CardRarity.EPIC, category = CardCategory.COMBAT, maxLevel = 4,
            synergyTags = listOf("Drone", "Automático"),
            levelPreview = { lvl -> "Drones: $lvl -> ${lvl + 1} (dano escala)" }
        ),
        EvolutionCardDef(
            id = 20, code = "20", name = "Lâminas Orbitais",
            description = "Cria lâminas energéticas que giram ao redor do herói e cortam inimigos.",
            rarity = CardRarity.EPIC, category = CardCategory.COMBAT, maxLevel = 4,
            synergyTags = listOf("Orbital", "Físico"),
            levelPreview = { lvl -> "Lâminas: ${lvl + 1} -> ${lvl + 2}" }
        ),
        EvolutionCardDef(
            id = 21, code = "21", name = "Meteoro Arcano",
            description = "Invoca periodicamente um meteoro sobre concentrações de inimigos.",
            rarity = CardRarity.EPIC, category = CardCategory.COSMIC, maxLevel = 4,
            synergyTags = listOf("Fogo", "Área"),
            levelPreview = { lvl -> "Dano: ${40 + lvl * 20} -> ${40 + (lvl + 1) * 20}" }
        ),
        EvolutionCardDef(
            id = 22, code = "22", name = "Onda Repulsora",
            description = "Em intervalos regulares, emite pulso que empurra inimigos próximos.",
            rarity = CardRarity.COMMON, category = CardCategory.COSMIC, maxLevel = 4,
            synergyTags = listOf("Controle", "Área"),
            levelPreview = { lvl -> "Recarga: ${6.0f - lvl * 0.7f}s -> ${6.0f - (lvl + 1) * 0.7f}s" }
        ),
        EvolutionCardDef(
            id = 23, code = "23", name = "Infecção Viral",
            description = "Inimigos envenenados espalham toxinas para alienígenas próximos ao morrer.",
            rarity = CardRarity.EPIC, category = CardCategory.BIO, maxLevel = 3,
            synergyTags = listOf("Veneno", "Sinergia"),
            requiredCardId = 13,
            levelPreview = { lvl -> "Alcance Viral: ${3.2f + lvl * 0.8f}m" }
        ),
        EvolutionCardDef(
            id = 24, code = "24", name = "Fúria Crescente",
            description = "Concede bônus temporário de dano ao abater inimigos em sequência.",
            rarity = CardRarity.RARE, category = CardCategory.COMBAT, maxLevel = 4,
            synergyTags = listOf("Abate", "Dano"),
            levelPreview = { lvl -> "Máx Bônus: +${18 + lvl * 8}% -> +${18 + (lvl + 1) * 8}%" }
        ),
        EvolutionCardDef(
            id = 25, code = "25", name = "Eco de Ataque",
            description = "Ataques têm chance de repetir parte do dano após um breve instante.",
            rarity = CardRarity.EPIC, category = CardCategory.COSMIC, maxLevel = 3,
            synergyTags = listOf("Eco", "Dano"),
            levelPreview = { lvl -> "Chance Eco: ${20 + lvl * 12}% -> ${20 + (lvl + 1) * 12}%" }
        ),
        EvolutionCardDef(
            id = 26, code = "26", name = "Campo Gravitacional",
            description = "Cria periodicamente uma zona que desacelera inimigos ao redor.",
            rarity = CardRarity.RARE, category = CardCategory.COSMIC, maxLevel = 4,
            synergyTags = listOf("Controle", "Gravitacional"),
            levelPreview = { lvl -> "Redução Vel: ${30 + lvl * 10}%" }
        ),
        EvolutionCardDef(
            id = 27, code = "27", name = "Colheita de Almas",
            description = "A cada 12 inimigos derrotados, restaura vida proporcional ao nível.",
            rarity = CardRarity.RARE, category = CardCategory.BIO, maxLevel = 4,
            synergyTags = listOf("Abate", "Sustentação"),
            levelPreview = { lvl -> "Cura: ${6 + lvl * 4} -> ${6 + (lvl + 1) * 4} HP" }
        ),
        EvolutionCardDef(
            id = 28, code = "28", name = "Armadura Reativa",
            description = "Ao receber dano, libera descarga elétrica em área (recarga de 2.5s).",
            rarity = CardRarity.RARE, category = CardCategory.COMBAT, maxLevel = 4,
            synergyTags = listOf("Eletricidade", "Defesa"),
            levelPreview = { lvl -> "Dano Choque: ${22 + lvl * 12} -> ${22 + (lvl + 1) * 12}" }
        ),
        EvolutionCardDef(
            id = 29, code = "29", name = "Evolução Suprema",
            description = "Aumenta o dano de todas as habilidades especiais e passivas (máx. +45%).",
            rarity = CardRarity.LEGENDARY, category = CardCategory.COSMIC, maxLevel = 3,
            synergyTags = listOf("Suprema", "Sinergia"),
            levelPreview = { lvl -> "Poder Habilidades: +${lvl * 15}% -> +${(lvl + 1) * 15}%" }
        ),
        EvolutionCardDef(
            id = 30, code = "30", name = "Renascimento Estelar",
            description = "Uma vez por partida, revive com 60% da vida e onda solar devastadora.",
            rarity = CardRarity.LEGENDARY, category = CardCategory.BIO, maxLevel = 1,
            synergyTags = listOf("Lendária", "Sobrevivência"),
            levelPreview = { _ -> "Reviver: 0 -> 1 uso por partida" }
        )
    )

    fun byId(id: Int): EvolutionCardDef? = allCards.firstOrNull { it.id == id }

    /**
     * Normalized rarity draw respecting max levels & prerequisites.
     */
    fun drawChoices(
        acquiredLevels: Map<Int, Int>,
        count: Int = 3,
        preferHighRarity: Boolean = false
    ): List<EvolutionCardDef> {
        val eligible = allCards.filter { def ->
            val currentLvl = acquiredLevels[def.id] ?: 0
            val underMax = currentLvl < def.maxLevel
            val prereqMet = def.requiredCardId == null || (acquiredLevels[def.requiredCardId] ?: 0) > 0
            underMax && prereqMet
        }.toMutableList()

        if (eligible.isEmpty()) return emptyList()
        val chosen = mutableListOf<EvolutionCardDef>()
        val pool = eligible.toMutableList()

        while (chosen.size < count && pool.isNotEmpty()) {
            val weights = pool.map { card ->
                val base = card.rarity.weight
                val bonus = if (preferHighRarity && card.rarity != CardRarity.COMMON) 2.2f else 1.0f
                val alreadyOwnedBoost = if ((acquiredLevels[card.id] ?: 0) > 0) 1.2f else 1.0f
                base * bonus * alreadyOwnedBoost
            }
            val totalWeight = weights.sum()
            var roll = Random.nextFloat() * totalWeight
            var pickedIndex = pool.lastIndex
            for (i in pool.indices) {
                roll -= weights[i]
                if (roll <= 0f) {
                    pickedIndex = i
                    break
                }
            }
            chosen.add(pool.removeAt(pickedIndex))
        }
        return chosen
    }
}
