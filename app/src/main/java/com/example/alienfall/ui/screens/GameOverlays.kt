package com.example.alienfall.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.alienfall.model.CardCategory
import com.example.alienfall.model.EvolutionCardCatalog
import com.example.alienfall.model.EvolutionCardDef
import com.example.alienfall.model.HudSnapshot
import com.example.ui.theme.ArmorSlate
import com.example.ui.theme.ArmorSlateElevated
import com.example.ui.theme.CrimsonCore
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PlasmaOrange
import com.example.ui.theme.SolarGold
import com.example.ui.theme.SteelBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun CardChoiceOverlay(
    choices: List<EvolutionCardDef>,
    acquiredLevels: Map<Int, Int>,
    playerLevel: Int,
    onPickCard: (EvolutionCardDef?) -> Unit
) {
    var pickedId by remember(choices) { mutableStateOf<Int?>(null) }

    LaunchedEffect(pickedId) {
        val id = pickedId ?: return@LaunchedEffect
        delay(220L)
        onPickCard(choices.firstOrNull { it.id == id })
        pickedId = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid.copy(alpha = 0.84f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 860.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "NÍVEL $playerLevel ALCANÇADO!",
                style = MaterialTheme.typography.headlineLarge,
                color = CyberCyan
            )
            Text(
                text = "Escolha uma carta de evolução para fortalecer seu sobrevivente",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))

            if (choices.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ArmorSlate,
                    border = androidx.compose.foundation.BorderStroke(2.dp, SolarGold)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Todas as Habilidades no Máximo!", style = MaterialTheme.typography.titleLarge, color = SolarGold)
                        Spacer(Modifier.height(8.dp))
                        Text("Recompensa alternativa: +50 Moedas e +35% de Vida restaurada.", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = { onPickCard(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = SolarGold, contentColor = ObsidianVoid)
                        ) {
                            Text("COLETAR RECOMPENSA", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    choices.forEach { card ->
                        val currentLvl = acquiredLevels[card.id] ?: 0
                        val isPicked = pickedId == card.id
                        val scale by animateFloatAsState(
                            targetValue = if (isPicked) 1.07f else 1.0f,
                            animationSpec = tween(180),
                            label = "card_pick_scale"
                        )
                        val artRes = when (card.category) {
                            CardCategory.COMBAT -> R.drawable.img_card_combat
                            CardCategory.COSMIC -> R.drawable.img_card_cosmic
                            CardCategory.BIO -> R.drawable.img_card_bio
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .scale(scale)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(enabled = pickedId == null) { pickedId = card.id }
                                .testTag("evolution_card_${card.id}"),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isPicked) ArmorSlateElevated else ArmorSlate,
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isPicked) 2.5.dp else 1.5.dp,
                                color = card.rarity.color
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(86.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                ) {
                                    Image(
                                        painter = painterResource(id = artRes),
                                        contentDescription = card.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(6.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        color = ObsidianVoid.copy(alpha = 0.85f)
                                    ) {
                                        Text(
                                            text = card.rarity.label.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = card.rarity.color,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        color = ObsidianVoid.copy(alpha = 0.85f)
                                    ) {
                                        Text(
                                            text = "Nv $currentLvl → ${currentLvl + 1}/${card.maxLevel}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CyberCyan,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "${card.code} • ${card.name}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = card.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = card.rarity.color.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = card.levelPreview(currentLvl),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = card.rarity.color,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SkillsSummaryDialog(
    hud: HudSnapshot,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid.copy(alpha = 0.8f))
            .clickable(onClick = onDismiss)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .clickable(enabled = false) {},
            shape = RoundedCornerShape(18.dp),
            color = ArmorSlate,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, CyberCyan)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("BÔNUS ACUMULADOS", style = MaterialTheme.typography.headlineMedium, color = CyberCyan)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Fechar", tint = TextPrimary)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Dano: +${hud.damageBonusPercent}%", style = MaterialTheme.typography.labelLarge, color = PlasmaOrange)
                    Text("Vel. Mov: +${hud.moveSpeedBonusPercent}%", style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                    Text("Crítico: ${hud.critChancePercent}%", style = MaterialTheme.typography.labelLarge, color = SolarGold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Cadência: +${hud.attackSpeedBonusPercent}%", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                    Text("Redução Dano: ${hud.damageReductionPercent}%", style = MaterialTheme.typography.labelLarge, color = CyberCyan)
                }

                Spacer(Modifier.height(10.dp))
                Text("Cartas Adquiridas (${hud.acquiredCards.size}):", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(Modifier.height(6.dp))

                if (hud.acquiredCards.isEmpty()) {
                    Text("Nenhuma carta coletada ainda. Derrote alienígenas para evoluir!", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                } else {
                    LazyColumn(
                        modifier = Modifier.height(220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(hud.acquiredCards.entries.toList()) { (id, lvl) ->
                            val card = EvolutionCardCatalog.byId(id) ?: return@items
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ArmorSlateElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, card.rarity.color.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(card.name, style = MaterialTheme.typography.titleMedium, color = card.rarity.color)
                                        Text(card.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("Nv $lvl/${card.maxLevel}", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PauseOverlay(
    hud: HudSnapshot,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onExitToMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(20.dp),
            color = ArmorSlate,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, CyberCyan)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("JOGO PAUSADO", style = MaterialTheme.typography.headlineLarge, color = CyberCyan)
                Text(
                    "Nível ${hud.level} • ${hud.kills} Abates • ${hud.coinsCollected} Moedas",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("resume_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianVoid)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("CONTINUAR", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("restart_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("REINICIAR MISSÃO", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onExitToMenu,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("exit_menu_button")
                ) {
                    Text("SALVAR MOEDAS E SAIR", style = MaterialTheme.typography.labelLarge, color = CrimsonCore)
                }
            }
        }
    }
}

@Composable
fun RunResultOverlay(
    isVictory: Boolean,
    hud: HudSnapshot,
    onContinueEndless: () -> Unit,
    onPlayAgain: () -> Unit,
    onReturnHome: () -> Unit
) {
    val mins = hud.survivalSeconds.toInt() / 60
    val secs = hud.survivalSeconds.toInt() % 60
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .padding(20.dp)
                .widthIn(max = 460.dp),
            shape = RoundedCornerShape(22.dp),
            color = ArmorSlate,
            border = androidx.compose.foundation.BorderStroke(2.dp, if (isVictory) SolarGold else CrimsonCore)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = if (isVictory) SolarGold else CrimsonCore,
                    modifier = Modifier.size(38.dp)
                )
                Text(
                    text = if (isVictory) "DEVORADOR CÓSMICO DERROTADO!" else "SOBREVIVENTE ABATIDO",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (isVictory) SolarGold else CrimsonCore,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Progresso e moedas salvos permanentemente.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ArmorSlateElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SteelBorder)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ResultStatRow("Tempo Sobrevivido", String.format("%02d:%02d", mins, secs))
                        ResultStatRow("Nível Alcançado", "${hud.level}")
                        ResultStatRow("Alienígenas Eliminados", "${hud.kills}")
                        ResultStatRow("Moedas Obtidas", "+${hud.coinsCollected}")
                    }
                }

                if (isVictory) {
                    Button(
                        onClick = onContinueEndless,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianVoid)
                    ) {
                        Text("CONTINUAR MODO INFINITO", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Button(
                    onClick = onPlayAgain,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("play_again_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = PlasmaOrange, contentColor = ObsidianVoid)
                ) {
                    Text("JOGAR NOVAMENTE", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onReturnHome,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("return_home_button")
                ) {
                    Text("MENU PRINCIPAL", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun ResultStatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.labelLarge, color = SolarGold)
    }
}
