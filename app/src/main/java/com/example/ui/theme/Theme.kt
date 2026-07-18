package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

object MineHostTheme {
    val cardCornerRadius = 24.dp
    val smallCardCornerRadius = 18.dp
    val buttonCornerRadius = 16.dp
    val screenHorizontalPadding = 16.dp
    val cardSpacing = 12.dp
    val topBarHeight = 48.dp
    val bottomBarHeight = 48.dp
    val elevationDefault = 5.dp
    val elevationHigh = 10.dp
}

private val LightColors = lightColorScheme(
    primary = MineHostBlue,
    onPrimary = MineHostSurface,
    secondary = MineHostPurple,
    onSecondary = MineHostSurface,
    tertiary = MineHostCyan,
    background = MineHostBackground,
    onBackground = MineHostTextPrimary,
    surface = MineHostSurface,
    onSurface = MineHostTextPrimary,
    surfaceVariant = MineHostSurfaceVariant,
    onSurfaceVariant = MineHostTextSecondary,
    error = MineHostRed,
    onError = MineHostSurface,
    outline = MineHostOutline,
    outlineVariant = MineHostDivider
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MineHostAppTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = MineHostBackgroundTop.toArgb()
            window.navigationBarColor = MineHostSurface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
