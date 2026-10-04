package com.example

import com.example.dsp.*
import com.example.model.AudioTrackData
import com.example.model.PresetType
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {

    @Test
    fun testBiquadPeakFilterGain() {
        val filter = BiquadFilter(
            type = BiquadFilter.Type.PEAK,
            frequency = 1000f,
            gainDb = 6.0f,
            q = 1.0f,
            sampleRate = 44100f
        )
        val gainAtCenter = filter.getGainDbAt(1000f)
        assertEquals(6.0f, gainAtCenter, 0.1f)
    }

    @Test
    fun testLimiterCeilingEnforcement() {
        val limiter = DynamicLimiter(
            sampleRate = 44100f,
            thresholdDb = -6.0f,
            ceilingDb = -0.3f,
            releaseMs = 50.0f
        )
        // Feed high-amplitude square/sine transient (above 0dBFS)
        val samplePair = floatArrayOf(2.5f, 2.5f)
        for (i in 0 until 500) {
            samplePair[0] = 2.0f
            samplePair[1] = 2.0f
            limiter.processSample(samplePair)
            // Ceiling must never be exceeded (approx -0.3 dB = 0.966)
            assertTrue("Sample exceeded ceiling: ${samplePair[0]}", samplePair[0] <= 1.0f)
            assertTrue("Sample exceeded ceiling: ${samplePair[1]}", samplePair[1] <= 1.0f)
        }
    }

    @Test
    fun testUrbanDspAutoCalibration() {
        val dspEngine = UrbanDspEngine()
        val demoTrack = AudioDecoder.generateUrbanDemoBeat(isOldSchool = true, durationSeconds = 1.0f)
        val analysis = dspEngine.analyzeTrack(demoTrack)

        // Track should have measured LUFS in typical unmastered range
        assertTrue("LUFS should be below -10: ${analysis.integratedLufs}", analysis.integratedLufs < -10.0f)

        // Auto-calibration should compute settings targeting preset LUFS
        val settings = dspEngine.autoCalibrateSettings(PresetType.OLD_SCHOOL_PERREO, analysis)
        assertTrue(settings.isAutoCalibrated)
        assertTrue(settings.autoAdjustmentLog.isNotEmpty())
        assertTrue(settings.makeupGainDb > 0f)
    }
}
