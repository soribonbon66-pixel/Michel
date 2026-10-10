package com.example.model

/**
 * 5-Band Parametric EQ + Dynamics configuration applied by the DSP chain.
 */
data class EqBand(
    val id: String,
    val name: String,
    val frequencyHz: Float,
    val gainDb: Float,
    val qFactor: Float = 1.0f
)

/**
 * Multiband Compressor band with high-precision parameters.
 */
data class CompressorBand(
    val id: String,
    val name: String,
    val frequencyRange: String,
    val gainDb: Float,        // -12.0 dB to +12.0 dB
    val thresholdDb: Float,   // -40.0 dB to 0.0 dB
    val ratio: Float,         // 1.0:1 to 20.0:1
    val attackMs: Float = 20.0f,
    val releaseMs: Float = 100.0f,
    val isBypassed: Boolean = false,
    val gainReductionDb: Float = 0.0f
)

data class MasteringSettings(
    val presetType: PresetType = PresetType.CLEAN_NEUTRAL,
    // 5-Band Parametric EQ (Default Flat 0.0 dB)
    val subBassBand: EqBand = EqBand("sub", "Sub-Bass", 50.0f, 0.0f, 1.2f),
    val kickPunchBand: EqBand = EqBand("kick", "Kick Punch", 85.0f, 0.0f, 1.4f),
    val mudCutBand: EqBand = EqBand("mud", "Mud Scoop", 280.0f, 0.0f, 1.8f),
    val snarePresenceBand: EqBand = EqBand("snare", "Snare Bite", 3200.0f, 0.0f, 1.2f),
    val airShelfBand: EqBand = EqBand("air", "Air Highs", 11000.0f, 0.0f, 0.7f),

    // Multiband Dynamics (Default Neutral 0 dB)
    val multibandBands: List<CompressorBand> = listOf(
        CompressorBand("low", "Low Band (Sub & Kick)", "20 Hz - 250 Hz", 0.0f, 0.0f, 1.5f, 30.0f, 80.0f),
        CompressorBand("mid", "Mid Band (Vocals & Snare)", "250 Hz - 4.5 kHz", 0.0f, 0.0f, 1.5f, 15.0f, 120.0f),
        CompressorBand("high", "High Band (Air & Cymbals)", "4.5 kHz - 20 kHz", 0.0f, 0.0f, 1.2f, 10.0f, 90.0f)
    ),

    // Analog Color & Dynamics (Default Neutral)
    val saturationDrive: Float = 0.0f,
    val monoSubFreqHz: Float = 90.0f,
    val stereoWidthFactor: Float = 1.0f,

    // Intelligent Peak Limiter (Transparent by default)
    val makeupGainDb: Float = 0.0f,
    val limiterThresholdDb: Float = 0.0f,
    val limiterCeilingDb: Float = -0.2f,
    val limiterReleaseMs: Float = 60.0f,

    // Metadata about adjustments
    val isAutoCalibrated: Boolean = false,
    val autoAdjustmentLog: List<String> = emptyList()
) {
    companion object {
        val CLEAN = MasteringSettings(
            presetType = PresetType.CLEAN_NEUTRAL,
            subBassBand = EqBand("sub", "Sub-Bass", 50.0f, 0.0f, 1.2f),
            kickPunchBand = EqBand("kick", "Kick Punch", 85.0f, 0.0f, 1.4f),
            mudCutBand = EqBand("mud", "Mud Scoop", 280.0f, 0.0f, 1.8f),
            snarePresenceBand = EqBand("snare", "Snare Bite", 3200.0f, 0.0f, 1.2f),
            airShelfBand = EqBand("air", "Air Highs", 11000.0f, 0.0f, 0.7f),
            saturationDrive = 0.0f,
            monoSubFreqHz = 90.0f,
            stereoWidthFactor = 1.0f,
            makeupGainDb = 0.0f,
            limiterThresholdDb = 0.0f,
            limiterCeilingDb = -0.2f,
            limiterReleaseMs = 60.0f,
            isAutoCalibrated = false,
            autoAdjustmentLog = emptyList()
        )
    }

    fun getAllBands(): List<EqBand> = listOf(
        subBassBand,
        kickPunchBand,
        mudCutBand,
        snarePresenceBand,
        airShelfBand
    )
}

data class MasteringResult(
    val originalAnalysis: AudioAnalysis,
    val masteredAnalysis: AudioAnalysis,
    val appliedSettings: MasteringSettings,
    val maxGainReductionDb: Float,
    val peakPreventedClips: Int,
    val dynamicRangeImprovementDb: Float,
    val processingTimeMs: Long
)
