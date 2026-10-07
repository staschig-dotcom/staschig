package ru.staschig.guitar.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Метроном с точностью до сэмпла: щелчки синтезируются прямо в поток AudioTrack,
 * поэтому темп не «плывёт», как у таймеров на Handler/delay.
 */
object MetronomeEngine {
    private const val SAMPLE_RATE = 44100
    private const val CHUNK = 512

    @Volatile var bpm: Int = 80
        set(value) { field = value.coerceIn(20, 300) }
    @Volatile var beatsPerBar: Int = 4
    /** 1 — четверти, 2 — восьмые, 3 — триоли, 4 — шестнадцатые. */
    @Volatile var subdivision: Int = 1
    @Volatile var accentFirst: Boolean = true
    @Volatile var volume: Float = 0.8f

    /** Тренажёр скорости: +[trainerStep] BPM каждые [trainerBars] тактов до [trainerTarget]. */
    @Volatile var trainerEnabled: Boolean = false
    @Volatile var trainerStep: Int = 5
    @Volatile var trainerBars: Int = 4
    @Volatile var trainerTarget: Int = 120

    @Volatile var isRunning: Boolean = false
        private set

    /** Щелчок 4 кГц с мягкой огибающей — для ритм-теста (не мешает слышать гитару). */
    @Volatile var rhythmClick: Boolean = false

    /** Кадры (позиции в аудиопотоке) всех щелчков с момента старта — для ритм-теста. */
    private val history = ArrayList<Long>()

    private var worker: Thread? = null
    private var track: AudioTrack? = null

    private class Tick(val frame: Long, val beat: Int, val bpm: Int)
    private val ticks = ArrayDeque<Tick>()
    private var lastHeard = Tick(0, -1, 80)

    private val accentClick = click(1760.0, 0.035)
    private val beatClick = click(1175.0, 0.03)
    private val subClick = click(880.0, 0.02, gain = 0.45)
    private val testClick = RhythmClick.samples(SAMPLE_RATE).map { it * 0.7f }.toFloatArray()
    private val testAccent = RhythmClick.samples(SAMPLE_RATE)

    private fun click(freq: Double, seconds: Double, gain: Double = 1.0): FloatArray {
        val n = (SAMPLE_RATE * seconds).toInt()
        return FloatArray(n) { i ->
            val t = i.toDouble() / SAMPLE_RATE
            (gain * sin(2 * PI * freq * t) * exp(-t * 90)).toFloat()
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        synchronized(ticks) { ticks.clear(); lastHeard = Tick(0, -1, bpm) }
        synchronized(history) { history.clear() }
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT
        )
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, CHUNK * 4 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        t.play()
        worker = thread(name = "metronome", isDaemon = true) { render(t) }
    }

    fun stop() {
        isRunning = false
        worker?.join(500)
        worker = null
        track?.let {
            runCatching { it.pause(); it.flush(); it.stop() }
            it.release()
        }
        track = null
    }

    fun toggle() = if (isRunning) stop() else start()

    private fun render(t: AudioTrack) {
        val buf = FloatArray(CHUNK)
        var frame = 0L
        var nextTick = 0L
        var tickIndex = 0 // номер доли с учётом дроблений внутри такта
        var barsAtTempo = 0
        var currentClick: FloatArray? = null
        var clickPos = 0

        while (isRunning) {
            for (i in 0 until CHUNK) {
                if (frame + i == nextTick) {
                    val sub = subdivision.coerceIn(1, 4)
                    val beats = beatsPerBar.coerceIn(1, 16)
                    val ticksInBar = beats * sub
                    if (tickIndex >= ticksInBar) {
                        tickIndex = 0
                        barsAtTempo++
                        if (trainerEnabled && barsAtTempo >= trainerBars && bpm < trainerTarget) {
                            bpm = minOf(trainerTarget, bpm + trainerStep)
                            barsAtTempo = 0
                        }
                    }
                    val isBeat = tickIndex % sub == 0
                    val beat = tickIndex / sub
                    currentClick = when {
                        rhythmClick -> if (isBeat && beat == 0) testAccent else testClick
                        !isBeat -> subClick
                        beat == 0 && accentFirst -> accentClick
                        else -> beatClick
                    }
                    clickPos = 0
                    synchronized(history) { history.add(nextTick) }
                    if (isBeat) synchronized(ticks) {
                        ticks.addLast(Tick(nextTick, beat, bpm))
                        while (ticks.size > 64) ticks.removeFirst()
                    }
                    tickIndex++
                    val samplesPerTick = SAMPLE_RATE * 60.0 / bpm / sub
                    nextTick += samplesPerTick.toLong().coerceAtLeast(1)
                }
                val c = currentClick
                buf[i] = if (c != null && clickPos < c.size) c[clickPos++] * volume else 0f
            }
            frame += CHUNK
            t.write(buf, 0, CHUNK, AudioTrack.WRITE_BLOCKING)
        }
    }

    /**
     * Моменты (System.nanoTime) всех прозвучавших щелчков. Пересчёт из позиции в потоке
     * делается по AudioTimestamp — это учитывает буферы и задержку вывода звука.
     */
    fun clickTimesNanos(): List<Long> {
        val t = track ?: return emptyList()
        val frames = synchronized(history) { history.toList() }
        val ts = AudioTimestamp()
        val ok = runCatching { t.getTimestamp(ts) }.getOrDefault(false)
        val (refFrame, refNanos) = if (ok) ts.framePosition to ts.nanoTime
        else t.playbackHeadPosition.toLong() to System.nanoTime()
        return frames.map { refNanos + (it - refFrame) * 1_000_000_000L / SAMPLE_RATE }
    }

    /**
     * Доля такта, которая сейчас реально звучит (по позиции воспроизведения),
     * и текущий темп. Используется для синхронной анимации в UI.
     */
    fun currentBeat(): Pair<Int, Int> {
        val head = track?.let { runCatching { it.playbackHeadPosition.toLong() }.getOrNull() }
            ?: return -1 to bpm
        synchronized(ticks) {
            while (ticks.isNotEmpty() && ticks.first().frame <= head) {
                lastHeard = ticks.removeFirst()
            }
            return lastHeard.beat to lastHeard.bpm
        }
    }
}
