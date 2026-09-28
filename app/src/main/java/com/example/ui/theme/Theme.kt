package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ObsidianColorScheme = darkColorScheme(
    primary = ObsidianPurple,
    onPrimary = Color.White,
    primaryContainer = ObsidianPurpleContainer,
    onPrimaryContainer = ObsidianPurpleLight,
    secondary = ObsidianPurpleLight,
    onSecondary = Color.Black,
    secondaryContainer = ObsidianSurfaceElevated,
    onSecondaryContainer = ObsidianTextPrimary,
    tertiary = ObsidianTeal,
    onTertiary = Color.Black,
    background = ObsidianBackground,
    onBackground = ObsidianTextPrimary,
    surface = ObsidianSurface,
    onSurface = ObsidianTextPrimary,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = ObsidianTextSecondary,
    outline = ObsidianBorder,
    outlineVariant = ObsidianBorderLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Preserve Obsidian identity
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ObsidianColorScheme,
        typography = Typography,
        content = content
    )
}
