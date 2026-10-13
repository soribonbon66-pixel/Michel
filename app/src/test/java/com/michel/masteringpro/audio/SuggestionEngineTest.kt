package com.michel.masteringpro.audio

import com.michel.masteringpro.model.AnalysisResult
import com.michel.masteringpro.model.SpectrumData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {
    @Test
    fun derivesGainLimiterEqAndCompressionFromMeasurements() {
        val analysis = AnalysisResult(
            integratedLufs = -8.3f,
            truePeakDbtp = 0.2f,
            samplePeakDbfs = 0.1f,
            loudnessRangeLu = 10f,
            rmsDbfs = -10f,
            crestFactorDb = 10f,
            dynamicRangeDb = 8.5f,
            clippingCount = 3,
            clippingPercent = 0.1f,
            lowBandVsReferenceDb = -2f,
            bodyBandVsReferenceDb = 4f,
            presenceVsReferenceDb = 5f,
            airVs1kDb = -14f,
            hasBassDeficit = false,
            hasBodyExcess = true,
            hasPresenceExcess = true,
            hasAirDeficit = true,
            stereoCorrelation = 0.2f,
            stereoWidthDb = -2f,
            monoCompatibilityPercent = 55f,
            channelBalanceDb = 0f,
            estimatedBpm = 120f,
            estimatedKey = "C mayor",
            spectrum = SpectrumData(48_000, FloatArray(2049)),
            issues = emptyList(),
            isSilent = false
        )

        val suggested = SuggestionEngine.suggest(analysis)
        assertEquals(-5.7f, suggested.settings.gainDb, 0.01f)
        assertTrue(suggested.settings.limiterEnabled)
        assertTrue(suggested.settings.compressorEnabled)
        assertEquals(-2f, suggested.settings.bodyEqDb, 0.01f)
        assertEquals(-1.5f, suggested.settings.presenceEqDb, 0.01f)
        assertEquals(1.5f, suggested.settings.airEqDb, 0.01f)
        assertTrue(suggested.notes.any { it.contains("fase", ignoreCase = true) })
    }
}
