package com.example.dsp

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Manages import, export, and parsing of custom mastering profiles and acoustic analysis reports.
 * Supports both standard JSON (.json) and Human-Readable Key-Value (.txt) formats.
 */
object MasteringProfileManager {

    /**
     * Parses text content (either JSON or Key-Value text format) into MasteringSettings.
     */
    fun parseProfile(content: String): Result<MasteringSettings> {
        return runCatching {
            val trimmed = content.trim()
            if (trimmed.startsWith("{")) {
                parseJsonProfile(trimmed)
            } else {
                parseKeyValueProfile(trimmed)
            }
        }
    }

    private fun parseJsonProfile(jsonString: String): MasteringSettings {
        val root = JSONObject(jsonString)

        // 1. Equalizer 5 Bands
        val eqObj = root.optJSONObject("eq") ?: JSONObject()
        val subObj = eqObj.optJSONObject("sub_bass") ?: JSONObject()
        val kickObj = eqObj.optJSONObject("kick_punch") ?: JSONObject()
        val mudObj = eqObj.optJSONObject("mud_cut") ?: JSONObject()
        val snareObj = eqObj.optJSONObject("snare_presence") ?: JSONObject()
        val airObj = eqObj.optJSONObject("air_shelf") ?: JSONObject()

        val subBand = EqBand(
            id = "sub",
            name = "Sub-Bass",
            frequencyHz = subObj.optDouble("frequency_hz", 50.0).toFloat(),
            gainDb = subObj.optDouble("gain_db", 3.8).toFloat(),
            qFactor = subObj.optDouble("q", 1.2).toFloat()
        )

        val kickBand = EqBand(
            id = "kick",
            name = "Kick Punch",
            frequencyHz = kickObj.optDouble("frequency_hz", 85.0).toFloat(),
            gainDb = kickObj.optDouble("gain_db", 2.4).toFloat(),
            qFactor = kickObj.optDouble("q", 1.4).toFloat()
        )

        val mudBand = EqBand(
            id = "mud",
            name = "Mud Scoop",
            frequencyHz = mudObj.optDouble("frequency_hz", 280.0).toFloat(),
            gainDb = mudObj.optDouble("gain_db", -2.2).toFloat(),
            qFactor = mudObj.optDouble("q", 1.8).toFloat()
        )

        val snareBand = EqBand(
            id = "snare",
            name = "Snare Bite",
            frequencyHz = snareObj.optDouble("frequency_hz", 3200.0).toFloat(),
            gainDb = snareObj.optDouble("gain_db", 2.0).toFloat(),
            qFactor = snareObj.optDouble("q", 1.2).toFloat()
        )

        val airBand = EqBand(
            id = "air",
            name = "Air Highs",
            frequencyHz = airObj.optDouble("frequency_hz", 11000.0).toFloat(),
            gainDb = airObj.optDouble("gain_db", 1.8).toFloat(),
            qFactor = airObj.optDouble("q", 0.7).toFloat()
        )

        // 2. Multiband Compressor
        val mbObj = root.optJSONObject("multiband") ?: JSONObject()
        val lowObj = mbObj.optJSONObject("low") ?: JSONObject()
        val midObj = mbObj.optJSONObject("mid") ?: JSONObject()
        val highObj = mbObj.optJSONObject("high") ?: JSONObject()

        val bands = listOf(
            CompressorBand(
                id = "low",
                name = "Low Band (Sub & Kick)",
                frequencyRange = "20 Hz - 250 Hz",
                gainDb = lowObj.optDouble("gain_db", 2.0).toFloat(),
                thresholdDb = lowObj.optDouble("threshold_db", -14.0).toFloat(),
                ratio = lowObj.optDouble("ratio", 3.5).toFloat(),
                attackMs = lowObj.optDouble("attack_ms", 30.0).toFloat(),
                releaseMs = lowObj.optDouble("release_ms", 80.0).toFloat(),
                isBypassed = lowObj.optBoolean("is_bypassed", false)
            ),
            CompressorBand(
                id = "mid",
                name = "Mid Band (Vocals & Snare)",
                frequencyRange = "250 Hz - 4.5 kHz",
                gainDb = midObj.optDouble("gain_db", 0.8).toFloat(),
                thresholdDb = midObj.optDouble("threshold_db", -18.0).toFloat(),
                ratio = midObj.optDouble("ratio", 2.5).toFloat(),
                attackMs = midObj.optDouble("attack_ms", 15.0).toFloat(),
                releaseMs = midObj.optDouble("release_ms", 120.0).toFloat(),
                isBypassed = midObj.optBoolean("is_bypassed", false)
            ),
            CompressorBand(
                id = "high",
                name = "High Band (Air & Cymbals)",
                frequencyRange = "4.5 kHz - 20 kHz",
                gainDb = highObj.optDouble("gain_db", 1.6).toFloat(),
                thresholdDb = highObj.optDouble("threshold_db", -16.0).toFloat(),
                ratio = highObj.optDouble("ratio", 2.0).toFloat(),
                attackMs = highObj.optDouble("attack_ms", 10.0).toFloat(),
                releaseMs = highObj.optDouble("release_ms", 90.0).toFloat(),
                isBypassed = highObj.optBoolean("is_bypassed", false)
            )
        )

        // 3. Analog & Limiter parameters
        val analogObj = root.optJSONObject("analog") ?: JSONObject()
        val limiterObj = root.optJSONObject("limiter") ?: JSONObject()

        val saturation = analogObj.optDouble("saturation_drive", root.optDouble("saturation_drive", 0.25)).toFloat()
        val monoSub = analogObj.optDouble("mono_sub_freq_hz", root.optDouble("mono_sub_freq_hz", 90.0)).toFloat()
        val stereoWidth = analogObj.optDouble("stereo_width_factor", root.optDouble("stereo_width_factor", 1.15)).toFloat()

        val makeup = limiterObj.optDouble("makeup_gain_db", root.optDouble("makeup_gain_db", 4.5)).toFloat()
        val threshold = limiterObj.optDouble("limiter_threshold_db", root.optDouble("limiter_threshold_db", -7.5)).toFloat()
        val ceiling = limiterObj.optDouble("limiter_ceiling_db", root.optDouble("limiter_ceiling_db", -0.3)).toFloat()
        val release = limiterObj.optDouble("limiter_release_ms", root.optDouble("limiter_release_ms", 60.0)).toFloat()

        val profileName = root.optString("profile_name", "Perfil Personalizado")

        return MasteringSettings(
            presetType = PresetType.OLD_SCHOOL_PERREO,
            subBassBand = subBand,
            kickPunchBand = kickBand,
            mudCutBand = mudBand,
            snarePresenceBand = snareBand,
            airShelfBand = airBand,
            multibandBands = bands,
            saturationDrive = saturation.coerceIn(0f, 1f),
            monoSubFreqHz = monoSub.coerceIn(20f, 250f),
            stereoWidthFactor = stereoWidth.coerceIn(0.5f, 2.0f),
            makeupGainDb = makeup.coerceIn(0f, 15f),
            limiterThresholdDb = threshold.coerceIn(-20f, 0f),
            limiterCeilingDb = ceiling.coerceIn(-3f, 0f),
            limiterReleaseMs = release.coerceIn(10f, 300f),
            isAutoCalibrated = false,
            autoAdjustmentLog = listOf("Cargado desde archivo de perfil: $profileName")
        )
    }

    private fun parseKeyValueProfile(text: String): MasteringSettings {
        val map = mutableMapOf<String, String>()
        text.lineSequence().forEach { line ->
            val clean = line.trim()
            if (clean.isNotEmpty() && !clean.startsWith("#") && !clean.startsWith("//") && !clean.startsWith("[")) {
                val parts = clean.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim().lowercase().replace("-", "_").replace(" ", "_")
                    val value = parts[1].trim()
                    map[key] = value
                }
            }
        }

        fun getFloat(key: String, default: Float): Float = map[key]?.toFloatOrNull() ?: default
        fun getBool(key: String, default: Boolean): Boolean = map[key]?.toBooleanStrictOrNull() ?: default

        val subBand = EqBand(
            id = "sub",
            name = "Sub-Bass",
            frequencyHz = getFloat("sub_bass_freq_hz", 50.0f),
            gainDb = getFloat("sub_bass_gain_db", 3.8f),
            qFactor = getFloat("sub_bass_q", 1.2f)
        )

        val kickBand = EqBand(
            id = "kick",
            name = "Kick Punch",
            frequencyHz = getFloat("kick_punch_freq_hz", 85.0f),
            gainDb = getFloat("kick_punch_gain_db", 2.4f),
            qFactor = getFloat("kick_punch_q", 1.4f)
        )

        val mudBand = EqBand(
            id = "mud",
            name = "Mud Scoop",
            frequencyHz = getFloat("mud_cut_freq_hz", 280.0f),
            gainDb = getFloat("mud_cut_gain_db", -2.2f),
            qFactor = getFloat("mud_cut_q", 1.8f)
        )

        val snareBand = EqBand(
            id = "snare",
            name = "Snare Bite",
            frequencyHz = getFloat("snare_presence_freq_hz", 3200.0f),
            gainDb = getFloat("snare_presence_gain_db", 2.0f),
            qFactor = getFloat("snare_presence_q", 1.2f)
        )

        val airBand = EqBand(
            id = "air",
            name = "Air Highs",
            frequencyHz = getFloat("air_shelf_freq_hz", 11000.0f),
            gainDb = getFloat("air_shelf_gain_db", 1.8f),
            qFactor = getFloat("air_shelf_q", 0.7f)
        )

        val bands = listOf(
            CompressorBand(
                id = "low",
                name = "Low Band (Sub & Kick)",
                frequencyRange = "20 Hz - 250 Hz",
                gainDb = getFloat("low_band_gain_db", 2.0f),
                thresholdDb = getFloat("low_band_threshold_db", -14.0f),
                ratio = getFloat("low_band_ratio", 3.5f),
                attackMs = getFloat("low_band_attack_ms", 30.0f),
                releaseMs = getFloat("low_band_release_ms", 80.0f),
                isBypassed = getBool("low_band_bypassed", false)
            ),
            CompressorBand(
                id = "mid",
                name = "Mid Band (Vocals & Snare)",
                frequencyRange = "250 Hz - 4.5 kHz",
                gainDb = getFloat("mid_band_gain_db", 0.8f),
                thresholdDb = getFloat("mid_band_threshold_db", -18.0f),
                ratio = getFloat("mid_band_ratio", 2.5f),
                attackMs = getFloat("mid_band_attack_ms", 15.0f),
                releaseMs = getFloat("mid_band_release_ms", 120.0f),
                isBypassed = getBool("mid_band_bypassed", false)
            ),
            CompressorBand(
                id = "high",
                name = "High Band (Air & Cymbals)",
                frequencyRange = "4.5 kHz - 20 kHz",
                gainDb = getFloat("high_band_gain_db", 1.6f),
                thresholdDb = getFloat("high_band_threshold_db", -16.0f),
                ratio = getFloat("high_band_ratio", 2.0f),
                attackMs = getFloat("high_band_attack_ms", 10.0f),
                releaseMs = getFloat("high_band_release_ms", 90.0f),
                isBypassed = getBool("high_band_bypassed", false)
            )
        )

        val saturation = getFloat("saturation_drive", 0.25f)
        val monoSub = getFloat("mono_sub_freq_hz", 90.0f)
        val stereoWidth = getFloat("stereo_width_factor", 1.15f)

        val makeup = getFloat("makeup_gain_db", 4.5f)
        val threshold = getFloat("limiter_threshold_db", -7.5f)
        val ceiling = getFloat("limiter_ceiling_db", -0.3f)
        val release = getFloat("limiter_release_ms", 60.0f)

        return MasteringSettings(
            presetType = PresetType.OLD_SCHOOL_PERREO,
            subBassBand = subBand,
            kickPunchBand = kickBand,
            mudCutBand = mudBand,
            snarePresenceBand = snareBand,
            airShelfBand = airBand,
            multibandBands = bands,
            saturationDrive = saturation.coerceIn(0f, 1f),
            monoSubFreqHz = monoSub.coerceIn(20f, 250f),
            stereoWidthFactor = stereoWidth.coerceIn(0.5f, 2.0f),
            makeupGainDb = makeup.coerceIn(0f, 15f),
            limiterThresholdDb = threshold.coerceIn(-20f, 0f),
            limiterCeilingDb = ceiling.coerceIn(-3f, 0f),
            limiterReleaseMs = release.coerceIn(10f, 300f),
            isAutoCalibrated = false,
            autoAdjustmentLog = listOf("Cargado desde archivo de texto plano personalizado (.txt)")
        )
    }

    /**
     * Builds an editable text template (.txt) with comments and parameter explanations.
     */
    fun generateEditableTxtTemplate(): String {
        return buildString {
            appendLine("# ========================================================")
            appendLine("# PERFIL DE MASTERIZACIÓN PERSONALIZADO - REGGAETON MASTER")
            appendLine("# Edita los valores y cárgalo en la app para aplicarlos al track")
            appendLine("# ========================================================")
            appendLine()
            appendLine("[ECUALIZADOR_5_BANDAS]")
            appendLine("# Sub-Bass (40Hz - 60Hz)")
            appendLine("sub_bass_freq_hz=50.0")
            appendLine("sub_bass_gain_db=4.0")
            appendLine("sub_bass_q=1.2")
            appendLine()
            appendLine("# Kick Punch (80Hz - 100Hz)")
            appendLine("kick_punch_freq_hz=85.0")
            appendLine("kick_punch_gain_db=2.5")
            appendLine("kick_punch_q=1.4")
            appendLine()
            appendLine("# Mud Scoop Limpieza (200Hz - 350Hz, negativo para cortar)")
            appendLine("mud_cut_freq_hz=280.0")
            appendLine("mud_cut_gain_db=-2.5")
            appendLine("mud_cut_q=1.8")
            appendLine()
            appendLine("# Snare Bite Redoblante (2.8kHz - 4.5kHz)")
            appendLine("snare_presence_freq_hz=3500.0")
            appendLine("snare_presence_gain_db=3.0")
            appendLine("snare_presence_q=1.2")
            appendLine()
            appendLine("# Air Shelf Brillo Seda (10kHz - 14kHz)")
            appendLine("air_shelf_freq_hz=12000.0")
            appendLine("air_shelf_gain_db=2.2")
            appendLine("air_shelf_q=0.7")
            appendLine()
            appendLine("[COMPRESOR_MULTIBANDA]")
            appendLine("low_band_gain_db=2.0")
            appendLine("low_band_threshold_db=-14.0")
            appendLine("low_band_ratio=3.5")
            appendLine("low_band_bypassed=false")
            appendLine()
            appendLine("mid_band_gain_db=0.8")
            appendLine("mid_band_threshold_db=-18.0")
            appendLine("mid_band_ratio=2.5")
            appendLine("mid_band_bypassed=false")
            appendLine()
            appendLine("high_band_gain_db=1.5")
            appendLine("high_band_threshold_db=-16.0")
            appendLine("high_band_ratio=2.0")
            appendLine("high_band_bypassed=false")
            appendLine()
            appendLine("[COLOR_ANALOGICO_Y_ESTÉREO]")
            appendLine("# Saturación armónica analógica (0.0 a 1.0)")
            appendLine("saturation_drive=0.30")
            appendLine("# Corte de subgrave en mono Hz (20 a 140Hz)")
            appendLine("mono_sub_freq_hz=90.0")
            appendLine("# Factor de apertura estéreo (1.0 = normal, 1.25 = amplio)")
            appendLine("stereo_width_factor=1.20")
            appendLine()
            appendLine("[LIMITADOR_TRUE_PEAK]")
            appendLine("# Ganancia de entrada al limitador en dB")
            appendLine("makeup_gain_db=5.5")
            appendLine("# Umbral dinámico del limitador en dB (-15.0 a -2.0)")
            appendLine("limiter_threshold_db=-8.0")
            appendLine("# Techo máximo True-Peak en dBTP (-2.5 a 0.0)")
            appendLine("limiter_ceiling_db=-0.2")
            appendLine("# Tiempo de recuperación release en milisegundos")
            appendLine("limiter_release_ms=50.0")
        }
    }

    /**
     * Generates a complete Acoustic Analysis and Applied Parameters report.
     */
    fun generateAnalysisReport(
        trackTitle: String,
        rawAnalysis: AudioAnalysis,
        masteredAnalysis: AudioAnalysis,
        settings: MasteringSettings
    ): String {
        return buildString {
            appendLine("=================================================================")
            appendLine("INFORME ACÚSTICO & PARÁMETROS DE MASTERIZACIÓN - REGGAETON MASTER")
            appendLine("Pista analizada: $trackTitle")
            appendLine("Fecha de exportación: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine("=================================================================")
            appendLine()
            appendLine("--- 1. MEDICIÓN DE SONORIDAD & NORMAS ITU-R BS.1770 / EBU R128 ---")
            appendLine("Sonoridad Integrada (Raw Mix):     ${"%.2f".format(rawAnalysis.integratedLufs)} LUFS")
            appendLine("Sonoridad Integrada (Mastered):    ${"%.2f".format(masteredAnalysis.integratedLufs)} LUFS")
            appendLine("Loudness Short-Term Máx (3s):       ${"%.2f".format(masteredAnalysis.shortTermMaxLufs)} LUFS")
            appendLine("Nivel de Pico True-Peak:           ${"%.2f".format(masteredAnalysis.truePeakDbfs)} dBTP")
            appendLine("Margen de Techo Aplicado:          ${"%.2f".format(settings.limiterCeilingDb)} dBTP")
            appendLine("Rango Dinámico / Crest Factor:     ${"%.2f".format(masteredAnalysis.dynamicRangeDb)} dB")
            appendLine("Detección de Clipping:             ${if (masteredAnalysis.hasClipping) "ALERTA (Picos > 0 dB)" else "LIMPIO (Protegido anti-clipping)"}")
            appendLine()
            appendLine("--- 2. BALANCE ESPECTRAL POR BANDAS DE FRECUENCIA ---")
            appendLine("Sub-Bass (20 - 60 Hz):             ${(masteredAnalysis.subBassEnergy * 100).toInt()}% de energía relativa (Raw: ${(rawAnalysis.subBassEnergy * 100).toInt()}%)")
            appendLine("Cuerpo de Graves (60 - 250 Hz):    ${(masteredAnalysis.bassEnergy * 100).toInt()}%")
            appendLine("Zona Turbia Mud (250 - 500 Hz):    ${(masteredAnalysis.lowMidEnergy * 100).toInt()}%")
            appendLine("Medios Vocales (500 Hz - 2 kHz):   ${(masteredAnalysis.midEnergy * 100).toInt()}%")
            appendLine("Ataque Redoblante (2 - 6 kHz):     ${(masteredAnalysis.highMidEnergy * 100).toInt()}%")
            appendLine("Brillo y Aire (6 - 20 kHz):        ${(masteredAnalysis.airEnergy * 100).toInt()}%")
            appendLine("Frecuencia Dominante de Pegada:    ${masteredAnalysis.dominantFrequencyHz.toInt()} Hz")
            appendLine()
            appendLine("--- 3. PARÁMETROS EXACTOS EDITABLES (LISTOS PARA RE-IMPORTAR) ---")
            appendLine("[ECUALIZADOR_5_BANDAS]")
            appendLine("sub_bass_freq_hz=${settings.subBassBand.frequencyHz}")
            appendLine("sub_bass_gain_db=${settings.subBassBand.gainDb}")
            appendLine("sub_bass_q=${settings.subBassBand.qFactor}")
            appendLine("kick_punch_freq_hz=${settings.kickPunchBand.frequencyHz}")
            appendLine("kick_punch_gain_db=${settings.kickPunchBand.gainDb}")
            appendLine("kick_punch_q=${settings.kickPunchBand.qFactor}")
            appendLine("mud_cut_freq_hz=${settings.mudCutBand.frequencyHz}")
            appendLine("mud_cut_gain_db=${settings.mudCutBand.gainDb}")
            appendLine("mud_cut_q=${settings.mudCutBand.qFactor}")
            appendLine("snare_presence_freq_hz=${settings.snarePresenceBand.frequencyHz}")
            appendLine("snare_presence_gain_db=${settings.snarePresenceBand.gainDb}")
            appendLine("snare_presence_q=${settings.snarePresenceBand.qFactor}")
            appendLine("air_shelf_freq_hz=${settings.airShelfBand.frequencyHz}")
            appendLine("air_shelf_gain_db=${settings.airShelfBand.gainDb}")
            appendLine("air_shelf_q=${settings.airShelfBand.qFactor}")
            appendLine()
            appendLine("[COMPRESOR_MULTIBANDA]")
            settings.multibandBands.forEach { b ->
                appendLine("${b.id}_band_gain_db=${b.gainDb}")
                appendLine("${b.id}_band_threshold_db=${b.thresholdDb}")
                appendLine("${b.id}_band_ratio=${b.ratio}")
                appendLine("${b.id}_band_bypassed=${b.isBypassed}")
            }
            appendLine()
            appendLine("[COLOR_ANALOGICO_Y_ESTÉREO]")
            appendLine("saturation_drive=${settings.saturationDrive}")
            appendLine("mono_sub_freq_hz=${settings.monoSubFreqHz}")
            appendLine("stereo_width_factor=${settings.stereoWidthFactor}")
            appendLine()
            appendLine("[LIMITADOR_TRUE_PEAK]")
            appendLine("makeup_gain_db=${settings.makeupGainDb}")
            appendLine("limiter_threshold_db=${settings.limiterThresholdDb}")
            appendLine("limiter_ceiling_db=${settings.limiterCeilingDb}")
            appendLine("limiter_release_ms=${settings.limiterReleaseMs}")
            appendLine()
            appendLine("=================================================================")
            appendLine("NOTA: Puedes guardar este bloque [3] en un archivo .txt o .json")
            appendLine("y cargarlo en la app para re-aplicar esta misma configuración.")
            appendLine("=================================================================")
        }
    }

    /**
     * Saves text to a public file in Downloads / Documents via MediaStore or internal storage.
     */
    suspend fun exportTextFile(
        context: Context,
        fileName: String,
        content: String,
        mimeType: String = "text/plain"
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val outputStream: OutputStream
            val targetUri: Uri

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ReggaetonMasterDSP")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IllegalStateException("No se pudo crear entrada en descargas")
                targetUri = uri
                outputStream = context.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("No se pudo abrir flujo de salida")
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ReggaetonMasterDSP")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                targetUri = Uri.fromFile(file)
                outputStream = FileOutputStream(file)
            }

            outputStream.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completeValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                context.contentResolver.update(targetUri, completeValues, null, null)
            }

            targetUri
        }
    }
}
