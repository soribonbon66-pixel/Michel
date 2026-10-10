package com.example.dsp

import kotlin.math.*

/**
 * High-accuracy Spectral Analyzer based on Fast Fourier Transform (FFT) and Welch's STFT method.
 * Computes contiguous, non-overlapping power spectral distribution across 6 acoustic bands:
 * - Sub-Bass: 20 Hz - 60 Hz
 * - Punch / Bass: 60 Hz - 250 Hz
 * - Mud Scoop: 250 Hz - 500 Hz
 * - Mids: 500 Hz - 2000 Hz
 * - High-Mids: 2000 Hz - 6000 Hz
 * - Air: 6000 Hz - 20000 Hz (or up to Nyquist if sampleRate < 40 kHz)
 *
 * Also determines the exact global spectral peak frequency with parabolic interpolation.
 */
object SpectralAnalyzer {

    const val FFT_SIZE = 4096

    data class SpectralDistribution(
        val subBassRatio: Float,
        val bassRatio: Float,
        val lowMidRatio: Float,
        val midRatio: Float,
        val highMidRatio: Float,
        val airRatio: Float,
        val peakFrequencyHz: Float,
        val totalEnergy: Double,
        val isSilent: Boolean
    ) {
        val subBassPercent: Float get() = subBassRatio * 100f
        val bassPercent: Float get() = bassRatio * 100f
        val lowMidPercent: Float get() = lowMidRatio * 100f
        val midPercent: Float get() = midRatio * 100f
        val highMidPercent: Float get() = highMidRatio * 100f
        val airPercent: Float get() = airRatio * 100f

        companion object {
            val SILENT = SpectralDistribution(
                subBassRatio = 0.0f,
                bassRatio = 0.0f,
                lowMidRatio = 0.0f,
                midRatio = 0.0f,
                highMidRatio = 0.0f,
                airRatio = 0.0f,
                peakFrequencyHz = Float.NaN,
                totalEnergy = 0.0,
                isSilent = true
            )
        }
    }

    /**
     * Precomputed Hann window of size FFT_SIZE.
     */
    private val hannWindow = FloatArray(FFT_SIZE) { n ->
        (0.5 * (1.0 - cos(2.0 * Math.PI * n / (FFT_SIZE - 1)))).toFloat()
    }

    /**
     * Analyzes PCM audio float samples and returns real spectral energy distribution.
     * Downmixes multi-channel audio to mono (average across channels) for spectral profiling.
     */
    fun analyze(
        samples: FloatArray,
        sampleRate: Int = 44100,
        channels: Int = 2
    ): SpectralDistribution {
        if (samples.isEmpty()) return SpectralDistribution.SILENT

        val totalFrames = samples.size / channels
        if (totalFrames == 0) return SpectralDistribution.SILENT

        // Downmix to mono buffer
        val mono = FloatArray(totalFrames)
        if (channels == 1) {
            System.arraycopy(samples, 0, mono, 0, totalFrames)
        } else {
            for (i in 0 until totalFrames) {
                var sum = 0.0f
                val offset = i * channels
                for (c in 0 until channels) {
                    sum += samples[offset + c]
                }
                mono[i] = sum / channels.toFloat()
            }
        }

        // Quick check for silence
        var maxAbs = 0.0f
        for (i in 0 until totalFrames) {
            val a = abs(mono[i])
            if (a > maxAbs) maxAbs = a
        }
        if (maxAbs < 1e-7f) {
            return SpectralDistribution.SILENT
        }

        val nyquist = sampleRate / 2.0f
        val binResolution = sampleRate.toFloat() / FFT_SIZE.toFloat()
        val halfFft = FFT_SIZE / 2

        // Spectral band accumulators
        var subEnergy = 0.0
        var bassEnergy = 0.0
        var lowMidEnergy = 0.0
        var midEnergy = 0.0
        var highMidEnergy = 0.0
        var airEnergy = 0.0

        val powerSpectrumSum = DoubleArray(halfFft)

        val real = FloatArray(FFT_SIZE)
        val imag = FloatArray(FFT_SIZE)

        // Select frames using Welch method: 50% overlap.
        // For efficiency on long files, up to 128 frames evenly distributed across the track.
        val hopSize = FFT_SIZE / 2
        val maxFramesToProcess = 128
        val availableBlocks = if (totalFrames >= FFT_SIZE) {
            (totalFrames - FFT_SIZE) / hopSize + 1
        } else 1

        val blockStep = max(1, availableBlocks / maxFramesToProcess)
        var blocksAccumulated = 0

        var blockIndex = 0
        while (blockIndex < availableBlocks) {
            val startSample = if (availableBlocks > 1) {
                (blockIndex * hopSize).coerceAtMost(totalFrames - FFT_SIZE)
            } else 0

            // Fill real buffer with windowed samples, zero imag
            val copyLen = min(FFT_SIZE, totalFrames - startSample)
            for (n in 0 until copyLen) {
                real[n] = mono[startSample + n] * hannWindow[n]
                imag[n] = 0.0f
            }
            for (n in copyLen until FFT_SIZE) {
                real[n] = 0.0f
                imag[n] = 0.0f
            }

            // In-place Radix-2 Cooley-Tukey FFT
            fft(real, imag)

            // Accumulate power spectrum P[k] = re^2 + im^2
            for (k in 1 until halfFft) {
                val p = (real[k] * real[k] + imag[k] * imag[k]).toDouble()
                powerSpectrumSum[k] += p

                val freq = k * binResolution
                when {
                    freq in 20.0f..60.0f -> subEnergy += p
                    freq > 60.0f && freq <= 250.0f -> bassEnergy += p
                    freq > 250.0f && freq <= 500.0f -> lowMidEnergy += p
                    freq > 500.0f && freq <= 2000.0f -> midEnergy += p
                    freq > 2000.0f && freq <= 6000.0f -> highMidEnergy += p
                    freq > 6000.0f && freq <= min(20000.0f, nyquist) -> airEnergy += p
                }
            }

            blocksAccumulated++
            blockIndex += blockStep
        }

        val totalBandEnergy = subEnergy + bassEnergy + lowMidEnergy + midEnergy + highMidEnergy + airEnergy
        if (totalBandEnergy <= 1e-15 || blocksAccumulated == 0) {
            return SpectralDistribution.SILENT
        }

        // Determine Global Spectral Peak Frequency (maximum power bin in audible range 20Hz - 20000Hz)
        val minBin = max(1, (20.0f / binResolution).toInt())
        val maxBin = min(halfFft - 2, (min(20000.0f, nyquist) / binResolution).toInt())

        var peakBin = minBin
        var peakPower = -1.0
        for (k in minBin..maxBin) {
            val p = powerSpectrumSum[k]
            if (p > peakPower) {
                peakPower = p
                peakBin = k
            }
        }

        // Sub-bin parabolic peak interpolation
        val interpolatedPeakHz = if (peakBin in (minBin + 1) until maxBin) {
            val pPrev = max(1e-12, powerSpectrumSum[peakBin - 1])
            val pMid = max(1e-12, powerSpectrumSum[peakBin])
            val pNext = max(1e-12, powerSpectrumSum[peakBin + 1])

            val y1 = ln(pPrev)
            val y2 = ln(pMid)
            val y3 = ln(pNext)
            val denom = y1 - 2.0 * y2 + y3
            val delta = if (abs(denom) > 1e-6) (0.5 * (y1 - y3) / denom).toFloat() else 0.0f
            (peakBin.toFloat() + delta.coerceIn(-1.0f, 1.0f)) * binResolution
        } else {
            peakBin.toFloat() * binResolution
        }

        // Normalize ratios so they strictly sum to 1.0 (100%)
        val subRatio = (subEnergy / totalBandEnergy).toFloat()
        val bassRatio = (bassEnergy / totalBandEnergy).toFloat()
        val lowMidRatio = (lowMidEnergy / totalBandEnergy).toFloat()
        val midRatio = (midEnergy / totalBandEnergy).toFloat()
        val highMidRatio = (highMidEnergy / totalBandEnergy).toFloat()
        val airRatio = (airEnergy / totalBandEnergy).toFloat()

        return SpectralDistribution(
            subBassRatio = subRatio,
            bassRatio = bassRatio,
            lowMidRatio = lowMidRatio,
            midRatio = midRatio,
            highMidRatio = highMidRatio,
            airRatio = airRatio,
            peakFrequencyHz = interpolatedPeakHz,
            totalEnergy = totalBandEnergy,
            isSilent = false
        )
    }

    /**
     * In-place Radix-2 Decimation-in-Time Fast Fourier Transform.
     */
    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        // Bit-reversal permutation
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR
                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        // Cooley-Tukey butterflies
        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val theta = -2.0 * Math.PI / len
            val wStepR = cos(theta).toFloat()
            val wStepI = sin(theta).toFloat()

            var i = 0
            while (i < n) {
                var wR = 1.0f
                var wI = 0.0f
                for (m in 0 until halfLen) {
                    val idxA = i + m
                    val idxB = idxA + halfLen
                    val tr = wR * real[idxB] - wI * imag[idxB]
                    val ti = wR * imag[idxB] + wI * real[idxB]

                    real[idxB] = real[idxA] - tr
                    imag[idxB] = imag[idxA] - ti
                    real[idxA] += tr
                    imag[idxA] += ti

                    val nextWR = wR * wStepR - wI * wStepI
                    wI = wR * wStepI + wI * wStepR
                    wR = nextWR
                }
                i += len
            }
            len = len shl 1
        }
    }
}
