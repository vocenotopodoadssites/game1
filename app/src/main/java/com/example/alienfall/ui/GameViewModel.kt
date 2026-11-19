package com.example.alienfall.ui

import android.app.Application
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alienfall.audio.MusicMode
import com.example.alienfall.audio.SfxType
import com.example.alienfall.audio.SoundEngine
import com.example.alienfall.data.PersistenceManager
import com.example.alienfall.engine.GameEngine
import com.example.alienfall.engine.RunPhase
import com.example.alienfall.model.EquipmentItem
import com.example.alienfall.model.EvolutionCardDef
import com.example.alienfall.model.GameSettings
import com.example.alienfall.model.HeroClass
import com.example.alienfall.model.HudSnapshot
import com.example.alienfall.model.PermanentUpgradeType
import com.example.alienfall.model.PersistentProgress
import com.example.alienfall.model.SkinVariant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppScreen {
    HOME,
    CHARACTER_SELECT,
    UPGRADES,
    STATS,
    SETTINGS,
    PLAYING
}

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val persistence = PersistenceManager(application)
    val soundEngine = SoundEngine()
    val gameEngine = GameEngine(soundEngine)

    private val _screen = MutableStateFlow(AppScreen.HOME)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    private val _progress = MutableStateFlow(PersistentProgress())
    val progress: StateFlow<PersistentProgress> = _progress.asStateFlow()

    private val _hud = MutableStateFlow(HudSnapshot())
    val hud: StateFlow<HudSnapshot> = _hud.asStateFlow()

    private val _runPhase = MutableStateFlow(RunPhase.RUNNING)
    val runPhase: StateFlow<RunPhase> = _runPhase.asStateFlow()

    private val _cardChoices = MutableStateFlow<List<EvolutionCardDef>>(emptyList())
    val cardChoices: StateFlow<List<EvolutionCardDef>> = _cardChoices.asStateFlow()

    private var runFinalized = false
    private val pressedKeys = HashSet<Int>()

    init {
        soundEngine.start()
        soundEngine.musicMode = MusicMode.MENU
        viewModelScope.launch {
            val loaded = persistence.load()
            _progress.value = loaded
            soundEngine.musicEnabled = loaded.settings.musicEnabled
            soundEngine.sfxEnabled = loaded.settings.sfxEnabled
        }
    }

    override fun onCleared() {
        super.onCleared()
        soundEngine.stop()
    }

    fun navigateTo(target: AppScreen) {
        _screen.value = target
        if (target != AppScreen.PLAYING) {
            soundEngine.musicMode = MusicMode.MENU
        }
    }

    fun startMission() {
        val p = _progress.value
        val hero = HeroClass.fromId(p.selectedHeroId)
        val skinId = p.selectedSkins[hero.id] ?: SkinVariant.DEFAULT.id
        val skin = SkinVariant.entries.firstOrNull { it.id == skinId } ?: SkinVariant.DEFAULT

        runFinalized = false
        pressedKeys.clear()
        gameEngine.startNewRun(hero, skin, p)
        _runPhase.value = RunPhase.RUNNING
        _cardChoices.value = emptyList()
        _hud.value = gameEngine.snapshotHud()
        _screen.value = AppScreen.PLAYING
    }

    fun syncHudFromRenderer() {
        val phaseNow = gameEngine.phase
        if (_runPhase.value != phaseNow) {
            _runPhase.value = phaseNow
            if (phaseNow == RunPhase.CARD_CHOICE) {
                _cardChoices.value = gameEngine.currentCardChoices
            } else if ((phaseNow == RunPhase.GAME_OVER || phaseNow == RunPhase.VICTORY) && !runFinalized) {
                finalizeRunStats()
            }
        }
        _hud.value = gameEngine.snapshotHud()
    }

    fun pickCard(card: EvolutionCardDef?) {
        gameEngine.selectEvolutionCard(card)
        _cardChoices.value = gameEngine.currentCardChoices
        _runPhase.value = gameEngine.phase
        _hud.value = gameEngine.snapshotHud()
    }

    fun togglePause() {
        if (_screen.value != AppScreen.PLAYING) return
        val currentlyPaused = gameEngine.phase == RunPhase.PAUSED
        if (gameEngine.phase == RunPhase.RUNNING || currentlyPaused) {
            gameEngine.setPaused(!currentlyPaused)
            _runPhase.value = gameEngine.phase
        }
    }

    fun toggleManualAim() {
        gameEngine.manualAimEnabled = !gameEngine.manualAimEnabled
        _hud.value = gameEngine.snapshotHud()
    }

    fun continueEndless() {
        runFinalized = false
        gameEngine.continueEndlessAfterVictory()
        _runPhase.value = gameEngine.phase
    }

    fun abandonAndReturnHome() {
        if (!runFinalized) {
            finalizeRunStats()
        }
        _screen.value = AppScreen.HOME
        soundEngine.musicMode = MusicMode.MENU
    }

    fun restartMission() {
        if (!runFinalized) {
            finalizeRunStats()
        }
        startMission()
    }

    private fun finalizeRunStats() {
        runFinalized = true
        val p = _progress.value
        val updated = p.copy(
            coins = p.coins + gameEngine.coinsCollected,
            totalKills = p.totalKills + gameEngine.kills,
            totalBossesDefeated = p.totalBossesDefeated + gameEngine.bossesDefeated,
            totalRuns = p.totalRuns + 1,
            bestSurvivalSeconds = maxOf(p.bestSurvivalSeconds, gameEngine.survivalSeconds.toInt()),
            bestKillsInRun = maxOf(p.bestKillsInRun, gameEngine.kills),
            highestLevelReached = maxOf(p.highestLevelReached, gameEngine.level)
        )
        saveProgress(updated)
    }

    fun selectOrUnlockHero(hero: HeroClass) {
        val p = _progress.value
        if (p.unlockedHeroIds.contains(hero.id)) {
            saveProgress(p.copy(selectedHeroId = hero.id))
            soundEngine.playSfx(SfxType.CARD_SELECT)
        } else if (p.coins >= hero.unlockCost) {
            saveProgress(
                p.copy(
                    coins = p.coins - hero.unlockCost,
                    unlockedHeroIds = p.unlockedHeroIds + hero.id,
                    selectedHeroId = hero.id
                )
            )
            soundEngine.playSfx(SfxType.LEVEL_UP)
        }
    }

    fun selectOrUnlockSkin(hero: HeroClass, skin: SkinVariant) {
        val p = _progress.value
        if (p.unlockedSkins.contains(skin.id)) {
            val map = p.selectedSkins.toMutableMap()
            map[hero.id] = skin.id
            saveProgress(p.copy(selectedSkins = map))
            soundEngine.playSfx(SfxType.CARD_SELECT)
        } else if (p.coins >= skin.cost) {
            val map = p.selectedSkins.toMutableMap()
            map[hero.id] = skin.id
            saveProgress(
                p.copy(
                    coins = p.coins - skin.cost,
                    unlockedSkins = p.unlockedSkins + skin.id,
                    selectedSkins = map
                )
            )
            soundEngine.playSfx(SfxType.LEVEL_UP)
        }
    }

    fun purchaseUpgrade(upgrade: PermanentUpgradeType) {
        val p = _progress.value
        val curLvl = p.permanentUpgrades[upgrade.id] ?: 0
        if (curLvl >= upgrade.maxLevel) return
        val cost = upgrade.costForNext(curLvl)
        if (p.coins < cost) return

        val nextMap = p.permanentUpgrades.toMutableMap()
        nextMap[upgrade.id] = curLvl + 1
        saveProgress(p.copy(coins = p.coins - cost, permanentUpgrades = nextMap))
        soundEngine.playSfx(SfxType.LEVEL_UP)
    }

    fun unlockOrEquipEquipment(item: EquipmentItem) {
        val p = _progress.value
        if (p.unlockedEquipments.contains(item.id)) {
            saveProgress(p.copy(equippedItemId = item.id))
            soundEngine.playSfx(SfxType.CARD_SELECT)
        } else if (p.coins >= item.cost) {
            saveProgress(
                p.copy(
                    coins = p.coins - item.cost,
                    unlockedEquipments = p.unlockedEquipments + item.id,
                    equippedItemId = item.id
                )
            )
            soundEngine.playSfx(SfxType.LEVEL_UP)
        }
    }

    fun updateSettings(newSettings: GameSettings) {
        soundEngine.musicEnabled = newSettings.musicEnabled
        soundEngine.sfxEnabled = newSettings.sfxEnabled
        saveProgress(_progress.value.copy(settings = newSettings))
    }

    private fun saveProgress(updated: PersistentProgress) {
        _progress.value = updated
        viewModelScope.launch {
            persistence.save(updated)
        }
    }

    // Hardware keyboard support (WASD, Arrows, Space=Dash, E=Special, ESC=Pause)
    fun handleKeyDown(keyCode: Int): Boolean {
        if (_screen.value != AppScreen.PLAYING) return false
        pressedKeys.add(keyCode)
        when (keyCode) {
            KeyEvent.KEYCODE_SPACE -> {
                gameEngine.triggerDash()
                return true
            }
            KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_SHIFT_LEFT -> {
                gameEngine.triggerSpecial()
                return true
            }
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_P -> {
                togglePause()
                return true
            }
        }
        recomputeKeyboardVector()
        return isMovementKey(keyCode)
    }

    fun handleKeyUp(keyCode: Int): Boolean {
        pressedKeys.remove(keyCode)
        if (_screen.value != AppScreen.PLAYING) return false
        recomputeKeyboardVector()
        return isMovementKey(keyCode)
    }

    private fun isMovementKey(code: Int): Boolean = code in setOf(
        KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT
    )

    private fun recomputeKeyboardVector() {
        var screenX = 0f
        var screenY = 0f
        if (pressedKeys.contains(KeyEvent.KEYCODE_W) || pressedKeys.contains(KeyEvent.KEYCODE_DPAD_UP)) screenY -= 1f
        if (pressedKeys.contains(KeyEvent.KEYCODE_S) || pressedKeys.contains(KeyEvent.KEYCODE_DPAD_DOWN)) screenY += 1f
        if (pressedKeys.contains(KeyEvent.KEYCODE_A) || pressedKeys.contains(KeyEvent.KEYCODE_DPAD_LEFT)) screenX -= 1f
        if (pressedKeys.contains(KeyEvent.KEYCODE_D) || pressedKeys.contains(KeyEvent.KEYCODE_DPAD_RIGHT)) screenX += 1f

        // Map screen input to isometric world XZ (camera sits at +X, +Z looking toward origin)
        val worldX = screenX * 0.707f + screenY * 0.707f
        val worldZ = -screenX * 0.707f + screenY * 0.707f
        gameEngine.moveInputX = worldX
        gameEngine.moveInputZ = worldZ
    }

    fun setTouchJoystickVector(screenDx: Float, screenDy: Float) {
        // Map screen joystick (x right, y down) to isometric camera axes
        val worldX = screenDx * 0.707f + screenDy * 0.707f
        val worldZ = -screenDx * 0.707f + screenDy * 0.707f
        gameEngine.moveInputX = worldX
        gameEngine.moveInputZ = worldZ
    }

    fun setTouchAimVector(screenDx: Float, screenDy: Float, active: Boolean) {
        gameEngine.manualAimActive = active
        if (active) {
            val worldX = screenDx * 0.707f + screenDy * 0.707f
            val worldZ = -screenDx * 0.707f + screenDy * 0.707f
            gameEngine.manualAimX = worldX
            gameEngine.manualAimZ = worldZ
        }
    }
}
