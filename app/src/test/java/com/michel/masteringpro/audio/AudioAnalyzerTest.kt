package com.michel.masteringpro.audio

import com.michel.masteringpro.model.AudioTrackData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioAnalyzerTest {
    @Test
    fun silenceIsReportedWithoutNanOrFalseClipping() {
        val track = AudioTrackData("silence.wav", 48_000, 2, FloatArray(48_000 * 2))
        val result = AudioAnalyzer.analyze(track)

        assertTrue(result.isSilent)
        assertEquals(-70f, result.integratedLufs, 0.001f)
        assertEquals(0L, result.clippingCount)
        assertTrue(result.truePeakDbtp.isFinite())
        assertEquals("—", result.estimatedKey)
    }

    @Test
    fun analysisFindsLoudnessAndAStableMidbandTone() {
        val sampleRate = 48_000
        val seconds = 3
        val samples = FloatArray(sampleRate * seconds * 2)
        for (frame in 0 until sampleRate * seconds) {
            val tone = (0.1 * sin(2.0 * PI * 1_000.0 * frame / sampleRate)).toFloat()
            samples[frame * 2] = tone
            samples[frame * 2 + 1] = tone
        }
        val result = AudioAnalyzer.analyze(AudioTrackData("tone.wav", sampleRate, 2, samples))

        assertTrue(result.integratedLufs in -40f..-10f)
        assertTrue(result.samplePeakDbfs < -15f)
        assertEquals(0L, result.clippingCount)
        assertEquals(1f, result.stereoCorrelation, 0.001f)
        assertTrue(result.monoCompatibilityPercent > 99f)
        assertTrue(result.spectrum.magnitudesDb.size == 2049)
    }
}
