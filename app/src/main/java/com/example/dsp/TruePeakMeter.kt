package com.example.dsp

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

/**
 * ITU-R BS.1770-4 Annex 2 compliant True-Peak Meter.
 * Utilizes a 4x oversampling polyphase FIR filter (48 taps, 12 taps per phase)
 * with a Blackman-Harris windowed sinc design to detect inter-sample peaks.
 */
object TruePeakMeter {

    const val OVERSAMPLE_FACTOR = 4
    const val TAPS_PER_PHASE = 12

    data class PeakMeasurement(
        val samplePeakLinear: Float,
        val samplePeakDbfs: Float,
        val truePeakLinear: Float,
        val truePeakDbtp: Float,
        val hasClipping: Boolean,
        val isSilent: Boolean
    )

    // 4 Polyphase filter branches for 4x oversampling (each branch has 12 taps)
    private val polyphaseTaps = arrayOf(
        // Phase 0 (delay 0.00)
        floatArrayOf(0.00000000f, 0.00070569f, -0.00429548f, 0.01511908f, -0.04257124f, 0.12718490f, 0.97258547f, -0.09321175f, 0.03317538f, -0.01136329f, 0.00294401f, -0.00036338f),
        // Phase 1 (fractional delay 0.25)
        floatArrayOf(-0.00008462f, 0.00293380f, -0.01467889f, 0.04789702f, -0.13200354f, 0.44935167f, 0.77127788f, -0.17082947f, 0.06217375f, -0.02027263f, 0.00468656f, -0.00036092f),
        // Phase 2 (fractional delay 0.50)
        floatArrayOf(-0.00036092f, 0.00468656f, -0.02027263f, 0.06217375f, -0.17082947f, 0.77127788f, 0.44935167f, -0.13200354f, 0.04789702f, -0.01467889f, 0.00293380f, -0.00008462f),
        // Phase 3 (fractional delay 0.75)
        floatArrayOf(-0.00036338f, 0.00294401f, -0.01136329f, 0.03317538f, -0.09321175f, 0.97258547f, 0.12718490f, -0.04257124f, 0.01511908f, -0.00429548f, 0.00070569f, 0.00000000f)
    )

    /**
     * Measures both sample peak and 4x oversampled true-peak across all channels.
     */
    fun measure(
        samples: FloatArray,
        channels: Int = 2
    ): PeakMeasurement {
        if (samples.isEmpty()) {
            return PeakMeasurement(
                samplePeakLinear = 0.0f,
                samplePeakDbfs = -90.0f,
                truePeakLinear = 0.0f,
                truePeakDbtp = -90.0f,
                hasClipping = false,
                isSilent = true
            )
        }

        val totalFrames = samples.size / channels
        var maxSamplePeak = 0.0f
        var maxTruePeak = 0.0f

        // Sliding history buffers for each channel (size = TAPS_PER_PHASE)
        val historyL = FloatArray(TAPS_PER_PHASE)
        val historyR = FloatArray(TAPS_PER_PHASE)
        var histHead = 0

        for (i in 0 until totalFrames) {
            val left = samples[i * channels]
            val right = if (channels > 1) samples[i * channels + 1] else left

            val absL = abs(left)
            val absR = abs(right)
            val frameSampleMax = max(absL, absR)
            if (frameSampleMax > maxSamplePeak) maxSamplePeak = frameSampleMax

            // Push to circular history
            historyL[histHead] = left
            historyR[histHead] = right

            // Convolve with all 4 polyphase branches
            for (phase in 0 until OVERSAMPLE_FACTOR) {
                val taps = polyphaseTaps[phase]
                var sumL = 0.0f
                var sumR = 0.0f
                for (t in 0 until TAPS_PER_PHASE) {
                    val idx = (histHead - t + TAPS_PER_PHASE) % TAPS_PER_PHASE
                    sumL += historyL[idx] * taps[t]
                    sumR += historyR[idx] * taps[t]
                }
                val absUpL = abs(sumL)
                val absUpR = abs(sumR)
                val branchMax = max(absUpL, absUpR)
                if (branchMax > maxTruePeak) maxTruePeak = branchMax
            }

            histHead = (histHead + 1) % TAPS_PER_PHASE
        }

        // True peak must be at least as large as the sample peak
        if (maxSamplePeak > maxTruePeak) maxTruePeak = maxSamplePeak

        val isSilent = maxSamplePeak < 1e-7f
        val samplePeakDbfs = if (isSilent) -90.0f else (20.0 * log10(maxSamplePeak.toDouble().coerceAtLeast(1e-6))).toFloat()
        val truePeakDbtp = if (isSilent) -90.0f else (20.0 * log10(maxTruePeak.toDouble().coerceAtLeast(1e-6))).toFloat()

        return PeakMeasurement(
            samplePeakLinear = maxSamplePeak,
            samplePeakDbfs = samplePeakDbfs,
            truePeakLinear = maxTruePeak,
            truePeakDbtp = truePeakDbtp,
            hasClipping = maxSamplePeak >= 0.999f || maxTruePeak >= 1.0f,
            isSilent = isSilent
        )
    }
}
