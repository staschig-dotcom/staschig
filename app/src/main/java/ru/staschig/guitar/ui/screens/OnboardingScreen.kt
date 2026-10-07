package ru.staschig.guitar.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.lessons.Level

private const val ONBOARDING_STEPS = 4

/** Знакомство при первом запуске: уровень → время → микрофон → готово. */
@Composable
fun OnboardingScreen(store: AppStore, onDone: () -> Unit, initialStep: Int = 1) {
    var step by rememberSaveable { mutableIntStateOf(initialStep) }
    val (micGranted, requestMic) = rememberMicPermission()
    BackHandler(enabled = step > 1) { step-- }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        StepProgress(step, ONBOARDING_STEPS)
        Spacer(Modifier.height(24.dp))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (step) {
                1 -> LevelStep(store)
                2 -> TimeStep(store)
                3 -> MicStep(micGranted)
                else -> DoneStep(store)
            }
        }
        Spacer(Modifier.height(12.dp))
        when (step) {
            3 -> {
                if (!micGranted) {
                    BigButton("Разрешить микрофон", requestMic)
                    TextButton(onClick = { step++ }, modifier = Modifier.fillMaxWidth()) { Text("Позже") }
                } else {
                    BigButton("Далее", { step++ })
                }
            }
            ONBOARDING_STEPS -> BigButton("Начать первое занятие", { store.finishOnboarding(); onDone() })
            else -> BigButton("Далее", { step++ })
        }
    }
}

@Composable
private fun Title(text: String, subtitle: String) {
    Text(text, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
    Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LevelStep(store: AppStore) {
    Title("Как вы играете сейчас?", "От этого зависит, с какого урока начать. Поменять можно в любой момент.")
    val options = listOf(
        Level.BEGINNER to "Только начинаю: не знаю аккордов или знаю пару штук",
        Level.INTERMEDIATE to "Играю аккорды и бой, хочу баррэ, перебор и соло",
        Level.ADVANCED to "Уверенно играю, хочу скорость, импровизацию и сложные пьесы",
    )
    options.forEach { (level, text) ->
        val selected = store.settings.level == level
        Card(
            modifier = Modifier.fillMaxWidth().clickable { store.updateSettings { it.copy(level = level) } },
            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            colors = CardDefaults.cardColors(
                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(level.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeStep(store: AppStore) {
    val s = store.settings
    Title("Сколько времени в день?", "Занятие будет разбито на шаги под это время. Регулярность важнее длительности.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(10, 15, 20, 30, 45, 60).forEach { m ->
            FilterChip(s.dailyMinutes == m, { store.updateSettings { it.copy(dailyMinutes = m) } }, {
                Text("$m мин", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            })
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("Дней в неделю: ${s.daysPerWeek}", style = MaterialTheme.typography.titleMedium)
    Slider(
        value = s.daysPerWeek.toFloat(),
        onValueChange = { v -> store.updateSettings { it.copy(daysPerWeek = v.toInt()) } },
        valueRange = 1f..7f,
        steps = 5,
    )
}

@Composable
private fun MicStep(granted: Boolean) {
    Title("Приложению нужен микрофон", "Он нужен, чтобы приложение слышало гитару.")
    NumberedSteps(
        listOf(
            "Тюнер подскажет, какую струну подтянуть.",
            "В режиме «Играть» приложение проверит каждую ноту и поставит звёзды.",
            "Ритм-тест покажет, спешите вы или тянете.",
            "Звук никуда не отправляется и не записывается.",
        )
    )
    if (granted) Text("✓ Доступ к микрофону есть", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun DoneStep(store: AppStore) {
    val s = store.settings
    Title("Всё готово!", "Каждое занятие — несколько шагов. Приложение ведёт по ним само.")
    NumberedSteps(
        listOf(
            "Настройка гитары — тюнер, 1 минута (раз в день).",
            "Разминка — пальцы и руки под метроном.",
            "Упражнение — новая техника.",
            "Песня — табы и аккорды, можно играть с проверкой.",
        )
    )
    Text(
        "Ваш режим: ${s.level.title.lowercase()} уровень, ${s.dailyMinutes} мин в день, ${s.daysPerWeek} дн. в неделю.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
