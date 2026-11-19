package com.example

import com.example.alienfall.audio.SoundEngine
import com.example.alienfall.engine.GameEngine
import com.example.alienfall.engine.RunPhase
import com.example.alienfall.model.BossMilestones
import com.example.alienfall.model.EvolutionCardCatalog
import com.example.alienfall.model.HeroClass
import com.example.alienfall.model.PersistentProgress
import com.example.alienfall.model.SkinVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlienfallCoreTest {

    @Test
    fun allThirtyCardsAndTenHeroesPresent() {
        assertEquals(30, EvolutionCardCatalog.allCards.size)
        assertEquals(10, HeroClass.entries.size)
        assertEquals(listOf(50, 150, 300, 500, 750, 1000), BossMilestones.schedule.map { it.killThreshold })
    }

    @Test
    fun cardDrawRespectsMaxLevelsAndPrerequisites() {
        // Card 23 (Infecção Viral) requires Card 13 (Aura Venenosa)
        val noPrereq = mutableMapOf<Int, Int>()
        EvolutionCardCatalog.allCards.forEach { if (it.id != 23 && it.id != 13) noPrereq[it.id] = it.maxLevel }
        val drawnWithoutPrereq = EvolutionCardCatalog.drawChoices(noPrereq, 3)
        assertEquals(1, drawnWithoutPrereq.size)
        assertEquals(13, drawnWithoutPrereq.first().id)

        // When Card 13 is acquired, Card 23 becomes eligible
        noPrereq[13] = 1
        val drawnWithPrereq = EvolutionCardCatalog.drawChoices(noPrereq, 3)
        assertTrue(drawnWithPrereq.any { it.id == 23 })

        // When ALL 30 cards are maxed, returns empty list
        val allMaxed = EvolutionCardCatalog.allCards.associate { it.id to it.maxLevel }
        assertTrue(EvolutionCardCatalog.drawChoices(allMaxed, 3).isEmpty())
    }

    @Test
    fun gameEngineStepsMovementAndCombat() {
        val engine = GameEngine(SoundEngine())
        engine.startNewRun(HeroClass.BERSERKER, SkinVariant.DEFAULT, PersistentProgress())
        assertEquals(RunPhase.RUNNING, engine.phase)
        engine.moveInputX = 1f
        engine.moveInputZ = 0f
        repeat(10) { engine.step(0.03f) }
        assertTrue(engine.playerX > 0.5f)
        assertFalse(engine.mapManager.allProps().isEmpty())
    }

    @Test
    fun selectingCardTriggersPowerVfxAndRespectsEffects() {
        val engine = GameEngine(SoundEngine())
        engine.startNewRun(
            HeroClass.BERSERKER,
            SkinVariant.DEFAULT,
            PersistentProgress(equippedItemId = "aegis_module")
        )

        // Level up via pickup -> CARD_CHOICE -> select card -> emits CARD_EVOLVE_BURST
        engine.pickups[0].apply {
            active = true
            kind = com.example.alienfall.model.PickupKind.XP_CRYSTAL
            x = 0f
            z = 0f
            value = 50
        }
        engine.step(0.02f)
        assertEquals(RunPhase.CARD_CHOICE, engine.phase)
        val picked = engine.currentCardChoices.first()
        engine.selectEvolutionCard(picked)
        assertEquals(RunPhase.RUNNING, engine.phase)
        assertTrue(engine.vfxList.any { it.active && it.kind == com.example.alienfall.model.VfxKind.CARD_EVOLVE_BURST })

        // Verify key card stats & synergies directly
        val baseDmg = engine.computePlayerDamage()
        val baseSpd = engine.computeMoveSpeed()
        val baseInterval = engine.computeAttackInterval()
        val baseCrit = engine.computeCritChance()
        val baseMaxHp = engine.maxHp

        engine.applyCardForTest(6) // Coração de Aço (+20 Max HP)
        assertEquals(baseMaxHp + 20f, engine.maxHp, 0.01f)

        engine.applyCardForTest(7) // Velocidade Sobrenatural (+10% speed)
        assertTrue(engine.computeMoveSpeed() > baseSpd * 1.09f)

        engine.applyCardForTest(8) // Ataque Acelerado
        assertTrue(engine.computeAttackInterval() < baseInterval)

        engine.applyCardForTest(9) // Força Brutal (+12% dmg)
        assertTrue(engine.computePlayerDamage() > baseDmg * 1.11f)

        engine.applyCardForTest(10) // Precisão Mortal (+5% crit)
        assertTrue(engine.computeCritChance() > baseCrit + 0.04f)

        // Card 17 (Escudo Regenerativo) preserves Aegis +15 bonus (+ 35 = 50)
        engine.applyCardForTest(17)
        assertEquals(50f, engine.maxShield, 0.01f)

        // Card 19 (Drone Protetor) + Card 30 (Renascimento Estelar)
        engine.applyCardForTest(19)
        assertEquals(1, engine.allies.size)
        engine.applyCardForTest(30)
        assertTrue(engine.isRebirthReady)

        // Card 11 (Projéteis Duplos) emits forward energy wave even on melee Berserker
        engine.applyCardForTest(11)
        engine.manualAimEnabled = true
        repeat(20) { engine.step(0.05f) }
        assertTrue(engine.projectiles.any { it.active && it.kind == com.example.alienfall.model.ProjectileKind.ENERGY_BOLT })
    }

    @Test
    fun arenaGroundTextureGeneratesCracksMagmaFissuresAndPuddles() {
        val pixels = com.example.alienfall.renderer.MeshBuilder.buildArenaGroundTexturePixels(64)
        assertEquals(64 * 64, pixels.size)
        var hasWarmEarth = false
        var hasMagmaGlow = false
        var hasSlatePuddle = false
        for (px in pixels) {
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            if (r in 45..130 && r > b) hasWarmEarth = true
            if (r >= 170 && g >= 70 && b < 110) hasMagmaGlow = true
            if (b > r + 10) hasSlatePuddle = true
        }
        assertTrue("Expected warm wasteland soil tones", hasWarmEarth)
        assertTrue("Expected glowing orange magma fissures", hasMagmaGlow)
        assertTrue("Expected reflective blue-slate puddles", hasSlatePuddle)
    }
}
