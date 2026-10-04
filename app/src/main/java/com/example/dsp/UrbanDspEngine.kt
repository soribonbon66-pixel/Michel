package com.example.dsp

import com.example.model.*
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
     * Step 2: Self-Adjusting Algorithm (Lógica auto-ajustable).
     * Modifies the base preset dynamically based on the track's real acoustic deficiencies.
     */
    fun autoCalibrateSettings(
        preset: PresetType,
        analysis: AudioAnalysis
    ): MasteringSettings {
        val log = mutableListOf<String>()

        // 1. Sub-bass Auto-tuning (40Hz - 60Hz):
        // Target sub-energy ratio in reggaeton is ~0.24 to 0.32
        val targetSubEnergy = 0.28f
        val subDeficit = targetSubEnergy - analysis.subBassEnergy
        val autoSubGain = (preset.subBassBaseGainDb + (subDeficit * 12.0f)).coerceIn(1.0f, 6.5f)
        log.add(
            "Sub-Bass [${preset.subBassFreqHz.toInt()}Hz]: Ajustado de +${preset.subBassBaseGainDb}dB a " +
                    "+${"%.1f".format(autoSubGain)}dB (Energía detectada: ${(analysis.subBassEnergy * 100).toInt()}%)"
        )

        // 2. Mud Cleanup (250Hz - 350Hz):
        // If boxy mid clutter is elevated (> 0.22 ratio), sharpen mud cut to free dynamic headroom for sub
        val isMuddy = analysis.lowMidEnergy > 0.20f
        val autoMudGain = if (isMuddy) {
            val extraCut = ((analysis.lowMidEnergy - 0.20f) * 15.0f).coerceIn(0.5f, 2.5f)
            val totalCut = preset.mudCutBaseGainDb - extraCut
            log.add("Mud Scoop [${preset.mudCutFreqHz.toInt()}Hz]: Atenuación incrementada a ${"%.1f".format(totalCut)}dB para eliminar resonancia turbia")
            totalCut
        } else {
            preset.mudCutBaseGainDb
        }

        // 3. Kick Punch & Dembow Snare Attack (85-100Hz & 3.2-4.0kHz):
        // If high-mid presence is weak (< 0.14 ratio), boost attack to cut through modern club speakers
        val snareDeficit = (0.18f - analysis.highMidEnergy).coerceAtLeast(0f)
        val autoSnareGain = (preset.snarePresenceBaseGainDb + snareDeficit * 8.0f).coerceIn(1.5f, 5.5f)
        log.add(
            "Ataque Redoblante/Caja [${preset.snarePresenceFreqHz.toInt()}Hz]: Realce dinámico fijado en " +
                    "+${"%.1f".format(autoSnareGain)}dB para definición de ritmo"
        )

        val autoKickGain = preset.kickPunchBaseGainDb

        // 4. Air & Sheen (11kHz - 13kHz)
        val airDeficit = (0.14f - analysis.airEnergy).coerceAtLeast(0f)
        val autoAirGain = (preset.airShelfBaseGainDb + airDeficit * 6.0f).coerceIn(1.0f, 4.5f)

        // 5. Intelligent Loudness & Limiter Threshold Calculation:
        // Delta between measured LUFS and target preset LUFS
        val currentLufs = analysis.integratedLufs.coerceIn(-30.0f, -5.0f)
        val targetLufs = preset.targetLufs
        val neededGain = (targetLufs - currentLufs).coerceIn(0.5f, 12.0f)

        // Limiter threshold and makeup gain
        val autoMakeupGain = neededGain * 0.85f // Conservative driver into limiter
        val autoLimiterThreshold = (-autoMakeupGain * 0.70f).coerceIn(-10.0f, -3.0f)
        val autoCeiling = preset.truePeakCeilingDb

        log.add(
            "Sonoridad Objetivo: ${preset.targetLufs} LUFS (Actual: ${"%.1f".format(analysis.integratedLufs)} LUFS, " +
                    "Ganancia Makeup: +${"%.1f".format(autoMakeupGain)}dB)"
        )
        log.add(
            "Limitador True-Peak: Umbral dinámico ${"%.1f".format(autoLimiterThreshold)}dB, Techo Ceiling " +
                    "${preset.truePeakCeilingDb} dBTP (Protección anti-clipping activada)"
        )

        return MasteringSettings(
            presetType = preset,
            subBassBand = EqBand("sub", "Sub-Bass 40-60Hz", preset.subBassFreqHz, autoSubGain, 1.2f),
            kickPunchBand = EqBand("kick", "Kick Punch", preset.kickPunchFreqHz, autoKickGain, 1.4f),
            mudCutBand = EqBand("mud", "Mud Scoop", preset.mudCutFreqHz, autoMudGain, 1.8f),
            snarePresenceBand = EqBand("snare", "Snare Bite", preset.snarePresenceFreqHz, autoSnareGain, 1.2f),
            airShelfBand = EqBand("air", "Air Highs", preset.airShelfFreqHz, autoAirGain, 0.7f),
            saturationDrive = preset.saturationDrive,
            monoSubFreqHz = preset.monoSubCutoffHz,
            stereoWidthFactor = 1.15f,
            makeupGainDb = autoMakeupGain,
            limiterThresholdDb = autoLimiterThreshold,
            limiterCeilingDb = autoCeiling,
            limiterReleaseMs = preset.limiterReleaseMs,
            isAutoCalibrated = true,
            autoAdjustmentLog = log
        )
    }

    /**
     * Step 3: Render processed audio through the complete mastering chain.
     */
    fun processTrack(
        inputTrack: AudioTrackData,
        settings: MasteringSettings,
        onProgress: (Float) -> Unit = {}
    ): Pair<AudioTrackData, MasteringResult> {
        val startTime = System.currentTimeMillis()
        val originalAnalysis = analyzeTrack(inputTrack)

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
