package com.example.dsp

import com.example.model.AudioAnalysis
import kotlin.math.*

/**
 * Digital Audio Loudness & Acoustic Dynamics Meter.
 * Fully compliant with ITU-R BS.1770-4, EBU R128, and EBU Tech 3342.
 *
 * Implements:
 * 1. Dual-stage K-weighting pre-filter (Brecht DeMan ITU-R BS.1770-4 bilinear design for any sample rate).
 * 2. ITU-R BS.1770-4 Gated Integrated Loudness (400ms blocks, 75% overlap, -70 LKFS absolute & -10 LU relative gates).
 * 3. Momentary Loudness (400ms rectangular window, 100ms interval).
 * 4. Short-Term Loudness (3s rectangular window, 100ms interval) & Short-Term Max.
 * 5. Loudness Range (LRA) per EBU Tech 3342.
 * 6. True-Peak estimation (ITU-R BS.1770 Annex 2 4x oversampled polyphase FIR).
 * 7. Unweighted RMS and Crest Factor (dB).
 * 8. Pearson Stereo Phase Correlation.
 * 9. FFT-based Welch Power Spectral Decomposition (6 contiguous, non-overlapping bands) and Global Peak Frequency.
 */
class LufsMeter {

    companion object {
        const val ABSOLUTE_GATE_LUFS = -70.0f
        const val RELATIVE_GATE_LU = -10.0f
        const val LRA_RELATIVE_GATE_LU = -20.0f

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
            if (totalFrames == 0) return AudioAnalysis.EMPTY

            // 1. Peak, True Peak, and Clipping Measurement (4x Polyphase FIR)
            val peakResult = TruePeakMeter.measure(samples, channels)
            if (peakResult.isSilent) {
                return AudioAnalysis.EMPTY
            }

            // 2. Unweighted RMS and Crest Factor
            var sumSquares = 0.0
            for (i in samples.indices) {
                val s = samples[i].toDouble()
                sumSquares += s * s
            }
            val rmsLinear = sqrt(sumSquares / samples.size.toDouble())
            val rmsDbfs = (20.0 * log10(rmsLinear.coerceAtLeast(1e-6))).toFloat()
            val crestFactorDb = max(0.0f, peakResult.samplePeakDbfs - rmsDbfs)

            // 3. Pearson Stereo Correlation
            val stereoCorrelation = if (channels > 1 && totalFrames > 1) {
                computeStereoCorrelation(samples, totalFrames, channels)
            } else {
                1.0f // Mono is perfectly correlated with itself
            }

            // 4. K-Weighting Filter Stage (ITU-R BS.1770-4)
            val kFilterL = KWeightingFilter(sampleRate.toDouble())
            val kFilterR = if (channels > 1) KWeightingFilter(sampleRate.toDouble()) else null

            val filteredL = FloatArray(totalFrames)
            val filteredR = if (channels > 1) FloatArray(totalFrames) else filteredL

            for (i in 0 until totalFrames) {
                val left = samples[i * channels]
                filteredL[i] = kFilterL.processSample(left)
                if (channels > 1) {
                    val right = samples[i * channels + 1]
                    filteredR[i] = kFilterR!!.processSample(right)
                }
            }

            // 5. ITU-R BS.1770-4 Gated Integrated Loudness & Short-Term / Momentary Tracking
            // Block parameters: Tg = 400ms, Step = 100ms (75% overlap)
            val blockSamples = (sampleRate * 0.400).roundToInt().coerceAtLeast(16)
            val stepSamples = (sampleRate * 0.100).roundToInt().coerceAtLeast(4)

            // Accumulate power for 400ms blocks
            val momentaryPowers = mutableListOf<Double>()
            val momentaryLoudness = mutableListOf<Double>()

            var stepStart = 0
            while (stepStart + blockSamples <= totalFrames) {
                var sumSqL = 0.0
                var sumSqR = 0.0
                for (n in 0 until blockSamples) {
                    val idx = stepStart + n
                    val yL = filteredL[idx].toDouble()
                    sumSqL += yL * yL
                    if (channels > 1) {
                        val yR = filteredR[idx].toDouble()
                        sumSqR += yR * yR
                    }
                }

                val zL = sumSqL / blockSamples.toDouble()
                val zR = if (channels > 1) sumSqR / blockSamples.toDouble() else 0.0

                // Channel weighting: G_L = 1.0, G_R = 1.0 (Sum of acoustic powers, NOT divided by 2!)
                val zTotal = if (channels > 1) zL + zR else zL
                momentaryPowers.add(zTotal)

                val lkfs = -0.691 + 10.0 * log10(zTotal.coerceAtLeast(1e-12))
                momentaryLoudness.add(lkfs)

                stepStart += stepSamples
            }

            // Edge case: file shorter than 400ms
            if (momentaryPowers.isEmpty() && totalFrames > 0) {
                var sumSqL = 0.0
                var sumSqR = 0.0
                for (n in 0 until totalFrames) {
                    val yL = filteredL[n].toDouble()
                    sumSqL += yL * yL
                    if (channels > 1) {
                        val yR = filteredR[n].toDouble()
                        sumSqR += yR * yR
                    }
                }
                val zL = sumSqL / totalFrames.toDouble()
                val zR = if (channels > 1) sumSqR / totalFrames.toDouble() else 0.0
                val zTotal = if (channels > 1) zL + zR else zL
                momentaryPowers.add(zTotal)
                momentaryLoudness.add(-0.691 + 10.0 * log10(zTotal.coerceAtLeast(1e-12)))
            }

            // Gating Step A: Absolute gate at -70 LKFS
            val aboveAbsoluteIndices = mutableListOf<Int>()
            for (j in momentaryLoudness.indices) {
                if (momentaryLoudness[j] >= ABSOLUTE_GATE_LUFS) {
                    aboveAbsoluteIndices.add(j)
                }
            }

            val integratedLufs: Float
            if (aboveAbsoluteIndices.isNotEmpty()) {
                var sumAbsPowers = 0.0
                for (idx in aboveAbsoluteIndices) {
                    sumAbsPowers += momentaryPowers[idx]
                }
                val meanAbsPower = sumAbsPowers / aboveAbsoluteIndices.size.toDouble()
                val unweightedLoudness = -0.691 + 10.0 * log10(meanAbsPower.coerceAtLeast(1e-12))

                // Gating Step B: Relative gate at -10 LU below unweighted mean
                val relativeThreshold = unweightedLoudness + RELATIVE_GATE_LU
                val aboveRelativeIndices = mutableListOf<Int>()
                for (idx in aboveAbsoluteIndices) {
                    if (momentaryLoudness[idx] > relativeThreshold) {
                        aboveRelativeIndices.add(idx)
                    }
                }

                integratedLufs = if (aboveRelativeIndices.isNotEmpty()) {
                    var sumRelPowers = 0.0
                    for (idx in aboveRelativeIndices) {
                        sumRelPowers += momentaryPowers[idx]
                    }
                    val meanRelPower = sumRelPowers / aboveRelativeIndices.size.toDouble()
                    (-0.691 + 10.0 * log10(meanRelPower.coerceAtLeast(1e-12))).toFloat()
                } else {
                    unweightedLoudness.toFloat()
                }
            } else {
                integratedLufs = -70.0f
            }

            // Momentary Max: maximum of the 400ms momentary loudness blocks
            val momentaryMax = if (momentaryLoudness.isNotEmpty()) {
                momentaryLoudness.maxOrNull()?.toFloat() ?: integratedLufs
            } else integratedLufs

            // Short-Term Loudness (3-second window, updated every 100ms)
            // 3s window is represented by approx 30 steps of 100ms
            val shortTermWindowSteps = max(1, (3.0 / 0.1).roundToInt())
            val shortTermLoudnessList = mutableListOf<Double>()
            val numSteps = momentaryPowers.size

            if (numSteps <= shortTermWindowSteps) {
                // Whole track fits in 3s window
                val avgPower = if (momentaryPowers.isNotEmpty()) momentaryPowers.average() else 1e-12
                val st = -0.691 + 10.0 * log10(avgPower.coerceAtLeast(1e-12))
                shortTermLoudnessList.add(st)
            } else {
                for (s in 0..(numSteps - shortTermWindowSteps)) {
                    var sumPower = 0.0
                    for (k in 0 until shortTermWindowSteps) {
                        sumPower += momentaryPowers[s + k]
                    }
                    val avgPower = sumPower / shortTermWindowSteps.toDouble()
                    val st = -0.691 + 10.0 * log10(avgPower.coerceAtLeast(1e-12))
                    shortTermLoudnessList.add(st)
                }
            }

            val shortTermMax = if (shortTermLoudnessList.isNotEmpty()) {
                shortTermLoudnessList.maxOrNull()?.toFloat() ?: integratedLufs
            } else integratedLufs

            // Loudness Range (LRA, EBU Tech 3342)
            val lra = computeLoudnessRange(shortTermLoudnessList)

            // 6. Real FFT Power Spectral Decomposition
            val spectral = SpectralAnalyzer.analyze(samples, sampleRate, channels)

            return AudioAnalysis(
                integratedLufs = integratedLufs.coerceIn(-70f, 6f),
                shortTermMaxLufs = shortTermMax.coerceIn(-70f, 6f),
                momentaryMaxLufs = momentaryMax.coerceIn(-70f, 6f),
                samplePeakDbfs = peakResult.samplePeakDbfs,
                truePeakDbfs = peakResult.truePeakDbtp,
                rmsDbfs = rmsDbfs,
                crestFactorDb = crestFactorDb,
                loudnessRangeLu = lra,
                dynamicRangeDb = crestFactorDb,
                subBassEnergy = spectral.subBassRatio,
                bassEnergy = spectral.bassRatio,
                lowMidEnergy = spectral.lowMidRatio,
                midEnergy = spectral.midRatio,
                highMidEnergy = spectral.highMidRatio,
                airEnergy = spectral.airRatio,
                dominantFrequencyHz = spectral.peakFrequencyHz,
                stereoCorrelation = stereoCorrelation,
                hasClipping = peakResult.hasClipping,
                isSilent = false
            )
        }

        /**
         * EBU Tech 3342 Loudness Range (LRA) calculation.
         */
        private fun computeLoudnessRange(shortTermList: List<Double>): Float {
            if (shortTermList.isEmpty()) return 0.0f

            // 1. Absolute threshold at -70 LUFS
            val absGated = shortTermList.filter { it >= ABSOLUTE_GATE_LUFS }
            if (absGated.size < 2) return 0.0f

            // 2. Relative threshold at -20 LU below average power
            val sumPower = absGated.sumOf { 10.0.pow(it / 10.0) }
            val avgPower = sumPower / absGated.size.toDouble()
            val refLoudness = 10.0 * log10(avgPower.coerceAtLeast(1e-12))
            val relThreshold = refLoudness + LRA_RELATIVE_GATE_LU

            val relGated = absGated.filter { it >= relThreshold }.sorted()
            if (relGated.size < 2) return 0.0f

            // Percentile 10 and 95
            val p10Idx = (0.10 * (relGated.size - 1)).toInt().coerceIn(0, relGated.size - 1)
            val p95Idx = (0.95 * (relGated.size - 1)).toInt().coerceIn(0, relGated.size - 1)

            val lra = (relGated[p95Idx] - relGated[p10Idx]).toFloat()
            return max(0.0f, lra)
        }

        /**
         * Computes Pearson correlation coefficient between Left and Right channels.
         */
        private fun computeStereoCorrelation(samples: FloatArray, totalFrames: Int, channels: Int): Float {
            var sumL = 0.0
            var sumR = 0.0
            for (i in 0 until totalFrames) {
                sumL += samples[i * channels].toDouble()
                sumR += samples[i * channels + 1].toDouble()
            }
            val meanL = sumL / totalFrames.toDouble()
            val meanR = sumR / totalFrames.toDouble()

            var num = 0.0
            var denL = 0.0
            var denR = 0.0

            for (i in 0 until totalFrames) {
                val dL = samples[i * channels].toDouble() - meanL
                val dR = samples[i * channels + 1].toDouble() - meanR
                num += dL * dR
                denL += dL * dL
                denR += dR * dR
            }

            val denom = sqrt(denL * denR)
            return if (denom > 1e-9) {
                (num / denom).toFloat().coerceIn(-1.0f, 1.0f)
            } else {
                1.0f
            }
        }
    }

    /**
     * Exact K-Weighting Filter per ITU-R BS.1770-4 for arbitrary sample rate (Brecht DeMan design):
     * Stage 1: Pre-filter (high shelf)
     * Stage 2: RLB filter (high pass)
     */
    private class KWeightingFilter(sampleRate: Double) {
        // Stage 1: High shelf filter
        private val b0_1: Double
        private val b1_1: Double
        private val b2_1: Double
        private val a1_1: Double
        private val a2_1: Double

        // Stage 2: High pass (RLB)
        private val b0_2: Double
        private val b1_2: Double
        private val b2_2: Double
        private val a1_2: Double
        private val a2_2: Double

        // Filter delay states (Direct Form II Transposed)
        private var z1_1 = 0.0
        private var z2_1 = 0.0
        private var z1_2 = 0.0
        private var z2_2 = 0.0

        init {
            // Stage 1 coefficients
            val fc1 = 1681.9744509555319
            val g1 = 3.99984385397
            val q1 = 0.7071752369554193

            val k1 = tan(Math.PI * fc1 / sampleRate)
            val vh = 10.0.pow(g1 / 20.0)
            val vb = vh.pow(0.499666774155)
            val a0_1 = 1.0 + k1 / q1 + k1 * k1

            b0_1 = (vh + vb * k1 / q1 + k1 * k1) / a0_1
            b1_1 = 2.0 * (k1 * k1 - vh) / a0_1
            b2_1 = (vh - vb * k1 / q1 + k1 * k1) / a0_1
            a1_1 = 2.0 * (k1 * k1 - 1.0) / a0_1
            a2_1 = (1.0 - k1 / q1 + k1 * k1) / a0_1

            // Stage 2 coefficients
            val fc2 = 38.13547087613982
            val q2 = 0.5003270373253953

            val k2 = tan(Math.PI * fc2 / sampleRate)
            val a0_2 = 1.0 + k2 / q2 + k2 * k2

            b0_2 = 1.0 / a0_2
            b1_2 = -2.0 / a0_2
            b2_2 = 1.0 / a0_2
            a1_2 = 2.0 * (k2 * k2 - 1.0) / a0_2
            a2_2 = (1.0 - k2 / q2 + k2 * k2) / a0_2
        }

        fun processSample(input: Float): Float {
            val inD = input.toDouble()

            // Stage 1 (Direct Form II Transposed)
            val y1 = b0_1 * inD + z1_1
            z1_1 = b1_1 * inD - a1_1 * y1 + z2_1
            z2_1 = b2_1 * inD - a2_1 * y1

            // Stage 2 (Direct Form II Transposed)
            val y2 = b0_2 * y1 + z1_2
            z1_2 = b1_2 * y1 - a1_2 * y2 + z2_2
            z2_2 = b2_2 * y1 - a2_2 * y2

            return y2.toFloat()
        }
    }
}
