package com.audioflow.player.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AudioFlowColorScheme = darkColorScheme(
    primary = SpotifyGreen,
    onPrimary = SpotifyBlack,
    primaryContainer = SpotifyGreenDark,
    onPrimaryContainer = TextPrimary,
    
    secondary = SpotifyGreen,
    onSecondary = SpotifyBlack,
    secondaryContainer = SpotifySurfaceVariant,
    onSecondaryContainer = TextPrimary,
    
    tertiary = SpotifyGreen,
    onTertiary = SpotifyBlack,
    
    background = SpotifyBlack,
    onBackground = TextPrimary,
    
    surface = SpotifySurface,
    onSurface = TextPrimary,
    surfaceVariant = SpotifySurfaceVariant,
    onSurfaceVariant = TextSecondary,
    
    error = ErrorRed,
    onError = TextPrimary,
    
    outline = Divider,
    outlineVariant = SpotifySurfaceVariant
)

@Composable
fun AudioFlowTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.let { activity ->
                activity.window.statusBarColor = SpotifyBlack.toArgb()
                activity.window.navigationBarColor = SpotifyBlack.toArgb()
                WindowCompat.getInsetsController(activity.window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = AudioFlowColorScheme,
        typography = AudioFlowTypography,
        content = content
    )
}
