package com.michel.masteringpro.audio

import com.michel.masteringpro.dsp.LoudnessEbuR128
import com.michel.masteringpro.dsp.SpectrumAnalyzer
import com.michel.masteringpro.dsp.TruePeakMeter
import com.michel.masteringpro.model.AnalysisIssue
import com.michel.masteringpro.model.AnalysisResult
import com.michel.masteringpro.model.AudioTrackData
import com.michel.masteringpro.model.IssueSeverity
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.math.pow

/** Offline analysis pipeline for loudness, dynamics, spectrum, phase and tonal estimates. */
object AudioAnalyzer {
    private val clippingThreshold = 10.0.pow(-0.1 / 20.0)

    fun analyze(pcm: FloatArray, sampleRate: Int = 48_000, channels: Int = 2): AnalysisResult =
        analyze(AudioTrackData("PCM", sampleRate, channels, pcm))

    fun analyze(track: AudioTrackData): AnalysisResult {
        val samples = track.samples
        val channels = track.channels
        val frames = track.frameCount
        if (frames == 0) return emptyAnalysis(track.sampleRate)

        var sumSquares = 0.0
        var leftSquares = 0.0
        var rightSquares = 0.0
        var cross = 0.0
        var midSquares = 0.0
        var sideSquares = 0.0
        var clippingCount = 0L
        for (frame in 0 until frames) {
            val offset = frame * channels
            val left = samples[offset].toDouble()
            val right = if (channels > 1) samples[offset + 1].toDouble() else left
            sumSquares += left * left
            leftSquares += left * left
            if (channels > 1) {
                sumSquares += right * right
                rightSquares += right * right
                cross += left * right
                val mid = (left + right) * 0.5
                val side = (left - right) * 0.5
                midSquares += mid * mid
                sideSquares += side * side
            }
            if (abs(left) > clippingThreshold) clippingCount++
            if (channels > 1 && abs(right) > clippingThreshold) clippingCount++
        }

        val totalSamples = samples.size.coerceAtLeast(1)
        val rms = sqrt(sumSquares / totalSamples)
        val rmsDb = amplitudeToDb(rms)
        val peakMetrics = TruePeakMeter.measure(samples, channels)
        val samplePeakDb = amplitudeToDb(peakMetrics.samplePeak.toDouble())
        val truePeakDb = amplitudeToDb(peakMetrics.truePeak.toDouble())
        val lufs = LoudnessEbuR128.measure(samples, track.sampleRate, channels)
        val spectrum = SpectrumAnalyzer.analyze(samples, track.sampleRate, channels)
        val isSilent = peakMetrics.samplePeak < 1e-7f

        val correlation = if (channels == 1 || isSilent) 1f else {
            val denominator = sqrt((leftSquares * rightSquares).coerceAtLeast(1e-24))
            (cross / denominator).toFloat().coerceIn(-1f, 1f)
        }
        val widthDb = if (channels == 1 || midSquares <= 1e-20) -60f else {
            amplitudeToDb(sqrt(sideSquares / midSquares))
        }
        val monoRms = if (channels == 1) rms else sqrt(midSquares / frames)
        val stereoRms = if (channels == 1) rms else sqrt((leftSquares + rightSquares) / (2.0 * frames))
        val monoCompatibility = if (stereoRms <= 1e-12) 100f else {
            (monoRms / stereoRms * 100.0).toFloat().coerceIn(0f, 100f)
        }
        val leftRms = sqrt(leftSquares / frames)
        val rightRms = if (channels > 1) sqrt(rightSquares / frames) else leftRms
        val channelBalance = if (leftRms <= 1e-12 || rightRms <= 1e-12) 0f else {
            abs(amplitudeToDb(leftRms) - amplitudeToDb(rightRms))
        }
        val crest = if (isSilent) 0f else (samplePeakDb - rmsDb).coerceAtLeast(0f)
        val plr = if (isSilent) 0f else (truePeakDb - lufs.integratedLufs).coerceAtLeast(0f)
        val clippedPercent = clippingCount.toDouble().div(totalSamples).times(100.0).toFloat()

        val hasBassDeficit = spectrum.lowBandVsReferenceDb < -6f
        val hasBodyExcess = spectrum.bodyBandVsReferenceDb > 3f
        val hasPresenceExcess = spectrum.presenceVsReferenceDb > 3f
        val hasAirDeficit = spectrum.airVs1kDb < -12f
        val issues = buildList {
            if (clippingCount > 0L) add(AnalysisIssue("${clippingCount} muestras superan -0.1 dBFS — CLIPPING", IssueSeverity.CRITICAL))
            if (!isSilent && truePeakDb > -1f) add(AnalysisIssue("True Peak ${formatDb(truePeakDb)} dBTP: conviene limitar a -1 dBTP", IssueSeverity.WARNING))
            if (!isSilent && lufs.integratedLufs > -9f) add(AnalysisIssue("Sonoridad ${formatDb(lufs.integratedLufs)} LUFS: nivel muy alto", IssueSeverity.WARNING))
            if (channels > 1 && channelBalance > 1.5f) add(AnalysisIssue("Desbalance L/R ${formatDb(channelBalance)} dB", IssueSeverity.WARNING))
            if (channels > 1 && correlation < 0.3f) add(AnalysisIssue("Problema de fase: correlación estéreo ${formatDb(correlation)}", IssueSeverity.CRITICAL))
            if (hasBassDeficit) add(AnalysisIssue("Poca energía espectral por debajo de 80 Hz", IssueSeverity.INFO))
            if (hasBodyExcess) add(AnalysisIssue("Posible exceso en el cuerpo (200–400 Hz)", IssueSeverity.WARNING))
            if (hasPresenceExcess) add(AnalysisIssue("Energía elevada en la zona de presencia (2–5 kHz)", IssueSeverity.WARNING))
            if (hasAirDeficit) add(AnalysisIssue("Falta de aire por encima de 12 kHz", IssueSeverity.INFO))
            if (isSilent) add(AnalysisIssue("El track parece estar en silencio digital", IssueSeverity.WARNING))
        }

        return AnalysisResult(
            integratedLufs = lufs.integratedLufs,
            truePeakDbtp = truePeakDb,
            samplePeakDbfs = samplePeakDb,
            loudnessRangeLu = lufs.loudnessRangeLu,
            rmsDbfs = rmsDb,
            crestFactorDb = crest,
            dynamicRangeDb = plr,
            clippingCount = clippingCount,
            clippingPercent = clippedPercent,
            lowBandVsReferenceDb = spectrum.lowBandVsReferenceDb,
            bodyBandVsReferenceDb = spectrum.bodyBandVsReferenceDb,
            presenceVsReferenceDb = spectrum.presenceVsReferenceDb,
            airVs1kDb = spectrum.airVs1kDb,
            hasBassDeficit = hasBassDeficit,
            hasBodyExcess = hasBodyExcess,
            hasPresenceExcess = hasPresenceExcess,
            hasAirDeficit = hasAirDeficit,
            stereoCorrelation = correlation,
            stereoWidthDb = widthDb,
            monoCompatibilityPercent = monoCompatibility,
            channelBalanceDb = channelBalance,
            estimatedBpm = estimateBpm(track),
            estimatedKey = spectrum.estimatedKey,
            spectrum = spectrum.spectrum,
            issues = issues,
            isSilent = isSilent
        )
    }

    private fun estimateBpm(track: AudioTrackData): Float? {
        val frames = track.frameCount
        val rate = track.sampleRate
        val blockFrames = (rate / 100).coerceAtLeast(1) // 10 ms RMS envelope
        val envelopeCount = frames / blockFrames
        if (envelopeCount < 400) return null
        val envelope = FloatArray(envelopeCount)
        for (block in 0 until envelopeCount) {
            val start = block * blockFrames
            var energy = 0.0
            for (frame in start until start + blockFrames) {
                val offset = frame * track.channels
                val mono = if (track.channels == 1) track.samples[offset].toDouble() else {
                    (track.samples[offset].toDouble() + track.samples[offset + 1]) * 0.5
                }
                energy += mono * mono
            }
            envelope[block] = sqrt(energy / blockFrames).toFloat()
        }
        val onset = FloatArray(envelopeCount)
        var mean = 0f
        for (i in 1 until envelopeCount) {
            onset[i] = (envelope[i] - envelope[i - 1]).coerceAtLeast(0f)
            mean += onset[i]
        }
        mean /= envelopeCount
        for (i in onset.indices) onset[i] -= mean

        var bestLag = 0
        var bestScore = Double.NEGATIVE_INFINITY
        val minLag = 33 // 180 BPM
        val maxLag = 100 // 60 BPM
        for (lag in minLag..maxLag) {
            if (lag >= envelopeCount) break
            var product = 0.0
            var leftPower = 0.0
            var rightPower = 0.0
            for (i in lag until envelopeCount) {
                val a = onset[i].toDouble()
                val b = onset[i - lag].toDouble()
                product += a * b
                leftPower += a * a
                rightPower += b * b
            }
            val normalized = product / sqrt((leftPower * rightPower).coerceAtLeast(1e-24))
            if (normalized > bestScore) {
                bestScore = normalized
                bestLag = lag
            }
        }
        if (bestLag == 0 || bestScore < 0.015) return null
        return (6_000.0 / bestLag).toFloat()
    }

    private fun emptyAnalysis(sampleRate: Int): AnalysisResult {
        return AnalysisResult(
            integratedLufs = -70f, truePeakDbtp = -120f, samplePeakDbfs = -120f,
            loudnessRangeLu = 0f, rmsDbfs = -120f, crestFactorDb = 0f, dynamicRangeDb = 0f,
            clippingCount = 0L, clippingPercent = 0f,
            lowBandVsReferenceDb = 0f, bodyBandVsReferenceDb = 0f,
            presenceVsReferenceDb = 0f, airVs1kDb = 0f,
            hasBassDeficit = false, hasBodyExcess = false, hasPresenceExcess = false, hasAirDeficit = false,
            stereoCorrelation = 1f, stereoWidthDb = -60f, monoCompatibilityPercent = 100f,
            channelBalanceDb = 0f, estimatedBpm = null, estimatedKey = "—",
            spectrum = com.michel.masteringpro.model.SpectrumData(sampleRate, FloatArray(2049) { -90f }),
            issues = emptyList(), isSilent = true
        )
    }

    private fun amplitudeToDb(amplitude: Double): Float =
        if (amplitude <= 1e-12) -120f else (20.0 * log10(amplitude)).toFloat()

    private fun formatDb(value: Float): String = String.format(java.util.Locale.US, "%.1f", value)
}
