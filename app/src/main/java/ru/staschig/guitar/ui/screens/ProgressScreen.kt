package ru.staschig.guitar.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.lessons.Curriculum
import ru.staschig.guitar.lessons.Level
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(store: AppStore) {
    val s = store.settings
    val weekGoal = s.dailyMinutes * s.daysPerWeek
    val week = store.minutesThisWeek()
    var confirmReset by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Прогресс") })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Серия", "${store.streak()} дн.", Modifier.weight(1f))
                Stat("Всего", "${store.totalMinutes() / 60} ч ${store.totalMinutes() % 60} м", Modifier.weight(1f))
                Stat("Уроков", "${store.completed.size}/${Curriculum.lessons.size}", Modifier.weight(1f))
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Неделя: $week из $weekGoal мин", fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(
                        progress = { (week.toFloat() / weekGoal).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Последние 14 дней (линия — дневная цель ${s.dailyMinutes} мин)",
                        style = MaterialTheme.typography.bodySmall)
                    val today = LocalDate.now()
                    val days = (13 downTo 0).map { store.minutesOn(today.minusDays(it.toLong())) }
                    val maxV = maxOf(days.maxOrNull() ?: 0, s.dailyMinutes, 1).toFloat()
                    val bar = MaterialTheme.colorScheme.primary
                    val goalColor = MaterialTheme.colorScheme.secondary
                    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                        val w = size.width / days.size
                        days.forEachIndexed { i, v ->
                            val h = size.height * v / maxV
                            drawRoundRect(
                                bar,
                                topLeft = Offset(i * w + w * 0.15f, size.height - h),
                                size = Size(w * 0.7f, h),
                                cornerRadius = CornerRadius(6f, 6f),
                            )
                        }
                        val gy = size.height - size.height * s.dailyMinutes / maxV
                        drawLine(goalColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 3f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)))
                    }
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Уровни", fontWeight = FontWeight.Bold)
                    Level.entries.forEach { level ->
                        val all = Curriculum.byLevel(level)
                        val done = all.count { it.id in store.completed }
                        Text("${level.title}: $done из ${all.size}")
                        LinearProgressIndicator(
                            progress = { done.toFloat() / all.size },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            if (store.bpmRecords.isNotEmpty()) {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Рекорды темпа (сыграно чисто)", fontWeight = FontWeight.Bold)
                        store.bpmRecords.entries.sortedBy { it.key }.forEach { (id, bpm) ->
                            Text("${exerciseTitle(id)}: $bpm BPM")
                        }
                    }
                }
            }

            TextButton(onClick = { confirmReset = true }) { Text("Сбросить прогресс") }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Сбросить прогресс?") },
            text = { Text("Удалятся пройденные уроки, журнал практики и рекорды темпа. Табы останутся.") },
            confirmButton = {
                TextButton(onClick = { store.resetProgress(); confirmReset = false }) { Text("Сбросить") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

private fun exerciseTitle(id: String): String {
    Curriculum.lessons.forEach { l ->
        if (l.warmup.id == id) return l.warmup.title
        if (l.technique.id == id) return l.technique.title
        if (l.song.id == id) return "${l.song.artist} — ${l.song.title}"
    }
    return id
}
