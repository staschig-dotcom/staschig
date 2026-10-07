package ru.staschig.guitar.play

import ru.staschig.guitar.audio.Biquad
import ru.staschig.guitar.audio.ChordRecognizer
import ru.staschig.guitar.audio.Notes
import ru.staschig.guitar.audio.OnsetDetector
import ru.staschig.guitar.audio.PitchDetector

/**
 * Разбор звука с микрофона для режима игры (без Android — тестируется на синтезе).
 * На каждый блок сэмплов: атаки (удары по струнам), высота тона (YIN) и хромаграмма.
 * Перед YIN/хромой срезаются частоты выше ~1.5 кГц — там щелчок метронома 4 кГц.
 */
class PlayDetector(private val sampleRate: Int = 44100) {
    class Frame(
        /** Индекс сэмпла (от начала потока), к которому относится результат — центр окна YIN. */
        val sample: Long,
        val detection: Detection,
        /** Индексы сэмплов обнаруженных атак. */
        val onsets: List<Long>,
    )

    // Чувствительнее, чем в ритм-тесте: повторный щипок звучащей струны даёт меньший скачок энергии.
    private val onsetDetector = OnsetDetector(sampleRate, OnsetDetector.Band.LOW, ratio = 2.5f)
    private val lp1 = Biquad.lowPass(sampleRate, 1500.0)
    private val lp2 = Biquad.lowPass(sampleRate, 1500.0)
    private val yinSize = 2048
    private val chromaSize = 8192
    private val yin = PitchDetector(sampleRate, yinSize)
    private val chromaCalc = ChordRecognizer(sampleRate, emptyList())
    private val ring = FloatArray(chromaSize)
    private val yinWindow = FloatArray(yinSize)
    private val chromaWindow = FloatArray(chromaSize)
    private var total = 0L
    private var blocks = 0
    private var lastChroma: FloatArray? = null
    private var prevPitched = false

    fun process(block: FloatArray): Frame {
        val onsets = onsetDetector.process(block)
        val n = block.size
        System.arraycopy(ring, n, ring, 0, chromaSize - n)
        for (i in 0 until n) ring[chromaSize - n + i] = lp2.process(lp1.process(block[i]))
        total += n
        blocks++

        System.arraycopy(ring, chromaSize - yinSize, yinWindow, 0, yinSize)
        val f = yin.detect(yinWindow, minRms = 0.004f)
        val midi = f?.takeIf { it in 60f..1400f }?.let { Notes.midiOf(it) }

        val loud = PitchDetector.rms(yinWindow) >= 0.004f
        if (!loud) lastChroma = null
        else if (blocks % 2 == 0 || lastChroma == null) {
            System.arraycopy(ring, 0, chromaWindow, 0, chromaSize)
            lastChroma = chromaCalc.chroma(chromaWindow)
        }
        val center = total - yinSize / 2
        // Второй признак удара: в момент щипка YIN на мгновение теряет высоту, затем снова её находит.
        val allOnsets = if (midi != null && !prevPitched && onsets.isEmpty()) listOf(center) else onsets
        prevPitched = midi != null
        return Frame(center, Detection(midi, lastChroma), allOnsets)
    }
}
