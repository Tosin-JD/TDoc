package com.tosin.docprocessor.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Expressive seed palette: deep teal primary, slate secondary, warm amber tertiary.

// ---- Light roles ----
val LightPrimary = Color(0xFF00696E)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFF6FF6FC)
val LightOnPrimaryContainer = Color(0xFF002021)

val LightSecondary = Color(0xFF4A6367)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFCCE8EC)
val LightOnSecondaryContainer = Color(0xFF051F23)

val LightTertiary = Color(0xFF966916)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFFDEA1)
val LightOnTertiaryContainer = Color(0xFF2F1D00)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

val LightSurface = Color(0xFFFAFDFD)
val LightSurfaceDim = Color(0xFFDADFDF)
val LightSurfaceBright = Color(0xFFFAFDFD)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF4F8F8)
val LightSurfaceContainer = Color(0xFFEFF3F3)
val LightSurfaceContainerHigh = Color(0xFFE9EDED)
val LightSurfaceContainerHighest = Color(0xFFE3E7E7)
val LightSurfaceVariant = Color(0xFFDCE4E4)
val LightOnSurface = Color(0xFF191C1C)
val LightOnSurfaceVariant = Color(0xFF3F4849)

val LightOutline = Color(0xFF6F7979)
val LightOutlineVariant = Color(0xFFBFC9C9)

val LightInverseSurface = Color(0xFF2E3131)
val LightInverseOnSurface = Color(0xFFF0F1F1)
val LightInversePrimary = Color(0xFF4DD9DF)

val LightScrim = Color(0xFF000000)

// ---- Dark roles ----
val DarkPrimary = Color(0xFF4DD9DF)
val DarkOnPrimary = Color(0xFF003738)
val DarkPrimaryContainer = Color(0xFF1C4F52)
val DarkOnPrimaryContainer = Color(0xFF6FF6FC)

val DarkSecondary = Color(0xFFB0CCD0)
val DarkOnSecondary = Color(0xFF1B3438)
val DarkSecondaryContainer = Color(0xFF324B4F)
val DarkOnSecondaryContainer = Color(0xFFCCE8EC)

val DarkTertiary = Color(0xFFF1C169)
val DarkOnTertiary = Color(0xFF4D3200)
val DarkTertiaryContainer = Color(0xFF6E4900)
val DarkOnTertiaryContainer = Color(0xFFFFDEA1)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkSurface = Color(0xFF111414)
val DarkSurfaceDim = Color(0xFF111414)
val DarkSurfaceBright = Color(0xFF373A3A)
val DarkSurfaceContainerLowest = Color(0xFF0B0E0F)
val DarkSurfaceContainerLow = Color(0xFF191C1C)
val DarkSurfaceContainer = Color(0xFF1D2020)
val DarkSurfaceContainerHigh = Color(0xFF272A2A)
val DarkSurfaceContainerHighest = Color(0xFF323535)
val DarkSurfaceVariant = Color(0xFF3F4849)
val DarkOnSurface = Color(0xFFE0E3E3)
val DarkOnSurfaceVariant = Color(0xFFBFC9C9)

val DarkOutline = Color(0xFF899393)
val DarkOutlineVariant = Color(0xFF3F4849)

val DarkInverseSurface = Color(0xFFE0E3E3)
val DarkInverseOnSurface = Color(0xFF2E3131)
val DarkInversePrimary = Color(0xFF00696E)

val DarkScrim = Color(0xFF000000)

val ExpressiveLightColors: ColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    surface = LightSurface,
    surfaceDim = LightSurfaceDim,
    surfaceBright = LightSurfaceBright,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceVariant = LightSurfaceVariant,
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary,
    scrim = LightScrim
)

val ExpressiveDarkColors: ColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    surface = DarkSurface,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceVariant = DarkSurfaceVariant,
    onSurface = DarkOnSurface,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary,
    scrim = DarkScrim
)