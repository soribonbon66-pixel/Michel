package com.michel.masteringpro.model

/** Averages from 4096-point Hann-windowed FFT frames. Values are dB relative to the strongest bin. */
data class SpectrumData(
    val sampleRate: Int,
    val magnitudesDb: FloatArray
)

enum class IssueSeverity { INFO, WARNING, CRITICAL }

data class AnalysisIssue(
    val message: String,
    val severity: IssueSeverity
)

data class AnalysisResult(
    val integratedLufs: Float,
    val truePeakDbtp: Float,
    val samplePeakDbfs: Float,
    val loudnessRangeLu: Float,
    val rmsDbfs: Float,
    val crestFactorDb: Float,
    val dynamicRangeDb: Float,
    val clippingCount: Long,
    val clippingPercent: Float,
    val lowBandVsReferenceDb: Float,
    val bodyBandVsReferenceDb: Float,
    val presenceVsReferenceDb: Float,
    val airVs1kDb: Float,
    val hasBassDeficit: Boolean,
    val hasBodyExcess: Boolean,
    val hasPresenceExcess: Boolean,
    val hasAirDeficit: Boolean,
    val stereoCorrelation: Float,
    val stereoWidthDb: Float,
    val monoCompatibilityPercent: Float,
    val channelBalanceDb: Float,
    val estimatedBpm: Float?,
    val estimatedKey: String,
    val spectrum: SpectrumData,
    val issues: List<AnalysisIssue>,
    val isSilent: Boolean
)
