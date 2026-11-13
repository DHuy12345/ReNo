package com.example.project_ck.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ReNoColorScheme = lightColorScheme(
    primary = PrimaryAccent,
    onPrimary = Color.White,
    primaryContainer = PrimaryAccentLight,
    onPrimaryContainer = PrimaryAccent,
    secondary = CardBackground,
    onSecondary = MainText,
    background = PrimaryBackground,
    onBackground = MainText,
    surface = SecondaryBackground,
    onSurface = MainText,
    error = ErrorColor,
    onError = Color.White,
    outline = BorderColor
)

@Composable
fun ReNoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ReNoColorScheme,
        typography = Typography,
        content = content
    )
}