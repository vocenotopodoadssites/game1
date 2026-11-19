package com.example

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.alienfall.ui.AppScreen
import com.example.alienfall.ui.GameViewModel
import com.example.alienfall.ui.screens.CharacterSelectScreen
import com.example.alienfall.ui.screens.GameScreen
import com.example.alienfall.ui.screens.HomeScreen
import com.example.alienfall.ui.screens.SettingsScreen
import com.example.alienfall.ui.screens.StatsScreen
import com.example.alienfall.ui.screens.UpgradesScreen
import com.example.ui.theme.AlienfallTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlienfallTheme {
                val screen by viewModel.screen.collectAsStateWithLifecycle()
                val progress by viewModel.progress.collectAsStateWithLifecycle()
                val hud by viewModel.hud.collectAsStateWithLifecycle()
                val runPhase by viewModel.runPhase.collectAsStateWithLifecycle()
                val cardChoices by viewModel.cardChoices.collectAsStateWithLifecycle()

                when (screen) {
                    AppScreen.HOME -> HomeScreen(
                        progress = progress,
                        onPlayClick = { viewModel.startMission() },
                        onNavigate = { viewModel.navigateTo(it) }
                    )
                    AppScreen.CHARACTER_SELECT -> CharacterSelectScreen(
                        progress = progress,
                        onSelectOrUnlockHero = { viewModel.selectOrUnlockHero(it) },
                        onSelectOrUnlockSkin = { hero, skin -> viewModel.selectOrUnlockSkin(hero, skin) },
                        onBack = { viewModel.navigateTo(AppScreen.HOME) }
                    )
                    AppScreen.UPGRADES -> UpgradesScreen(
                        progress = progress,
                        onPurchaseUpgrade = { viewModel.purchaseUpgrade(it) },
                        onUnlockOrEquipEquipment = { viewModel.unlockOrEquipEquipment(it) },
                        onSelectOrUnlockSkin = { hero, skin -> viewModel.selectOrUnlockSkin(hero, skin) },
                        onBack = { viewModel.navigateTo(AppScreen.HOME) }
                    )
                    AppScreen.STATS -> StatsScreen(
                        progress = progress,
                        onBack = { viewModel.navigateTo(AppScreen.HOME) }
                    )
                    AppScreen.SETTINGS -> SettingsScreen(
                        progress = progress,
                        onUpdateSettings = { viewModel.updateSettings(it) },
                        onBack = { viewModel.navigateTo(AppScreen.HOME) }
                    )
                    AppScreen.PLAYING -> GameScreen(
                        engine = viewModel.gameEngine,
                        hud = hud,
                        runPhase = runPhase,
                        cardChoices = cardChoices,
                        onFrameHudUpdate = { viewModel.syncHudFromRenderer() },
                        onJoystickMove = { dx, dy -> viewModel.setTouchJoystickVector(dx, dy) },
                        onAimJoystick = { dx, dy, active -> viewModel.setTouchAimVector(dx, dy, active) },
                        onDashClick = { viewModel.gameEngine.triggerDash() },
                        onSpecialClick = { viewModel.gameEngine.triggerSpecial() },
                        onToggleManualAim = { viewModel.toggleManualAim() },
                        onPauseToggle = { viewModel.togglePause() },
                        onPickCard = { viewModel.pickCard(it) },
                        onContinueEndless = { viewModel.continueEndless() },
                        onRestart = { viewModel.restartMission() },
                        onExitToMenu = { viewModel.abandonAndReturnHome() }
                    )
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (viewModel.handleKeyDown(keyCode)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (viewModel.handleKeyUp(keyCode)) return true
        return super.onKeyUp(keyCode, event)
    }
}
