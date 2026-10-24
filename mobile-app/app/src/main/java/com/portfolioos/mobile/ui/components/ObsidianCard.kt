package com.portfolioos.mobile.ui.components

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.portfolioos.mobile.ui.theme.ColorTokens
import com.portfolioos.mobile.ui.theme.ShapeTokens
import com.portfolioos.mobile.ui.theme.SpacingTokens

/**
 * ObsidianCard - Modern Material 3 Expressive glassmorphic container
 * Features:
 * - Layered Obsidian surface depth (SurfaceCard / GlassSurfaceBase)
 * - Hardware-accelerated GPU RenderEffect blur on Android 12+ (API 31+)
 * - Micro-haptic tactile feedback on interaction
 * - Micro-border highlight aligned to the Web Cockpit OKLCH tokens
 */
@Composable
fun ObsidianCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = ColorTokens.SurfaceCard,
    borderColor: Color = ColorTokens.CardBorder,
    borderWidth: Dp = 1.dp,
    blurRadius: Dp = 0.dp,
    contentPadding: Dp = SpacingTokens.lg,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0.dp) {
        Modifier.graphicsLayer {
            val radiusPx = blurRadius.toPx()
            renderEffect = android.graphics.RenderEffect
                .createBlurEffect(radiusPx, radiusPx, android.graphics.Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
    } else {
        Modifier
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    } else {
        Modifier
    }

    Card(
        shape = ShapeTokens.GlassCardShape,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(borderWidth, borderColor),
        modifier = modifier
            .then(blurModifier)
            .then(clickableModifier)
    ) {
        Box(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}
