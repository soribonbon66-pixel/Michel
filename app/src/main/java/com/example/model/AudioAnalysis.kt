package com.example.model

/**
 * Acoustic and loudness measurements extracted from audio buffer.
 */
data class AudioAnalysis(
    val integratedLufs: Float,       // e.g. -16.4 LUFS
    val shortTermMaxLufs: Float,     // Peak short-term window loudness
    val truePeakDbfs: Float,         // Peak level in dBFS (or dBTP)
    val dynamicRangeDb: Float,       // Crest factor / PLR (Peak to Loudness Ratio)
    val subBassEnergy: Float,        // 20Hz - 60Hz relative energy (0.0 .. 1.0)
    val bassEnergy: Float,           // 60Hz - 250Hz relative energy
    val lowMidEnergy: Float,         // 250Hz - 500Hz relative energy (mud zone)
    val midEnergy: Float,            // 500Hz - 2kHz relative energy
    val highMidEnergy: Float,        // 2kHz - 6kHz relative energy (kick & snare bite)
    val airEnergy: Float,            // 6kHz - 20kHz relative energy
    val dominantFrequencyHz: Float,  // Main fundamental frequency
    val hasClipping: Boolean         // Detected samples >= 0.999f
) {
    companion object {
        val EMPTY = AudioAnalysis(
            integratedLufs = -24.0f,
            shortTermMaxLufs = -20.0f,
            truePeakDbfs = -6.0f,
            dynamicRangeDb = 12.0f,
            subBassEnergy = 0.15f,
            bassEnergy = 0.20f,
            lowMidEnergy = 0.20f,
            midEnergy = 0.20f,
            highMidEnergy = 0.15f,
            airEnergy = 0.10f,
            dominantFrequencyHz = 55.0f,
            hasClipping = false
        )
    }
}
