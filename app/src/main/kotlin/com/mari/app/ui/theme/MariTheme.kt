package com.mari.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val MariLightColorScheme = lightColorScheme(
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

private val MariDarkColorScheme = darkColorScheme(
    primary = Color(0xFFC8C2FF),
    onPrimary = Color(0xFF2D2878),
    primaryContainer = Color(0xFF453FA0),
    onPrimaryContainer = MariPurpleContainer,
    secondary = Color(0xFF7FDBC8),
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF005047),
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
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> MariDarkColorScheme
        else -> MariLightColorScheme
    }

    MaterialTheme(
        colorScheme = colors,
        typography = MariTypography,
        shapes = MariShapes,
        content = content,
    )
}
