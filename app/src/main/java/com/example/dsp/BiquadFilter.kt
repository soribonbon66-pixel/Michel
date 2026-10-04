package com.example.dsp

import kotlin.math.*

/**
 * High-performance 2nd-order Direct Form II Transposed Biquad IIR Filter.
 * Implements Robert Bristow-Johnson Audio EQ Cookbook formulas.
 */
class BiquadFilter(
    private var type: Type = Type.PEAK,
    private var frequency: Float = 1000f,
    private var gainDb: Float = 0f,
    private var q: Float = 1.0f,
    private var sampleRate: Float = 44100f
) {
    enum class Type {
        LOW_SHELF,
        HIGH_SHELF,
        PEAK,
        HIGH_PASS,
        LOW_PASS
    }

    // Normalized filter coefficients
    private var b0 = 1.0f
    private var b1 = 0.0f
    private var b2 = 0.0f
    private var a1 = 0.0f
    private var a2 = 0.0f

    // Filter delay states for stereo processing [channel 0, channel 1]
    private val z1 = FloatArray(2)
    private val z2 = FloatArray(2)

    init {
        recalculate()
    }

    fun configure(
        type: Type = this.type,
        frequency: Float = this.frequency,
        gainDb: Float = this.gainDb,
        q: Float = this.q,
        sampleRate: Float = this.sampleRate
    ) {
        this.type = type
        this.frequency = frequency.coerceIn(20f, (sampleRate / 2f) - 100f)
        this.gainDb = gainDb.coerceIn(-24f, 24f)
        this.q = q.coerceIn(0.1f, 10f)
        this.sampleRate = sampleRate
        recalculate()
    }

    fun resetState() {
        z1[0] = 0f
        z1[1] = 0f
        z2[0] = 0f
        z2[1] = 0f
    }

    private fun recalculate() {
        val omega = (2.0 * Math.PI * frequency.toDouble() / sampleRate.toDouble()).toFloat()
        val sinOmega = sin(omega.toDouble()).toFloat()
        val cosOmega = cos(omega.toDouble()).toFloat()
        val alpha = sinOmega / (2.0f * q)
        val a = 10.0.pow(gainDb.toDouble() / 40.0).toFloat()

        var a0 = 1.0f
        var tb0 = 1.0f
        var tb1 = 0.0f
        var tb2 = 0.0f
        var ta1 = 0.0f
        var ta2 = 0.0f

        when (type) {
            Type.PEAK -> {
                tb0 = 1.0f + alpha * a
                tb1 = -2.0f * cosOmega
                tb2 = 1.0f - alpha * a
                a0 = 1.0f + alpha / a
                ta1 = -2.0f * cosOmega
                ta2 = 1.0f - alpha / a
            }
            Type.LOW_SHELF -> {
                val sqrtA = sqrt(a.toDouble()).toFloat()
                val twoSqrtAAlpha = 2.0f * sqrtA * alpha
                tb0 = a * ((a + 1.0f) - (a - 1.0f) * cosOmega + twoSqrtAAlpha)
                tb1 = 2.0f * a * ((a - 1.0f) - (a + 1.0f) * cosOmega)
                tb2 = a * ((a + 1.0f) - (a - 1.0f) * cosOmega - twoSqrtAAlpha)
                a0 = (a + 1.0f) + (a - 1.0f) * cosOmega + twoSqrtAAlpha
                ta1 = -2.0f * ((a - 1.0f) + (a + 1.0f) * cosOmega)
                ta2 = (a + 1.0f) + (a - 1.0f) * cosOmega - twoSqrtAAlpha
            }
            Type.HIGH_SHELF -> {
                val sqrtA = sqrt(a.toDouble()).toFloat()
                val twoSqrtAAlpha = 2.0f * sqrtA * alpha
                tb0 = a * ((a + 1.0f) + (a - 1.0f) * cosOmega + twoSqrtAAlpha)
                tb1 = -2.0f * a * ((a - 1.0f) + (a + 1.0f) * cosOmega)
                tb2 = a * ((a + 1.0f) + (a - 1.0f) * cosOmega - twoSqrtAAlpha)
                a0 = (a + 1.0f) - (a - 1.0f) * cosOmega + twoSqrtAAlpha
                ta1 = 2.0f * ((a - 1.0f) - (a + 1.0f) * cosOmega)
                ta2 = (a + 1.0f) - (a - 1.0f) * cosOmega - twoSqrtAAlpha
            }
            Type.HIGH_PASS -> {
                tb0 = (1.0f + cosOmega) / 2.0f
                tb1 = -(1.0f + cosOmega)
                tb2 = (1.0f + cosOmega) / 2.0f
                a0 = 1.0f + alpha
                ta1 = -2.0f * cosOmega
                ta2 = 1.0f - alpha
            }
            Type.LOW_PASS -> {
                tb0 = (1.0f - cosOmega) / 2.0f
                tb1 = 1.0f - cosOmega
                tb2 = (1.0f - cosOmega) / 2.0f
                a0 = 1.0f + alpha
                ta1 = -2.0f * cosOmega
                ta2 = 1.0f - alpha
            }
        }

        b0 = tb0 / a0
        b1 = tb1 / a0
        b2 = tb2 / a0
        a1 = ta1 / a0
        a2 = ta2 / a0
    }

    /**
     * Process a single audio sample for a given channel (0 or 1).
     * Direct Form II Transposed prevents numerical overflow.
     */
    fun processSample(input: Float, channel: Int = 0): Float {
        val out = b0 * input + z1[channel]
        z1[channel] = b1 * input - a1 * out + z2[channel]
        z2[channel] = b2 * input - a2 * out
        return out
    }

    /**
     * Compute magnitude response in dB at a specific frequency for graphical visualizer.
     */
    fun getGainDbAt(freq: Float): Float {
        val omega = 2.0 * Math.PI * freq.toDouble() / sampleRate.toDouble()
        val cos1 = cos(omega)
        val cos2 = cos(2.0 * omega)
        val sin1 = sin(omega)
        val sin2 = sin(2.0 * omega)

        val numReal = b0.toDouble() + b1.toDouble() * cos1 + b2.toDouble() * cos2
        val numImag = -b1.toDouble() * sin1 - b2.toDouble() * sin2

        val denReal = 1.0 + a1.toDouble() * cos1 + a2.toDouble() * cos2
        val denImag = -a1.toDouble() * sin1 - a2.toDouble() * sin2

        val numMag2 = numReal * numReal + numImag * numImag
        val denMag2 = denReal * denReal + denImag * denImag

        val mag2 = if (denMag2 > 0.0) numMag2 / denMag2 else 1.0
        return (10.0 * log10(mag2.coerceAtLeast(1e-12))).toFloat()
    }
}
