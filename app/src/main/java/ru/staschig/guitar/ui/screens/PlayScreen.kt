package ru.staschig.guitar.ui.screens

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.staschig.guitar.audio.MetronomeEngine
import ru.staschig.guitar.audio.MicStream
import ru.staschig.guitar.audio.Notes
import ru.staschig.guitar.audio.TonePlayer
import ru.staschig.guitar.data.AppStore
import ru.staschig.guitar.play.ExampleSynth
import ru.staschig.guitar.play.PlayDetector
import ru.staschig.guitar.play.PlayJudge
import ru.staschig.guitar.play.PlayResult
import ru.staschig.guitar.play.PlayScore
import ru.staschig.guitar.ui.theme.InTune
import ru.staschig.guitar.ui.theme.OutOfTune

private enum class PlayPhase { LOADING, SETUP, DEMO, PLAYING, RESULT }

/** Окна попадания в реальных мс (в мс партитуры умножаются на скорость). */
private const val EARLY_MS = 160.0
private const val LATE_MS = 260.0
/** Сколько секунд ленты видно впереди «линии игры». */
private const val LOOKAHEAD_MS = 4000.0

/**
 * Режим «Играть с проверкой» (в духе Yousician): ноты едут по ленте к линии,
 * микрофон слушает гитару и засчитывает каждую ноту. [query] — "tex=…" или "file=…".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(store: AppStore, id: String, title: String, query: String, onBack: () -> Unit) {
    val (granted, requestMic) = rememberMicPermission()
    var phase by remember { mutableStateOf(PlayPhase.LOADING) }
    var score by remember { mutableStateOf<PlayScore?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var track by remember { mutableStateOf<Int?>(null) }

    // Настройки прохождения
    var speed by remember { mutableIntStateOf(70) }
    var waitMode by remember { mutableStateOf(false) }
    var range by remember { mutableStateOf(0f..0f) }
    var loop by remember { mutableStateOf(false) }

    var result by remember { mutableStateOf<PlayResult?>(null) }
    var newRecord by remember { mutableStateOf(false) }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            MetronomeEngine.stop()
            MetronomeEngine.rhythmClick = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, maxLines = 1) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
        )
        when (phase) {
            PlayPhase.LOADING -> {
                Text(loadError ?: "Готовлю ноты…", Modifier.padding(16.dp))
                ScoreExtractor(query + (track?.let { "&track=$it" } ?: "")) { json ->
                    runCatching { PlayScore.parse(json) }
                        .onSuccess { s ->
                            score = s
                            track = s.track
                            range = 0f..(s.bars.size - 1).toFloat()
                            speed = if (s.tempo > 100) 60 else 70
                            phase = PlayPhase.SETUP
                        }
                        .onFailure { loadError = "Не удалось разобрать таб: ${it.message}" }
                }
            }
            PlayPhase.SETUP -> SetupPanel(
                store = store,
                id = id,
                score = score!!,
                granted = granted,
                requestMic = requestMic,
                speed = speed, onSpeed = { speed = it },
                range = range, onRange = { range = it },
                loop = loop, onLoop = { loop = it },
                onTrack = { track = it; phase = PlayPhase.LOADING },
                onListen = { phase = PlayPhase.DEMO },
                onStart = { wait -> waitMode = wait; phase = PlayPhase.PLAYING },
            )
            PlayPhase.DEMO -> {
                val full = score!!
                val section = remember(range) {
                    full.section(range.start.roundToInt(), range.endInclusive.roundToInt())
                }
                DemoRun(section, speed, onDone = { phase = PlayPhase.SETUP })
            }
            PlayPhase.PLAYING -> {
                val full = score!!
                val section = remember(range) {
                    full.section(range.start.roundToInt(), range.endInclusive.roundToInt())
                }
                PlayRun(
                    store = store,
                    score = section,
                    speed = speed,
                    waitMode = waitMode,
                    loop = loop,
                    onFinish = { r ->
                        val isFull = range.start.roundToInt() == 0 && range.endInclusive.roundToInt() == full.bars.lastIndex
                        // Рекорд пишем только за всю пьесу в темпе; опыт — за любое прохождение.
                        newRecord = store.addPlayResult(
                            if (isFull && !waitMode) id else "$id#practice",
                            r.stars, r.accuracy, speed, r.xp,
                        ) && isFull && !waitMode
                        result = r
                        phase = PlayPhase.RESULT
                    },
                    onLap = { r -> store.addPlayResult("$id#practice", r.stars, r.accuracy, speed, r.xp) },
                    onStop = { phase = PlayPhase.SETUP },
                )
            }
            PlayPhase.RESULT -> ResultPanel(
                result = result!!,
                newRecord = newRecord,
                speed = speed,
                waitMode = waitMode,
                store = store,
                onRetry = { phase = PlayPhase.PLAYING },
                onSlower = { speed = (speed - 10).coerceAtLeast(40); phase = PlayPhase.PLAYING },
                onWait = { waitMode = true; phase = PlayPhase.PLAYING },
                onSetup = { phase = PlayPhase.SETUP },
            )
        }
    }
}

/** Невидимый WebView: alphaTab разбирает таб и отдаёт ноты со временем в JSON (assets/alphatab/extract.html). */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
internal fun ScoreExtractor(query: String, onJson: (String) -> Unit) {
    val loader = rememberAlphaTabAssetLoader()
    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(query) { onDispose { webView?.destroy() } }
    AndroidView(
        modifier = Modifier.size(1.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(v: WebView, request: WebResourceRequest): WebResourceResponse? =
                        loader.shouldInterceptRequest(request.url)
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onScore(json: String) {
                        scope.launch { onJson(json) } // из потока JS — на главный поток
                    }
                }, "Android")
                loadUrl("${ALPHATAB_BASE}extract.html?$query")
                webView = this
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SetupPanel(
    store: AppStore,
    id: String,
    score: PlayScore,
    granted: Boolean,
    requestMic: () -> Unit,
    speed: Int, onSpeed: (Int) -> Unit,
    range: ClosedFloatingPointRange<Float>, onRange: (ClosedFloatingPointRange<Float>) -> Unit,
    loop: Boolean, onLoop: (Boolean) -> Unit,
    onTrack: (Int) -> Unit,
    onListen: () -> Unit,
    onStart: (waitMode: Boolean) -> Unit,
) {
    val best = store.playBest[id]
    var more by remember { mutableStateOf(false) }
    val from = range.start.roundToInt() + 1
    val to = range.endInclusive.roundToInt() + 1
    val whole = from == 1 && to == score.bars.size
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                listOf(score.artist, score.title).filter { it.isNotBlank() }.joinToString(" — "),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "${score.events.size} нот · ${score.tempo.roundToInt()} BPM" +
                    (if (whole) "" else " · такты $from–$to") +
                    (best?.let { " · рекорд ${stars(it.stars)}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            NumberedSteps(
                listOf(
                    "Послушайте пример — ноты поедут по ленте под звук.",
                    "Разучите: лента ждёт, пока вы сыграете каждую ноту.",
                    "Сыграйте в темпе под метроном — за точность дают звёзды.",
                )
            )
            Text("Скорость: $speed%", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(40, 50, 60, 70, 80, 90, 100).forEach { v ->
                    FilterChip(speed == v, { onSpeed(v) }, { Text("$v%") })
                }
            }

            TextButton(onClick = { more = !more }) { Text(if (more) "Скрыть дополнительное ▴" else "Дополнительно: дорожка, фрагмент ▾") }
            if (more) {
                if (score.tracks.size > 1) {
                    Text("Ваш инструмент (дорожка)", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        score.tracks.forEachIndexed { i, name ->
                            FilterChip(score.track == i, { if (score.track != i) onTrack(i) }, { Text(name.ifBlank { "Дорожка ${i + 1}" }) })
                        }
                    }
                }
                if (score.bars.size > 1) {
                    Text(if (whole) "Фрагмент: вся пьеса" else "Фрагмент: такты $from–$to", style = MaterialTheme.typography.titleSmall)
                    RangeSlider(
                        value = range,
                        onValueChange = { onRange(it.start.roundToInt().toFloat()..it.endInclusive.roundToInt().toFloat()) },
                        valueRange = 0f..(score.bars.size - 1).toFloat(),
                        steps = (score.bars.size - 2).coerceAtLeast(0),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Повторять фрагмент по кругу", Modifier.weight(1f))
                        Switch(loop, onLoop)
                    }
                }
                Text(
                    "Играйте в тишине, телефон — рядом с гитарой, звук — через динамик. " +
                        "Калибровка в ритм-тесте делает проверку попадания в такт точнее.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Кнопки — всегда внизу экрана, по порядку шагов.
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onListen, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("🔊  1. Послушать пример") }
            if (!granted) {
                Text("Чтобы приложение слышало вашу игру, нужен микрофон.", style = MaterialTheme.typography.bodySmall)
                BigButton("Разрешить микрофон", requestMic)
            } else {
                OutlinedButton(onClick = { onStart(true) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("🐢  2. Разучить (лента ждёт)") }
                BigButton("▶  3. Играть в темпе", { onStart(false) })
            }
        }
    }
}

/** «Послушать пример»: синтезированная гитара играет партию, ноты едут по ленте синхронно. */
@Composable
private fun DemoRun(score: PlayScore, speed: Int, onDone: () -> Unit) {
    val s = speed / 100.0
    val judge = remember { PlayJudge(score.events) }
    var songMs by remember { mutableDoubleStateOf(-1e9) }
    var ready by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { TonePlayer.stop() } }
    LaunchedEffect(Unit) {
        val audio = withContext(Dispatchers.Default) { ExampleSynth.renderNotes(score.events, s) }
        ready = true
        TonePlayer.playSamples(audio)
        // Небольшая поправка на задержку вывода звука, чтобы нота совпадала с линией.
        val start = System.nanoTime() + 120_000_000L
        while (true) {
            withFrameNanos { now ->
                songMs = (now - start) / 1e6 * s
                score.events.forEachIndexed { i, ev ->
                    if (ev.timeMs <= songMs) judge.states[i] = PlayJudge.State.HIT
                }
            }
            if (songMs > score.durationMs + 500 || (!TonePlayer.isPlaying && songMs > 1000)) break
        }
        onDone()
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (ready) "🔊 Пример · $speed% — слушайте и смотрите на ноты" else "Готовлю звук…",
                Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
            )
            OutlinedButton(onClick = { TonePlayer.stop(); onDone() }) { Text("Стоп") }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Highway(score, judge, songMs, s, false, 0, null, Modifier.fillMaxSize())
        }
    }
}

/** Одно прохождение: метроном, микрофон, судья и лента. */
@Composable
private fun PlayRun(
    store: AppStore,
    score: PlayScore,
    speed: Int,
    waitMode: Boolean,
    loop: Boolean,
    onFinish: (PlayResult) -> Unit,
    onLap: (PlayResult) -> Unit,
    onStop: () -> Unit,
) {
    val s = speed / 100.0
    var runNumber by remember { mutableIntStateOf(0) }
    val judge = remember(runNumber) { PlayJudge(score.events, EARLY_MS * s, LATE_MS * s) }
    /** Время партитуры (мс) сейчас; < 0 — идёт отсчёт. */
    var songMs by remember(runNumber) { mutableDoubleStateOf(-1e9) }
    var heard by remember { mutableStateOf<String?>(null) }
    var combo by remember(runNumber) { mutableIntStateOf(0) }
    var lastHit by remember(runNumber) { mutableStateOf<Pair<Int, String>?>(null) }
    var version by remember(runNumber) { mutableIntStateOf(0) } // перерисовка ленты после попаданий
    val latencyNs = (store.settings.latencyMs ?: 0) * 1_000_000L

    // Начало партитуры в System.nanoTime(): задаётся по первому реальному щелчку метронома.
    val t0 = remember(runNumber) { longArrayOf(Long.MAX_VALUE) }

    DisposableEffect(runNumber) {
        val detector = PlayDetector(44100)
        var blockStart = 0L
        val mic = MicStream(block = 1024) { samples, startNanos ->
            val frame = detector.process(samples)
            val frameNanos = startNanos + (frame.sample - blockStart) * 1_000_000_000L / 44100 - latencyNs
            blockStart += samples.size
            val origin = t0[0]
            if (origin == Long.MAX_VALUE) return@MicStream
            fun toSong(nanos: Long) = (nanos - origin) / 1e6 * s
            synchronized(judge) {
                frame.onsets.forEach { o ->
                    judge.onOnset(toSong(startNanos + (o - (blockStart - samples.size)) * 1_000_000_000L / 44100 - latencyNs))
                }
                val idx = if (waitMode) {
                    val w = judge.nextPending()
                    if (w >= 0 && songMs >= score.events[w].timeMs - EARLY_MS * s) judge.onFrame(0.0, frame.detection, w) else -1
                } else {
                    judge.onFrame(toSong(frameNanos), frame.detection)
                }
                if (idx >= 0) {
                    combo++
                    val dev = abs(judge.deviations[idx]) / s
                    lastHit = idx to when {
                        waitMode -> "✓"
                        dev <= 70 -> "Отлично!"
                        dev <= 140 -> "Хорошо"
                        else -> "Есть"
                    }
                    version++
                }
            }
            heard = frame.detection.midi?.let { Notes.nameWithOctave(it.roundToInt()) }
        }
        mic.start()
        onDispose { mic.stop() }
    }

    LaunchedEffect(runNumber) {
        val m = MetronomeEngine
        if (!waitMode) {
            val beatRealMs = score.beatMs / s
            m.stop()
            m.rhythmClick = true
            m.trainerEnabled = false
            m.subdivision = 1
            m.accentFirst = true
            m.beatsPerBar = score.beatsPerBar
            m.bpm = (60000.0 / beatRealMs).roundToInt()
            m.start()
            delay(300) // к этому моменту у AudioTrack есть точная метка времени воспроизведения
            var clicks = m.clickTimesNanos()
            while (clicks.isEmpty()) {
                delay(15)
                clicks = m.clickTimesNanos()
            }
            val barNs = (score.beatsPerBar * beatRealMs * 1e6).toLong()
            val pickupNs = if (score.anacrusis && score.bars.size > 1) (score.bars[1] / s * 1e6).toLong() else 0L
            // Такт отсчёта; затузакт (если есть) — в конце следующего такта, чтобы сильная доля совпала с 1-м тактом.
            val firstBar = clicks.first() + barNs * (if (pickupNs > 0) 2 else 1)
            t0[0] = firstBar - pickupNs
        } else {
            t0[0] = System.nanoTime() + 1_500_000_000L
        }
        var lastFrame = 0L
        var waitSong = -1500.0 * s
        while (true) {
            withFrameNanos { now ->
                if (waitMode) {
                    val dt = if (lastFrame == 0L) 0.0 else (now - lastFrame) / 1e6 * s
                    val next = synchronized(judge) { judge.nextPending() }
                    val limit = if (next >= 0) score.events[next].timeMs.toDouble() else Double.MAX_VALUE
                    waitSong = minOf(waitSong + dt, limit)
                    songMs = waitSong
                } else {
                    songMs = (now - t0[0]) / 1e6 * s
                    synchronized(judge) { if (judge.expire(songMs).isNotEmpty()) { combo = 0; version++ } }
                }
                lastFrame = now
            }
            val done = synchronized(judge) { judge.nextPending() < 0 } && songMs > score.durationMs - 1
            if (songMs > score.durationMs + 600 || (waitMode && done)) {
                synchronized(judge) { judge.expire(Double.MAX_VALUE) }
                val r = PlayResult.of(judge, speed, waitMode)
                val realMs = score.durationMs / s
                if (realMs >= 30_000) store.addPracticeMinutes(((realMs + 30_000) / 60_000).toInt())
                if (loop) {
                    onLap(r)
                    runNumber++
                    return@LaunchedEffect
                }
                m.stop()
                m.rhythmClick = false
                onFinish(r)
                return@LaunchedEffect
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Табло
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val resolved = judge.resolved
            val acc = if (resolved == 0) 100 else judge.hits * 100 / resolved
            Column(Modifier.weight(1f)) {
                Text("Точность $acc% · ${judge.hits}/${score.events.size}", fontWeight = FontWeight.Bold)
                Text(
                    "Серия $combo · слышу ${heard ?: "—"}" + if (loop) " · круг ${runNumber + 1}" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = {
                MetronomeEngine.stop()
                MetronomeEngine.rhythmClick = false
                onStop()
            }) { Text("Стоп") }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Highway(score, judge, songMs, s, waitMode, version, lastHit, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Highway(
    score: PlayScore,
    judge: PlayJudge,
    songMs: Double,
    s: Double,
    waitMode: Boolean,
    @Suppress("UNUSED_PARAMETER") version: Int,
    lastHit: Pair<Int, String>?,
    modifier: Modifier,
) {
    val measurer = rememberTextMeasurer()
    val cs = MaterialTheme.colorScheme
    val pending = cs.primary
    val lineColor = cs.onSurfaceVariant.copy(alpha = 0.5f)
    val barColor = cs.onSurfaceVariant.copy(alpha = 0.18f)
    val nowColor = cs.secondary
    val fretStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B1B1F), textAlign = TextAlign.Center)
    val labelStyle = TextStyle(fontSize = 13.sp, color = cs.onSurfaceVariant)
    val bigStyle = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold, color = cs.primary)
    val feedbackStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = InTune)
    val labels = if (score.strings == 6) listOf("e", "B", "G", "D", "A", "E") else (1..score.strings).map { "$it" }
    val next = judge.nextPending()

    Canvas(modifier.background(cs.surface)) {
        val top = 36.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        val gap = (bottom - top) / (score.strings - 1).coerceAtLeast(1)
        val nowX = size.width * 0.22f
        val pxPerMs = ((size.width - nowX) / (LOOKAHEAD_MS * s)).toFloat()
        fun x(t: Double) = nowX + ((t - songMs) * pxPerMs).toFloat()
        fun y(line: Int) = top + line * gap

        // Тактовые линии
        score.bars.forEach { b ->
            val bx = x(b.toDouble())
            if (bx in 0f..size.width) drawLine(barColor, Offset(bx, top - 12f), Offset(bx, bottom + 12f), 3f)
        }
        // Струны и подписи
        for (i in 0 until score.strings) {
            drawLine(lineColor, Offset(0f, y(i)), Offset(size.width, y(i)), 2f)
            drawText(measurer, labels.getOrElse(i) { "" }, Offset(6f, y(i) - 22f), labelStyle)
        }
        // Линия игры
        drawLine(nowColor, Offset(nowX, top - 24f), Offset(nowX, bottom + 24f), if (waitMode) 8f else 5f)

        val radius = 15.dp.toPx()
        score.events.forEachIndexed { i, ev ->
            val hx = x(ev.timeMs.toDouble())
            val tail = (ev.durationMs * pxPerMs).coerceAtLeast(0f)
            if (hx + tail < -radius || hx > size.width + radius) return@forEachIndexed
            val color = when (judge.states[i]) {
                PlayJudge.State.HIT -> InTune
                PlayJudge.State.MISSED -> OutOfTune.copy(alpha = 0.55f)
                PlayJudge.State.PENDING -> pending
            }
            ev.notes.forEach { n ->
                val ny = y(n.line.coerceIn(0, score.strings - 1))
                drawRoundRect(
                    color.copy(alpha = color.alpha * 0.35f),
                    topLeft = Offset(hx, ny - radius * 0.45f),
                    size = Size(tail, radius * 0.9f),
                    cornerRadius = CornerRadius(radius * 0.45f),
                )
                drawCircle(color, radius, Offset(hx, ny))
                if (i == next) drawCircle(Color.White, radius + 4f, Offset(hx, ny), style = Stroke(4f))
                val text = measurer.measure(n.fret.toString(), fretStyle)
                drawText(text, topLeft = Offset(hx - text.size.width / 2f, ny - text.size.height / 2f))
            }
        }

        // Отсчёт
        if (songMs < 0 && !waitMode) {
            val beatsLeft = ceil(-songMs / score.beatMs).toInt()
            if (beatsLeft in 1..score.beatsPerBar * 2) {
                val text = measurer.measure("$beatsLeft", bigStyle)
                drawText(text, topLeft = Offset(size.width / 2 - text.size.width / 2f, size.height / 2 - text.size.height / 2f))
            }
        }
        if (waitMode && songMs < 0) {
            val text = measurer.measure("Готовьтесь…", bigStyle.copy(fontSize = 32.sp))
            drawText(text, topLeft = Offset(size.width / 2 - text.size.width / 2f, size.height / 2 - text.size.height / 2f))
        }
        // Отклик на попадание
        lastHit?.let { (idx, label) ->
            if (idx in score.events.indices && songMs - score.events[idx].timeMs < 700 * s) {
                drawText(measurer, label, Offset(nowX + 12f, 4f), feedbackStyle)
            }
        }
    }
}

private fun stars(n: Int) = "★".repeat(n) + "☆".repeat(3 - n)

@Composable
private fun ResultPanel(
    result: PlayResult,
    newRecord: Boolean,
    speed: Int,
    waitMode: Boolean,
    store: AppStore,
    onRetry: () -> Unit,
    onSlower: () -> Unit,
    onWait: () -> Unit,
    onSetup: () -> Unit,
) {
    val (level, inLevel, need) = PlayResult.level(store.xp)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stars(result.stars), fontSize = 56.sp, color = MaterialTheme.colorScheme.primary)
        if (newRecord) Text("Новый рекорд!", style = MaterialTheme.typography.titleMedium, color = InTune)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Точность ${result.accuracy}% — ${result.hits} из ${result.total} нот", fontWeight = FontWeight.Bold)
                Text("Скорость $speed%" + if (waitMode) " · режим ожидания" else "")
                if (!waitMode && result.hits > 0) {
                    val d = result.meanDeviationMs
                    Text(
                        when {
                            d > 40 -> "В среднем запаздываете на ${(d / (speed / 100.0)).roundToInt()} мс"
                            d < -40 -> "В среднем спешите на ${(-d / (speed / 100.0)).roundToInt()} мс"
                            else -> "Ритм ровный"
                        }
                    )
                }
                Text("+${result.xp} XP · уровень $level ($inLevel / $need XP)")
            }
        }
        Text(
            when {
                result.stars == 3 -> if (speed < 100) "Отлично! Попробуйте быстрее." else "Безупречно. Берите следующую пьесу."
                result.accuracy < 50 && !waitMode -> "Слишком быстро: снизьте скорость или включите «Ждать меня», чтобы выучить ноты."
                else -> "Повторите ещё раз — со второго-третьего раза точность обычно заметно растёт."
            },
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Ещё раз") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (speed > 40) OutlinedButton(onClick = onSlower) { Text("Медленнее (${speed - 10}%)") }
            if (!waitMode) OutlinedButton(onClick = onWait) { Text("Ждать меня") }
        }
        OutlinedButton(onClick = onSetup) { Text("Настройки") }
    }
}
