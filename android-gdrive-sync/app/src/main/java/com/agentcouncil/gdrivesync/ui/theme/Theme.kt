package com.agentcouncil.gdrivesync.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary          = md_theme_light_primary,
    onPrimary        = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    secondary        = md_theme_light_secondary,
    surface          = md_theme_light_surface,
    background       = md_theme_light_background,
)

private val DarkColors = darkColorScheme(
    primary          = md_theme_dark_primary,
    onPrimary        = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    secondary        = md_theme_dark_secondary,
    surface          = md_theme_dark_surface,
    background       = md_theme_dark_background,
)

@Composable
fun GDriveSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else      -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
