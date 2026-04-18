package com.mari.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = MariPurple,
    onPrimary = Color.White,
    primaryContainer = MariPurpleContainer,
    onPrimaryContainer = MariOnPurpleContainer,
    secondary = MariGreen,
    onSecondary = Color.White,
    secondaryContainer = MariGreenContainer,
    onSecondaryContainer = Color(0xFF0A2000),
    tertiary = MariOrange,
    onTertiary = Color.White,
    tertiaryContainer = MariOrangeContainer,
    onTertiaryContainer = Color(0xFF2C1600),
    error = MariRed,
    onError = Color.White,
    errorContainer = MariRedContainer,
    onErrorContainer = Color(0xFF410002),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = MariGreyContainer,
    onSurfaceVariant = MariGrey,
    outline = Color(0xFF79747E),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = MariPurpleContainer,
    secondary = Color(0xFF96D97B),
    onSecondary = Color(0xFF0A3900),
    secondaryContainer = Color(0xFF1E5200),
    onSecondaryContainer = MariGreenContainer,
    tertiary = Color(0xFFFFB963),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693C00),
    onTertiaryContainer = MariOrangeContainer,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
)

@Composable
fun MariTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = MariTypography,
        content = content,
    )
}
