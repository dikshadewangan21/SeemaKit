package com.seemakit.field

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Digital India / National Cadastre Brand Palette
object GovColors {
    val DeepBlue = Color(0xFF0B3C5D)        // Primary Ashoka Navy
    val DeepBlueDark = Color(0xFF07273D)    // Dark Navy
    val DeepBlueLight = Color(0xFF1E567C)   // Secondary Navy
    val Saffron = Color(0xFFE66710)         // Tiranga Saffron
    val SaffronLight = Color(0xFFFFF3E0)    // Saffron Tint container
    val SaffronDark = Color(0xFFB44800)     // Deep Saffron for text
    val Green = Color(0xFF138808)           // India Green
    val GreenLight = Color(0xFFE8F5E9)      // Green Tint container
    val GreenDark = Color(0xFF0D6305)       // Deep Green for text
    val White = Color(0xFFFFFFFF)
    val BackgroundLight = Color(0xFFF8FAFC) // Clean light slate
    val PrimaryContainer = Color(0xFFE2ECF5) // Soft Navy Tint
    val Border = Color(0xFFCBD5E1)          // Crisp 1px official border
    val BorderSubtle = Color(0xFFE2E8F0)    // Light divider border
    val TextPrimary = Color(0xFF0F172A)     // Slate 900
    val TextSecondary = Color(0xFF475569)   // Slate 600
    val TricolorSaffron = Color(0xFFFF9933)
    val TricolorWhite = Color(0xFFFFFFFF)
    val TricolorGreen = Color(0xFF138808)
}

private val LightColors = lightColorScheme(
    primary = GovColors.DeepBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2ECF5),
    onPrimaryContainer = GovColors.DeepBlueDark,
    secondary = GovColors.DeepBlueLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EEF5),
    onSecondaryContainer = Color(0xFF0A2239),
    tertiary = GovColors.Saffron,
    onTertiary = Color.White,
    tertiaryContainer = GovColors.SaffronLight,
    onTertiaryContainer = GovColors.SaffronDark,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = GovColors.BackgroundLight,
    onBackground = GovColors.TextPrimary,
    surface = GovColors.White,
    onSurface = GovColors.TextPrimary,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = GovColors.TextSecondary,
    outline = GovColors.Border,
    outlineVariant = GovColors.BorderSubtle
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CB8E6),
    onPrimary = Color(0xFF003254),
    primaryContainer = Color(0xFF052A42),
    onPrimaryContainer = Color(0xFFCEE5FF),
    secondary = Color(0xFFB0C9DE),
    onSecondary = Color(0xFF183245),
    secondaryContainer = Color(0xFF2F495D),
    onSecondaryContainer = Color(0xFFCEE5FF),
    tertiary = Color(0xFFFFB784),
    onTertiary = Color(0xFF4D2600),
    tertiaryContainer = Color(0xFF6E3900),
    onTertiaryContainer = Color(0xFF002024),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    background = Color(0xFF0B131B),
    onBackground = Color(0xFFE1E7EE),
    surface = Color(0xFF0F1A24),
    onSurface = Color(0xFFE1E7EE),
    surfaceVariant = Color(0xFF1B2834),
    onSurfaceVariant = Color(0xFF8F9BA6),
    outline = Color(0xFF334656),
    outlineVariant = Color(0xFF223240)
)

@Composable
fun SeemaKitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
