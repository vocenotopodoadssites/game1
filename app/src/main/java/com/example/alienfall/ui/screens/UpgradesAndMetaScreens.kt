package com.example.alienfall.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.alienfall.model.EquipmentItem
import com.example.alienfall.model.GameSettings
import com.example.alienfall.model.GraphicsQuality
import com.example.alienfall.model.HeroClass
import com.example.alienfall.model.PermanentUpgradeType
import com.example.alienfall.model.PersistentProgress
import com.example.alienfall.model.SkinVariant
import com.example.ui.theme.ArmorSlate
import com.example.ui.theme.ArmorSlateElevated
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PlasmaOrange
import com.example.ui.theme.SolarGold
import com.example.ui.theme.SteelBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun UpgradesScreen(
    progress: PersistentProgress,
    onPurchaseUpgrade: (PermanentUpgradeType) -> Unit,
    onUnlockOrEquipEquipment: (EquipmentItem) -> Unit,
    onSelectOrUnlockSkin: (HeroClass, SkinVariant) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var tabIndex by remember { mutableIntStateOf(0) }
    val selectedHero = HeroClass.fromId(progress.selectedHeroId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        MetaHeader(title = "PROGRESSÃO PERMANENTE", coins = progress.coins, onBack = onBack)

        TabRow(
            selectedTabIndex = tabIndex,
            containerColor = ArmorSlate,
            contentColor = PlasmaOrange
        ) {
            Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Atributos") })
            Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Equipamentos") })
            Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, text = { Text("Visuais") })
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (tabIndex) {
                0 -> {
                    items(PermanentUpgradeType.entries) { upg ->
                        val lvl = progress.permanentUpgrades[upg.id] ?: 0
                        val maxed = lvl >= upg.maxLevel
                        val cost = upg.costForNext(lvl)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ArmorSlate,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SteelBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${upg.title} (Nível $lvl/${upg.maxLevel})", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text(upg.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                    Text(upg.bonusPerLevelText, style = MaterialTheme.typography.labelSmall, color = CyberCyan)
                                }
                                Spacer(Modifier.width(10.dp))
                                Button(
                                    onClick = { onPurchaseUpgrade(upg) },
                                    enabled = !maxed && progress.coins >= cost,
                                    modifier = Modifier.testTag("upgrade_${upg.id}"),
                                    colors = ButtonDefaults.buttonColors(containerColor = PlasmaOrange, contentColor = ObsidianVoid)
                                ) {
                                    Text(if (maxed) "MÁX" else "$cost", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
                1 -> {
                    items(EquipmentItem.entries) { item ->
                        val owned = progress.unlockedEquipments.contains(item.id)
                        val equipped = progress.equippedItemId == item.id
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (equipped) ArmorSlateElevated else ArmorSlate,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (equipped) CyberCyan else SteelBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                    Text(item.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                    Text(item.bonusTag, style = MaterialTheme.typography.labelSmall, color = SolarGold)
                                }
                                Spacer(Modifier.width(10.dp))
                                Button(
                                    onClick = { onUnlockOrEquipEquipment(item) },
                                    enabled = owned || progress.coins >= item.cost,
                                    modifier = Modifier.testTag("equip_${item.id}"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (equipped) CyberCyan else PlasmaOrange,
                                        contentColor = ObsidianVoid
                                    )
                                ) {
                                    Text(
                                        text = when {
                                            equipped -> "EQUIPADO"
                                            owned -> "EQUIPAR"
                                            else -> "${item.cost}"
                                        },
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    items(SkinVariant.entries) { skin ->
                        val owned = progress.unlockedSkins.contains(skin.id)
                        val equipped = (progress.selectedSkins[selectedHero.id] ?: SkinVariant.DEFAULT.id) == skin.id
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ArmorSlate,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (equipped) Color(skin.accentHex) else SteelBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(skin.displayName, style = MaterialTheme.typography.titleMedium, color = Color(skin.accentHex))
                                    Text("Aplicado ao herói selecionado (${selectedHero.title}).", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                }
                                Spacer(Modifier.width(10.dp))
                                Button(
                                    onClick = { onSelectOrUnlockSkin(selectedHero, skin) },
                                    enabled = owned || progress.coins >= skin.cost,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (equipped) CyberCyan else PlasmaOrange,
                                        contentColor = ObsidianVoid
                                    )
                                ) {
                                    Text(
                                        text = when {
                                            equipped -> "ATIVO"
                                            owned -> "USAR"
                                            else -> "${skin.cost}"
                                        },
                                        style = MaterialTheme.typography.labelLarge
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
fun StatsScreen(
    progress: PersistentProgress,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        MetaHeader(title = "ESTATÍSTICAS DE COMBATE", coins = progress.coins, onBack = onBack)
        Spacer(Modifier.height(10.dp))

        val mins = progress.bestSurvivalSeconds / 60
        val secs = progress.bestSurvivalSeconds % 60
        val entries = listOf(
            "Partidas Jogadas" to "${progress.totalRuns}",
            "Alienígenas Eliminados (Total)" to "${progress.totalKills}",
            "Chefes Derrotados" to "${progress.totalBossesDefeated}",
            "Recorde de Abates em Partida" to "${progress.bestKillsInRun}",
            "Maior Nível Alcançado" to "${progress.highestLevelReached}",
            "Melhor Tempo de Sobrevivência" to String.format("%02d:%02d", mins, secs),
            "Heróis Desbloqueados" to "${progress.unlockedHeroIds.size} / ${HeroClass.entries.size}"
        )

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(entries) { (label, value) ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ArmorSlate,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SteelBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = CyberCyan)
                    }
                }
            }

            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ArmorSlateElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, PlasmaOrange.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Sistema de Modelos 3D (.glb) & Áudio", style = MaterialTheme.typography.titleMedium, color = PlasmaOrange)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Todos os 10 heróis e 8 alienígenas operam com malhas 3D articuladas em OpenGL ES 3.0 e áudio procedural em tempo real. Caso deseje substituir por malhas externas .glb, adicione-as em assets/models/heroes/*.glb e assets/models/aliens/*.glb.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    progress: PersistentProgress,
    onUpdateSettings: (GameSettings) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val settings = progress.settings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        MetaHeader(title = "CONFIGURAÇÕES", coins = progress.coins, onBack = onBack)
        Spacer(Modifier.height(12.dp))

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ArmorSlate,
            border = androidx.compose.foundation.BorderStroke(1.dp, SteelBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Qualidade Gráfica 3D", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GraphicsQuality.entries.forEach { q ->
                        val active = settings.graphicsQuality == q
                        Button(
                            onClick = { onUpdateSettings(settings.copy(graphicsQuality = q)) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quality_${q.name}"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (active) CyberCyan else ArmorSlateElevated,
                                contentColor = if (active) ObsidianVoid else TextPrimary
                            )
                        ) {
                            Text(q.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                SettingSwitchRow(
                    title = "Música Sci-Fi Dinâmica",
                    checked = settings.musicEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(musicEnabled = it)) }
                )
                SettingSwitchRow(
                    title = "Efeitos Sonoros de Combate",
                    checked = settings.sfxEnabled,
                    onCheckedChange = { onUpdateSettings(settings.copy(sfxEnabled = it)) }
                )
                SettingSwitchRow(
                    title = "Mira Manual por Padrão",
                    checked = settings.manualAimDefault,
                    onCheckedChange = { onUpdateSettings(settings.copy(manualAimDefault = it)) }
                )
                SettingSwitchRow(
                    title = "Exibir Números de Dano",
                    checked = settings.showDamageNumbers,
                    onCheckedChange = { onUpdateSettings(settings.copy(showDamageNumbers = it)) }
                )
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MetaHeader(title: String, coins: Int, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = TextPrimary)
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ArmorSlate,
            border = androidx.compose.foundation.BorderStroke(1.dp, SolarGold.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.MonetizationOn, contentDescription = null, tint = SolarGold, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("$coins", style = MaterialTheme.typography.labelLarge, color = SolarGold)
            }
        }
    }
}
