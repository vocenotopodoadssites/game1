package com.example.alienfall.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.alienfall.model.HeroClass
import com.example.alienfall.model.PersistentProgress
import com.example.alienfall.model.SkinVariant
import com.example.alienfall.ui.components.HeroShowcase3DView
import com.example.ui.theme.ArmorSlate
import com.example.ui.theme.ArmorSlateElevated
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DeepSpaceSurface
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PlasmaOrange
import com.example.ui.theme.SolarGold
import com.example.ui.theme.SteelBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun CharacterSelectScreen(
    progress: PersistentProgress,
    onSelectOrUnlockHero: (HeroClass) -> Unit,
    onSelectOrUnlockSkin: (HeroClass, SkinVariant) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var previewHero by remember(progress.selectedHeroId) {
        mutableStateOf(HeroClass.fromId(progress.selectedHeroId))
    }
    val isUnlocked = progress.unlockedHeroIds.contains(previewHero.id)
    val isSelected = progress.selectedHeroId == previewHero.id
    val activeSkinId = progress.selectedSkins[previewHero.id] ?: SkinVariant.DEFAULT.id
    val activeSkin = SkinVariant.entries.firstOrNull { it.id == activeSkinId } ?: SkinVariant.DEFAULT

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = TextPrimary)
                }
                Text(
                    text = "ESCOLHER SOBREVIVENTE",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
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
                    Text("${progress.coins}", style = MaterialTheme.typography.labelLarge, color = SolarGold)
                }
            }
        }

        // 3D Preview + Details Card
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(DeepSpaceSurface)
                .border(1.dp, previewHero.primaryColor.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
        ) {
            HeroShowcase3DView(
                hero = previewHero,
                skin = activeSkin,
                modifier = Modifier.fillMaxSize()
            )

            // Stats & Info Overlay at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(ObsidianVoid.copy(alpha = 0.82f))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${previewHero.title} — ${previewHero.subtitle}",
                            style = MaterialTheme.typography.titleLarge,
                            color = previewHero.primaryColor
                        )
                        Text(
                            text = previewHero.roleDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { onSelectOrUnlockHero(previewHero) },
                        enabled = isUnlocked || progress.coins >= previewHero.unlockCost,
                        modifier = Modifier.testTag("hero_action_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) CyberCyan else PlasmaOrange,
                            contentColor = ObsidianVoid
                        )
                    ) {
                        Text(
                            text = when {
                                isSelected -> "SELECIONADO"
                                isUnlocked -> "EQUIPAR"
                                else -> "DESBLOQUEAR (${previewHero.unlockCost})"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBar("VIDA", previewHero.baseHp / 160f, "${previewHero.baseHp.toInt()}", Color(0xFF22C55E), Modifier.weight(1f))
                    StatBar("DANO", previewHero.baseDamage / 24f, "${previewHero.baseDamage.toInt()}", PlasmaOrange, Modifier.weight(1f))
                    StatBar("VEL", previewHero.moveSpeed / 7.5f, String.format("%.1f", previewHero.moveSpeed), CyberCyan, Modifier.weight(1f))
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Passiva (${previewHero.passiveName}): ${previewHero.passiveDesc}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SolarGold
                )
                Text(
                    text = "Especial (${previewHero.specialName}): ${previewHero.specialDesc}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberCyan
                )

                // Skin Variants Row
                if (isUnlocked) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkinVariant.entries.forEach { skin ->
                            val skinOwned = progress.unlockedSkins.contains(skin.id)
                            val skinPicked = activeSkin == skin
                            Surface(
                                modifier = Modifier
                                    .clickable { onSelectOrUnlockSkin(previewHero, skin) }
                                    .testTag("skin_${skin.id}"),
                                shape = RoundedCornerShape(10.dp),
                                color = if (skinPicked) Color(skin.accentHex).copy(alpha = 0.25f) else ArmorSlate,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (skinPicked) Color(skin.accentHex) else SteelBorder
                                )
                            ) {
                                Text(
                                    text = if (skinOwned) skin.displayName.split(" ").first() else "${skin.displayName.split(" ").first()} (${skin.cost})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (skinPicked) Color(skin.accentHex) else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Roster Carousel (All 10 classes!)
        LazyRow(
            contentPadding = PaddingValues(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(HeroClass.entries) { hero ->
                val owned = progress.unlockedHeroIds.contains(hero.id)
                val selected = progress.selectedHeroId == hero.id
                val focused = previewHero == hero
                Surface(
                    modifier = Modifier
                        .width(136.dp)
                        .height(78.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            previewHero = hero
                            if (owned) onSelectOrUnlockHero(hero)
                        }
                        .testTag("hero_card_${hero.id}"),
                    shape = RoundedCornerShape(14.dp),
                    color = if (focused) ArmorSlateElevated else ArmorSlate,
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (focused) 2.dp else 1.dp,
                        color = if (focused) hero.primaryColor else SteelBorder
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = hero.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = hero.primaryColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Selecionado", tint = CyberCyan, modifier = Modifier.size(16.dp))
                            } else if (!owned) {
                                Icon(Icons.Filled.Lock, contentDescription = "Bloqueado", tint = SolarGold, modifier = Modifier.size(15.dp))
                            }
                        }
                        Text(
                            text = if (owned) hero.subtitle else "${hero.unlockCost} moedas",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (owned) TextSecondary else SolarGold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBar(
    label: String,
    ratio: Float,
    valueText: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(valueText, style = MaterialTheme.typography.labelSmall, color = TextPrimary)
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            color = color,
            trackColor = ArmorSlateElevated,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}
