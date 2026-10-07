package ru.staschig.guitar.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ru.staschig.guitar.audio.Notes
import ru.staschig.guitar.audio.TonePlayer
import ru.staschig.guitar.audio.TunerEngine
import ru.staschig.guitar.audio.Tunings
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.ui.theme.InTune
import ru.staschig.guitar.ui.theme.OutOfTune
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TunerScreen(store: AppStore) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    val tuning = Tunings.byId(store.settings.tuningId)
    val a4 = store.settings.a4.toFloat()
    var freq by remember { mutableStateOf<Float?>(null) }
    var auto by remember { mutableStateOf(true) }
    var manualString by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(granted, lifecycle) {
        val engine = TunerEngine { freq = it }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (granted) engine.start()
                Lifecycle.Event.ON_PAUSE -> engine.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer) // сразу получит ON_RESUME, если экран уже активен
        onDispose {
            lifecycle.removeObserver(observer)
            engine.stop()
            TonePlayer.stop()
        }
    }

    // Целевая струна: ближайшая по высоте (авто) или выбранная вручную.
    val f = freq
    val target = if (auto && f != null) {
        tuning.midi.indices.minBy { abs(Notes.midiOf(f, a4) - tuning.midi[it]) }
    } else manualString
    val targetMidi = tuning.midi[target]
    val cents = f?.let { Notes.cents(it, targetMidi, a4) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Тюнер") })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box {
                OutlinedButton(onClick = { menuOpen = true }) { Text(tuning.title) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    Tunings.all.forEach { t ->
                        DropdownMenuItem(
                            text = { Text("${t.group}: ${t.title}") },
                            onClick = {
                                store.updateSettings { it.copy(tuningId = t.id) }
                                menuOpen = false
                            },
                        )
                    }
                }
            }

            if (!granted) {
                Text("Тюнеру нужен доступ к микрофону, чтобы слышать гитару.")
                Button(onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text("Разрешить микрофон")
                }
            }

            // Стрелка
            val inTune = cents != null && abs(cents) <= 5f
            val color = when {
                cents == null -> MaterialTheme.colorScheme.onSurfaceVariant
                inTune -> InTune
                else -> OutOfTune
            }
            Text(
                Notes.name(targetMidi),
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                when {
                    f == null -> "Сыграйте открытую струну"
                    inTune -> "Строит ✓  (${"%.1f".format(f)} Гц)"
                    (cents ?: 0f) < 0 -> "Ниже на ${abs(cents ?: 0f).toInt()} центов — подтяните  (${"%.1f".format(f)} Гц)"
                    else -> "Выше на ${(cents ?: 0f).toInt()} центов — ослабьте  (${"%.1f".format(f)} Гц)"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            val needleColor = color
            val trackColor = MaterialTheme.colorScheme.surfaceVariant
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                val center = Offset(size.width / 2, size.height)
                val radius = minOf(size.width / 2, size.height) - 8f
                // шкала от -50 до +50 центов
                for (c in -50..50 step 10) {
                    val angle = Math.toRadians(-90.0 + c * 1.6)
                    val inner = if (c == 0) radius * 0.75f else radius * 0.85f
                    drawLine(
                        trackColor,
                        center + Offset((cos(angle) * inner).toFloat(), (sin(angle) * inner).toFloat()),
                        center + Offset((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat()),
                        strokeWidth = if (c == 0) 6f else 3f,
                    )
                }
                val c = (cents ?: 0f).coerceIn(-50f, 50f)
                val angle = Math.toRadians(-90.0 + c * 1.6)
                drawLine(
                    needleColor,
                    center,
                    center + Offset((cos(angle) * radius * 0.95f).toFloat(), (sin(angle) * radius * 0.95f).toFloat()),
                    strokeWidth = 8f,
                    cap = StrokeCap.Round,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Авто-определение струны")
                Switch(checked = auto, onCheckedChange = { auto = it }, modifier = Modifier.padding(start = 8.dp))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tuning.midi.forEachIndexed { i, midi ->
                    FilterChip(
                        selected = target == i,
                        onClick = { auto = false; manualString = i },
                        label = { Text(Notes.name(midi)) },
                    )
                }
            }
            Text(
                "Струны от 6-й (толстой) к 1-й. Нажмите «Эталон», чтобы услышать нужную ноту.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { TonePlayer.play(Notes.frequency(targetMidi, a4)) }) {
                Text("🔊 Эталон ${Notes.nameWithOctave(targetMidi)} (${"%.1f".format(Notes.frequency(targetMidi, a4))} Гц)")
            }
            Text("Калибровка: A4 = ${store.settings.a4} Гц (меняется в настройках)",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
