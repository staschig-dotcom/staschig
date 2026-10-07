package ru.staschig.guitar.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Итеративное БПФ radix-2 (in-place). Размер — степень двойки. */
object Fft {
    fun transform(re: FloatArray, im: FloatArray) {
        val n = re.size
        require(n == im.size && n and (n - 1) == 0) { "size must be power of 2" }
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wRe = cos(ang).toFloat()
            val wIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = a + len / 2
                    val tRe = re[b] * curRe - im[b] * curIm
                    val tIm = re[b] * curIm + im[b] * curRe
                    re[b] = re[a] - tRe; im[b] = im[a] - tIm
                    re[a] += tRe; im[a] += tIm
                    val nRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** Амплитудный спектр сигнала с окном Ханна (n/2 бинов). */
    fun magnitudes(samples: FloatArray): FloatArray {
        val n = samples.size
        val re = FloatArray(n) { i -> samples[i] * (0.5f - 0.5f * cos(2 * PI * i / (n - 1)).toFloat()) }
        val im = FloatArray(n)
        transform(re, im)
        return FloatArray(n / 2) { k -> kotlin.math.sqrt(re[k] * re[k] + im[k] * im[k]) }
    }
}
