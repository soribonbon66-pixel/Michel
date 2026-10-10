package com.example

import com.example.dsp.*
import com.example.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

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
        // Feed high-amplitude transient (above 0dBFS)
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
    fun testAudioTrackAnalysisAndNeutralDefaults() {
        val dspEngine = UrbanDspEngine()
        val demoTrack = AudioDecoder.generateUrbanDemoBeat(isOldSchool = true, durationSeconds = 1.0f)
        val analysis = dspEngine.analyzeTrack(demoTrack)

        // Track should have measured LUFS in typical unmastered range
        assertTrue("LUFS should be below -10: ${analysis.integratedLufs}", analysis.integratedLufs < -10.0f)
        assertEquals(2, demoTrack.channels)

        // Verify default settings are neutral (CLEAN_NEUTRAL)
        val defaultSettings = MasteringSettings.CLEAN
        assertEquals(PresetType.CLEAN_NEUTRAL, defaultSettings.presetType)
        assertEquals(0.0f, defaultSettings.subBassBand.gainDb, 0.001f)
        assertEquals(0.0f, defaultSettings.kickPunchBand.gainDb, 0.001f)
        assertEquals(0.0f, defaultSettings.mudCutBand.gainDb, 0.001f)
        assertEquals(0.0f, defaultSettings.snarePresenceBand.gainDb, 0.001f)
        assertEquals(0.0f, defaultSettings.airShelfBand.gainDb, 0.001f)
        assertEquals(0.0f, defaultSettings.saturationDrive, 0.001f)
    }

    @Test
    fun testDspEngineProcessTrack() = runBlocking {
        val dspEngine = UrbanDspEngine()
        val demoTrack = AudioDecoder.generateUrbanDemoBeat(isOldSchool = true, durationSeconds = 0.5f)
        val cleanSettings = MasteringSettings.CLEAN

        val (masteredTrack, result) = dspEngine.processTrack(demoTrack, cleanSettings)
        assertNotNull(masteredTrack)
        assertNotNull(result)
        assertEquals(demoTrack.samples.size, masteredTrack.samples.size)
        assertTrue(result.masteredAnalysis.integratedLufs.isFinite())
    }

    @Test
    fun testProfileParserAndSerializer() {
        val templateText = MasteringProfileManager.generateEditableTxtTemplate()
        assertTrue(templateText.contains("limiter_ceiling_db=-0.2"))
        assertTrue(templateText.contains("makeup_gain_db=5.5"))

        val parseResult = MasteringProfileManager.parseProfile(templateText)
        assertTrue(parseResult.isSuccess)
        val parsedSettings = parseResult.getOrNull()
        assertNotNull(parsedSettings)
        assertEquals(-0.2f, parsedSettings!!.limiterCeilingDb, 0.01f)
        assertEquals(5.5f, parsedSettings.makeupGainDb, 0.01f)
    }
}

