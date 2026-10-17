package ru.staschig.guitar.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.staschig.guitar.lessons.Chord

/** Пояснение к цифрам на схеме аккорда. */
const val FINGER_LEGEND = "Цифры — пальцы левой руки: 1 указательный, 2 средний, 3 безымянный, 4 мизинец, Б — большой. " +
    "○ — струна звучит открытой, ✕ — не играть. Полоса — баррэ (один палец на нескольких струнах)."

/**
 * Аккордовая диаграмма: вертикальные струны (6-я слева), 5 ладов.
 * В кружках — номер пальца; баррэ рисуется полосой. [legend] — подпись, что значат цифры.
 */
@Composable
fun ChordDiagram(chord: Chord, modifier: Modifier = Modifier, width: Dp = 110.dp, legend: Boolean = false) {
    val fretted = chord.frets.filterNotNull().filter { it > 0 }
    val base = if ((fretted.maxOrNull() ?: 0) > 4) fretted.min() else 1
    val line = MaterialTheme.colorScheme.onSurface
    val dot = MaterialTheme.colorScheme.primary
    val onDot = MaterialTheme.colorScheme.onPrimary
    val measurer = rememberTextMeasurer()
    val fingerStyle = TextStyle(
        fontSize = (width.value / 9).sp,
        fontWeight = FontWeight.Bold,
        color = onDot,
        textAlign = TextAlign.Center,
    )
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
            val r = sx * 0.38f
            // Баррэ — полоса под указательным пальцем.
            chord.barre?.let { (fret, from, to) ->
                val y = top + (fret - base + 0.5f) * fy
                drawRoundRect(
                    dot,
                    topLeft = Offset(left + from * sx - r, y - r),
                    size = Size((to - from) * sx + 2 * r, 2 * r),
                    cornerRadius = CornerRadius(r, r),
                )
            }
            chord.frets.forEachIndexed { s, fret ->
                val x = left + s * sx
                when {
                    fret == null -> {
                        val y = top * 0.5f
                        drawLine(line, Offset(x - r * 0.6f, y - r * 0.6f), Offset(x + r * 0.6f, y + r * 0.6f), 3f)
                        drawLine(line, Offset(x + r * 0.6f, y - r * 0.6f), Offset(x - r * 0.6f, y + r * 0.6f), 3f)
                    }
                    fret == 0 -> drawCircle(line, r * 0.6f, Offset(x, top * 0.5f), style = Stroke(3f))
                    else -> {
                        val center = Offset(x, top + (fret - base + 0.5f) * fy)
                        drawCircle(dot, r, center)
                        val finger = chord.fingers.getOrElse(s) { 0 }
                        if (finger > 0) {
                            val label = if (finger == 5) "Б" else "$finger"
                            val t = measurer.measure(label, fingerStyle)
                            drawText(t, topLeft = Offset(center.x - t.size.width / 2f, center.y - t.size.height / 2f))
                        }
                    }
                }
            }
        }
        if (base > 1) Text("$base лад", style = MaterialTheme.typography.bodySmall)
        if (legend) {
            Text(
                FINGER_LEGEND,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(width * 2.2f),
            )
        }
    }
}
