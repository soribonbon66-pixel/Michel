package com.michel.masteringpro.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/** Fast stereo-linked sample-peak limiter with immediate attack and smoothed release. */
class Limiter(
    sampleRate: Int,
    ceilingDb: Float,
    releaseMs: Float
) {
    private val ceiling = 10.0.pow(ceilingDb.coerceIn(-12f, 0f).toDouble() / 20.0).toFloat()
    private val releaseCoefficient = exp(-1.0 / (releaseMs.coerceAtLeast(1f) * 0.001 * sampleRate.coerceAtLeast(8_000))).toFloat()
    private var gain = 1f

    var outputLeft: Float = 0f
        private set
    var outputRight: Float = 0f
        private set
    var maxGainReductionDb: Float = 0f
        private set

    fun processFrame(left: Float, right: Float, channels: Int) {
        val peak = if (channels > 1) maxOf(abs(left), abs(right)) else abs(left)
        val wanted = if (peak > ceiling) ceiling / peak else 1f
        gain = if (wanted < gain) wanted else releaseCoefficient * gain + (1f - releaseCoefficient) * wanted
        maxGainReductionDb = maxOf(maxGainReductionDb, (-20.0 * kotlin.math.log10(gain.coerceAtLeast(1e-8f))).toFloat())
        // Guard sample peaks even on hard transients; the post-render true-peak pass adds margin.
        outputLeft = (left * gain).coerceIn(-ceiling, ceiling)
        outputRight = if (channels > 1) (right * gain).coerceIn(-ceiling, ceiling) else outputLeft
    }
}
