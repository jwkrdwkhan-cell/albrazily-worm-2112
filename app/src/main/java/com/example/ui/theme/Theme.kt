package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val NexusDarkColorScheme = darkColorScheme(
    primary = LiquidGold,
    onPrimary = ObsidianBlack,
    primaryContainer = ObsidianElevated,
    onPrimaryContainer = GoldSoft,
    secondary = NeuralCyan,
    onSecondary = ObsidianBlack,
    secondaryContainer = ColorTokens.CyanContainer,
    onSecondaryContainer = NeuralCyan,
    tertiary = VioletPulse,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = TextSecondary,
    outline = BorderGlass,
    error = CrimsonAlert,
    onError = ObsidianBlack
)

private object ColorTokens {
    val CyanContainer = androidx.compose.ui.graphics.Color(0xFF0F2D3A)
}

val NexusShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun NexusAITheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NexusDarkColorScheme,
        typography = NexusTypography,
        shapes = NexusShapes,
        content = content
    )
}
