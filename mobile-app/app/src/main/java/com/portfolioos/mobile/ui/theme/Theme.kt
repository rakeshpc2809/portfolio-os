package com.portfolioos.mobile.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalColorTokens = staticCompositionLocalOf { ColorTokens }
val LocalTypographyTokens = staticCompositionLocalOf { TypographyTokens }
val LocalShapeTokens = staticCompositionLocalOf { ShapeTokens }
val LocalSpacingTokens = staticCompositionLocalOf { SpacingTokens }

private val PortfolioDarkColorScheme = darkColorScheme(
    background = ColorTokens.ObsidianBackground,
    surface = ColorTokens.SurfaceCard,
    surfaceVariant = ColorTokens.GlassSurfaceBase,
    primary = ColorTokens.CyanBright,
    secondary = ColorTokens.PurpleAccent,
    tertiary = ColorTokens.ElectricLime,
    error = ColorTokens.RedNegative,
    onBackground = ColorTokens.TextMain,
    onSurface = ColorTokens.TextMain
)

@Composable
fun PortfolioOSTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalColorTokens provides ColorTokens,
        LocalTypographyTokens provides TypographyTokens,
        LocalShapeTokens provides ShapeTokens,
        LocalSpacingTokens provides SpacingTokens
    ) {
        MaterialTheme(
            colorScheme = PortfolioDarkColorScheme,
            content = content
        )
    }
}
