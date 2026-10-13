package com.michel.masteringpro.audio

import com.michel.masteringpro.model.AnalysisResult
import com.michel.masteringpro.model.MasteringSettings
import com.michel.masteringpro.model.SuggestedSettings
import java.util.Locale

/** Rule-based offline suggestions. All values are derived from the measured track. */
object SuggestionEngine {
    fun suggest(analysis: AnalysisResult): SuggestedSettings {
        if (analysis.isSilent) {
            return SuggestedSettings(
                settings = MasteringSettings(),
                notes = listOf("No se proponen cambios: el análisis detectó silencio digital. Comprueba el archivo antes de masterizar.")
            )
        }
        val notes = mutableListOf<String>()
        val gain = if (analysis.integratedLufs > -12f || analysis.integratedLufs < -18f) {
            (-14f - analysis.integratedLufs).coerceIn(-24f, 24f)
        } else {
            0f
        }
        if (gain != 0f) {
            notes += "Ganancia de entrada ${gain.signedDb()} dB para acercar el track a -14 LUFS."
        } else {
            notes += "La sonoridad está dentro del margen de trabajo; no se propone ganancia inicial."
        }

        val limiterRequired = analysis.clippingCount > 0L
        val limiterSuggested = limiterRequired || analysis.truePeakDbtp > -1f
        if (limiterRequired) {
            notes += "Hay muestras recortadas: el limitador queda activado obligatoriamente."
        } else if (analysis.truePeakDbtp > -1f) {
            notes += "True Peak por encima de -1 dBTP: limitador a -1 dB con release de 50 ms."
        }

        val bodyGain = if (analysis.hasBodyExcess) -2f else 0f
        if (analysis.hasBodyExcess) notes += "Exceso en 200–400 Hz: Bell de -2 dB en 300 Hz, Q 1.2."

        val presenceGain = if (analysis.hasPresenceExcess) -1.5f else 0f
        if (analysis.hasPresenceExcess) notes += "Exceso de presencia entre 2–5 kHz: reducción de -1.5 dB en 3.5 kHz."

        val airGain = if (analysis.hasAirDeficit) 1.5f else 0f
        if (analysis.hasAirDeficit) notes += "Aire por debajo de -12 dB respecto a 1 kHz: shelf de +1.5 dB en 12 kHz."

        val compressorSuggested = analysis.loudnessRangeLu > 9f
        if (compressorSuggested) notes += "LRA superior a 9 LU: compresión suave 1.5:1, -18 dB, ataque 30 ms y release 100 ms."

        if (analysis.hasBassDeficit) {
            notes += "Se detecta poca energía bajo 80 Hz; revisa el balance de graves antes de realzar."
        }
        if (analysis.stereoCorrelation < 0.3f) {
            notes += "Problema de fase: correlación estéreo inferior a 0.3; revisa la compatibilidad mono."
        }

        return SuggestedSettings(
            settings = MasteringSettings(
                gainDb = gain,
                lowEqDb = 0f,
                bodyEqDb = bodyGain,
                presenceEqDb = presenceGain,
                airEqDb = airGain,
                compressorEnabled = compressorSuggested,
                compressorThresholdDb = -18f,
                compressorRatio = 1.5f,
                compressorAttackMs = 30f,
                compressorReleaseMs = 100f,
                limiterEnabled = limiterSuggested,
                limiterThresholdDb = -1f,
                limiterReleaseMs = 50f,
                targetLufs = -14f
            ),
            notes = notes
        )
    }

    private fun Float.signedDb(): String = String.format(Locale.US, "%+.1f", this)
}
