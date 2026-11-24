package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val HotCastDarkColorScheme = darkColorScheme(
    primary = CyanBroadcast,
    onPrimary = Color(0xFF002026),
    primaryContainer = CyanContainerDark,
    onPrimaryContainer = OnCyanContainerDark,
    secondary = AmberHotspot,
    onSecondary = Color(0xFF261900),
    secondaryContainer = AmberContainerDark,
    onSecondaryContainer = OnAmberContainerDark,
    tertiary = EmeraldLive,
    onTertiary = Color(0xFF002116),
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = ObsidianCard,
    surfaceContainer = ObsidianSurfaceVariant,
    outline = ObsidianOutline,
    error = RoseAlert,
    onError = Color.White
)

private val HotCastLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCE9F8),
    onPrimaryContainer = Color(0xFF002438),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEF3C7),
    onSecondaryContainer = Color(0xFF451A03),
    tertiary = EmeraldLive,
    onTertiary = Color.White,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = Color(0xFFCBD5E1),
    error = RoseAlert,
    onError = Color.White
)

val HotCastShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun HotCastTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) HotCastDarkColorScheme else HotCastLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = HotCastTypography,
        shapes = HotCastShapes,
        content = content
    )
}
