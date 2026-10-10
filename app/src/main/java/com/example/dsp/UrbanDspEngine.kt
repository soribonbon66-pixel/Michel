package com.example.dsp

import com.example.model.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.*

/**
 * Complete Mastering DSP Engine for Urban, Reggaeton, and Dembow productions.
 * Implements:
 * 1. Pre-Analysis (LUFS + Spectral Decomposition).
 * 2. Self-Adjusting Auto-Calibration Algorithm.
 * 3. 5-Band Parametric Filter Chain.
 * 4. Mid/Side Mono-Sub Matrix.
 * 5. Analog Harmonic Saturation.
 * 6. Dynamic Lookahead Peak Limiter with True-Peak Anti-Clipping.
 */
class UrbanDspEngine {

    /**
     * Step 1: Pre-analyze the incoming raw track.
     */
    fun analyzeTrack(track: AudioTrackData): AudioAnalysis {
        return LufsMeter.analyze(
            samples = track.samples,
            sampleRate = track.sampleRate,
            channels = track.channels
        )
    }

    /**
     * Step 2: Render audio through the complete mastering chain using the user's settings.
     */
    suspend fun processTrack(
        inputTrack: AudioTrackData,
        settings: MasteringSettings,
        precomputedRawAnalysis: AudioAnalysis? = null,
        onProgress: (Float) -> Unit = {}
    ): Pair<AudioTrackData, MasteringResult> {
        val startTime = System.currentTimeMillis()
        val originalAnalysis = precomputedRawAnalysis ?: analyzeTrack(inputTrack)

        val sampleRate = inputTrack.sampleRate.toFloat()
        val channels = inputTrack.channels
        val inSamples = inputTrack.samples
        val totalFrames = inSamples.size / channels
        val outSamples = FloatArray(inSamples.size)

        // Initialize DSP components
        val subFilter = BiquadFilter(
            type = BiquadFilter.Type.LOW_SHELF,
            frequency = settings.subBassBand.frequencyHz,
            gainDb = settings.subBassBand.gainDb,
            q = settings.subBassBand.qFactor,
            sampleRate = sampleRate
        )

        val kickFilter = BiquadFilter(
            type = BiquadFilter.Type.PEAK,
            frequency = settings.kickPunchBand.frequencyHz,
            gainDb = settings.kickPunchBand.gainDb,
            q = settings.kickPunchBand.qFactor,
            sampleRate = sampleRate
        )

        val mudFilter = BiquadFilter(
            type = BiquadFilter.Type.PEAK,
            frequency = settings.mudCutBand.frequencyHz,
            gainDb = settings.mudCutBand.gainDb,
            q = settings.mudCutBand.qFactor,
            sampleRate = sampleRate
        )

        val snareFilter = BiquadFilter(
            type = BiquadFilter.Type.PEAK,
            frequency = settings.snarePresenceBand.frequencyHz,
            gainDb = settings.snarePresenceBand.gainDb,
            q = settings.snarePresenceBand.qFactor,
            sampleRate = sampleRate
        )

        val airFilter = BiquadFilter(
            type = BiquadFilter.Type.HIGH_SHELF,
            frequency = settings.airShelfBand.frequencyHz,
            gainDb = settings.airShelfBand.gainDb,
            q = settings.airShelfBand.qFactor,
            sampleRate = sampleRate
        )

        // Subsonics cleanup filter: removes < 25Hz rumbling to save amplifier power
        val subsonicFilter = BiquadFilter(
            type = BiquadFilter.Type.HIGH_PASS,
            frequency = 25.0f,
            gainDb = 0f,
            q = 0.707f,
            sampleRate = sampleRate
        )

        val stereoEnhancer = StereoEnhancer(
            sampleRate = sampleRate,
            monoCutoffHz = settings.monoSubFreqHz,
            widthFactor = settings.stereoWidthFactor
        )

        val saturatorL = HarmonicSaturator(settings.saturationDrive)
        val saturatorR = HarmonicSaturator(settings.saturationDrive)

        val limiter = DynamicLimiter(
            sampleRate = sampleRate,
            thresholdDb = settings.limiterThresholdDb,
            ceilingDb = settings.limiterCeilingDb,
            releaseMs = settings.limiterReleaseMs
        )

        val makeupLinear = 10.0f.pow(settings.makeupGainDb / 20.0f)
        val pairBuffer = FloatArray(2)
        var preventedClips = 0

        val progressStep = (totalFrames / 20).coerceAtLeast(1024)

        for (i in 0 until totalFrames) {
            val idx = i * channels
            var l = inSamples[idx]
            var r = if (channels > 1) inSamples[idx + 1] else l

            // 1. Subsonic filter
            l = subsonicFilter.processSample(l, 0)
            r = subsonicFilter.processSample(r, 1)

            // 2. 5-Band Parametric EQ
            l = subFilter.processSample(l, 0)
            r = subFilter.processSample(r, 1)

            l = kickFilter.processSample(l, 0)
            r = kickFilter.processSample(r, 1)

            l = mudFilter.processSample(l, 0)
            r = mudFilter.processSample(r, 1)

            l = snareFilter.processSample(l, 0)
            r = snareFilter.processSample(r, 1)

            l = airFilter.processSample(l, 0)
            r = airFilter.processSample(r, 1)

            // 3. Stereo enhancement & Mono-sub matrix
            if (channels > 1) {
                pairBuffer[0] = l
                pairBuffer[1] = r
                stereoEnhancer.processSample(pairBuffer)
                l = pairBuffer[0]
                r = pairBuffer[1]
            }

            // 4. Analog Harmonic Saturation
            l = saturatorL.processSample(l)
            r = saturatorR.processSample(r)

            // 5. Makeup Gain into Limiter
            l *= makeupLinear
            r *= makeupLinear

            // 6. Dynamic True Peak Limiter
            pairBuffer[0] = l
            pairBuffer[1] = r
            val prePeak = max(abs(l), abs(r))
            limiter.processSample(pairBuffer)
            val postPeak = max(abs(pairBuffer[0]), abs(pairBuffer[1]))
            if (prePeak >= 1.0f && postPeak <= 1.0f) {
                preventedClips++
            }

            outSamples[idx] = pairBuffer[0]
            if (channels > 1) {
                outSamples[idx + 1] = pairBuffer[1]
            }

            if (i % progressStep == 0) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                onProgress(i.toFloat() / totalFrames)
            }
        }
        onProgress(1.0f)

        val masteredTrack = inputTrack.copy(
            title = "${inputTrack.title} [Mastered]",
            samples = outSamples
        )

        val masteredAnalysis = LufsMeter.analyze(
            samples = outSamples,
            sampleRate = inputTrack.sampleRate,
            channels = inputTrack.channels
        )

        val result = MasteringResult(
            originalAnalysis = originalAnalysis,
            masteredAnalysis = masteredAnalysis,
            appliedSettings = settings,
            maxGainReductionDb = limiter.maxGainReductionDb,
            peakPreventedClips = preventedClips,
            dynamicRangeImprovementDb = originalAnalysis.dynamicRangeDb - masteredAnalysis.dynamicRangeDb,
            processingTimeMs = System.currentTimeMillis() - startTime
        )

        return Pair(masteredTrack, result)
    }
}
