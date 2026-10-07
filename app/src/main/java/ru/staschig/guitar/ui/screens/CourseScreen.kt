package ru.staschig.guitar.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.lessons.Curriculum
import ru.staschig.guitar.lessons.Level

/** Карта курса: уровни и уроки по порядку, отмечены пройденные и следующий. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseScreen(store: AppStore, onStartLesson: (String) -> Unit) {
    val next = store.nextLesson()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Курс") })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Level.entries.forEach { level ->
                val lessons = Curriculum.byLevel(level)
                val done = lessons.count { it.id in store.completed }
                item(key = "h_" + level.name) {
                    Column(Modifier.padding(top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(level.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            Text("$done из ${lessons.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(progress = { done.toFloat() / lessons.size }, modifier = Modifier.fillMaxWidth())
                    }
                }
                items(lessons, key = { it.id }) { lesson ->
                    val isDone = lesson.id in store.completed
                    val isNext = lesson.id == next?.id
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onStartLesson(lesson.id) },
                        border = if (isNext) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        colors = CardDefaults.cardColors(
                            containerColor = if (isNext) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(36.dp).background(
                                    when {
                                        isDone -> MaterialTheme.colorScheme.tertiary
                                        isNext -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.surface
                                    },
                                    CircleShape,
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (isDone) "✓" else "${lesson.number}",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDone || isNext) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(lesson.technique.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "🎵 ${lesson.song.artist} — ${lesson.song.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            if (isNext) Text("Дальше ▶", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}
