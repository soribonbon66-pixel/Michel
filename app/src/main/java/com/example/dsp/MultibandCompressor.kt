package com.example.dsp

import com.example.model.CompressorBand
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Three-band, stereo-linked compressor using complementary one-pole splits.
 * The bands sum back to the input before their individual dynamics/makeup gain.
 * Crossovers are currently fixed at 250 Hz and 4.5 kHz to match the UI labels.
 */
class MultibandCompressor(
    private val sampleRate: Float,
    bands: List<CompressorBand>
) {
    private data class BandState(
        val settings: CompressorBand,
        var envelope: Float = 0f,
        var gainReductionDb: Float = 0f,
        var attackCoeff: Float = 0f,
        var releaseCoeff: Float = 0f,
        var makeupLinear: Float = 1f
    )

    private val low = BandState(bands.firstOrNull { it.id == "low" } ?: defaultBand("low", "Low Band", 1.5f))
    private val mid = BandState(bands.firstOrNull { it.id == "mid" } ?: defaultBand("mid", "Mid Band", 1.5f))
    private val high = BandState(bands.firstOrNull { it.id == "high" } ?: defaultBand("high", "High Band", 1.2f))

    private val lowAlpha = onePoleAlpha(250f)
    private val highAlpha = onePoleAlpha(4500f)
    private val lowState = FloatArray(2)
    private val upperLowState = FloatArray(2)

    init {
        configureState(low)
        configureState(mid)
        configureState(high)
    }

    fun reset() {
        lowState.fill(0f)
        upperLowState.fill(0f)
        low.envelope = 0f; mid.envelope = 0f; high.envelope = 0f
        low.gainReductionDb = 0f; mid.gainReductionDb = 0f; high.gainReductionDb = 0f
    }

    /** Processes one stereo frame in-place. */
    fun processSample(pair: FloatArray) {
        val left = finiteOrZero(pair[0])
        val right = finiteOrZero(pair[1])
        val lowL = lowPass(left, 0, lowAlpha, lowState)
        val lowR = lowPass(right, 1, lowAlpha, lowState)
        val residualL = left - lowL
        val residualR = right - lowR
        val midL = lowPass(residualL, 0, highAlpha, upperLowState)
        val midR = lowPass(residualR, 1, highAlpha, upperLowState)
        val highL = residualL - midL
        val highR = residualR - midR

        val lowPair = compressBand(low, lowL, lowR)
        val midPair = compressBand(mid, midL, midR)
        val highPair = compressBand(high, highL, highR)
        pair[0] = finiteOrZero(lowPair[0] + midPair[0] + highPair[0])
        pair[1] = finiteOrZero(lowPair[1] + midPair[1] + highPair[1])
    }

    fun gainReductionDb(): Map<String, Float> = mapOf(
        "low" to low.gainReductionDb,
        "mid" to mid.gainReductionDb,
        "high" to high.gainReductionDb
    )

    private fun compressBand(state: BandState, left: Float, right: Float): FloatArray {
        if (state.settings.isBypassed) return floatArrayOf(left, right)
        val detector = max(abs(left), abs(right))
        val threshold = state.settings.thresholdDb.coerceIn(-60f, 0f)
        val ratio = state.settings.ratio.coerceIn(1f, 20f)
        val targetEnvelope = detector
        val coeff = if (targetEnvelope > state.envelope) state.attackCoeff else state.releaseCoeff
        state.envelope = coeff * state.envelope + (1f - coeff) * targetEnvelope
        val envelopeDb = 20f * log10(max(state.envelope, 1e-9f))
        val smoothedOver = envelopeDb - threshold
        val smoothedReduction = if (smoothedOver > 0f && ratio > 1f) smoothedOver * (1f - 1f / ratio) else 0f
        // The detector is stereo-linked to preserve the stereo image.
        state.gainReductionDb = min(36f, max(0f, smoothedReduction))
        val gain = 10f.pow((-state.gainReductionDb + state.settings.gainDb.coerceIn(-12f, 12f)) / 20f)
        return floatArrayOf(left * gain, right * gain)
    }

    private fun configureState(state: BandState) {
        val attack = state.settings.attackMs.coerceIn(0.1f, 200f)
        val release = state.settings.releaseMs.coerceIn(5f, 2000f)
        state.attackCoeff = exp(-1f / (attack * 0.001f * sampleRate.coerceAtLeast(8000f)))
        state.releaseCoeff = exp(-1f / (release * 0.001f * sampleRate.coerceAtLeast(8000f)))
        state.makeupLinear = 10f.pow(state.settings.gainDb.coerceIn(-12f, 12f) / 20f)
    }

    private fun lowPass(input: Float, channel: Int, alpha: Float, state: FloatArray): Float {
        state[channel] += alpha * (input - state[channel])
        return state[channel]
    }

    private fun onePoleAlpha(cutoff: Float): Float =
        (1f - exp((-2.0 * Math.PI * cutoff / sampleRate.coerceAtLeast(8000f)).toFloat())).coerceIn(0.0001f, 0.99f)

    private fun finiteOrZero(value: Float): Float = if (value.isFinite()) value else 0f

    companion object {
        private fun defaultBand(id: String, name: String, ratio: Float) = CompressorBand(
            id = id, name = name, frequencyRange = "", gainDb = 0f,
            thresholdDb = 0f, ratio = ratio, attackMs = 20f, releaseMs = 100f
        )
    }
}
