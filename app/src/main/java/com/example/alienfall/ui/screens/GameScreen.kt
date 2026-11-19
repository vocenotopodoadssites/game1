package com.example.alienfall.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import com.example.alienfall.engine.GameEngine
import com.example.alienfall.engine.RunPhase
import com.example.alienfall.model.EvolutionCardCatalog
import com.example.alienfall.model.EvolutionCardDef
import com.example.alienfall.model.HudSnapshot
import com.example.alienfall.ui.components.GameGLSurfaceView
import com.example.alienfall.ui.components.TacticalMinimap
import com.example.alienfall.ui.components.VirtualJoystick
import com.example.ui.theme.ArmorSlate
import com.example.ui.theme.ArmorSlateElevated
import com.example.ui.theme.CrimsonCore
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PlasmaOrange
import com.example.ui.theme.ShieldBlue
import com.example.ui.theme.SolarGold
import com.example.ui.theme.SteelBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun GameScreen(
    engine: GameEngine,
    hud: HudSnapshot,
    runPhase: RunPhase,
    cardChoices: List<EvolutionCardDef>,
    onFrameHudUpdate: () -> Unit,
    onJoystickMove: (Float, Float) -> Unit,
    onAimJoystick: (Float, Float, Boolean) -> Unit,
    onDashClick: () -> Unit,
    onSpecialClick: () -> Unit,
    onToggleManualAim: () -> Unit,
    onPauseToggle: () -> Unit,
    onPickCard: (EvolutionCardDef?) -> Unit,
    onContinueEndless: () -> Unit,
    onRestart: () -> Unit,
    onExitToMenu: () -> Unit
) {
    BackHandler {
        if (runPhase == RunPhase.RUNNING || runPhase == RunPhase.PAUSED) {
            onPauseToggle()
        }
    }

    var showSkillsDialog by remember { mutableStateOf(false) }
    var touchOrigin by remember { mutableStateOf<Offset?>(null) }
    var touchCurrent by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current
    val maxDragPx = remember(density) { with(density) { 58.dp.toPx() } }
    val deadzonePx = remember(density) { with(density) { 4.dp.toPx() } }

    LaunchedEffect(runPhase) {
        if (runPhase != RunPhase.RUNNING) {
            touchOrigin = null
            touchCurrent = null
            onJoystickMove(0f, 0f)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(ObsidianVoid)) {
        // Real-Time 3D Isometric OpenGL World
        GameGLSurfaceView(
            engine = engine,
            onFrameHudUpdate = onFrameHudUpdate,
            modifier = Modifier.fillMaxSize()
        )

        // Touch-anywhere full-screen movement controller (underneath HUD buttons)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("touch_move_surface")
                .pointerInput(runPhase) {
                    if (runPhase != RunPhase.RUNNING) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var origin = down.position
                        touchOrigin = origin
                        touchCurrent = origin
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }
                                val pos = change.position
                                var delta = pos - origin
                                var dist = hypot(delta.x, delta.y)
                                if (dist > maxDragPx) {
                                    val dir = delta / dist
                                    origin = pos - dir * maxDragPx
                                    delta = pos - origin
                                    dist = maxDragPx
                                }
                                touchOrigin = origin
                                touchCurrent = pos
                                if (dist > deadzonePx) {
                                    onJoystickMove(
                                        (delta.x / maxDragPx).coerceIn(-1f, 1f),
                                        (delta.y / maxDragPx).coerceIn(-1f, 1f)
                                    )
                                } else {
                                    onJoystickMove(0f, 0f)
                                }
                                change.consume()
                            }
                        } finally {
                            touchOrigin = null
                            touchCurrent = null
                            onJoystickMove(0f, 0f)
                        }
                    }
                }
        ) {
            val origin = touchOrigin
            val current = touchCurrent
            if (origin != null && current != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = CyberCyan.copy(alpha = 0.22f),
                        radius = maxDragPx,
                        center = origin,
                        style = Stroke(width = 2.5f)
                    )
                    drawCircle(
                        color = ObsidianVoid.copy(alpha = 0.28f),
                        radius = maxDragPx,
                        center = origin
                    )
                    drawLine(
                        color = CyberCyan.copy(alpha = 0.45f),
                        start = origin,
                        end = current,
                        strokeWidth = 3f
                    )
                    drawCircle(
                        color = CyberCyan.copy(alpha = 0.85f),
                        radius = maxDragPx * 0.34f,
                        center = current
                    )
                }
            }
        }

        // Gameplay HUD
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top HUD Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Top-Left: HP, Shield, XP, Level, Equipped Cards Strip
                Column(
                    modifier = Modifier.widthIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ArmorSlate.copy(alpha = 0.84f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SteelBorder)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${hud.hero.title} • Nv ${hud.level}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = hud.hero.primaryColor
                                )
                                Text(
                                    text = "${hud.hp.toInt()}/${hud.maxHp.toInt()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { (hud.hp / hud.maxHp.coerceAtLeast(1f)).coerceIn(0f, 1f) },
                                color = Color(0xFF22C55E),
                                trackColor = ArmorSlateElevated,
                                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))
                            )
                            if (hud.maxShield > 0f) {
                                LinearProgressIndicator(
                                    progress = { (hud.shield / hud.maxShield).coerceIn(0f, 1f) },
                                    color = ShieldBlue,
                                    trackColor = ArmorSlateElevated,
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp))
                                )
                            }
                            LinearProgressIndicator(
                                progress = { (hud.xp.toFloat() / hud.xpToNext.coerceAtLeast(1)).coerceIn(0f, 1f) },
                                color = CyberCyan,
                                trackColor = ArmorSlateElevated,
                                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }

                    // Equipped Skills chip row (tappable to view accumulated bonuses)
                    Surface(
                        modifier = Modifier
                            .clickable { showSkillsDialog = true }
                            .testTag("skills_summary_button"),
                        shape = RoundedCornerShape(10.dp),
                        color = ArmorSlate.copy(alpha = 0.8f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SteelBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.List, contentDescription = "Habilidades equipadas", tint = CyberCyan, modifier = Modifier.size(16.dp))
                            if (hud.acquiredCards.isEmpty()) {
                                Text("Habilidades (0)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            } else {
                                hud.acquiredCards.entries.take(4).forEach { (id, lvl) ->
                                    val def = EvolutionCardCatalog.byId(id)
                                    if (def != null) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = def.rarity.color.copy(alpha = 0.22f)
                                        ) {
                                            Text(
                                                text = "${def.code}:$lvl",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = def.rarity.color,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                if (hud.acquiredCards.size > 4) {
                                    Text("+${hud.acquiredCards.size - 4}", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                                }
                            }
                        }
                    }
                }

                // Top-Center: Active Boss Health Bar + Announcements
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (hud.activeBossName != null) {
                        Surface(
                            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianVoid.copy(alpha = 0.86f),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, CrimsonCore)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = hud.activeBossName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = CrimsonCore,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = hud.activeBossSubtitle ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { (hud.activeBossHp / hud.activeBossMaxHp.coerceAtLeast(1f)).coerceIn(0f, 1f) },
                                    color = CrimsonCore,
                                    trackColor = ArmorSlateElevated,
                                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = hud.bossAnnouncement != null, enter = fadeIn(), exit = fadeOut()) {
                        Surface(
                            modifier = Modifier.padding(top = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = CrimsonCore.copy(alpha = 0.92f)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Warning, contentDescription = null, tint = SolarGold, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(hud.bossAnnouncement ?: "", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                            }
                        }
                    }

                    AnimatedVisibility(visible = hud.poiAnnouncement != null, enter = fadeIn(), exit = fadeOut()) {
                        Surface(
                            modifier = Modifier.padding(top = 6.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = CyberCyan.copy(alpha = 0.9f)
                        ) {
                            Text(
                                text = hud.poiAnnouncement ?: "",
                                style = MaterialTheme.typography.labelLarge,
                                color = ObsidianVoid,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Top-Right: Telemetry + Pause + Tactical Minimap
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ArmorSlate.copy(alpha = 0.84f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SteelBorder)
                        ) {
                            val m = hud.survivalSeconds.toInt() / 60
                            val s = hud.survivalSeconds.toInt() % 60
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(String.format("%02d:%02d", m, s), style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                Text("☠ ${hud.kills}", style = MaterialTheme.typography.labelLarge, color = CrimsonCore)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.MonetizationOn, contentDescription = null, tint = SolarGold, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("${hud.coinsCollected}", style = MaterialTheme.typography.labelLarge, color = SolarGold)
                                }
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = ArmorSlate.copy(alpha = 0.88f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SteelBorder)
                        ) {
                            IconButton(
                                onClick = onPauseToggle,
                                modifier = Modifier.size(48.dp).testTag("pause_button")
                            ) {
                                Icon(Icons.Filled.Pause, contentDescription = "Pausar", tint = TextPrimary)
                            }
                        }
                    }

                    TacticalMinimap(hud = hud)
                }
            }

            // Bottom Controls Row (Subtle touch hint + Aim Toggle + Special + Dash)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                AnimatedVisibility(
                    visible = hud.survivalSeconds < 7f && touchOrigin == null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianVoid.copy(alpha = 0.65f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "Toque e arraste na tela para mover",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                if (hud.survivalSeconds >= 7f || touchOrigin != null) {
                    Spacer(Modifier.width(1.dp))
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (hud.manualAimEnabled) {
                        VirtualJoystick(
                            accentColor = PlasmaOrange,
                            tag = "aim_joystick",
                            size = 104.dp,
                            onMove = onAimJoystick
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Manual/Auto Aim toggle pill
                        Surface(
                            modifier = Modifier
                                .clickable(onClick = onToggleManualAim)
                                .testTag("aim_mode_button"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (hud.manualAimEnabled) PlasmaOrange else ArmorSlate.copy(alpha = 0.85f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (hud.manualAimEnabled) PlasmaOrange else SteelBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CenterFocusStrong,
                                    contentDescription = "Alternar mira",
                                    tint = if (hud.manualAimEnabled) ObsidianVoid else CyberCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (hud.manualAimEnabled) "MIRA MANUAL" else "MIRA AUTO",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hud.manualAimEnabled) ObsidianVoid else TextPrimary
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Special Skill Button
                            ActionCooldownButton(
                                label = "ESPECIAL",
                                subLabel = "E",
                                readiness = hud.specialCooldownRatio,
                                accent = hud.hero.primaryColor,
                                icon = Icons.Filled.FlashOn,
                                tag = "special_button",
                                onClick = onSpecialClick
                            )
                            // Dash Button
                            ActionCooldownButton(
                                label = "ESQUIVA",
                                subLabel = "ESPAÇO",
                                readiness = hud.dashCooldownRatio,
                                accent = CyberCyan,
                                icon = Icons.Filled.Bolt,
                                tag = "dash_button",
                                onClick = onDashClick
                            )
                        }
                    }
                }
            }
        }

        // Overlays
        if (showSkillsDialog) {
            SkillsSummaryDialog(hud = hud, onDismiss = { showSkillsDialog = false })
        }

        when (runPhase) {
            RunPhase.CARD_CHOICE -> {
                CardChoiceOverlay(
                    choices = cardChoices,
                    acquiredLevels = hud.acquiredCards,
                    playerLevel = hud.level,
                    onPickCard = onPickCard
                )
            }
            RunPhase.PAUSED -> {
                PauseOverlay(
                    hud = hud,
                    onResume = onPauseToggle,
                    onRestart = onRestart,
                    onExitToMenu = onExitToMenu
                )
            }
            RunPhase.GAME_OVER -> {
                RunResultOverlay(
                    isVictory = false,
                    hud = hud,
                    onContinueEndless = onContinueEndless,
                    onPlayAgain = onRestart,
                    onReturnHome = onExitToMenu
                )
            }
            RunPhase.VICTORY -> {
                RunResultOverlay(
                    isVictory = true,
                    hud = hud,
                    onContinueEndless = onContinueEndless,
                    onPlayAgain = onRestart,
                    onReturnHome = onExitToMenu
                )
            }
            RunPhase.RUNNING -> Unit
        }
    }
}

@Composable
private fun ActionCooldownButton(
    label: String,
    subLabel: String,
    readiness: Float,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tag: String,
    onClick: () -> Unit
) {
    val ready = readiness >= 0.99f
    Surface(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable(enabled = ready, onClick = onClick)
            .testTag(tag),
        shape = CircleShape,
        color = if (ready) accent.copy(alpha = 0.25f) else ArmorSlate.copy(alpha = 0.75f),
        border = androidx.compose.foundation.BorderStroke(2.dp, if (ready) accent else SteelBorder)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (ready) accent else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = if (ready) label else "${(readiness * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (ready) TextPrimary else TextSecondary
                )
                Text(
                    text = subLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent.copy(alpha = 0.7f)
                )
            }
        }
    }
}
