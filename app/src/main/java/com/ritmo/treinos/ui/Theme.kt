package com.ritmo.treinos.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val dark = darkColorScheme(primary = Color(0xFFC3F85C), onPrimary = Color(0xFF203000), primaryContainer = Color(0xFF283A16), onPrimaryContainer = Color(0xFFD5FBA6), background = Color(0xFF101412), onBackground = Color(0xFFE8EDE7), surface = Color(0xFF171D19), surfaceContainer = Color(0xFF202721), surfaceVariant = Color(0xFF2C352E), onSurfaceVariant = Color(0xFFB9C5B9), outline = Color(0xFF69796B), secondary = Color(0xFFABCCB7))
private val light = lightColorScheme(primary = Color(0xFF466B13), onPrimary = Color.White, primaryContainer = Color(0xFFD5FBA6), onPrimaryContainer = Color(0xFF172700), background = Color(0xFFF6F9F2), surface = Color(0xFFF6F9F2), secondary = Color(0xFF40664F))
@Composable fun RitmoTheme(mode: String, content: @Composable () -> Unit) {
    val isDark = mode == "dark" || (mode == "system" && isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (isDark) dark else light, typography = Typography(
        headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
        headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    ), content = content)
}
