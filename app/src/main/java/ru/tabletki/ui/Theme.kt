package ru.tabletki.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

private val Light = lightColorScheme(
    primary = Color(0xFF0B5D3B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBD9),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF3E5A4B),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    tertiaryContainer = Color(0xFFFFE08A),
    onTertiaryContainer = Color(0xFF261A00),
    background = Color.White,
    surface = Color.White,
    onBackground = Color(0xFF111111),
    onSurface = Color(0xFF111111),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FD8AE),
    onPrimary = Color(0xFF00391D),
    primaryContainer = Color(0xFF005230),
    onPrimaryContainer = Color(0xFFCDEBD9),
    tertiaryContainer = Color(0xFF5A4300),
    onTertiaryContainer = Color(0xFFFFE08A),
)

/** Обычная плотность экрана без увеличения шрифта, для элементов, которым тесно (нижнее меню). */
val LocalBaseDensity = androidx.compose.runtime.staticCompositionLocalOf<Density?> { null }

/** Крупный режим увеличивает весь текст в 1,3 раза поверх системной настройки. */
@Composable
fun TabletkiTheme(largeText: Boolean, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val scaled = if (largeText) Density(density.density, density.fontScale * 1.3f) else density
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light) {
        CompositionLocalProvider(LocalDensity provides scaled, LocalBaseDensity provides density, content = content)
    }
}
