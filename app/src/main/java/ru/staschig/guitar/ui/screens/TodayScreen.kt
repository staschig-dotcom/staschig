package ru.staschig.guitar.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.lessons.StepKind

/** Главный экран: что делать сегодня — одна главная кнопка. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    store: AppStore,
    onStartLesson: (String) -> Unit,
    onOpen: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val s = store.settings
    val today = store.minutesToday()
    val lesson = store.nextLesson()
    val date = LocalDate.now()
    val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ru")).replaceFirstChar { it.uppercase() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Сегодня") },
            actions = { IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Настройки") } },
        )
        val progressBlock: @Composable () -> Unit = {
            // Прогресс дня
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$dayName · $today из ${s.dailyMinutes} мин", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    val streak = store.streak()
                    if (streak > 0) Text("🔥 $streak дн. подряд", style = MaterialTheme.typography.titleSmall)
                }
                LinearProgressIndicator(
                    progress = { (today.toFloat() / s.dailyMinutes).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (today >= s.dailyMinutes) {
                    Text("✓ Цель на сегодня выполнена. Можно позаниматься ещё.", color = MaterialTheme.colorScheme.tertiary)
                }
        }

        }
        val lessonBlock: @Composable () -> Unit = {
            // Следующее занятие
            if (lesson == null) {
                Card {
                    Text(
                        "Все уроки курса пройдены 🎉 Повторяйте любимые уроки во вкладке «Курс» и играйте пьесы с проверкой.",
                        Modifier.padding(16.dp),
                    )
                }
            } else {
                val steps = lesson.steps(s.dailyMinutes, s.focus)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Урок ${lesson.number} · ${lesson.level.title}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(lesson.technique.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        val plan = buildList {
                            if (!store.tunedToday()) add("Настроить гитару" to 1)
                            steps.forEach { st ->
                                add(
                                    when (st.kind) {
                                        StepKind.WARMUP -> "Разминка: ${st.title}"
                                        StepKind.TECHNIQUE -> "Упражнение: ${st.title}"
                                        StepKind.SONG -> "Песня: ${st.title}"
                                    } to st.minutes
                                )
                            }
                        }
                        plan.forEachIndexed { i, (text, min) ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text("${i + 1}.", Modifier.width(22.dp), fontWeight = FontWeight.Bold)
                                Text(text, Modifier.weight(1f))
                                Text("$min мин", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        BigButton("▶  Начать занятие · ${plan.sumOf { it.second }} мин", { onStartLesson(lesson.id) })
                    }
                }
        }

        }
        val toolsBlock: @Composable () -> Unit = {
            Text("Инструменты", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile(Icons.Filled.GraphicEq, "Тюнер", Modifier.weight(1f)) { onOpen("tuner") }
                QuickTile(Icons.Filled.Timer, "Метроном", Modifier.weight(1f)) { onOpen("metronome") }
                QuickTile(Icons.Filled.SportsEsports, "Играть", Modifier.weight(1f)) { onOpen("playlist") }
        }
        }
        if (isWide()) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(
                    Modifier.weight(0.4f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    progressBlock()
                    toolsBlock()
                }
                Column(Modifier.weight(0.6f).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    lessonBlock()
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                progressBlock()
                lessonBlock()
                toolsBlock()
                Spacer(Modifier.size(8.dp))
            }
        }
    }
}

@Composable
fun QuickTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}
