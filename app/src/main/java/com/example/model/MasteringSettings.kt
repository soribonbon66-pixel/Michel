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

data class MasteringSettings(
    val presetType: PresetType = PresetType.OLD_SCHOOL_PERREO,
    // 5-Band Parametric EQ
    val subBassBand: EqBand = EqBand("sub", "Sub-Bass", 50.0f, 3.8f, 1.2f),
    val kickPunchBand: EqBand = EqBand("kick", "Kick Punch", 85.0f, 2.4f, 1.4f),
    val mudCutBand: EqBand = EqBand("mud", "Mud Scoop", 280.0f, -2.2f, 1.8f),
    val snarePresenceBand: EqBand = EqBand("snare", "Snare Bite", 3200.0f, 2.0f, 1.2f),
    val airShelfBand: EqBand = EqBand("air", "Air Highs", 11000.0f, 1.8f, 0.7f),

    // Analog Color & Dynamics
    val saturationDrive: Float = 0.25f,
    val monoSubFreqHz: Float = 90.0f,
    val stereoWidthFactor: Float = 1.15f,

    // Intelligent Peak Limiter
    val makeupGainDb: Float = 4.5f,
    val limiterThresholdDb: Float = -7.5f,
    val limiterCeilingDb: Float = -0.3f,
    val limiterReleaseMs: Float = 60.0f,

    // Metadata about self-adjustment applied
    val isAutoCalibrated: Boolean = false,
    val autoAdjustmentLog: List<String> = emptyList()
) {
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
