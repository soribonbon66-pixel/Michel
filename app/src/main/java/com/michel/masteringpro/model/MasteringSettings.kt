package com.michel.masteringpro.model

data class MasteringSettings(
    val gainDb: Float = 0f,
    val lowEqDb: Float = 0f,
    val bodyEqDb: Float = 0f,
    val presenceEqDb: Float = 0f,
    val airEqDb: Float = 0f,
    val compressorEnabled: Boolean = false,
    val compressorThresholdDb: Float = -18f,
    val compressorRatio: Float = 1.5f,
    val compressorAttackMs: Float = 30f,
    val compressorReleaseMs: Float = 100f,
    val limiterEnabled: Boolean = false,
    val limiterThresholdDb: Float = -1f,
    val limiterReleaseMs: Float = 50f,
    val targetLufs: Float = -14f
)

data class SuggestedSettings(
    val settings: MasteringSettings,
    val notes: List<String>
)

data class MasteringSession(
    val track: AudioTrackData,
    val analysis: AnalysisResult,
    val suggestion: SuggestedSettings,
    val settings: MasteringSettings = suggestion.settings
)
