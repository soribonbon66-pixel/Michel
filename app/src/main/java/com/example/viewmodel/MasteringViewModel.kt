package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dsp.*
import com.example.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.coroutines.cancellation.CancellationException

sealed class ExportState {
    object Idle : ExportState()
    data class Exporting(val progress: Float) : ExportState()
    data class Success(
        val uri: Uri,
        val fileName: String,
        val title: String = "¡Master Renderizado!",
        val description: String = "Archivo guardado exitosamente en Música/ReggaetonMasterDSP:",
        val mimeType: String = "audio/wav",
        val previewContent: String? = null
    ) : ExportState()
    data class Error(val message: String) : ExportState()
}

enum class StudioTab {
    SPECTRUM,
    EQ_CURVE,
    DYNAMICS,
    REPORT
}

data class StudioUiState(
    val rawTrack: AudioTrackData? = null,
    val masteredTrack: AudioTrackData? = null,
    val rawAnalysis: AudioAnalysis = AudioAnalysis.EMPTY,
    val masteredAnalysis: AudioAnalysis = AudioAnalysis.EMPTY,
    val settings: MasteringSettings = MasteringSettings(),
    val result: MasteringResult? = null,
    val isPlaying: Boolean = false,
    val isBypassActive: Boolean = false, // true = Hearing RAW (A), false = Hearing MASTERED (B)
    val isProcessing: Boolean = false,
    val processingProgress: Float = 0.0f,
    val playbackCurrentSec: Float = 0.0f,
    val playbackTotalSec: Float = 0.0f,
    val playbackProgress: Float = 0.0f,
    val livePeakL: Float = 0.0f,
    val livePeakR: Float = 0.0f,
    val isLooping: Boolean = true,
    val selectedCompressorBandId: String = "low",
    val exportState: ExportState = ExportState.Idle,
    val activeTab: StudioTab = StudioTab.SPECTRUM,
    val showReportDialog: Boolean = false,
    val isCustomProfileActive: Boolean = false,
    val customProfileName: String? = null,
    val statusMessage: String = "Carga un archivo o genera una pista demo para comenzar"
)

class MasteringViewModel(application: Application) : AndroidViewModel(application) {

    private val dspEngine = UrbanDspEngine()

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private var audioPlayer: RealtimeAudioPlayer? = null
    private var reprocessJob: Job? = null

    init {
        initAudioPlayer()
        // Carga un beat demo limpio y realiza el análisis completo del track sin presets
        loadDemoBeat()
    }

    private fun initAudioPlayer() {
        audioPlayer = RealtimeAudioPlayer(
            onPlaybackPosition = { cur, tot, prog ->
                _uiState.update {
                    it.copy(
                        playbackCurrentSec = cur,
                        playbackTotalSec = tot,
                        playbackProgress = prog
                    )
                }
            },
            onMeterUpdate = { pL, pR ->
                _uiState.update {
                    it.copy(
                        livePeakL = pL,
                        livePeakR = pR
                    )
                }
            },
            onPlaybackStateChanged = { playing ->
                _uiState.update { it.copy(isPlaying = playing) }
            }
        )
    }

    fun loadAudioFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Cargando y analizando track de audio..."
                )
            }
            audioPlayer?.pause()
            try {
                val track = AudioDecoder.decodeFromUri(context, uri)
                applyTrackClean(track)
            } catch (t: Throwable) {
                t.printStackTrace()
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = "Error al abrir audio: ${t.localizedMessage ?: t.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    fun loadDemoBeat() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Cargando beat urbano demo (95 BPM)..."
                )
            }
            audioPlayer?.pause()
            try {
                val demoTrack = withContext(Dispatchers.Default) {
                    AudioDecoder.generateUrbanDemoBeat(isOldSchool = true, durationSeconds = 10.0f)
                }
                applyTrackClean(demoTrack)
            } catch (t: Throwable) {
                t.printStackTrace()
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = "Error al generar demo: ${t.localizedMessage ?: t.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    /**
     * Al cargar un track, la app analiza y muestra un análisis completo del track.
     * No aplica presets iniciales: los ajustes comienzan planos/limpios (0 dB).
     */
    private suspend fun applyTrackClean(track: AudioTrackData) = withContext(Dispatchers.Default) {
        try {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Analizando sonoridad LUFS, picos y espectro acústico..."
                )
            }
            // 1. Análisis completo del track
            val preAnalysis = dspEngine.analyzeTrack(track)

            // 2. Ajustes iniciales limpios sin presets iniciales
            val cleanSettings = MasteringSettings.CLEAN

            // 3. Renderizado transparente del máster inicial
            val (masteredTrack, initialResult) = dspEngine.processTrack(
                inputTrack = track,
                settings = cleanSettings,
                precomputedRawAnalysis = preAnalysis
            )

            audioPlayer?.setTracks(track, masteredTrack)
            audioPlayer?.isBypassActive = _uiState.value.isBypassActive

            _uiState.update {
                it.copy(
                    rawTrack = track,
                    masteredTrack = masteredTrack,
                    rawAnalysis = preAnalysis,
                    masteredAnalysis = initialResult.masteredAnalysis,
                    settings = cleanSettings,
                    result = initialResult,
                    isProcessing = false,
                    processingProgress = 1.0f,
                    isCustomProfileActive = false,
                    customProfileName = null,
                    statusMessage = "Track analizado: ${"%.1f".format(preAnalysis.integratedLufs)} LUFS. Modifica los sliders o sube un archivo con presets."
                )
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    statusMessage = "Error al analizar track: ${t.localizedMessage ?: t.javaClass.simpleName}"
                )
            }
        }
    }

    /**
     * Restablece todos los parámetros a 0 dB (plano/neutro).
     */
    fun resetToFlatSettings() {
        val raw = _uiState.value.rawTrack ?: return
        reprocessWithSettings(MasteringSettings.CLEAN, debounceMs = 50L)
        _uiState.update {
            it.copy(
                isCustomProfileActive = false,
                customProfileName = null,
                statusMessage = "Ajustes restablecidos a neutro (0 dB planos)"
            )
        }
    }

    fun toggleBypass() {
        val newBypass = !_uiState.value.isBypassActive
        audioPlayer?.isBypassActive = newBypass
        _uiState.update { it.copy(isBypassActive = newBypass) }
    }

    fun togglePlayPause() {
        audioPlayer?.togglePlayPause()
    }

    fun seek(progress: Float) {
        audioPlayer?.seekTo(progress)
    }

    fun toggleLoop() {
        val newLoop = !_uiState.value.isLooping
        audioPlayer?.isLooping = newLoop
        _uiState.update { it.copy(isLooping = newLoop) }
    }

    fun setActiveTab(tab: StudioTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun selectCompressorBand(bandId: String) {
        _uiState.update { it.copy(selectedCompressorBandId = bandId) }
    }

    fun updateCompressorGain(bandId: String, gainDb: Float) {
        val clamped = (Math.round(gainDb.coerceIn(-12.0f, 12.0f) * 10f) / 10f)
        val curSettings = _uiState.value.settings
        val updatedBands = curSettings.multibandBands.map { band ->
            if (band.id == bandId) band.copy(gainDb = clamped) else band
        }
        reprocessWithSettings(curSettings.copy(multibandBands = updatedBands))
    }

    fun updateCompressorThreshold(bandId: String, thresholdDb: Float) {
        val clamped = (Math.round(thresholdDb.coerceIn(-40.0f, 0.0f) * 10f) / 10f)
        val curSettings = _uiState.value.settings
        val updatedBands = curSettings.multibandBands.map { band ->
            if (band.id == bandId) band.copy(thresholdDb = clamped) else band
        }
        reprocessWithSettings(curSettings.copy(multibandBands = updatedBands))
    }

    fun updateCompressorRatio(bandId: String, ratio: Float) {
        val clamped = (Math.round(ratio.coerceIn(1.0f, 20.0f) * 10f) / 10f)
        val curSettings = _uiState.value.settings
        val updatedBands = curSettings.multibandBands.map { band ->
            if (band.id == bandId) band.copy(ratio = clamped) else band
        }
        reprocessWithSettings(curSettings.copy(multibandBands = updatedBands))
    }

    fun nudgeCompressorGain(bandId: String, deltaDb: Float) {
        val band = _uiState.value.settings.multibandBands.find { it.id == bandId } ?: return
        updateCompressorGain(bandId, band.gainDb + deltaDb)
    }

    fun nudgeCompressorThreshold(bandId: String, deltaDb: Float) {
        val band = _uiState.value.settings.multibandBands.find { it.id == bandId } ?: return
        updateCompressorThreshold(bandId, band.thresholdDb + deltaDb)
    }

    fun nudgeCompressorRatio(bandId: String, deltaRatio: Float) {
        val band = _uiState.value.settings.multibandBands.find { it.id == bandId } ?: return
        updateCompressorRatio(bandId, band.ratio + deltaRatio)
    }

    fun toggleCompressorBandBypass(bandId: String) {
        val curSettings = _uiState.value.settings
        val updatedBands = curSettings.multibandBands.map { band ->
            if (band.id == bandId) band.copy(isBypassed = !band.isBypassed) else band
        }
        reprocessWithSettings(curSettings.copy(multibandBands = updatedBands))
    }

    fun toggleReportDialog(show: Boolean) {
        _uiState.update { it.copy(showReportDialog = show) }
    }

    fun updateEqBandGain(bandId: String, newGainDb: Float) {
        val curSettings = _uiState.value.settings
        val updated = when (bandId) {
            "sub" -> curSettings.copy(subBassBand = curSettings.subBassBand.copy(gainDb = newGainDb))
            "kick" -> curSettings.copy(kickPunchBand = curSettings.kickPunchBand.copy(gainDb = newGainDb))
            "mud" -> curSettings.copy(mudCutBand = curSettings.mudCutBand.copy(gainDb = newGainDb))
            "snare" -> curSettings.copy(snarePresenceBand = curSettings.snarePresenceBand.copy(gainDb = newGainDb))
            "air" -> curSettings.copy(airShelfBand = curSettings.airShelfBand.copy(gainDb = newGainDb))
            else -> curSettings
        }
        reprocessWithSettings(updated)
    }

    fun updateMakeupGain(gainDb: Float) {
        val curSettings = _uiState.value.settings
        reprocessWithSettings(curSettings.copy(makeupGainDb = gainDb))
    }

    fun updateSaturationDrive(drive: Float) {
        val curSettings = _uiState.value.settings
        reprocessWithSettings(curSettings.copy(saturationDrive = drive))
    }

    fun updateLimiterCeiling(ceilingDb: Float) {
        val curSettings = _uiState.value.settings
        reprocessWithSettings(curSettings.copy(limiterCeilingDb = ceilingDb.coerceIn(-3.0f, 0.0f)))
    }

    fun updateLimiterThreshold(thresholdDb: Float) {
        val curSettings = _uiState.value.settings
        reprocessWithSettings(curSettings.copy(limiterThresholdDb = thresholdDb.coerceIn(-20.0f, 0.0f)))
    }

    fun updateStereoWidth(width: Float) {
        val curSettings = _uiState.value.settings
        reprocessWithSettings(curSettings.copy(stereoWidthFactor = width.coerceIn(0.5f, 2.0f)))
    }

    fun updateMonoSubFreq(freqHz: Float) {
        val curSettings = _uiState.value.settings
        reprocessWithSettings(curSettings.copy(monoSubFreqHz = freqHz.coerceIn(20.0f, 150.0f)))
    }

    private fun reprocessWithSettings(newSettings: MasteringSettings, debounceMs: Long = 200L) {
        val raw = _uiState.value.rawTrack ?: return

        // 1. Inmediatamente reflejar el valor en el estado para que los sliders y textos se muevan a 60/120fps sin lag
        _uiState.update { it.copy(settings = newSettings) }

        // 2. Cancelar cualquier tarea previa de procesamiento pesado
        reprocessJob?.cancel()

        // 3. Lanzar procesamiento diferido con debounce para evitar congelamientos por arrastre continuo
        reprocessJob = viewModelScope.launch(Dispatchers.Default) {
            if (debounceMs > 0L) {
                delay(debounceMs)
            }
            if (!isActive) return@launch

            try {
                val (mastered, result) = dspEngine.processTrack(
                    inputTrack = raw,
                    settings = newSettings,
                    precomputedRawAnalysis = _uiState.value.rawAnalysis
                )
                if (!isActive) return@launch

                audioPlayer?.updateMasteredTrack(mastered)
                _uiState.update {
                    it.copy(
                        masteredTrack = mastered,
                        masteredAnalysis = result.masteredAnalysis,
                        result = result
                    )
                }
            } catch (t: Throwable) {
                if (t !is CancellationException) {
                    t.printStackTrace()
                }
            }
        }
    }

    /**
     * Carga un archivo de perfil (.txt o .json) con valores editables y los aplica directamente al máster,
     * sin aplicar los presets preestablecidos.
     */
    fun loadProfileFromUri(context: Context, uri: Uri) {
        val raw = _uiState.value.rawTrack
        if (raw == null) {
            _uiState.update {
                it.copy(statusMessage = "Carga primero un track musical antes de aplicar el archivo de perfil")
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Leyendo archivo de perfil personalizado (.txt / .json)..."
                )
            }

            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("No se pudo abrir el archivo seleccionado")
                val content = inputStream.bufferedReader().use { it.readText() }

                val parseResult = MasteringProfileManager.parseProfile(content)
                val customSettings = parseResult.getOrThrow()

                withContext(Dispatchers.Default) {
                    _uiState.update {
                        it.copy(
                            statusMessage = "Aplicando valores del archivo al masterizado (sin presets)...",
                            settings = customSettings,
                            isCustomProfileActive = true,
                            customProfileName = "Perfil Personalizado (.txt/.json)"
                        )
                    }

                    val (mastered, result) = dspEngine.processTrack(
                        inputTrack = raw,
                        settings = customSettings,
                        precomputedRawAnalysis = _uiState.value.rawAnalysis
                    ) { progress ->
                        _uiState.update { it.copy(processingProgress = progress) }
                    }

                    audioPlayer?.updateMasteredTrack(mastered)

                    _uiState.update {
                        it.copy(
                            masteredTrack = mastered,
                            masteredAnalysis = result.masteredAnalysis,
                            result = result,
                            isProcessing = false,
                            statusMessage = "Valores del archivo aplicados con éxito: ${"%.1f".format(result.masteredAnalysis.integratedLufs)} LUFS"
                        )
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = "Error al aplicar perfil: ${t.localizedMessage ?: "Formato de archivo inválido"}"
                    )
                }
            }
        }
    }

    /**
     * Analiza el track actual y descarga un archivo .txt con todos los valores medidos del análisis
     * y los parámetros exactos aplicados en el máster.
     */
    fun exportAnalysisAndParameters(context: Context) {
        val raw = _uiState.value.rawTrack
        if (raw == null) {
            _uiState.update {
                it.copy(
                    exportState = ExportState.Error("Carga primero un track musical para analizar y exportar sus parámetros.")
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    exportState = ExportState.Exporting(0.5f),
                    statusMessage = "Generando informe acústico y archivo de parámetros..."
                )
            }

            val title = raw.title.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val fileName = "${title}_Analisis_y_Parametros.txt"
            val reportContent = MasteringProfileManager.generateAnalysisReport(
                trackTitle = raw.title,
                rawAnalysis = _uiState.value.rawAnalysis,
                masteredAnalysis = _uiState.value.masteredAnalysis,
                settings = _uiState.value.settings
            )

            val result = MasteringProfileManager.exportTextFile(context, fileName, reportContent)
            result.onSuccess { uri ->
                _uiState.update {
                    it.copy(
                        exportState = ExportState.Success(
                            uri = uri,
                            fileName = fileName,
                            title = "¡Informe Acústico Generado!",
                            description = "El análisis completo y los valores editables se han guardado en Descargas/ReggaetonMasterDSP:",
                            mimeType = "text/plain",
                            previewContent = reportContent
                        ),
                        statusMessage = "Informe acústico guardado en Descargas: $fileName"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(exportState = ExportState.Error(err.localizedMessage ?: "Error al exportar análisis"))
                }
            }
        }
    }

    /**
     * Descarga una plantilla .txt editable con todos los parámetros disponibles.
     */
    fun downloadProfileTemplate(context: Context) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    exportState = ExportState.Exporting(0.5f),
                    statusMessage = "Generando plantilla editable..."
                )
            }

            val templateContent = MasteringProfileManager.generateEditableTxtTemplate()
            val fileName = "plantilla_mastering_reggaeton.txt"
            val result = MasteringProfileManager.exportTextFile(context, fileName, templateContent)
            result.onSuccess { uri ->
                _uiState.update {
                    it.copy(
                        exportState = ExportState.Success(
                            uri = uri,
                            fileName = fileName,
                            title = "¡Plantilla Editable Descargada!",
                            description = "Plantilla guardada en Descargas/ReggaetonMasterDSP. Puedes editar los valores en cualquier bloc de notas y re-importarla en la app:",
                            mimeType = "text/plain",
                            previewContent = templateContent
                        ),
                        statusMessage = "Plantilla editable guardada en Descargas: $fileName"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(exportState = ExportState.Error(err.localizedMessage ?: "Error al guardar plantilla"))
                }
            }
        }
    }

    fun exportMasterWav(context: Context) {
        val mastered = _uiState.value.masteredTrack ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(exportState = ExportState.Exporting(0.5f)) }
            val result = WavWriter.exportToWav(context, mastered)
            result.onSuccess { uri ->
                _uiState.update {
                    it.copy(
                        exportState = ExportState.Success(
                            uri = uri,
                            fileName = "${mastered.title}.wav"
                        )
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(exportState = ExportState.Error(err.localizedMessage ?: "Error al guardar WAV"))
                }
            }
        }
    }

    fun dismissExportState() {
        _uiState.update { it.copy(exportState = ExportState.Idle) }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer?.release()
    }
}
