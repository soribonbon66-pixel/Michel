package com.example

import com.example.dsp.*
import com.example.model.AudioTrackData
import com.example.model.PresetType
import com.example.viewmodel.AudioProcessingViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @Test
    fun testAudioProcessingViewModelPresetAndBands() {
        val viewModel = AudioProcessingViewModel()
        assertEquals(PresetType.OLD_SCHOOL_PERREO, viewModel.state.value.selectedPreset)

        // Select Dembow Oscuro
        viewModel.selectPreset(PresetType.DEMBOW_OSCURO, autoCalibrate = false)
        assertEquals(PresetType.DEMBOW_OSCURO, viewModel.state.value.selectedPreset)
        assertEquals(-8.0f, viewModel.state.value.targetLufs, 0.01f)

        // Update band gain
        viewModel.updateBandGain("sub", 5.5f)
        assertEquals(5.5f, viewModel.state.value.eqSettings.subBassBand.gainDb, 0.01f)

        // Update limiter ceiling
        viewModel.setLimiterCeiling(-0.15f)
        assertEquals(-0.15f, viewModel.state.value.limiterCeilingDb, 0.01f)
    }

    @Test
    fun testMultibandCompressorHighPrecisionControls() {
        val viewModel = AudioProcessingViewModel()
        val lowBand = viewModel.state.value.multibandBands.first { it.id == "low" }

        // Test high-precision gain setting
        viewModel.updateCompressorGain("low", 3.2f)
        assertEquals(3.2f, viewModel.state.value.multibandBands.first { it.id == "low" }.gainDb, 0.01f)

        // Test micro-stepping nudge
        viewModel.nudgeCompressorGain("low", 0.1f)
        assertEquals(3.3f, viewModel.state.value.multibandBands.first { it.id == "low" }.gainDb, 0.01f)

        // Test threshold control
        viewModel.updateCompressorThreshold("low", -18.5f)
        assertEquals(-18.5f, viewModel.state.value.multibandBands.first { it.id == "low" }.thresholdDb, 0.01f)

        // Test ratio control
        viewModel.updateCompressorRatio("low", 4.2f)
        assertEquals(4.2f, viewModel.state.value.multibandBands.first { it.id == "low" }.ratio, 0.01f)

        // Test band bypass toggle
        assertFalse(viewModel.state.value.multibandBands.first { it.id == "low" }.isBypassed)
        viewModel.toggleCompressorBandBypass("low")
        assertTrue(viewModel.state.value.multibandBands.first { it.id == "low" }.isBypassed)
    }
}
