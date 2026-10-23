package com.zoomy.wallpaper.ui.theme

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

private val DefaultLightScheme = lightColorScheme(
    primary = Color(0xFF0061A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF535F70),
    secondaryContainer = Color(0xFFD7E3F7),
    background = Color(0xFFF8F9FF),
    surface = Color(0xFFF8F9FF)
)

private val DefaultDarkScheme = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB),
    secondaryContainer = Color(0xFF3B4858),
    background = Color(0xFF111318),
    surface = Color(0xFF111318)
)

/**
 * [themeMode]: 0 = System, 1 = Light, 2 = Dark, 3 = Pure AMOLED Black
 */
@Composable
fun ZoomyTheme(
    themeMode: Int = 0,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        1 -> false
        2, 3 -> true
        else -> systemDark
    }

    val baseScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (useDark) DefaultDarkScheme else DefaultLightScheme
    }

    val finalScheme = if (themeMode == 3) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color(0xFF0D0F14),
            surfaceContainerLow = Color(0xFF080A0D),
            surfaceContainerHigh = Color(0xFF151820)
        )
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = finalScheme,
        content = content
    )
}
