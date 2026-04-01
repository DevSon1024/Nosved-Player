package com.devson.nosvedplayer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    // Primary - Google Blue
    primary                = NosvedBlue40,
    onPrimary              = Color.White,
    primaryContainer       = NosvedLightPrimCont,
    onPrimaryContainer     = NosvedBlueDeep,

    // Secondary - Indigo / periwinkle
    secondary              = NosvedIndigo40,
    onSecondary            = Color.White,
    secondaryContainer     = NosvedLightSecCont,
    onSecondaryContainer   = Color(0xFF001587),

    // Tertiary - Google Green
    tertiary               = NosvedGreen40,
    onTertiary             = Color.White,
    tertiaryContainer      = Color(0xFFC8F5D5),
    onTertiaryContainer    = Color(0xFF002111),

    // Error
    error                  = NosvedErrorRed,
    onError                = Color.White,
    errorContainer         = Color(0xFFFFDAD6),
    onErrorContainer       = Color(0xFF410002),

    // Backgrounds & surfaces - the key Google-style tokens
    background             = NosvedLightBg,             // #EEF1FB periwinkle-blue ★
    onBackground           = NosvedLightOnSurf,         // near-black

    surface                = NosvedLightSurface,        // #FFFFFF pure white cards ★
    onSurface              = NosvedLightOnSurf,

    surfaceVariant         = NosvedLightSurface2,       // slightly tinted
    onSurfaceVariant       = NosvedLightSubtext,        // secondary text

    // Container hierarchy (TopAppBar, sheets, dialogs)
    surfaceContainerLowest = Color(0xFFF7F9FF),
    surfaceContainerLow    = NosvedLightBg,             // same periwinkle
    surfaceContainer       = Color(0xFFE8EDFF),         // slightly deeper periwinkle
    surfaceContainerHigh   = Color(0xFFFFFFFF),         // white (card backing)
    surfaceContainerHighest= Color(0xFFFFFFFF),         // white (dialog backing)

    // Borders & shape
    outline                = NosvedLightOutline,        // #C5C9E0
    outlineVariant         = Color(0xFFDDE1F5),

    // Inverse (snackbars, tooltips)
    inverseSurface         = Color(0xFF2E3141),
    inverseOnSurface       = Color(0xFFECEEFF),
    inversePrimary         = NosvedBlue80              // #AEC6FF
)

//  Dark scheme 
private val DarkColorScheme = darkColorScheme(
    primary                = NosvedBlue80,              // #AEC6FF light-blue tint
    onPrimary              = Color(0xFF002E6E),
    primaryContainer       = Color(0xFF004398),
    onPrimaryContainer     = NosvedBlue80,

    secondary              = NosvedIndigo80,            // #C5CAE9
    onSecondary            = Color(0xFF1D2578),
    secondaryContainer     = Color(0xFF2D3691),
    onSecondaryContainer   = NosvedIndigo80,

    tertiary               = NosvedGreen80,
    onTertiary             = Color(0xFF00391D),
    tertiaryContainer      = Color(0xFF00522C),
    onTertiaryContainer    = NosvedGreen80,

    error                  = Color(0xFFFFB4AB),
    onError                = Color(0xFF690005),
    errorContainer         = Color(0xFF93000A),
    onErrorContainer       = Color(0xFFFFDAD6),

    background             = NosvedDarkBg,
    onBackground           = NosvedDarkOnSurface,

    surface                = NosvedDarkSurface,
    onSurface              = NosvedDarkOnSurface,

    surfaceVariant         = NosvedDarkSurface2,
    onSurfaceVariant       = NosvedDarkSubtext,

    surfaceContainerLowest = NosvedDarkBg,
    surfaceContainerLow    = NosvedDarkSurface,
    surfaceContainer       = NosvedDarkSurface2,
    surfaceContainerHigh   = NosvedDarkSurface3,
    surfaceContainerHighest= Color(0xFF283350),

    outline                = NosvedDarkOutline,
    outlineVariant         = Color(0xFF263044),

    inverseSurface         = NosvedDarkOnSurface,
    inverseOnSurface       = NosvedDarkBg,
    inversePrimary         = NosvedBlue40
)

/**
 * @param forceDark Explicit dark theme override; null = follow system default.
 * @param dynamicColor If true and SDK >= 31, use Material You wallpaper colours.
 */
@Composable
fun NosvedPlayerTheme(
    forceDark: Boolean? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme  = forceDark ?: systemDark

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as android.app.Activity).window
            window.statusBarColor     = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()

            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars    = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}

/**
 * Fixes the navigation bar colour in dialogs/bottom-sheets that create their own window.
 */
@Composable
fun DialogNavigationBarThemeFix() {
    val view      = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            if (window != null) {
                window.navigationBarColor = Color.Transparent.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
}