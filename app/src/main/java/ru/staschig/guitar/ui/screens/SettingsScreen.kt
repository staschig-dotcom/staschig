package ru.staschig.guitar.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.data.Settings
import ru.staschig.guitar.lessons.Focus
import ru.staschig.guitar.lessons.Level
import ru.staschig.guitar.reminders.Reminders

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

            ReminderSettings(store)

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderSettings(store: AppStore) {
    val context = LocalContext.current
    val s = store.settings
    fun update(t: (Settings) -> Settings) {
        store.updateSettings(t)
        Reminders.schedule(context, store.settings)
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Напоминания о занятиях", style = MaterialTheme.typography.titleMedium)
            Text(
                "Придут, только если дневная цель ещё не выполнена.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = s.reminderOn, onCheckedChange = { on ->
            if (on && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            update { it.copy(reminderOn = on) }
        })
    }
    if (s.reminderOn) {
        OutlinedButton(onClick = {
            TimePickerDialog(context, { _, h, m -> update { it.copy(reminderHour = h, reminderMinute = m) } },
                s.reminderHour, s.reminderMinute, true).show()
        }) { Text("Время: %02d:%02d".format(s.reminderHour, s.reminderMinute)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEachIndexed { i, name ->
                val day = i + 1
                FilterChip(day in s.reminderDays, {
                    update { it.copy(reminderDays = if (day in it.reminderDays) it.reminderDays - day else it.reminderDays + day) }
                }, { Text(name) })
            }
        }
    }
}
