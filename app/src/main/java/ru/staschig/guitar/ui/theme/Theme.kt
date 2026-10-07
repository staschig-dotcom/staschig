package ru.staschig.guitar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Color(0xFFF4A261),
    onPrimary = Color(0xFF2B1600),
    primaryContainer = Color(0xFF5C3A1A),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFF8ECAE6),
    onSecondary = Color(0xFF00222F),
    tertiary = Color(0xFF90BE6D),
    background = Color(0xFF1B1B1F),
    surface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFF2C2C33),
    onSurfaceVariant = Color(0xFFC9C5CA),
    error = Color(0xFFE76F51),
)

val InTune = Color(0xFF90BE6D)
val OutOfTune = Color(0xFFE76F51)

@Composable
fun GuitarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
