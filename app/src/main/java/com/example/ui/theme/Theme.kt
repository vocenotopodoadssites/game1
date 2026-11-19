package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AlienfallColorScheme = darkColorScheme(
    primary = PlasmaOrange,
    onPrimary = ObsidianVoid,
    primaryContainer = CrimsonCore,
    onPrimaryContainer = TextPrimary,
    secondary = CyberCyan,
    onSecondary = ObsidianVoid,
    secondaryContainer = ArmorSlateElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = ArcaneViolet,
    onTertiary = TextPrimary,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = DeepSpaceSurface,
    onSurface = TextPrimary,
    surfaceVariant = ArmorSlate,
    onSurfaceVariant = TextSecondary,
    outline = SteelBorder,
    error = CrimsonCore,
    onError = TextPrimary
)

@Composable
fun AlienfallTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AlienfallColorScheme,
        typography = Typography,
        content = content
    )
}
