package com.example.seanimetv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeanimeTVTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = SeanimeBackground,
            surface = SeanimeSurface,
            surfaceVariant = SeanimeSurfaceVariant,
            primary = SeanimePrimary,
            primaryContainer = SeanimePrimaryContainer,
            secondary = Color(0xFF818CF8),
            onBackground = SeanimeOnSurface,
            onSurface = SeanimeOnSurface,
            onSurfaceVariant = SeanimeOnSurfaceMuted,
            onPrimary = Color.White,
            border = SeanimeBorder,
        ),
        typography = Typography,
        content = content
    )
}
