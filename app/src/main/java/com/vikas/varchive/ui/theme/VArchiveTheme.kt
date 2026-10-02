package com.vikas.varchive.ui.theme

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
import com.vikas.varchive.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF006A68),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF1EE),
    onPrimaryContainer = Color(0xFF00201F),
    secondary = Color(0xFF4A6362),
    tertiary = Color(0xFF4B607C),
    background = Color(0xFFF6FAF9),
    surface = Color(0xFFF6FAF9),
    surfaceVariant = Color(0xFFDAE5E3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80D5D2),
    onPrimary = Color(0xFF003736),
    primaryContainer = Color(0xFF00504E),
    onPrimaryContainer = Color(0xFF9CF1EE),
    secondary = Color(0xFFB1CCCA),
    tertiary = Color(0xFFB3C8E8),
    background = Color(0xFF0F1414),
    surface = Color(0xFF0F1414),
    surfaceVariant = Color(0xFF3F4948),
)

private val AmoledColors = DarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainer = Color(0xFF101414),
    surfaceContainerHigh = Color(0xFF171C1C),
)

@Composable
fun VArchiveTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = mode == ThemeMode.DARK || mode == ThemeMode.AMOLED || (mode == ThemeMode.SYSTEM && systemDark)
    val context = LocalContext.current
    val colors = when {
        mode == ThemeMode.AMOLED -> AmoledColors
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 && !dark -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
