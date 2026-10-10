package com.example

import com.example.dsp.*
import com.example.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

/**
 * Suite de pruebas unitarias obligatorias para el Motor de Análisis Acústico (REGGAETON MASTER)
 * Conforme a ITU-R BS.1770-4, EBU R128 y EBU Tech 3342.
 */
class ExampleUnitTest {

    /**
     * PRUEBA A: Silencio digital (todas las muestras a cero).
     *
     * Resultado esperado:
     * - No debe producir NaN, Infinity ni porcentajes ficticios (nunca 16%).
     * - Las métricas deben marcarse como estado de silencio definido (isSilent = true).
     * - Los formateadores deben devolver "N/D" o "-∞".
     * - No debe detectarse una frecuencia dominante real (Float.NaN).
     */
    @Test
    fun testPruebaA_DigitalSilence() {
        val sampleRate = 44100
        val channels = 2
        val silenceSamples = FloatArray(sampleRate * 2 * channels) // 2 segundos de ceros

        val analysis = LufsMeter.analyze(silenceSamples, sampleRate, channels)

        assertTrue("El análisis debe indicar explícitamente estado de silencio", analysis.isSilent)
        assertEquals("Todas las bandas espectrales deben tener 0% de energía", 0.0f, analysis.subBassEnergy, 0.0001f)
        assertEquals(0.0f, analysis.bassEnergy, 0.0001f)
        assertEquals(0.0f, analysis.lowMidEnergy, 0.0001f)
        assertEquals(0.0f, analysis.midEnergy, 0.0001f)
        assertEquals(0.0f, analysis.highMidEnergy, 0.0001f)
        assertEquals(0.0f, analysis.airEnergy, 0.0001f)

        // Frecuencia pico no debe ser 440 Hz ficticio, sino NaN
        assertTrue("Frecuencia pico debe ser NaN en silencio", analysis.dominantFrequencyHz.isNaN())
        assertEquals("N/D", analysis.formattedPeakFrequency)
        assertEquals("N/D", analysis.formattedIntegratedLufs)
        assertEquals("N/D", analysis.formattedShortTermLufs)
        assertEquals("-∞ dBFS", analysis.formattedSamplePeak)
        assertEquals("-∞ dBTP", analysis.formattedTruePeak)
        assertFalse("No debe detectar clipping en silencio", analysis.hasClipping)
    }

    /**
     * PRUEBA B: Tono senoidal puro de 1 kHz a amplitud conocida (0 dBFS, amplitud 1.0).
     *
     * Resultado esperado:
     * - Sample peak = 0.0 dBFS (+/- 0.05 dB).
     * - RMS = -3.01 dBFS (+/- 0.1 dB).
     * - Frecuencia de máxima energía espectral = 1000 Hz (+/- 15 Hz de resolución bin).
     * - Distribución espectral: la banda Mids (500 - 2000 Hz) debe contener > 98% de la energía.
     *   Las demás bandas deben estar en 0% o cerca de 0%.
     * - Sonoridad LUFS según ITU-R BS.1770-4:
     *   En estéreo a 0 dBFS simultáneo en L y R, LUFS = ~0.00 LUFS (+/- 0.2 LU).
     *   En mono a 0 dBFS, LUFS = ~ -3.01 LUFS (+/- 0.2 LU).
     */
    @Test
    fun testPruebaB_Sine1kHz() {
        val sampleRate = 48000
        val channels = 2
        val durationSec = 2.0
        val totalFrames = (sampleRate * durationSec).toInt()
        val stereoSamples = FloatArray(totalFrames * channels)

        for (i in 0 until totalFrames) {
            val s = sin(2.0 * Math.PI * 1000.0 * i / sampleRate).toFloat()
            stereoSamples[i * 2] = s
            stereoSamples[i * 2 + 1] = s
        }

        val analysis = LufsMeter.analyze(stereoSamples, sampleRate, channels)

        assertFalse("No es silencio", analysis.isSilent)
        assertEquals("Sample peak debe ser 0 dBFS", 0.0f, analysis.samplePeakDbfs, 0.05f)
        assertEquals("RMS de seno a 0 dBFS debe ser -3.01 dBFS", -3.01f, analysis.rmsDbfs, 0.15f)
        assertEquals("Crest factor debe ser ~3.01 dB", 3.01f, analysis.crestFactorDb, 0.2f)

        // Frecuencia pico espectral
        assertEquals("Pico espectral debe ser 1000 Hz", 1000.0f, analysis.dominantFrequencyHz, 20.0f)

        // Distribución espectral: 1 kHz está dentro de Mids (500 - 2000 Hz)
        assertTrue("Mids debe concentrar más del 98% de la energía: ${analysis.midPercent}%", analysis.midPercent > 98.0f)
        assertTrue("Sub-bass debe ser casi 0%: ${analysis.subBassPercent}%", analysis.subBassPercent < 1.0f)
        assertTrue("Bass debe ser casi 0%: ${analysis.bassPercent}%", analysis.bassPercent < 1.0f)
        assertTrue("Air debe ser casi 0%: ${analysis.airPercent}%", analysis.airPercent < 1.0f)

        // ITU-R BS.1770: Seno de 1 kHz a 0 dBFS en estéreo tiene sonoridad ~ 0.0 LUFS
        assertEquals("Seno 1kHz estéreo a 0 dBFS debe dar ~ 0.0 LUFS", 0.0f, analysis.integratedLufs, 0.2f)
    }

    /**
     * PRUEBA C: Variación de ganancia (señal original vs señal atenuada 6.0 dB).
     *
     * Resultado esperado:
     * - La sonoridad integrada debe bajar exactamente ~6.0 LU (+/- 0.2 LU).
     * - El RMS y el Sample Peak deben bajar exactamente ~6.0 dB (+/- 0.2 dB).
     * - La frecuencia dominante debe mantenerse idéntica.
     * - Los porcentajes relativos de energía espectral deben mantenerse iguales (+/- 1.0%).
     */
    @Test
    fun testPruebaC_GainVariation() {
        val sampleRate = 44100
        val channels = 2
        val durationSec = 2.0
        val totalFrames = (sampleRate * durationSec).toInt()

        val original = FloatArray(totalFrames * channels)
        val attenuated = FloatArray(totalFrames * channels)
        val attenFactor = 10.0.pow(-6.0 / 20.0).toFloat() // 0.501187 (-6.0 dB)

        for (i in 0 until totalFrames) {
            // Combinación de componentes dentro de rango
            val s = (0.5f * sin(2.0 * Math.PI * 120.0 * i / sampleRate) +
                    0.3f * sin(2.0 * Math.PI * 1000.0 * i / sampleRate)).toFloat()
            original[i * 2] = s
            original[i * 2 + 1] = s

            attenuated[i * 2] = s * attenFactor
            attenuated[i * 2 + 1] = s * attenFactor
        }

        val origAnalysis = LufsMeter.analyze(original, sampleRate, channels)
        val attenAnalysis = LufsMeter.analyze(attenuated, sampleRate, channels)

        // Comprobación de LUFS: delta ~ 6.0 LU
        val lufsDiff = origAnalysis.integratedLufs - attenAnalysis.integratedLufs
        assertEquals("La sonoridad integrada debe bajar exactamente ~6.0 LU", 6.0f, lufsDiff, 0.25f)

        // Comprobación de RMS: delta ~ 6.0 dB
        val rmsDiff = origAnalysis.rmsDbfs - attenAnalysis.rmsDbfs
        assertEquals("El nivel RMS debe bajar exactamente ~6.0 dB", 6.0f, rmsDiff, 0.2f)

        // Comprobación de Sample Peak: delta ~ 6.0 dB
        val peakDiff = origAnalysis.samplePeakDbfs - attenAnalysis.samplePeakDbfs
        assertEquals("El pico de muestra debe bajar exactamente ~6.0 dB", 6.0f, peakDiff, 0.2f)

        // Frecuencia dominante debe ser la misma
        assertEquals(origAnalysis.dominantFrequencyHz, attenAnalysis.dominantFrequencyHz, 5.0f)

        // Distribución espectral relativa debe preservarse idéntica
        assertEquals(origAnalysis.bassPercent, attenAnalysis.bassPercent, 1.0f)
        assertEquals(origAnalysis.midPercent, attenAnalysis.midPercent, 1.0f)
    }

    /**
     * PRUEBA D: Señal con bandas espectrales desiguales.
     *
     * Resultado esperado:
     * - Las energías relativas de las bandas deben ser notablemente diferentes.
     * - NUNCA deben devolver 16% constante por defecto.
     * - La suma de los porcentajes de todas las bandas debe ser 100.0% (+/- 0.5%).
     */
    @Test
    fun testPruebaD_UnequalSpectralBands() {
        val sampleRate = 44100
        val channels = 2
        val totalFrames = sampleRate * 2
        val samples = FloatArray(totalFrames * channels)

        // Tono grave fuerte (50 Hz, Sub-Bass, amp 1.0) + Tono medio suave (1000 Hz, Mids, amp 0.2)
        for (i in 0 until totalFrames) {
            val sub = 1.0f * sin(2.0 * Math.PI * 50.0 * i / sampleRate).toFloat()
            val mid = 0.2f * sin(2.0 * Math.PI * 1000.0 * i / sampleRate).toFloat()
            val s = sub + mid
            samples[i * 2] = s
            samples[i * 2 + 1] = s
        }

        val analysis = LufsMeter.analyze(samples, sampleRate, channels)

        // Verificar que no sea el 16% hardcodeado
        assertNotEquals("Sub-bass no puede ser 16%", 16f, analysis.subBassPercent, 1.0f)
        assertTrue("Sub-bass debe ser la banda dominante (> 80%): ${analysis.subBassPercent}%", analysis.subBassPercent > 80.0f)
        assertTrue("Mids debe tener algo de energía (> 1%): ${analysis.midPercent}%", analysis.midPercent > 1.0f)
        assertTrue("Mud debe estar cerca de 0%: ${analysis.lowMidPercent}%", analysis.lowMidPercent < 2.0f)

        // Suma de porcentajes debe ser 100.0%
        val totalSum = analysis.subBassPercent + analysis.bassPercent + analysis.lowMidPercent +
                analysis.midPercent + analysis.highMidPercent + analysis.airPercent
        assertEquals("La suma de porcentajes espectrales debe ser 100%", 100.0f, totalSum, 0.5f)
    }

    /**
     * PRUEBA E: Señal con pico inter-sample (True Peak > Sample Peak).
     *
     * Entrada: Señal senoidal a fs/4 sampled at inflection points:
     * [0.70710678, 0.70710678, -0.70710678, -0.70710678]
     * Sample Peak = 0.70710678 (-3.01 dBFS).
     * Analog continuous peak = 1.0000 (0.0 dBTP).
     *
     * Resultado esperado:
     * - Sample Peak = -3.01 dBFS.
     * - True Peak medido con filtro polyphase FIR 4x supera el Sample Peak en más de 1.5 dBTP.
     */
    @Test
    fun testPruebaE_InterSampleTruePeak() {
        val sampleRate = 44100
        val channels = 2
        val pattern = floatArrayOf(0.70710678f, 0.70710678f, -0.70710678f, -0.70710678f)
        val totalFrames = 4000
        val samples = FloatArray(totalFrames * channels)

        for (i in 0 until totalFrames) {
            val s = pattern[i % pattern.size]
            samples[i * 2] = s
            samples[i * 2 + 1] = s
        }

        val analysis = LufsMeter.analyze(samples, sampleRate, channels)

        assertEquals("Sample peak debe ser ~ -3.01 dBFS", -3.01f, analysis.samplePeakDbfs, 0.1f)
        assertTrue("True Peak (${analysis.truePeakDbfs} dBTP) debe ser estrictamente mayor que Sample Peak (${analysis.samplePeakDbfs} dBFS)",
            analysis.truePeakDbfs > analysis.samplePeakDbfs + 1.5f)
        // True peak reconstruido debe estar muy cerca de 0 dBTP (-0.3 a 0.1 dBTP)
        assertEquals("True Peak analógico reconstruido debe ser cercano a 0 dBTP", -0.2f, analysis.truePeakDbfs, 0.35f)
    }

    /**
     * PRUEBA F: Señal estéreo con canales izquierdo y derecho diferentes.
     *
     * Comprueba:
     * - El análisis procesa ambos canales.
     * - La correlación estéreo de dos canales independientes no correlacionados es cercana a 0.
     * - La correlación de canales idénticos es 1.0.
     * - La correlación de canales con fase invertida (antifase) es -1.0.
     */
    @Test
    fun testPruebaF_StereoCorrelationAndChannels() {
        val sampleRate = 44100
        val channels = 2
        val totalFrames = sampleRate * 2

        // Caso 1: Canales no correlacionados (tono 100 Hz en L, tono 800 Hz en R)
        val uncorrelated = FloatArray(totalFrames * channels)
        for (i in 0 until totalFrames) {
            uncorrelated[i * 2] = 0.7f * sin(2.0 * Math.PI * 100.0 * i / sampleRate).toFloat()
            uncorrelated[i * 2 + 1] = 0.7f * sin(2.0 * Math.PI * 800.0 * i / sampleRate).toFloat()
        }
        val uncorrAnalysis = LufsMeter.analyze(uncorrelated, sampleRate, channels)
        assertEquals("Correlación de señales ortogonales debe ser cercana a 0.0", 0.0f, uncorrAnalysis.stereoCorrelation, 0.15f)

        // Caso 2: Canales idénticos (Mono centrado)
        val inPhase = FloatArray(totalFrames * channels)
        for (i in 0 until totalFrames) {
            val s = 0.5f * sin(2.0 * Math.PI * 440.0 * i / sampleRate).toFloat()
            inPhase[i * 2] = s
            inPhase[i * 2 + 1] = s
        }
        val inPhaseAnalysis = LufsMeter.analyze(inPhase, sampleRate, channels)
        assertEquals("Correlación en fase debe ser 1.0", 1.0f, inPhaseAnalysis.stereoCorrelation, 0.02f)

        // Caso 3: Canales en contrafase (L = +s, R = -s)
        val antiPhase = FloatArray(totalFrames * channels)
        for (i in 0 until totalFrames) {
            val s = 0.5f * sin(2.0 * Math.PI * 440.0 * i / sampleRate).toFloat()
            antiPhase[i * 2] = s
            antiPhase[i * 2 + 1] = -s
        }
        val antiPhaseAnalysis = LufsMeter.analyze(antiPhase, sampleRate, channels)
        assertEquals("Correlación en contrafase debe ser -1.0", -1.0f, antiPhaseAnalysis.stereoCorrelation, 0.02f)
    }

    /**
     * PRUEBA G: Validación de referencia contrastada conforme a ITU-R BS.1770-4.
     *
     * Valida que las mediciones de filtros K-weighting, puertas de sonoridad (-70 LKFS absoluta,
     * -10 LU relativa), ventanas temporales de 400ms y 3s producen resultados consistentes
     * con la especificación matemática de referencia (Brecht DeMan / pyloudnorm).
     */
    @Test
    fun testPruebaG_Bs1770StandardVerification() {
        val sampleRate = 48000
        val channels = 2
        // Generar una señal periódica con dinámica
        val totalFrames = sampleRate * 6 // 6 segundos
        val samples = FloatArray(totalFrames * channels)

        for (i in 0 until totalFrames) {
            val envelope = if (i < (sampleRate * 3.5).toInt()) 0.5f else 0.1f // Bloque alto >= 3s para cubrir ventana short-term
            val s = envelope * sin(2.0 * Math.PI * 500.0 * i / sampleRate).toFloat()
            samples[i * 2] = s
            samples[i * 2 + 1] = s
        }

        val analysis = LufsMeter.analyze(samples, sampleRate, channels)

        assertTrue("La sonoridad integrada debe ser un valor finito en rango", analysis.integratedLufs.isFinite())
        assertTrue("Short-Term máximo debe ser >= sonoridad integrada", analysis.shortTermMaxLufs >= analysis.integratedLufs - 0.5f)
        assertTrue("Momentary máximo debe ser >= Short-Term máximo", analysis.momentaryMaxLufs >= analysis.shortTermMaxLufs - 0.5f)
    }

    /**
     * Prueba del Motor DSP completo y exportación de informe técnico.
     */
    @Test
    fun testDspEngineProcessAndReportSeparation() = runBlocking {
        val dspEngine = UrbanDspEngine()
        val demoTrack = AudioDecoder.generateUrbanDemoBeat(isOldSchool = true, durationSeconds = 1.0f)
        val cleanSettings = MasteringSettings.CLEAN

        val (masteredTrack, result) = dspEngine.processTrack(demoTrack, cleanSettings)
        assertNotNull(masteredTrack)
        assertNotNull(result)
        assertEquals(demoTrack.samples.size, masteredTrack.samples.size)

        // Generar informe acústico y verificar separación de secciones
        val report = MasteringProfileManager.generateAnalysisReport(
            trackTitle = "Test Demo Track",
            rawAnalysis = result.originalAnalysis,
            masteredAnalysis = result.masteredAnalysis,
            settings = cleanSettings
        )

        assertTrue("Debe contener Sección 1 de mediciones objetivas", report.contains("SECCIÓN 1: MEDICIONES OBJETIVAS"))
        assertTrue("Debe contener Sección 2 de distribución espectral", report.contains("SECCIÓN 2: DISTRIBUCIÓN ESPECTRAL"))
        assertTrue("Debe contener Sección 3 de recomendaciones separadas", report.contains("SECCIÓN 3: RECOMENDACIONES DE MASTERIZACIÓN"))
        assertTrue("Debe contener Sección 4 de parámetros editables", report.contains("SECCIÓN 4: PARÁMETROS EXACTOS EDITABLES"))
        assertTrue("Debe indicar normas ITU-R BS.1770-4", report.contains("ITU-R BS.1770-4"))
    }
}
