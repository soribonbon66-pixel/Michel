package com.example

import com.example.dsp.MasteringProfileManager
import com.example.model.AudioAnalysis
import com.example.model.MasteringSettings
import org.junit.Assert.*
import org.junit.Test

class ProfileManagerTest {

    @Test
    fun testParseTxtProfile() {
        val txt = """
            # Perfil de prueba
            sub_bass_gain_db = 6.2
            snare_presence_gain_db = 3.8
            saturation_drive = 0.45
            limiter_ceiling_db = -0.15
            makeup_gain_db = 6.0
        """.trimIndent()

        val result = MasteringProfileManager.parseProfile(txt)
        assertTrue(result.isSuccess)
        val settings = result.getOrThrow()

        assertEquals(6.2f, settings.subBassBand.gainDb, 0.01f)
        assertEquals(3.8f, settings.snarePresenceBand.gainDb, 0.01f)
        assertEquals(0.45f, settings.saturationDrive, 0.01f)
        assertEquals(-0.15f, settings.limiterCeilingDb, 0.01f)
        assertEquals(6.0f, settings.makeupGainDb, 0.01f)
    }

    @Test
    fun testParseJsonProfile() {
        val json = """
            {
              "profile_name": "Test Perreo Master",
              "eq": {
                "sub_bass": { "frequency_hz": 48.0, "gain_db": 5.5, "q": 1.3 },
                "snare_presence": { "frequency_hz": 3600.0, "gain_db": 4.0, "q": 1.1 }
              },
              "analog": {
                "saturation_drive": 0.50,
                "mono_sub_freq_hz": 95.0
              },
              "limiter": {
                "makeup_gain_db": 7.0,
                "limiter_ceiling_db": -0.25
              }
            }
        """.trimIndent()

        val result = MasteringProfileManager.parseProfile(json)
        assertTrue(result.isSuccess)
        val settings = result.getOrThrow()

        assertEquals(5.5f, settings.subBassBand.gainDb, 0.01f)
        assertEquals(48.0f, settings.subBassBand.frequencyHz, 0.01f)
        assertEquals(4.0f, settings.snarePresenceBand.gainDb, 0.01f)
        assertEquals(0.50f, settings.saturationDrive, 0.01f)
        assertEquals(-0.25f, settings.limiterCeilingDb, 0.01f)
    }

    @Test
    fun testGenerateAnalysisReportAndTemplate() {
        val template = MasteringProfileManager.generateEditableTxtTemplate()
        assertTrue(template.contains("sub_bass_gain_db"))
        assertTrue(template.contains("limiter_ceiling_db"))

        val report = MasteringProfileManager.generateAnalysisReport(
            trackTitle = "Bichota_Mix",
            rawAnalysis = AudioAnalysis.EMPTY,
            masteredAnalysis = AudioAnalysis.EMPTY.copy(integratedLufs = -8.5f),
            settings = MasteringSettings()
        )
        assertTrue(report.contains("Bichota_Mix"))
        assertTrue(report.contains("-8.50 LUFS"))
    }
}
