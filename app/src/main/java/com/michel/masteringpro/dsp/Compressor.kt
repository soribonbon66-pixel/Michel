package com.michel.masteringpro.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow

/** Stereo-linked feed-forward peak compressor with attack/release envelope smoothing. */
class Compressor(
    sampleRate: Int,
    private val thresholdDb: Float,
    private val ratio: Float,
    attackMs: Float,
    releaseMs: Float
) {
    private val sampleRateSafe = sampleRate.coerceAtLeast(8_000)
    private val attackCoefficient = exp(-1.0 / (attackMs.coerceAtLeast(0.1f) * 0.001 * sampleRateSafe)).toFloat()
    private val releaseCoefficient = exp(-1.0 / (releaseMs.coerceAtLeast(1f) * 0.001 * sampleRateSafe)).toFloat()
    private val ratioSafe = ratio.coerceAtLeast(1f)
    private var envelope = 0f
    private var smoothedGain = 1f

    var outputLeft: Float = 0f
        private set
    var outputRight: Float = 0f
        private set
    var maxGainReductionDb: Float = 0f
        private set

    fun processFrame(left: Float, right: Float, channels: Int) {
        val detector = if (channels > 1) maxOf(abs(left), abs(right)) else abs(left)
        val coefficient = if (detector > envelope) attackCoefficient else releaseCoefficient
        envelope = coefficient * envelope + (1f - coefficient) * detector
        val levelDb = 20f * log10(envelope.coerceAtLeast(1e-8f))
        val overDb = levelDb - thresholdDb
        val desiredDb = if (overDb > 0f) -overDb * (1f - 1f / ratioSafe) else 0f
        val desiredGain = 10.0.pow(desiredDb.toDouble() / 20.0).toFloat()
        val gainCoefficient = if (desiredGain < smoothedGain) attackCoefficient else releaseCoefficient
        smoothedGain = gainCoefficient * smoothedGain + (1f - gainCoefficient) * desiredGain
        maxGainReductionDb = maxOf(maxGainReductionDb, -20f * log10(smoothedGain.coerceAtLeast(1e-8f)))
        outputLeft = left * smoothedGain
        outputRight = if (channels > 1) right * smoothedGain else outputLeft
    }
}
