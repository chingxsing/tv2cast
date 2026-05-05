package com.tv2cast.app.ui.theme

import androidx.compose.ui.graphics.Color

// Dark theme colors matching the Web UI
val BgPrimary = Color(0xFF0A0A0F)
val BgSecondary = Color(0xFF12121A)
val BgCard = Color(0xFF141420)
val BgCardHover = Color(0xFF1E1E30)

val Accent = Color(0xFF7C5CFF)
val AccentLight = Color(0xFFA78BFA)
val AccentCyan = Color(0xFF00D4FF)

val TextPrimary = Color(0xFFF0F0F5)
val TextSecondary = Color(0xFF8888A0)
val TextMuted = Color(0xFF555568)

val Success = Color(0xFF34D399)
val Warning = Color(0xFFFBBF24)
val Error = Color(0xFFF87171)

// Extension colors
fun getExtensionColor(ext: String): Color = when (ext.lowercase()) {
    "mp4" -> Color(0xFF4A9EFF)
    "mkv" -> Color(0xFF34D399)
    "avi" -> Color(0xFFF87171)
    "mov" -> Color(0xFFFBBF24)
    "webm" -> Color(0xFFA78BFA)
    "m4v" -> Color(0xFF60A5FA)
    else -> TextSecondary
}
