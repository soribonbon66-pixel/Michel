package com.example.model

/**
 * Acoustic and loudness measurements extracted from an audio buffer.
 * Fully compliant with ITU-R BS.1770-4 and EBU R128 / EBU Tech 3342.
 */
data class AudioAnalysis(
    val integratedLufs: Float,          // ITU-R BS.1770-4 Gated Integrated Loudness (LUFS)
    val shortTermMaxLufs: Float,        // Maximum 3-second short-term loudness (LUFS)
    val momentaryMaxLufs: Float = integratedLufs, // Maximum 400ms momentary loudness (LUFS)
    val samplePeakDbfs: Float = 0.0f,   // Maximum raw sample peak level (dBFS)
    val truePeakDbfs: Float = 0.0f,     // 4x oversampled true-peak level (dBTP)
    val rmsDbfs: Float = -24.0f,        // Unweighted RMS level of active audio (dBFS)
    val crestFactorDb: Float = 12.0f,   // Crest factor = Sample Peak (dBFS) - RMS (dBFS)
    val loudnessRangeLu: Float = 6.0f,  // Loudness Range (LRA) per EBU Tech 3342 (LU)
    val dynamicRangeDb: Float = crestFactorDb, // PLR / Dynamic Crest Range (dB)
    val subBassEnergy: Float = 0.0f,    // 20Hz - 60Hz relative energy ratio (0.0 .. 1.0)
    val bassEnergy: Float = 0.0f,       // 60Hz - 250Hz relative energy ratio (0.0 .. 1.0)
    val lowMidEnergy: Float = 0.0f,     // 250Hz - 500Hz relative energy ratio (0.0 .. 1.0)
    val midEnergy: Float = 0.0f,        // 500Hz - 2kHz relative energy ratio (0.0 .. 1.0)
    val highMidEnergy: Float = 0.0f,    // 2kHz - 6kHz relative energy ratio (0.0 .. 1.0)
    val airEnergy: Float = 0.0f,        // 6kHz - 20kHz relative energy ratio (0.0 .. 1.0)
    val dominantFrequencyHz: Float = Float.NaN, // Global spectral peak frequency (Hz), NaN if silent
    val stereoCorrelation: Float = 1.0f,// Pearson correlation between L and R (-1.0 .. +1.0)
    val hasClipping: Boolean = false,   // Detected samples >= 0.999f or true peak >= 0.0 dBTP
    val isSilent: Boolean = false       // True if digital silence or below threshold
) {
    val subBassPercent: Float get() = subBassEnergy * 100f
    val bassPercent: Float get() = bassEnergy * 100f
    val lowMidPercent: Float get() = lowMidEnergy * 100f
    val midPercent: Float get() = midEnergy * 100f
    val highMidPercent: Float get() = highMidEnergy * 100f
    val airPercent: Float get() = airEnergy * 100f

    val formattedIntegratedLufs: String
        get() = if (isSilent || integratedLufs <= -70.0f) "N/D" else "%.1f LUFS".format(integratedLufs)

    val formattedShortTermLufs: String
        get() = if (isSilent || shortTermMaxLufs <= -70.0f) "N/D" else "%.1f LUFS".format(shortTermMaxLufs)

    val formattedMomentaryLufs: String
        get() = if (isSilent || momentaryMaxLufs <= -70.0f) "N/D" else "%.1f LUFS".format(momentaryMaxLufs)

    val formattedSamplePeak: String
        get() = if (isSilent) "-∞ dBFS" else "%.2f dBFS".format(samplePeakDbfs)

    val formattedTruePeak: String
        get() = if (isSilent) "-∞ dBTP" else "%.2f dBTP".format(truePeakDbfs)

    val formattedRms: String
        get() = if (isSilent) "-∞ dBFS" else "%.2f dBFS".format(rmsDbfs)

    val formattedCrestFactor: String
        get() = if (isSilent) "N/D" else "%.1f dB".format(crestFactorDb)

    val formattedLra: String
        get() = if (isSilent || loudnessRangeLu <= 0f) "N/D" else "%.1f LU".format(loudnessRangeLu)

    val formattedPeakFrequency: String
        get() = if (isSilent || dominantFrequencyHz.isNaN() || dominantFrequencyHz <= 0f) "N/D" else "${dominantFrequencyHz.toInt()} Hz"

    val formattedStereoCorrelation: String
        get() = if (isSilent) "N/D" else "%.2f".format(stereoCorrelation)

    companion object {
        val EMPTY = AudioAnalysis(
            integratedLufs = -70.0f,
            shortTermMaxLufs = -70.0f,
            momentaryMaxLufs = -70.0f,
            samplePeakDbfs = -90.0f,
            truePeakDbfs = -90.0f,
            rmsDbfs = -90.0f,
            crestFactorDb = 0.0f,
            loudnessRangeLu = 0.0f,
            dynamicRangeDb = 0.0f,
            subBassEnergy = 0.0f,
            bassEnergy = 0.0f,
            lowMidEnergy = 0.0f,
            midEnergy = 0.0f,
            highMidEnergy = 0.0f,
            airEnergy = 0.0f,
            dominantFrequencyHz = Float.NaN,
            stereoCorrelation = 0.0f,
            hasClipping = false,
            isSilent = true
        )
    }
}
