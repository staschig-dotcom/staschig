package ru.staschig.guitar.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.lessons.Focus
import ru.staschig.guitar.lessons.Level

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(store: AppStore, onBack: () -> Unit) {
    val s = store.settings
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Режим обучения") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Уровень сложности", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Level.entries.forEach { l ->
                    FilterChip(s.level == l, { store.updateSettings { it.copy(level = l) } }, { Text(l.title) })
                }
            }

            Text("Время занятия в день", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 15, 20, 30, 45, 60, 90).forEach { m ->
                    FilterChip(s.dailyMinutes == m, { store.updateSettings { it.copy(dailyMinutes = m) } },
                        { Text("$m мин") })
                }
            }
            Text(
                "Длительность блоков урока рассчитывается из этого времени.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Дней занятий в неделю: ${s.daysPerWeek}", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = s.daysPerWeek.toFloat(),
                onValueChange = { v -> store.updateSettings { it.copy(daysPerWeek = v.toInt()) } },
                valueRange = 1f..7f,
                steps = 5,
            )

            Text("Фокус занятий", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Focus.entries.forEach { f ->
                    FilterChip(s.focus == f, { store.updateSettings { it.copy(focus = f) } }, { Text(f.title) })
                }
            }
            Text(
                "Разминка ${(s.focus.warmup * 100).toInt()}% · техника ${(s.focus.technique * 100).toInt()}% · " +
                    "песня ${(s.focus.song * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Калибровка тюнера: A4 = ${s.a4} Гц", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = s.a4.toFloat(),
                onValueChange = { v -> store.updateSettings { it.copy(a4 = v.toInt()) } },
                valueRange = 430f..450f,
                steps = 19,
            )
        }
    }
}
