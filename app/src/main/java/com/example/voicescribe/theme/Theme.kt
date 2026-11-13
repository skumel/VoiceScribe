package com.example.voicescribe.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.voicescribe.model.AppThemePalette
import com.example.voicescribe.model.ThemeMode

private val DefaultDarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val DefaultLightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val OceanLightScheme = lightColorScheme(
    primary = OceanPrimaryLight,
    secondary = OceanSecondaryLight,
    tertiary = OceanTertiaryLight,
    background = OceanBackgroundLight,
    surface = OceanSurfaceLight
)

private val OceanDarkScheme = darkColorScheme(
    primary = OceanPrimaryDark,
    secondary = OceanSecondaryDark,
    tertiary = OceanTertiaryDark,
    background = OceanBackgroundDark,
    surface = OceanSurfaceDark
)

private val EmeraldLightScheme = lightColorScheme(
    primary = EmeraldPrimaryLight,
    secondary = EmeraldSecondaryLight,
    tertiary = EmeraldTertiaryLight,
    background = EmeraldBackgroundLight,
    surface = EmeraldSurfaceLight
)

private val EmeraldDarkScheme = darkColorScheme(
    primary = EmeraldPrimaryDark,
    secondary = EmeraldSecondaryDark,
    tertiary = EmeraldTertiaryDark,
    background = EmeraldBackgroundDark,
    surface = EmeraldSurfaceDark
)

private val SunsetLightScheme = lightColorScheme(
    primary = SunsetPrimaryLight,
    secondary = SunsetSecondaryLight,
    tertiary = SunsetTertiaryLight,
    background = SunsetBackgroundLight,
    surface = SunsetSurfaceLight
)

private val SunsetDarkScheme = darkColorScheme(
    primary = SunsetPrimaryDark,
    secondary = SunsetSecondaryDark,
    tertiary = SunsetTertiaryDark,
    background = SunsetBackgroundDark,
    surface = SunsetSurfaceDark
)

private val MidnightLightScheme = lightColorScheme(
    primary = MidnightPrimaryLight,
    secondary = MidnightSecondaryLight,
    tertiary = MidnightTertiaryLight,
    background = MidnightBackgroundLight,
    surface = MidnightSurfaceLight
)

private val MidnightDarkScheme = darkColorScheme(
    primary = MidnightPrimaryDark,
    secondary = MidnightSecondaryDark,
    tertiary = MidnightTertiaryDark,
    background = MidnightBackgroundDark,
    surface = MidnightSurfaceDark
)

private val RoseLightScheme = lightColorScheme(
    primary = RosePrimaryLight,
    secondary = RoseSecondaryLight,
    tertiary = RoseTertiaryLight,
    background = RoseBackgroundLight,
    surface = RoseSurfaceLight
)

private val RoseDarkScheme = darkColorScheme(
    primary = RosePrimaryDark,
    secondary = RoseSecondaryDark,
    tertiary = RoseTertiaryDark,
    background = RoseBackgroundDark,
    surface = RoseSurfaceDark
)

private val SlateLightScheme = lightColorScheme(
    primary = SlatePrimaryLight,
    secondary = SlateSecondaryLight,
    tertiary = SlateTertiaryLight,
    background = SlateBackgroundLight,
    surface = SlateSurfaceLight
)

private val SlateDarkScheme = darkColorScheme(
    primary = SlatePrimaryDark,
    secondary = SlateSecondaryDark,
    tertiary = SlateTertiaryDark,
    background = SlateBackgroundDark,
    surface = SlateSurfaceDark
)

@Composable
fun VoiceScribeTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    palette: AppThemePalette = AppThemePalette.DYNAMIC,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme: ColorScheme = when (palette) {
        AppThemePalette.DYNAMIC -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (isDark) DefaultDarkColorScheme else DefaultLightColorScheme
            }
        }
        AppThemePalette.OCEAN_BLUE -> if (isDark) OceanDarkScheme else OceanLightScheme
        AppThemePalette.EMERALD_FOREST -> if (isDark) EmeraldDarkScheme else EmeraldLightScheme
        AppThemePalette.SUNSET_GLOW -> if (isDark) SunsetDarkScheme else SunsetLightScheme
        AppThemePalette.MIDNIGHT_NEBULA -> if (isDark) MidnightDarkScheme else MidnightLightScheme
        AppThemePalette.ROSE_BLOOM -> if (isDark) RoseDarkScheme else RoseLightScheme
        AppThemePalette.SLATE_MONO -> if (isDark) SlateDarkScheme else SlateLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
