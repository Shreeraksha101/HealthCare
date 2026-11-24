package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = CareTealPrimary,
    onPrimary = CareTealOnPrimary,
    primaryContainer = CareTealContainer,
    onPrimaryContainer = CareOnTealContainer,
    secondary = CareBlueSecondary,
    onSecondary = CareBlueOnSecondary,
    secondaryContainer = CareBlueContainer,
    onSecondaryContainer = CareOnBlueContainer,
    tertiary = CareGreenTertiary,
    onTertiary = CareGreenOnTertiary,
    tertiaryContainer = CareGreenContainer,
    onTertiaryContainer = CareOnGreenContainer,
    background = CareBackgroundLight,
    onBackground = CareTextPrimaryLight,
    surface = CareSurfaceLight,
    onSurface = CareTextPrimaryLight,
    surfaceVariant = CareSurfaceVariantLight,
    onSurfaceVariant = CareTextSecondaryLight,
    outline = CareOutlineLight,
    error = EmergencyRed,
    errorContainer = EmergencyContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = CareTealDark,
    onPrimary = CareOnTealDark,
    primaryContainer = CareTealContainerDark,
    onPrimaryContainer = CareOnTealContainerDark,
    secondary = CareBlueDark,
    onSecondary = CareOnBlueDark,
    secondaryContainer = CareBlueContainerDark,
    onSecondaryContainer = CareOnBlueContainerDark,
    tertiary = CareGreenDark,
    onTertiary = CareOnGreenDark,
    tertiaryContainer = CareGreenContainerDark,
    onTertiaryContainer = CareOnGreenContainerDark,
    background = CareBackgroundDark,
    onBackground = CareTextPrimaryDark,
    surface = CareSurfaceDark,
    onSurface = CareTextPrimaryDark,
    surfaceVariant = CareSurfaceVariantDark,
    onSurfaceVariant = CareTextSecondaryDark,
    outline = CareOutlineDark,
    error = EmergencyRed,
    errorContainer = EmergencyContainer
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun HealthCareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
