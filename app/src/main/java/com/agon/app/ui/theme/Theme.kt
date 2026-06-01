package com.agon.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = NovaCyan,
    onPrimary = NovaDarkBackground,
    primaryContainer = NovaBlue.copy(alpha = 0.32f),
    onPrimaryContainer = NovaCyan,
    secondary = NovaViolet,
    tertiary = NovaMint,
    background = NovaDarkBackground,
    onBackground = androidx.compose.ui.graphics.Color(0xFFE6ECFF),
    surface = NovaDarkSurface,
    onSurface = androidx.compose.ui.graphics.Color(0xFFE6ECFF),
    surfaceVariant = NovaDarkSurfaceHigh,
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFA7B0C8),
    outline = NovaDarkOutline,
    error = NovaError,
)

private val LightColorScheme = lightColorScheme(
    primary = NovaBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = NovaLightSurfaceHigh,
    onPrimaryContainer = NovaBlue,
    secondary = NovaViolet,
    tertiary = NovaMint,
    background = NovaLightBackground,
    onBackground = NovaLightText,
    surface = NovaLightSurface,
    onSurface = NovaLightText,
    surfaceVariant = NovaLightSurfaceHigh,
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF4B5563),
    outline = androidx.compose.ui.graphics.Color(0xFFCBD5E1),
    error = androidx.compose.ui.graphics.Color(0xFFDC2626),
)

@Composable
fun AgonAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
