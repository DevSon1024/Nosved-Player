package com.devson.nosvedplayer.ui.theme

import androidx.compose.ui.graphics.Color

//  NOSVED PLAYER - Shared colour tokens
//  Full per-palette ColorSchemes live in AppThemePalette.kt.
//  Constants here are used directly by Theme.kt default schemes and in the
//  player UI (seek-bar tint, status badges, etc.).

// CINEMATIC palette raw tokens 
//  Primary Accent: Electric Cyan
val NosvedCyan80        = Color(0xFF80EEFF)   // Light tint  (dark-theme primary)
val NosvedCyan40        = Color(0xFF00BCD4)   // Mid tone    (light-theme primary)
val NosvedCyanDeep      = Color(0xFF006978)   // Deep        (primaryContainer text, dark)
val NosvedCyanAccent    = Color(0xFF00E5FF)   // Pure neon   (seek-bar, highlights)

//  Secondary Accent: Amber / Gold
val NosvedAmber80       = Color(0xFFFFE082)   // Light tint  (dark-theme secondary)
val NosvedAmber40       = Color(0xFFFFB300)   // Saturated   (light-theme secondary)

//  Tertiary: Deep Violet
val NosvedViolet80      = Color(0xFFD0BCFF)   // Light tint  (dark-theme tertiary)
val NosvedViolet40      = Color(0xFF7C4DFF)   // Saturated   (light-theme tertiary)

// NOSVED BLUE palette raw tokens
val NosvedBlue80        = Color(0xFFAEC6FF)   // Light tint  (dark theme primary)
val NosvedBlue40        = Color(0xFF1A73E8)   // Saturated   (light theme primary)
val NosvedBlueDeep      = Color(0xFF0D47A1)   // Deep blue   (dark primaryContainer text)

val NosvedIndigo80      = Color(0xFFC5CAE9)   // Light tint  (dark theme secondary)
val NosvedIndigo40      = Color(0xFF3949AB)   // Medium      (light theme secondary)

val NosvedGreen80       = Color(0xFFB9F6CA)   // Light       (dark theme tertiary)
val NosvedGreen40       = Color(0xFF1E8E3E)   // Medium      (light theme tertiary)

//  Blue light surfaces (Google Play / Material 3 style)
val NosvedBlueLightBg       = Color(0xFFEEF1FB)   // Soft periwinkle-blue background
val NosvedBlueLightSurface  = Color(0xFFFFFFFF)   // Pure white cards / dialogs
val NosvedBlueLightSurface2 = Color(0xFFF3F6FF)   // Slightly tinted surface container
val NosvedBlueLightOutline  = Color(0xFFC5C9E0)   // Border / divider
val NosvedBlueLightOnSurf   = Color(0xFF1A1C22)   // Near-black primary text
val NosvedBlueLightSubtext  = Color(0xFF44474F)   // Secondary / hint text
val NosvedBlueLightPrimCont = Color(0xFFDAE2FF)   // Primary container (chips, badges)
val NosvedBlueLightSecCont  = Color(0xFFE0E5FF)   // Secondary container (icon bgs)

//  Blue dark surfaces
val NosvedBlueDarkBg        = Color(0xFF0E1117)   // Background
val NosvedBlueDarkSurface   = Color(0xFF161B27)   // Card / surface level 1
val NosvedBlueDarkSurface2  = Color(0xFF1E2537)   // Card / surface level 2 (elevated)
val NosvedBlueDarkSurface3  = Color(0xFF222C3F)   // Highest surface container
val NosvedBlueDarkOutline   = Color(0xFF3A4660)   // Border / outline
val NosvedBlueDarkOnSurface = Color(0xFFE2E5F0)   // Primary text on dark
val NosvedBlueDarkSubtext   = Color(0xFF8D97B2)   // Secondary / hint text

// Shared surface tokens (Cinematic — default)
val NosvedLightBg       = Color(0xFFF0F4F8)
val NosvedLightSurface  = Color(0xFFFFFFFF)
val NosvedLightSurface2 = Color(0xFFE8EDF4)
val NosvedLightOutline  = Color(0xFFB0BAC8)
val NosvedLightOnSurf   = Color(0xFF0D1117)
val NosvedLightSubtext  = Color(0xFF444F5C)
val NosvedLightPrimCont = Color(0xFFB2EBF2)
val NosvedLightSecCont  = Color(0xFFFFECB3)

val NosvedDarkBg        = Color(0xFF0A0C10)
val NosvedDarkSurface   = Color(0xFF0F1318)
val NosvedDarkSurface2  = Color(0xFF161B22)
val NosvedDarkSurface3  = Color(0xFF1C222C)
val NosvedDarkOutline   = Color(0xFF2A3340)
val NosvedDarkOnSurface = Color(0xFFDDE3EC)
val NosvedDarkSubtext   = Color(0xFF8896A8)

// Error / status
val NosvedErrorRed      = Color(0xFFCF6679)   // Error / destructive
val NosvedSeekBarTint   = NosvedCyanAccent    // Progress / scrubber tint (Cinematic default)