package com.example.dsp

import com.example.model.MasteringSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class ProcessingRegressionTest {
    @Test
    fun neutralMultibandRecombinesWithoutChangingSignal() {
        val settings = MasteringSettings.CLEAN
        val compressor = MultibandCompressor(48000f, settings.multibandBands)
        val pair = FloatArray(2)
        var maxError = 0f
        for (i in 0 until 12000) {
            val sampleL = (0.6 * sin(2.0 * Math.PI * 997.0 * i / 48000.0)).toFloat()
            val sampleR = sampleL * 0.7f
            pair[0] = sampleL
            pair[1] = sampleR
            compressor.processSample(pair)
            maxError = maxOf(maxError, abs(pair[0] - sampleL), abs(pair[1] - sampleR))
        }
        assertTrue("Neutral multiband split should recombine transparently", maxError < 1e-5f)
    }

    @Test
    fun lookaheadLimiterBoundsTransientAfterLatencyTrim() {
        val ceilingDb = -0.2f
        val limiter = DynamicLimiter(48000f, 0f, ceilingDb, 60f)
        val frames = 12000
        val latency = limiter.latencyFrames
        val rendered = FloatArray(frames + latency)
        val pair = FloatArray(2)
        for (i in 0 until frames) {
            val sample = if (i == 2000) 1.25f else (0.25 * sin(i * 0.04)).toFloat()
            pair[0] = sample
            pair[1] = sample
            limiter.processSample(pair)
            rendered[i] = pair[0]
        }
        repeat(latency) { j ->
            pair[0] = 0f
            pair[1] = 0f
            limiter.processSample(pair)
            rendered[frames + j] = pair[0]
        }
        var peak = 0f
        for (i in latency until latency + frames) peak = maxOf(peak, abs(rendered[i]))
        val ceiling = Math.pow(10.0, ceilingDb / 20.0).toFloat()
        assertTrue("Output sample peak must not exceed the configured ceiling", peak <= ceiling + 1e-5f)
        assertTrue("Transient should trigger measurable gain reduction", limiter.maxGainReductionDb > 0.1f)
        assertEquals("Limiter must preserve the configured latency for trimming", (0.003f * 48000f).toInt(), latency)
    }
}
