package com.example.vishnu.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Sand,
    onPrimaryContainer = Ink,
    secondary = InkMuted,
    onSecondary = Color.White,
    secondaryContainer = Linen,
    onSecondaryContainer = Ink,
    tertiary = Brass,
    onTertiary = Color.White,
    tertiaryContainer = ForestSoft,
    onTertiaryContainer = Forest,
    background = Cream,
    onBackground = Ink,
    surface = Cream,
    onSurface = Ink,
    surfaceVariant = Linen,
    onSurfaceVariant = InkMuted,
    surfaceTint = Color.Transparent,
    outline = Hairline,
    outlineVariant = HairlineSoft,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Color(0xFFF3EFE5),
    surfaceContainer = Color(0xFFEFEADF),
    surfaceContainerHigh = Color(0xFFEAE4D7),
    surfaceContainerHighest = Linen
)

private val DarkColorScheme = darkColorScheme(
    primary = Sage,
    onPrimary = Color(0xFF1B241E),
    primaryContainer = SageDeep,
    onPrimaryContainer = Color(0xFFE3EBE0),
    secondary = ParchmentMuted,
    onSecondary = Charcoal,
    secondaryContainer = Umber,
    onSecondaryContainer = Parchment,
    tertiary = BrassLight,
    onTertiary = Charcoal,
    tertiaryContainer = SageDeep,
    onTertiaryContainer = Parchment,
    background = Charcoal,
    onBackground = Parchment,
    surface = Charcoal,
    onSurface = Parchment,
    surfaceVariant = Umber,
    onSurfaceVariant = ParchmentMuted,
    surfaceTint = Color.Transparent,
    outline = HairlineDark,
    outlineVariant = Color(0xFF38342F),
    surfaceContainerLowest = CharcoalRaised,
    surfaceContainerLow = Color(0xFF211F1C),
    surfaceContainer = Color(0xFF282622),
    surfaceContainerHigh = Umber,
    surfaceContainerHighest = UmberHigh
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun VishnuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
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
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
