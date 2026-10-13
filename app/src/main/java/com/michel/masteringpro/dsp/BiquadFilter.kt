package com.michel.masteringpro.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** RBJ Audio EQ Cookbook biquad, Direct Form II Transposed, with independent L/R state. */
class BiquadFilter(
    private val type: Type,
    frequencyHz: Float,
    gainDb: Float,
    q: Float,
    sampleRate: Int
) {
    enum class Type { PEAKING, LOW_SHELF, HIGH_SHELF, HIGH_PASS }

    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0
    private val z1 = DoubleArray(2)
    private val z2 = DoubleArray(2)

    init {
        val fs = sampleRate.toDouble().coerceAtLeast(8_000.0)
        val f = frequencyHz.toDouble().coerceIn(10.0, fs * 0.49)
        val w0 = 2.0 * PI * f / fs
        val cosW0 = cos(w0)
        val sinW0 = sin(w0)
        val qSafe = q.toDouble().coerceIn(0.1, 18.0)
        val a = 10.0.pow(gainDb.toDouble() / 40.0)
        var aa0: Double
        var bb0: Double
        var bb1: Double
        var bb2: Double
        var aa1: Double
        var aa2: Double

        when (type) {
            Type.PEAKING -> {
                val alpha = sinW0 / (2.0 * qSafe)
                bb0 = 1.0 + alpha * a
                bb1 = -2.0 * cosW0
                bb2 = 1.0 - alpha * a
                aa0 = 1.0 + alpha / a
                aa1 = -2.0 * cosW0
                aa2 = 1.0 - alpha / a
            }
            Type.LOW_SHELF, Type.HIGH_SHELF -> {
                val slope = 1.0
                val alpha = sinW0 / 2.0 * sqrt((a + 1.0 / a) * (1.0 / slope - 1.0) + 2.0)
                val beta = 2.0 * sqrt(a) * alpha
                if (type == Type.LOW_SHELF) {
                    bb0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + beta)
                    bb1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
                    bb2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - beta)
                    aa0 = (a + 1.0) + (a - 1.0) * cosW0 + beta
                    aa1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
                    aa2 = (a + 1.0) + (a - 1.0) * cosW0 - beta
                } else {
                    bb0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + beta)
                    bb1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
                    bb2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - beta)
                    aa0 = (a + 1.0) - (a - 1.0) * cosW0 + beta
                    aa1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
                    aa2 = (a + 1.0) - (a - 1.0) * cosW0 - beta
                }
            }
            Type.HIGH_PASS -> {
                val alpha = sinW0 / (2.0 * qSafe)
                bb0 = (1.0 + cosW0) / 2.0
                bb1 = -(1.0 + cosW0)
                bb2 = (1.0 + cosW0) / 2.0
                aa0 = 1.0 + alpha
                aa1 = -2.0 * cosW0
                aa2 = 1.0 - alpha
            }
        }

        b0 = bb0 / aa0
        b1 = bb1 / aa0
        b2 = bb2 / aa0
        a1 = aa1 / aa0
        a2 = aa2 / aa0
    }

    fun processSample(input: Float, channel: Int): Float {
        val ch = channel.coerceIn(0, 1)
        val x = input.toDouble()
        val output = b0 * x + z1[ch]
        z1[ch] = b1 * x - a1 * output + z2[ch]
        z2[ch] = b2 * x - a2 * output
        return output.toFloat()
    }
}
