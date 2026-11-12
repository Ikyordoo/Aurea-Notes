package com.aurea.notes.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AureaWarmEditorialLightScheme = lightColorScheme(
    primary = EspressoAccent,
    onPrimary = WarmPaper,
    primaryContainer = WarmParchment,
    onPrimaryContainer = DeepInk,
    secondary = EspressoSecondary,
    onSecondary = WarmPaper,
    background = WarmIvory,
    onBackground = DeepInk,
    surface = WarmPaper,
    onSurface = DeepInk,
    surfaceVariant = WarmParchment,
    onSurfaceVariant = EspressoMuted,
    outline = SoftHairline
)

private val AureaWarmEditorialDarkScheme = darkColorScheme(
    primary = WarmParchmentAccent,
    onPrimary = DarkEspressoCanvas,
    primaryContainer = EspressoAccent,
    onPrimaryContainer = WarmIvoryText,
    secondary = WarmSandMuted,
    onSecondary = DarkEspressoCanvas,
    background = DarkEspressoCanvas,
    onBackground = WarmIvoryText,
    surface = DarkWalnutSurface,
    onSurface = WarmIvoryText,
    surfaceVariant = DarkUmberVariant,
    onSurfaceVariant = WarmSandMuted,
    outline = DarkHairline
)

@Composable
fun AureaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        AureaWarmEditorialDarkScheme
    } else {
        AureaWarmEditorialLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AureaTypography,
        content = content
    )
}
