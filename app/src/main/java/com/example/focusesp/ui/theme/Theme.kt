package com.example.focusesp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = OnPrimaryNavy,
    primaryContainer = BlueContainer,
    onPrimaryContainer = OnBlueContainer,
    secondary = IceBlue,
    onSecondary = OnPrimaryNavy,
    secondaryContainer = NavySurfaceElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = CyanAccent,
    onTertiary = OnPrimaryNavy,
    tertiaryContainer = CyanContainer,
    onTertiaryContainer = OnCyanContainer,
    background = MidnightBackground,
    onBackground = TextPrimary,
    surface = NavySurface,
    onSurface = TextPrimary,
    surfaceDim = MidnightBackground,
    surfaceBright = NavySurfaceHigh,
    surfaceContainerLowest = MidnightBackground,
    surfaceContainerLow = NavySurface,
    surfaceContainer = NavySurface,
    surfaceContainerHigh = NavySurfaceElevated,
    surfaceContainerHighest = NavySurfaceHigh,
    surfaceVariant = NavySurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceTint = ElectricBlue,
    inverseSurface = TextPrimary,
    inverseOnSurface = MidnightBackground,
    outline = NavyOutline,
    outlineVariant = NavyOutlineSoft,
    error = DangerRed,
    onError = OnPrimaryNavy,
    errorContainer = DangerContainer,
    onErrorContainer = OnDangerContainer
)

private val LightColorScheme = lightColorScheme(
    primary = LightBlue,
    onPrimary = LightOnPrimary,
    primaryContainer = LightBlueContainer,
    onPrimaryContainer = LightOnBlueContainer,
    secondary = LightSecondaryAccent,
    onSecondary = LightOnPrimary,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightOnSurface,
    tertiary = LightTertiary,
    onTertiary = LightOnPrimary,
    tertiaryContainer = LightSurfaceElevated,
    onTertiaryContainer = LightOnSurface,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceDim = LightSurfaceElevated,
    surfaceBright = LightSurface,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceElevated,
    surfaceContainerHighest = LightSurfaceElevated,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightBlue,
    inverseSurface = LightOnSurface,
    inverseOnSurface = LightSurface,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = LightError,
    onError = LightOnPrimary,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer
)

/**
 * FocusESP uses the fixed midnight-navy blue-black identity everywhere. The
 * theme is intentionally forced to dark so the product looks identical on every
 * device regardless of the system setting; dynamic colour is never used. A full
 * light scheme is still defined for completeness and future opt-in.
 */
@Composable
fun FocusESPTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
