package com.example.alienfall.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.alienfall.model.HeroClass
import com.example.alienfall.model.PersistentProgress
import com.example.alienfall.model.SkinVariant
import com.example.alienfall.ui.AppScreen
import com.example.alienfall.ui.components.HeroShowcase3DView
import com.example.ui.theme.ArmorSlate
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PlasmaOrange
import com.example.ui.theme.SolarGold
import com.example.ui.theme.SteelBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    progress: PersistentProgress,
    onPlayClick: () -> Unit,
    onNavigate: (AppScreen) -> Unit
) {
    val selectedHero = HeroClass.fromId(progress.selectedHeroId)
    val skinId = progress.selectedSkins[selectedHero.id] ?: SkinVariant.DEFAULT.id
    val selectedSkin = SkinVariant.entries.firstOrNull { it.id == skinId } ?: SkinVariant.DEFAULT

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        // Atmospheric wasteland backdrop
        Image(
            painter = painterResource(id = R.drawable.img_hero_banner),
            contentDescription = "Cenário pós-apocalíptico Alienfall",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.32f)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ObsidianVoid.copy(alpha = 0.85f),
                            ObsidianVoid.copy(alpha = 0.45f),
                            ObsidianVoid.copy(alpha = 0.94f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .widthIn(max = 760.dp)
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar: Brand + Coins pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ALIENFALL",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 30.sp,
                            letterSpacing = 4.sp
                        ),
                        color = PlasmaOrange
                    )
                    Text(
                        text = "ÚLTIMA RESISTÊNCIA",
                        style = MaterialTheme.typography.labelLarge,
                        color = CyberCyan
                    )
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = ArmorSlate.copy(alpha = 0.9f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SolarGold.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MonetizationOn,
                            contentDescription = "Moedas",
                            tint = SolarGold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${progress.coins}",
                            style = MaterialTheme.typography.titleLarge,
                            color = SolarGold
                        )
                    }
                }
            }

            // Central Interactive 3D Hero Showcase
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                HeroShowcase3DView(
                    hero = selectedHero,
                    skin = selectedSkin,
                    modifier = Modifier.fillMaxSize()
                )

                // Hero badge floating near bottom of showcase
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = ArmorSlate.copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, selectedHero.primaryColor.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = selectedHero.primaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${selectedHero.title} • ${selectedHero.subtitle}",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Primary JOGAR Button
            Button(
                onClick = onPlayClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("play_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PlasmaOrange,
                    contentColor = ObsidianVoid
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Iniciar partida",
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "JOGAR MISSÃO",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4 Secondary Navigation Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HomeMenuTile(
                    icon = Icons.Filled.Groups,
                    label = "Heróis",
                    tag = "nav_characters_button",
                    accent = CyberCyan,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(AppScreen.CHARACTER_SELECT) }
                )
                HomeMenuTile(
                    icon = Icons.Filled.Upgrade,
                    label = "Melhorias",
                    tag = "nav_upgrades_button",
                    accent = SolarGold,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(AppScreen.UPGRADES) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HomeMenuTile(
                    icon = Icons.Filled.BarChart,
                    label = "Estatísticas",
                    tag = "nav_stats_button",
                    accent = PlasmaOrange,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(AppScreen.STATS) }
                )
                HomeMenuTile(
                    icon = Icons.Filled.Settings,
                    label = "Config.",
                    tag = "nav_settings_button",
                    accent = TextSecondary,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigate(AppScreen.SETTINGS) }
                )
            }
        }
    }
}

@Composable
private fun HomeMenuTile(
    icon: ImageVector,
    label: String,
    tag: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(52.dp)
            .testTag(tag),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = ArmorSlate.copy(alpha = 0.85f),
            contentColor = TextPrimary
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, SteelBorder)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
