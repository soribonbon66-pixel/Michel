package com.michel.masteringpro.dsp

import com.michel.masteringpro.model.SpectrumData
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.sin

/** Pure Kotlin radix-2 FFT, averaged across 4096-point Hann windows. */
object SpectrumAnalyzer {
    data class Result(
        val spectrum: SpectrumData,
        val lowBandVsReferenceDb: Float,
        val bodyBandVsReferenceDb: Float,
        val presenceVsReferenceDb: Float,
        val airVs1kDb: Float,
        val estimatedKey: String
    )

    private const val FFT_SIZE = 4096
    private const val HALF = FFT_SIZE / 2
    private const val MAX_WINDOWS = 512
    private val noteNames = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    private val majorProfile = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val minorProfile = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

    fun analyze(samples: FloatArray, sampleRate: Int, channels: Int): Result {
        val frameCount = samples.size / channels
        val powerSums = DoubleArray(HALF + 1)
        val chroma = DoubleArray(12)
        val window = DoubleArray(FFT_SIZE) { i -> 0.5 - 0.5 * cos(2.0 * PI * i / (FFT_SIZE - 1)) }
        val real = DoubleArray(FFT_SIZE)
        val imaginary = DoubleArray(FFT_SIZE)
        val hop = FFT_SIZE / 2
        val possibleWindows = if (frameCount <= FFT_SIZE) 1 else 1 + (frameCount - FFT_SIZE) / hop
        val windows = possibleWindows.coerceAtMost(MAX_WINDOWS)
        val stride = if (windows <= 1 || frameCount <= FFT_SIZE) 0 else ((frameCount - FFT_SIZE) / (windows - 1)).coerceAtLeast(hop)
        var windowCount = 0

        for (windowIndex in 0 until windows) {
            val startFrame = if (frameCount <= FFT_SIZE) 0 else windowIndex * stride
            real.fill(0.0)
            imaginary.fill(0.0)
            val available = minOf(FFT_SIZE, frameCount - startFrame)
            for (i in 0 until available) {
                val offset = (startFrame + i) * channels
                val mono = if (channels == 1) samples[offset].toDouble() else (samples[offset].toDouble() + samples[offset + 1]) * 0.5
                real[i] = mono * window[i]
            }
            fft(real, imaginary)
            for (bin in 1..HALF) {
                val power = real[bin] * real[bin] + imaginary[bin] * imaginary[bin]
                powerSums[bin] += power
                val hz = bin.toDouble() * sampleRate / FFT_SIZE
                if (hz in 45.0..5_000.0 && power > 0.0) {
                    val midi = (69.0 + 12.0 * log2(hz / 440.0)).roundToInt()
                    val pitchClass = ((midi % 12) + 12) % 12
                    chroma[pitchClass] += sqrt(power)
                }
            }
            windowCount++
        }
        if (windowCount > 0) {
            val count = windowCount.toDouble()
            for (i in powerSums.indices) powerSums[i] = powerSums[i] / count
            for (i in chroma.indices) chroma[i] = chroma[i] / count
        }
        val maxPower = powerSums.maxOrNull() ?: 0.0
        val spectrumDb = FloatArray(HALF + 1) { bin ->
            if (maxPower <= 1e-30 || powerSums[bin] <= 1e-30) -90f
            else (10.0 * log10Safe(powerSums[bin] / maxPower)).toFloat().coerceIn(-90f, 0f)
        }
        val low = bandDb(powerSums, sampleRate, 20.0, 80.0)
        val bassReference = bandDb(powerSums, sampleRate, 80.0, 500.0)
        val body = bandDb(powerSums, sampleRate, 200.0, 400.0)
        val bodyReference = bandDb(powerSums, sampleRate, 80.0, 12_000.0)
        val presence = bandDb(powerSums, sampleRate, 2_000.0, 5_000.0)
        val presenceReference = bandDb(powerSums, sampleRate, 500.0, 2_000.0)
        val air = bandDb(powerSums, sampleRate, 12_000.0, minOf(18_000.0, sampleRate / 2.0))
        val oneK = bandDb(powerSums, sampleRate, 800.0, 1_200.0)
        val key = estimateKey(chroma)

        return Result(
            spectrum = SpectrumData(sampleRate, spectrumDb),
            lowBandVsReferenceDb = (low - bassReference).toFloat().finiteOrZero(),
            bodyBandVsReferenceDb = (body - bodyReference).toFloat().finiteOrZero(),
            presenceVsReferenceDb = (presence - presenceReference).toFloat().finiteOrZero(),
            airVs1kDb = (air - oneK).toFloat().finiteOrZero(),
            estimatedKey = key
        )
    }

    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val r = real[i]
                real[i] = real[j]
                real[j] = r
                val im = imaginary[i]
                imaginary[i] = imaginary[j]
                imaginary[j] = im
            }
        }
        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val stepReal = cos(angle)
            val stepImaginary = sin(angle)
            val halfLength = length / 2
            var base = 0
            while (base < n) {
                var twiddleReal = 1.0
                var twiddleImaginary = 0.0
                for (k in 0 until halfLength) {
                    val even = base + k
                    val odd = even + halfLength
                    val oddReal = real[odd] * twiddleReal - imaginary[odd] * twiddleImaginary
                    val oddImaginary = real[odd] * twiddleImaginary + imaginary[odd] * twiddleReal
                    real[odd] = real[even] - oddReal
                    imaginary[odd] = imaginary[even] - oddImaginary
                    real[even] += oddReal
                    imaginary[even] += oddImaginary
                    val nextTwiddleReal = twiddleReal * stepReal - twiddleImaginary * stepImaginary
                    twiddleImaginary = twiddleReal * stepImaginary + twiddleImaginary * stepReal
                    twiddleReal = nextTwiddleReal
                }
                base += length
            }
            length = length shl 1
        }
    }

    private fun bandDb(power: DoubleArray, sampleRate: Int, lowHz: Double, highHz: Double): Double {
        val minBin = max(1, kotlin.math.ceil(lowHz * FFT_SIZE / sampleRate).toInt())
        val maxBin = minOf(HALF, kotlin.math.floor(highHz * FFT_SIZE / sampleRate).toInt())
        if (maxBin < minBin) return -90.0
        var sum = 0.0
        var count = 0
        for (bin in minBin..maxBin) {
            sum += power[bin]
            count++
        }
        return if (count == 0) -90.0 else 10.0 * log10Safe(sum / count)
    }

    private fun estimateKey(chroma: DoubleArray): String {
        val sum = chroma.sum()
        if (sum < 1e-12) return "—"
        val normalized = DoubleArray(12) { chroma[it] / sum }
        var bestScore = Double.NEGATIVE_INFINITY
        var bestKey = "—"
        for (root in 0 until 12) {
            val majorScore = correlation(normalized, majorProfile, root)
            val minorScore = correlation(normalized, minorProfile, root)
            if (majorScore > bestScore) {
                bestScore = majorScore
                bestKey = "${noteNames[root]} mayor"
            }
            if (minorScore > bestScore) {
                bestScore = minorScore
                bestKey = "${noteNames[root]} menor"
            }
        }
        return if (bestScore < 0.12) "—" else bestKey
    }

    private fun correlation(chroma: DoubleArray, profile: DoubleArray, root: Int): Double {
        val meanA = chroma.average()
        val meanB = profile.average()
        var covariance = 0.0
        var varianceA = 0.0
        var varianceB = 0.0
        for (i in 0 until 12) {
            val a = chroma[(i + root) % 12] - meanA
            val b = profile[i] - meanB
            covariance += a * b
            varianceA += a * a
            varianceB += b * b
        }
        return covariance / sqrt((varianceA * varianceB).coerceAtLeast(1e-18))
    }

    private fun log10Safe(value: Double): Double = ln(value.coerceAtLeast(1e-30)) / ln(10.0)
    private fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f
}
