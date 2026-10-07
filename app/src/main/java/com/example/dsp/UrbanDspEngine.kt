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

    /**
     * Step 4: Closed-Loop Feedback Auto-Correction (Medición del resultado y auto-corrección adaptativa).
     * Mide el audio resultante tras el máster inicial y corrige los parámetros aplicados para alcanzar con
     * precisión milimétrica la sonoridad objetivo (LUFS), margen True-Peak y respuesta en frecuencias.
     */
    fun autoCorrectSettings(
        currentSettings: MasteringSettings,
        targetPreset: PresetType,
        masteredAnalysis: AudioAnalysis,
        rawAnalysis: AudioAnalysis
    ): Pair<MasteringSettings, List<String>> {
        val correctionLog = mutableListOf<String>()
        val targetLufs = targetPreset.targetLufs
        val achievedLufs = masteredAnalysis.integratedLufs

        // 1. Error de sonoridad LUFS (Delta entre lo obtenido y la meta)
        val lufsError = targetLufs - achievedLufs
        val adjustedMakeupGain = (currentSettings.makeupGainDb + (lufsError * 0.85f)).coerceIn(0.0f, 15.0f)
        val adjustedThreshold = (-adjustedMakeupGain * 0.70f).coerceIn(-12.0f, -2.0f)

        if (abs(lufsError) >= 0.2f) {
            correctionLog.add(
                "Auto-corrección LUFS: Master inicial en ${"%.1f".format(achievedLufs)} LUFS (Objetivo: ${targetLufs} LUFS). " +
                        "Makeup gain ajustado de +${"%.1f".format(currentSettings.makeupGainDb)}dB a +${"%.1f".format(adjustedMakeupGain)}dB (Corrección: ${if (lufsError > 0) "+" else ""}${"%.1f".format(lufsError)} LU)"
            )
        }

        // 2. Control de margen True-Peak para evitar distorsión inter-sample
        val ceiling = targetPreset.truePeakCeilingDb
        val peakOvershoot = masteredAnalysis.truePeakDbfs - ceiling
        val adjustedCeiling = if (peakOvershoot > 0f) {
            val safeCeil = (ceiling - peakOvershoot - 0.2f).coerceAtLeast(-2.5f)
            correctionLog.add("Auto-corrección True-Peak: Pico de ${"%.2f".format(masteredAnalysis.truePeakDbfs)} dBTP sobrepasó techo. Techo reajustado a ${"%.1f".format(safeCeil)} dBTP")
            safeCeil
        } else {
            currentSettings.limiterCeilingDb
        }

        // 3. Compensación de pérdida de subgraves por limitación extrema
        var adjustedSubGain = currentSettings.subBassBand.gainDb
        if (masteredAnalysis.subBassEnergy < 0.26f && rawAnalysis.subBassEnergy >= 0.20f) {
            val subBoost = ((0.28f - masteredAnalysis.subBassEnergy) * 8.0f).coerceIn(0.2f, 1.8f)
            adjustedSubGain = (adjustedSubGain + subBoost).coerceIn(1.0f, 7.0f)
            correctionLog.add("Auto-corrección Sub-Bass: Recuperación de pegada en graves profundos (+${"%.1f".format(subBoost)}dB)")
        }

        // 4. Limpieza de resonancia turbia residual
        var adjustedMudGain = currentSettings.mudCutBand.gainDb
        if (masteredAnalysis.lowMidEnergy > 0.22f) {
            val extraCut = ((masteredAnalysis.lowMidEnergy - 0.20f) * 8.0f).coerceIn(0.3f, 1.5f)
            adjustedMudGain = (adjustedMudGain - extraCut).coerceIn(-6.5f, -0.5f)
            correctionLog.add("Auto-corrección Mud Scoop: Atenuación adicional a ${"%.1f".format(adjustedMudGain)}dB para mayor nitidez")
        }

        val totalLog = currentSettings.autoAdjustmentLog + correctionLog

        val correctedSettings = currentSettings.copy(
            makeupGainDb = adjustedMakeupGain,
            limiterThresholdDb = adjustedThreshold,
            limiterCeilingDb = adjustedCeiling,
            subBassBand = currentSettings.subBassBand.copy(gainDb = adjustedSubGain),
            mudCutBand = currentSettings.mudCutBand.copy(gainDb = adjustedMudGain),
            autoAdjustmentLog = totalLog
        )

        return Pair(correctedSettings, correctionLog)
    }
}
