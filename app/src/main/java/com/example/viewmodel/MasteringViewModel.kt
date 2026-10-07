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
    data class Success(val uri: Uri, val fileName: String) : ExportState()
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
    val selectedPreset: PresetType = PresetType.OLD_SCHOOL_PERREO,
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
        // Automatically load demo old-school perreo beat so producer can audition immediately
        loadDemoBeat(isOldSchool = true)
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
                    statusMessage = "Cargando y decodificando audio..."
                )
            }
            audioPlayer?.pause()
            try {
                val track = AudioDecoder.decodeFromUri(context, uri)
                applyTrackAndMaster(track, _uiState.value.selectedPreset, autoCalibrate = true)
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

    fun loadDemoBeat(isOldSchool: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = if (isOldSchool) "Sintetizando beat demo Old-School 95BPM..." else "Sintetizando dembow 118BPM..."
                )
            }
            audioPlayer?.pause()
            val preset = if (isOldSchool) PresetType.OLD_SCHOOL_PERREO else PresetType.DEMBOW_OSCURO
            try {
                val demoTrack = withContext(Dispatchers.Default) {
                    AudioDecoder.generateUrbanDemoBeat(isOldSchool = isOldSchool, durationSeconds = 10.0f)
                }
                applyTrackAndMaster(demoTrack, preset, autoCalibrate = true)
            } catch (t: Throwable) {
                t.printStackTrace()
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = "Error al sintetizar demo: ${t.localizedMessage ?: t.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    private suspend fun applyTrackAndMaster(
        track: AudioTrackData,
        preset: PresetType,
        autoCalibrate: Boolean
    ) = withContext(Dispatchers.Default) {
        try {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Paso 1/4: Analizando sonoridad LUFS y acústica inicial..."
                )
            }
            val preAnalysis = dspEngine.analyzeTrack(track)

            val initialSettings = if (autoCalibrate) {
                _uiState.update { it.copy(statusMessage = "Paso 2/4: Calibrando parámetros para ${preset.title}...") }
                dspEngine.autoCalibrateSettings(preset, preAnalysis)
            } else {
                _uiState.value.settings.copy(presetType = preset)
            }

            _uiState.update { it.copy(statusMessage = "Paso 3/4: Renderizando máster inicial...") }
            val (pass1Track, pass1Result) = dspEngine.processTrack(
                inputTrack = track,
                settings = initialSettings,
                precomputedRawAnalysis = preAnalysis
            ) { progress ->
                _uiState.update { it.copy(processingProgress = progress * 0.7f) }
            }

            // Closed-loop adaptive feedback: Mide el resultado del máster y auto-corrige
            val (finalTrack, finalResult, finalSettings) = if (autoCalibrate) {
                _uiState.update { it.copy(statusMessage = "Paso 4/4: Midiendo resultado y aplicando auto-corrección adaptativa...") }
                val (correctedSettings, corrections) = dspEngine.autoCorrectSettings(
                    currentSettings = initialSettings,
                    targetPreset = preset,
                    masteredAnalysis = pass1Result.masteredAnalysis,
                    rawAnalysis = preAnalysis
                )

                if (corrections.isNotEmpty()) {
                    _uiState.update { it.copy(statusMessage = "Aplicando auto-corrección fina (${corrections.size} ajustes)...") }
                    val (pass2Track, pass2Result) = dspEngine.processTrack(
                        inputTrack = track,
                        settings = correctedSettings,
                        precomputedRawAnalysis = preAnalysis
                    ) { progress ->
                        _uiState.update { it.copy(processingProgress = 0.7f + progress * 0.3f) }
                    }
                    Triple(pass2Track, pass2Result, correctedSettings)
                } else {
                    Triple(pass1Track, pass1Result, initialSettings)
                }
            } else {
                Triple(pass1Track, pass1Result, initialSettings)
            }

            audioPlayer?.setTracks(track, finalTrack)
            audioPlayer?.isBypassActive = _uiState.value.isBypassActive

            _uiState.update {
                it.copy(
                    rawTrack = track,
                    masteredTrack = finalTrack,
                    rawAnalysis = preAnalysis,
                    masteredAnalysis = finalResult.masteredAnalysis,
                    selectedPreset = preset,
                    settings = finalSettings,
                    result = finalResult,
                    isProcessing = false,
                    processingProgress = 1.0f,
                    statusMessage = "Máster optimizado y auto-corregido: ${"%.1f".format(finalResult.masteredAnalysis.integratedLufs)} LUFS (Techo: ${finalSettings.limiterCeilingDb} dBTP)"
                )
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    statusMessage = "Error en procesamiento DSP: ${t.localizedMessage ?: t.javaClass.simpleName}"
                )
            }
        }
    }

    fun selectPreset(preset: PresetType) {
        val raw = _uiState.value.rawTrack ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(selectedPreset = preset, isProcessing = true) }
            applyTrackAndMaster(raw, preset, autoCalibrate = true)
        }
    }

    fun autoCalibrate() {
        val raw = _uiState.value.rawTrack ?: return
        val currentPreset = _uiState.value.selectedPreset
        viewModelScope.launch {
            applyTrackAndMaster(raw, currentPreset, autoCalibrate = true)
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
        reprocessWithSettings(curSettings.copy(limiterCeilingDb = ceilingDb))
    }

    private fun reprocessWithSettings(newSettings: MasteringSettings, debounceMs: Long = 180L) {
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
     * Permite ejecutar la auto-corrección en cualquier momento sobre el resultado actual.
     */
    fun autoCorrectCurrentMaster() {
        val raw = _uiState.value.rawTrack ?: return
        val currentResult = _uiState.value.result ?: return
        val currentPreset = _uiState.value.selectedPreset

        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    statusMessage = "Midiendo máster actual y recalculando auto-corrección..."
                )
            }

            val (correctedSettings, corrections) = dspEngine.autoCorrectSettings(
                currentSettings = _uiState.value.settings,
                targetPreset = currentPreset,
                masteredAnalysis = currentResult.masteredAnalysis,
                rawAnalysis = _uiState.value.rawAnalysis
            )

            if (corrections.isNotEmpty()) {
                val (mastered, result) = dspEngine.processTrack(
                    inputTrack = raw,
                    settings = correctedSettings,
                    precomputedRawAnalysis = _uiState.value.rawAnalysis
                )
                audioPlayer?.updateMasteredTrack(mastered)
                _uiState.update {
                    it.copy(
                        masteredTrack = mastered,
                        masteredAnalysis = result.masteredAnalysis,
                        settings = correctedSettings,
                        result = result,
                        isProcessing = false,
                        statusMessage = "Auto-corrección completada (${corrections.size} correcciones aplicadas)"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = "El máster ya cumple exactamente los objetivos acústicos"
                    )
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
