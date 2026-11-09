package com.sminkeliste.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Rose = Color(0xFFD6336C)     // the app's pink
val Leaf = Color(0xFF8E1F4C)     // deep berry (cards, update dialog); named like the other apps' shared code
val Blush = Color(0xFFFFE0EA)
val Petal = Color(0xFFFFF0F5)
val Sun = Color(0xFFFFD166)      // gold accents
val Sale = Color(0xFFB0124F)     // sale tags (dark enough to read on white)
val Ink = Color(0xFF2B1520)
val Paper = Color(0xFFFFF6F9)

/** The soft pink gradient used for headers. */
val PinkGlow = Brush.linearGradient(listOf(Color(0xFFF783AC), Color(0xFFD6336C), Color(0xFFA61E4D)))

private val Light = lightColorScheme(
    primary = Rose, onPrimary = Color.White,
    primaryContainer = Blush, onPrimaryContainer = Color(0xFF6B0F36),
    secondary = Color(0xFF9C4F96), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6E1F4), onSecondaryContainer = Color(0xFF4A1546),
    tertiary = Color(0xFF8A6100), tertiaryContainer = Color(0xFFFFEFC7), onTertiaryContainer = Color(0xFF4A3400),
    background = Paper, onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = Color(0xFFF7E4EB), onSurfaceVariant = Color(0xFF6A4D59),
    surfaceContainer = Petal, surfaceContainerHigh = Color(0xFFFBE8EF),
    outline = Color(0xFFD9B3C2), outlineVariant = Color(0xFFF0D7E1),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF8FB5), onPrimary = Color(0xFF5E0A2E),
    primaryContainer = Color(0xFF7A1D45), onPrimaryContainer = Color(0xFFFFD9E5),
    secondary = Color(0xFFE6A8E0), onSecondary = Color(0xFF4A1546),
    secondaryContainer = Color(0xFF5E2A5A), onSecondaryContainer = Color(0xFFFBD7F5),
    tertiary = Sun, tertiaryContainer = Color(0xFF4A3400), onTertiaryContainer = Color(0xFFFFE3A0),
    background = Color(0xFF1A0F14), onBackground = Color(0xFFF5E1E8),
    surface = Color(0xFF241519), onSurface = Color(0xFFF5E1E8),
    surfaceVariant = Color(0xFF3A2630), onSurfaceVariant = Color(0xFFD8BCC7),
    surfaceContainer = Color(0xFF2A1A20), surfaceContainerHigh = Color(0xFF332028),
    outline = Color(0xFF7D5E6B), outlineVariant = Color(0xFF4D3640),
)

@Composable
fun SminkeTheme(content: @Composable () -> Unit) {
    val t = Typography()
    val scheme = if (isSystemInDarkTheme()) Dark else Light
    MaterialTheme(
        colorScheme = scheme,
        typography = t.copy(
            headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
            titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}
