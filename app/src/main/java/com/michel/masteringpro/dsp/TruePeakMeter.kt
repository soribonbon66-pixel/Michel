package com.michel.masteringpro.dsp

import kotlin.math.abs
import kotlin.math.log10

/** 4x Catmull–Rom interpolation estimate of inter-sample true peak. */
object TruePeakMeter {
    data class Result(val samplePeak: Float, val truePeak: Float)

    fun measure(samples: FloatArray, channels: Int): Result {
        val frames = samples.size / channels
        if (frames == 0) return Result(0f, 0f)
        var samplePeak = 0.0
        var truePeak = 0.0
        for (frame in 0 until frames) {
            for (channel in 0 until channels) {
                val index = frame * channels + channel
                val y1 = samples[index].toDouble()
                samplePeak = maxOf(samplePeak, abs(y1))
                truePeak = maxOf(truePeak, abs(y1))
                val y0 = samples[((frame - 1).coerceAtLeast(0)) * channels + channel].toDouble()
                val y2 = samples[((frame + 1).coerceAtMost(frames - 1)) * channels + channel].toDouble()
                val y3 = samples[((frame + 2).coerceAtMost(frames - 1)) * channels + channel].toDouble()
                val a = 2.0 * y1
                val b = -y0 + y2
                val c = 2.0 * y0 - 5.0 * y1 + 4.0 * y2 - y3
                val d = -y0 + 3.0 * y1 - 3.0 * y2 + y3
                for (phase in 1..3) {
                    val t = phase * 0.25
                    val interpolated = 0.5 * (a + b * t + c * t * t + d * t * t * t)
                    truePeak = maxOf(truePeak, abs(interpolated))
                }
            }
        }
        return Result(samplePeak.toFloat(), truePeak.toFloat())
    }

    fun dbfs(amplitude: Float): Float = if (amplitude <= 0f) -120f else (20.0 * log10(amplitude.toDouble())).toFloat()
}
