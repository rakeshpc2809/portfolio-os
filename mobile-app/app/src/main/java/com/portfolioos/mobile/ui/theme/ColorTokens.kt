package com.portfolioos.mobile.ui.theme

import androidx.compose.ui.graphics.Color

object ColorTokens {
    // Signature Obsidian Deep Navy (#050811 background)
    val ObsidianBackground = Color(0xFF050811)
    val SurfaceCard = Color(0xFF0A0E1A)
    val GlassSurfaceBase = Color(0xFF0E1424)
    val CardBorder = Color(0x1FFFFFFF) // rgba(255, 255, 255, 0.12)
    val SubtleDivider = Color(0xFF1E293B)

    // Text Scale (Aligned with WCAG AAA 7.2:1 contrast against #050811)
    val TextMain = Color(0xFFF8FAFC)      // Slate 50
    val TextMuted = Color(0xFF94A3B8)     // Slate 400 (Upgraded from Slate 500 #64748B for WCAG AAA 7.2:1)
    val TextSubtext = Color(0xFF94A3B8)   // Slate 400
    val TextLightSub = Color(0xFFCBD5E1)  // Slate 300

    // Web Standard Accent Colors (Obsidian Terminal Direction 1)
    val CyanBright = Color(0xFF06B6D4)    // Web --accent-cyan (#06b6d4)
    val CyanGlow = Color(0x2606B6D4)      // rgba(6, 182, 212, 0.15)
    val CyanSky = Color(0xFF38BDF8)       // Web Sky 400 (#38bdf8)
    val PurpleAccent = Color(0xFF8B5CF6)  // Web --purple-accent (#8b5cf6)
    val PurpleLight = Color(0xFFC084FC)   // Web Purple 400 (#c084fc)
    val AmberWarning = Color(0xFFF59E0B)  // Web --accent-amber (#f59e0b)
    val GreenPositive = Color(0xFF10B981) // Web --accent-emerald (#10b981)
    val EmeraldLight = Color(0xFF34D399)  // Web Emerald 400 (#34d399)
    val RedNegative = Color(0xFFF43F5E)   // Web --accent-rose (#f43f5e)
    val ElectricLime = Color(0xFF10B981)  // Shifted from high-glare #D0FF00 to Terminal Emerald #10B981
}
