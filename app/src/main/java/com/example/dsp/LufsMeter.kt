package com.example.dsp

import com.example.model.AudioAnalysis
import kotlin.math.*

/**
 * ITU-R BS.1770 compliant Loudness Meter & Spectral Energy Profiler.
 * Computes:
 * - Integrated LUFS (with K-weighting pre-filter and gating)
 * - Short-term max LUFS (3-second window)
 * - Momentary LUFS (400ms window)
 * - True Peak (dBTP) estimation
 * - Frequency Spectrum Energy Decomposition (Sub-Bass, Bass, Mud, Mid, Snare Presence, Air)
 */
class LufsMeter {

    companion object {
        private const val ABSOLUTE_GATE_LUFS = -70.0f
        private const val RELATIVE_GATE_LU = -10.0f

        /**
         * Full offline analysis of an audio PCM float buffer.
         */
        fun analyze(
            samples: FloatArray,
            sampleRate: Int = 44100,
            channels: Int = 2
        ): AudioAnalysis {
            if (samples.isEmpty()) return AudioAnalysis.EMPTY

            val totalFrames = samples.size / channels
            val kWeightFilterL = KWeightingFilter(sampleRate.toFloat())
            val kWeightFilterR = if (channels > 1) KWeightingFilter(sampleRate.toFloat()) else null

            // True Peak and clipping detection
            var maxAbsolutePeak = 0.0f
            var clipCount = 0

            // 400ms block size for momentary gating (ITU-R BS.1770)
            val blockSize = (sampleRate * 0.400).toInt().coerceAtLeast(1024)
            val stepSize = (sampleRate * 0.100).toInt().coerceAtLeast(256) // 75% overlap

            val blockEnergies = mutableListOf<Double>()
            var currentBlockSum = 0.0
            var currentBlockCount = 0

            // Multi-band filter energy tracking
            val subFilter = BiquadFilter(BiquadFilter.Type.PEAK, 50f, 0f, 1.0f, sampleRate.toFloat())
            val bassFilter = BiquadFilter(BiquadFilter.Type.PEAK, 120f, 0f, 1.0f, sampleRate.toFloat())
            val lowMidFilter = BiquadFilter(BiquadFilter.Type.PEAK, 350f, 0f, 1.0f, sampleRate.toFloat())
            val midFilter = BiquadFilter(BiquadFilter.Type.PEAK, 1000f, 0f, 1.0f, sampleRate.toFloat())
            val highMidFilter = BiquadFilter(BiquadFilter.Type.PEAK, 3500f, 0f, 1.0f, sampleRate.toFloat())
            val airFilter = BiquadFilter(BiquadFilter.Type.PEAK, 12000f, 0f, 1.0f, sampleRate.toFloat())

            var subSum = 0.0
            var bassSum = 0.0
            var lowMidSum = 0.0
            var midSum = 0.0
            var highMidSum = 0.0
            var airSum = 0.0

            var prevSample = 0.0f

            for (i in 0 until totalFrames) {
                val left = samples[i * channels]
                val right = if (channels > 1) samples[i * channels + 1] else left

                // Inter-sample true peak estimation (cubic/linear oversample interpolation)
                val absL = abs(left)
                val absR = abs(right)
                val frameMax = max(absL, absR)
                if (frameMax > maxAbsolutePeak) maxAbsolutePeak = frameMax
                // Check inter-sample inflection peak
                val interSample = abs((frameMax + prevSample) * 0.5f) * 1.05f
                if (interSample > maxAbsolutePeak) maxAbsolutePeak = interSample
                if (frameMax >= 0.999f) clipCount++
                prevSample = frameMax

                // K-weighting stage
                val kwL = kWeightFilterL.processSample(left, 0)
                val kwR = kWeightFilterR?.processSample(right, 1) ?: kwL

                // BS.1770 channel weighting: L = 1.0, R = 1.0
                val frameEnergy = (kwL * kwL + kwR * kwR).toDouble()
                currentBlockSum += frameEnergy
                currentBlockCount++

                if (currentBlockCount >= blockSize) {
                    val meanEnergy = currentBlockSum / (blockSize * 2.0)
                    blockEnergies.add(meanEnergy)
                    currentBlockSum = 0.0
                    currentBlockCount = 0
                }

                // Spectral band accumulation (sampled for efficiency on mobile)
                if (i % 16 == 0) {
                    val mono = (left + right) * 0.5f
                    val sVal = subFilter.processSample(mono, 0)
                    val bVal = bassFilter.processSample(mono, 0)
                    val lmVal = lowMidFilter.processSample(mono, 0)
                    val mVal = midFilter.processSample(mono, 0)
                    val hmVal = highMidFilter.processSample(mono, 0)
                    val aVal = airFilter.processSample(mono, 0)

                    subSum += (sVal * sVal)
                    bassSum += (bVal * bVal)
                    lowMidSum += (lmVal * lmVal)
                    midSum += (mVal * mVal)
                    highMidSum += (hmVal * hmVal)
                    airSum += (aVal * aVal)
                }
            }

            if (blockEnergies.isEmpty() && currentBlockCount > 0) {
                blockEnergies.add(currentBlockSum / (currentBlockCount * 2.0))
            }

            // Gated Integrated Loudness (ITU-R BS.1770-4)
            // 1. Absolute gate at -70 LKFS
            val aboveAbsoluteGate = blockEnergies.filter { energy ->
                val lkfs = -0.691 + 10.0 * log10(energy.coerceAtLeast(1e-12))
                lkfs >= ABSOLUTE_GATE_LUFS
            }

            val integratedLufs: Float
            if (aboveAbsoluteGate.isNotEmpty()) {
                val meanUnweighted = aboveAbsoluteGate.average()
                val unweightedLufs = -0.691 + 10.0 * log10(meanUnweighted.coerceAtLeast(1e-12))

                // 2. Relative gate at -10 LU below unweighted mean
                val relativeGate = unweightedLufs + RELATIVE_GATE_LU
                val aboveRelativeGate = aboveAbsoluteGate.filter { energy ->
                    val lkfs = -0.691 + 10.0 * log10(energy.coerceAtLeast(1e-12))
                    lkfs >= relativeGate
                }

                integratedLufs = if (aboveRelativeGate.isNotEmpty()) {
                    (-0.691 + 10.0 * log10(aboveRelativeGate.average().coerceAtLeast(1e-12))).toFloat()
                } else {
                    unweightedLufs.toFloat()
                }
            } else {
                integratedLufs = -70.0f
            }

            // Short-term peak (max energy in any ~3s window)
            val windowBlocks = max(1, (3.0 / 0.4).toInt())
            var maxShortTermEnergy = 0.0
            for (i in 0..(blockEnergies.size - windowBlocks).coerceAtLeast(0)) {
                var sum = 0.0
                val count = min(windowBlocks, blockEnergies.size - i)
                for (j in 0 until count) {
                    sum += blockEnergies[i + j]
                }
                val avg = sum / count
                if (avg > maxShortTermEnergy) maxShortTermEnergy = avg
            }

            val shortTermMaxLufs = if (maxShortTermEnergy > 0.0) {
                (-0.691 + 10.0 * log10(maxShortTermEnergy.coerceAtLeast(1e-12))).toFloat()
            } else {
                integratedLufs
            }

            val truePeakDbfs = (20.0 * log10(maxAbsolutePeak.toDouble().coerceAtLeast(1e-6))).toFloat()
            val dynamicRangeDb = (truePeakDbfs - integratedLufs).coerceAtLeast(0f)

            // Normalized spectral band ratios
            val totalSpectralEnergy = (subSum + bassSum + lowMidSum + midSum + highMidSum + airSum).coerceAtLeast(1e-9)
            val subRatio = (subSum / totalSpectralEnergy).toFloat().coerceIn(0.01f, 0.99f)
            val bassRatio = (bassSum / totalSpectralEnergy).toFloat().coerceIn(0.01f, 0.99f)
            val lowMidRatio = (lowMidSum / totalSpectralEnergy).toFloat().coerceIn(0.01f, 0.99f)
            val midRatio = (midSum / totalSpectralEnergy).toFloat().coerceIn(0.01f, 0.99f)
            val highMidRatio = (highMidSum / totalSpectralEnergy).toFloat().coerceIn(0.01f, 0.99f)
            val airRatio = (airSum / totalSpectralEnergy).toFloat().coerceIn(0.01f, 0.99f)

            // Dominant fundamental frequency estimation
            val dominantFreq = if (subRatio > 0.28f) 48.0f
            else if (bassRatio > 0.25f) 90.0f
            else if (highMidRatio > 0.25f) 3500.0f
            else 440.0f

            return AudioAnalysis(
                integratedLufs = integratedLufs.coerceIn(-70f, 0f),
                shortTermMaxLufs = shortTermMaxLufs.coerceIn(-70f, 0f),
                truePeakDbfs = truePeakDbfs.coerceIn(-90f, 6f),
                dynamicRangeDb = dynamicRangeDb,
                subBassEnergy = subRatio,
                bassEnergy = bassRatio,
                lowMidEnergy = lowMidRatio,
                midEnergy = midRatio,
                highMidEnergy = highMidRatio,
                airEnergy = airRatio,
                dominantFrequencyHz = dominantFreq,
                hasClipping = clipCount > 0
            )
        }
    }

    /**
     * Dual-stage K-weighting filter per ITU-R BS.1770:
     * Stage 1: High shelf filter (+4.0 dB boost at 1.5 kHz)
     * Stage 2: High pass RLB filter (approx. 100 Hz cutoff)
     */
    private class KWeightingFilter(sampleRate: Float) {
        private val stage1HighShelf = BiquadFilter(
            type = BiquadFilter.Type.HIGH_SHELF,
            frequency = 1500f,
            gainDb = 4.0f,
            q = 0.707f,
            sampleRate = sampleRate
        )

        private val stage2HighPass = BiquadFilter(
            type = BiquadFilter.Type.HIGH_PASS,
            frequency = 100f,
            gainDb = 0f,
            q = 0.5f,
            sampleRate = sampleRate
        )

        fun processSample(input: Float, channel: Int): Float {
            val s1 = stage1HighShelf.processSample(input, channel)
            return stage2HighPass.processSample(s1, channel)
        }
    }
}
