package ru.staschig.guitar.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.staschig.guitar.lessons.Chord

/** Аккордовая диаграмма: вертикальные струны (6-я слева), 5 ладов. */
@Composable
fun ChordDiagram(chord: Chord, modifier: Modifier = Modifier, width: Dp = 110.dp) {
    val fretted = chord.frets.filterNotNull().filter { it > 0 }
    val base = if ((fretted.maxOrNull() ?: 0) > 4) fretted.min() else 1
    val line = MaterialTheme.colorScheme.onSurface
    val dot = MaterialTheme.colorScheme.primary
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(chord.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Canvas(Modifier.size(width, width * 1.25f)) {
            val top = size.height * 0.14f
            val left = size.width * 0.12f
            val right = size.width * 0.88f
            val bottom = size.height * 0.96f
            val sx = (right - left) / 5
            val fy = (bottom - top) / 5
            for (s in 0..5) drawLine(line, Offset(left + s * sx, top), Offset(left + s * sx, bottom), 2f)
            for (f in 0..5) {
                val w = if (f == 0 && base == 1) 7f else 2f
                drawLine(line, Offset(left, top + f * fy), Offset(right, top + f * fy), w)
            }
            val r = sx * 0.32f
            chord.frets.forEachIndexed { s, fret ->
                val x = left + s * sx
                when {
                    fret == null -> {
                        val y = top * 0.5f
                        drawLine(line, Offset(x - r * 0.7f, y - r * 0.7f), Offset(x + r * 0.7f, y + r * 0.7f), 3f)
                        drawLine(line, Offset(x + r * 0.7f, y - r * 0.7f), Offset(x - r * 0.7f, y + r * 0.7f), 3f)
                    }
                    fret == 0 -> drawCircle(line, r * 0.75f, Offset(x, top * 0.5f), style = Stroke(3f))
                    else -> drawCircle(dot, r, Offset(x, top + (fret - base + 0.5f) * fy))
                }
            }
        }
        if (base > 1) Text("$base лад", style = MaterialTheme.typography.bodySmall)
    }
}
