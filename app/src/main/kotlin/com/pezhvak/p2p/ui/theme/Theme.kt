package com.pezhvak.p2p.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Pezhvak brand palette – deep teal mesh aesthetic
private val PezhvakTeal = Color(0xFF00C6A2)
private val PezhvakTealDark = Color(0xFF009B80)
private val PezhvakPurple = Color(0xFF7B52FF)
private val PezhvakSurface = Color(0xFF0D1117)
private val PezhvakSurface2 = Color(0xFF161B22)
private val PezhvakSurface3 = Color(0xFF21262D)
private val PezhvakOnSurface = Color(0xFFE6EDF3)
private val PezhvakOnSurface2 = Color(0xFF8B949E)

private val DarkColorScheme = darkColorScheme(
    primary = PezhvakTeal,
    onPrimary = Color(0xFF003829),
    primaryContainer = Color(0xFF005141),
    onPrimaryContainer = Color(0xFF6FF8D6),

    secondary = PezhvakPurple,
    onSecondary = Color(0xFF2A0066),
    secondaryContainer = Color(0xFF3F1F8F),
    onSecondaryContainer = Color(0xFFCFBCFF),

    tertiary = Color(0xFF4EABF7),
    onTertiary = Color(0xFF003354),

    background = PezhvakSurface,
    onBackground = PezhvakOnSurface,

    surface = PezhvakSurface,
    onSurface = PezhvakOnSurface,
    surfaceVariant = PezhvakSurface2,
    onSurfaceVariant = PezhvakOnSurface2,

    outline = Color(0xFF30363D),
    outlineVariant = Color(0xFF21262D),

    error = Color(0xFFFF6B6B),
    onError = Color(0xFF690005),
)

private val LightColorScheme = lightColorScheme(
    primary = PezhvakTealDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2F5E4),
    onPrimaryContainer = Color(0xFF002117),

    secondary = Color(0xFF5E35B1),
    onSecondary = Color.White,

    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1C2128),

    surface = Color.White,
    onSurface = Color(0xFF1C2128),
    surfaceVariant = Color(0xFFF0F6FF),
    onSurfaceVariant = Color(0xFF57606A),

    outline = Color(0xFFD0D7DE),
)

@Composable
fun PezhvakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // We use brand colors, not dynamic
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PezhvakTypography,
        shapes = PezhvakShapes,
        content = content,
    )
}
