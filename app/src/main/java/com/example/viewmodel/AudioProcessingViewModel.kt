package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dsp.LufsMeter
import com.example.dsp.UrbanDspEngine
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * UI State specifically for managing LUFS loudness analysis metrics,
 * parametric equalization presets, and high-precision Multiband Compressor parameters.
 */
data class AudioProcessingState(
    // 1. LUFS Loudness & Dynamics Analysis State
    val lufsAnalysis: AudioAnalysis = AudioAnalysis.EMPTY,
    val isAnalyzingLufs: Boolean = false,
    val targetLufs: Float = PresetType.OLD_SCHOOL_PERREO.targetLufs,
    val lufsDeltaToTarget: Float = 0.0f,
    val isLufsCompliant: Boolean = false,

    // 2. Preset & Parametric Equalizer Parameters
    val selectedPreset: PresetType = PresetType.OLD_SCHOOL_PERREO,
    val eqSettings: MasteringSettings = MasteringSettings(presetType = PresetType.OLD_SCHOOL_PERREO),
    val isAutoAdjusted: Boolean = false,
    val adjustmentDecisions: List<String> = emptyList(),

    // 3. High-Precision Multiband Compressor Parameters
    val multibandBands: List<CompressorBand> = listOf(
        CompressorBand("low", "Low Band (Sub & Kick)", "20 Hz - 250 Hz", 2.4f, -14.0f, 3.5f, 30.0f, 80.0f),
        CompressorBand("mid", "Mid Band (Vocals & Snare)", "250 Hz - 4.5 kHz", 0.8f, -18.0f, 2.5f, 15.0f, 120.0f),
        CompressorBand("high", "High Band (Air & Cymbals)", "4.5 kHz - 20 kHz", 1.6f, -16.0f, 2.0f, 10.0f, 90.0f)
    ),
    val selectedCompressorBandId: String = "low",

    // 4. Limiter & Master Saturation Parameters
    val makeupGainDb: Float = 4.5f,
    val limiterCeilingDb: Float = -0.3f,
    val limiterThresholdDb: Float = -7.5f,
    val saturationDrive: Float = 0.25f
)

/**
 * ViewModel dedicated to managing LUFS acoustic analysis state,
 * equalization preset parameters, and high-precision multiband compression.
 */
class AudioProcessingViewModel(
    private val dspEngine: UrbanDspEngine = UrbanDspEngine()
) : ViewModel() {

    private val _state = MutableStateFlow(AudioProcessingState())
    val state: StateFlow<AudioProcessingState> = _state.asStateFlow()

    fun selectCompressorBand(bandId: String) {
        _state.update { it.copy(selectedCompressorBandId = bandId) }
    }

    /**
     * Updates Gain (dB) for a specific compressor band with high precision (-12.0dB to +12.0dB).
     */
    fun updateCompressorGain(bandId: String, gainDb: Float) {
        val clamped = (Math.round(gainDb.coerceIn(-12.0f, 12.0f) * 10f) / 10f)
        _state.update { state ->
            val updatedBands = state.multibandBands.map { band ->
                if (band.id == bandId) band.copy(gainDb = clamped) else band
            }
            state.copy(
                multibandBands = updatedBands,
                eqSettings = state.eqSettings.copy(multibandBands = updatedBands)
            )
        }
    }

    /**
     * Updates Threshold (dB) for a specific compressor band (-40.0dB to 0.0dB).
     */
    fun updateCompressorThreshold(bandId: String, thresholdDb: Float) {
        val clamped = (Math.round(thresholdDb.coerceIn(-40.0f, 0.0f) * 10f) / 10f)
        _state.update { state ->
            val updatedBands = state.multibandBands.map { band ->
                if (band.id == bandId) band.copy(thresholdDb = clamped) else band
            }
            state.copy(
                multibandBands = updatedBands,
                eqSettings = state.eqSettings.copy(multibandBands = updatedBands)
            )
        }
    }

    /**
     * Updates Ratio for a specific compressor band (1.0:1 to 20.0:1).
     */
    fun updateCompressorRatio(bandId: String, ratio: Float) {
        val clamped = (Math.round(ratio.coerceIn(1.0f, 20.0f) * 10f) / 10f)
        _state.update { state ->
            val updatedBands = state.multibandBands.map { band ->
                if (band.id == bandId) band.copy(ratio = clamped) else band
            }
            state.copy(
                multibandBands = updatedBands,
                eqSettings = state.eqSettings.copy(multibandBands = updatedBands)
            )
        }
    }

    /**
     * Fine micro-adjustment nudging (e.g. +0.1dB or -0.1dB) for Gain.
     */
    fun nudgeCompressorGain(bandId: String, deltaDb: Float) {
        val band = _state.value.multibandBands.find { it.id == bandId } ?: return
        updateCompressorGain(bandId, band.gainDb + deltaDb)
    }

    /**
     * Fine micro-adjustment nudging for Threshold.
     */
    fun nudgeCompressorThreshold(bandId: String, deltaDb: Float) {
        val band = _state.value.multibandBands.find { it.id == bandId } ?: return
        updateCompressorThreshold(bandId, band.thresholdDb + deltaDb)
    }

    /**
     * Fine micro-adjustment nudging for Ratio.
     */
    fun nudgeCompressorRatio(bandId: String, deltaRatio: Float) {
        val band = _state.value.multibandBands.find { it.id == bandId } ?: return
        updateCompressorRatio(bandId, band.ratio + deltaRatio)
    }

    /**
     * Toggles bypass for an individual compressor band.
     */
    fun toggleCompressorBandBypass(bandId: String) {
        _state.update { state ->
            val updatedBands = state.multibandBands.map { band ->
                if (band.id == bandId) band.copy(isBypassed = !band.isBypassed) else band
            }
            state.copy(
                multibandBands = updatedBands,
                eqSettings = state.eqSettings.copy(multibandBands = updatedBands)
            )
        }
    }

    /**
     * Executes ITU-R BS.1770 LUFS analysis on an audio sample buffer.
     */
    fun analyzeAudioBuffer(
        samples: FloatArray,
        sampleRate: Int = 44100,
        channels: Int = 2,
        autoCalibratePreset: Boolean = true
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isAnalyzingLufs = true) }

            val analysis = withContext(Dispatchers.Default) {
                LufsMeter.analyze(
                    samples = samples,
                    sampleRate = sampleRate,
                    channels = channels
                )
            }

            val target = _state.value.selectedPreset.targetLufs
            val delta = target - analysis.integratedLufs
            val compliant = kotlin.math.abs(delta) <= 0.5f

            _state.update {
                it.copy(
                    lufsAnalysis = analysis,
                    isAnalyzingLufs = false,
                    targetLufs = target,
                    lufsDeltaToTarget = delta,
                    isLufsCompliant = compliant
                )
            }

            if (autoCalibratePreset) {
                applySelfAdjustingPreset(_state.value.selectedPreset, analysis)
            }
        }
    }

    /**
     * Selects an urban EQ preset and recalculates target LUFS and parameters.
     */
    fun selectPreset(preset: PresetType, autoCalibrate: Boolean = true) {
        val currentAnalysis = _state.value.lufsAnalysis
        val target = preset.targetLufs
        val delta = target - currentAnalysis.integratedLufs
        val compliant = kotlin.math.abs(delta) <= 0.5f

        _state.update {
            it.copy(
                selectedPreset = preset,
                targetLufs = target,
                lufsDeltaToTarget = delta,
                isLufsCompliant = compliant
            )
        }

        if (autoCalibrate && currentAnalysis != AudioAnalysis.EMPTY) {
            applySelfAdjustingPreset(preset, currentAnalysis)
        } else {
            val baseSettings = MasteringSettings(
                presetType = preset,
                subBassBand = EqBand("sub", "Sub-Bass 40-60Hz", preset.subBassFreqHz, preset.subBassBaseGainDb, 1.2f),
                kickPunchBand = EqBand("kick", "Kick Punch", preset.kickPunchFreqHz, preset.kickPunchBaseGainDb, 1.4f),
                mudCutBand = EqBand("mud", "Mud Scoop", preset.mudCutFreqHz, preset.mudCutBaseGainDb, 1.8f),
                snarePresenceBand = EqBand("snare", "Snare Bite", preset.snarePresenceFreqHz, preset.snarePresenceBaseGainDb, 1.2f),
                airShelfBand = EqBand("air", "Air Highs", preset.airShelfFreqHz, preset.airShelfBaseGainDb, 0.7f),
                saturationDrive = preset.saturationDrive,
                monoSubFreqHz = preset.monoSubCutoffHz,
                limiterCeilingDb = preset.truePeakCeilingDb,
                limiterReleaseMs = preset.limiterReleaseMs,
                isAutoCalibrated = false,
                autoAdjustmentLog = emptyList()
            )
            _state.update {
                it.copy(
                    eqSettings = baseSettings,
                    makeupGainDb = 0f,
                    limiterCeilingDb = preset.truePeakCeilingDb,
                    saturationDrive = preset.saturationDrive,
                    isAutoAdjusted = false,
                    adjustmentDecisions = emptyList()
                )
            }
        }
    }

    /**
     * Executes self-adjusting parametric calibration based on the current LUFS and spectral map.
     */
    fun autoCalibrate() {
        val analysis = _state.value.lufsAnalysis
        val preset = _state.value.selectedPreset
        applySelfAdjustingPreset(preset, analysis)
    }

    private fun applySelfAdjustingPreset(preset: PresetType, analysis: AudioAnalysis) {
        val adjustedSettings = dspEngine.autoCalibrateSettings(preset, analysis)
        _state.update {
            it.copy(
                eqSettings = adjustedSettings,
                makeupGainDb = adjustedSettings.makeupGainDb,
                limiterCeilingDb = adjustedSettings.limiterCeilingDb,
                limiterThresholdDb = adjustedSettings.limiterThresholdDb,
                saturationDrive = adjustedSettings.saturationDrive,
                isAutoAdjusted = true,
                adjustmentDecisions = adjustedSettings.autoAdjustmentLog
            )
        }
    }

    fun updateBandGain(bandId: String, gainDb: Float) {
        val currentSettings = _state.value.eqSettings
        val updated = when (bandId) {
            "sub" -> currentSettings.copy(subBassBand = currentSettings.subBassBand.copy(gainDb = gainDb))
            "kick" -> currentSettings.copy(kickPunchBand = currentSettings.kickPunchBand.copy(gainDb = gainDb))
            "mud" -> currentSettings.copy(mudCutBand = currentSettings.mudCutBand.copy(gainDb = gainDb))
            "snare" -> currentSettings.copy(snarePresenceBand = currentSettings.snarePresenceBand.copy(gainDb = gainDb))
            "air" -> currentSettings.copy(airShelfBand = currentSettings.airShelfBand.copy(gainDb = gainDb))
            else -> currentSettings
        }
        _state.update { it.copy(eqSettings = updated) }
    }

    fun setMakeupGain(gainDb: Float) {
        _state.update {
            it.copy(
                makeupGainDb = gainDb,
                eqSettings = it.eqSettings.copy(makeupGainDb = gainDb)
            )
        }
    }

    fun setLimiterCeiling(ceilingDb: Float) {
        _state.update {
            it.copy(
                limiterCeilingDb = ceilingDb,
                eqSettings = it.eqSettings.copy(limiterCeilingDb = ceilingDb)
            )
        }
    }

    fun setSaturationDrive(drive: Float) {
        _state.update {
            it.copy(
                saturationDrive = drive,
                eqSettings = it.eqSettings.copy(saturationDrive = drive)
            )
        }
    }

    fun resetToDefaults() {
        selectPreset(_state.value.selectedPreset, autoCalibrate = false)
    }
}
