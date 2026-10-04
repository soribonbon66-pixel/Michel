package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dsp.*
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                    statusMessage = "Decodificando audio PCM..."
                )
            }
            audioPlayer?.pause()
            try {
                val track = AudioDecoder.decodeFromUri(context, uri)
                applyTrackAndMaster(track, _uiState.value.selectedPreset, autoCalibrate = true)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = "Error al abrir archivo: ${e.localizedMessage ?: "Formato desconocido"}"
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
            val demoTrack = withContext(Dispatchers.Default) {
                AudioDecoder.generateUrbanDemoBeat(isOldSchool = isOldSchool, durationSeconds = 10.0f)
            }
            applyTrackAndMaster(demoTrack, preset, autoCalibrate = true)
        }
    }

    private suspend fun applyTrackAndMaster(
        track: AudioTrackData,
        preset: PresetType,
        autoCalibrate: Boolean
    ) = withContext(Dispatchers.Default) {
        _uiState.update {
            it.copy(
                isProcessing = true,
                statusMessage = "Analizando sonoridad LUFS & balance espectral..."
            )
        }
        val preAnalysis = dspEngine.analyzeTrack(track)

        val settings = if (autoCalibrate) {
            _uiState.update { it.copy(statusMessage = "Autocalibrando parámetros y umbrales para ${preset.title}...") }
            dspEngine.autoCalibrateSettings(preset, preAnalysis)
        } else {
            _uiState.value.settings.copy(presetType = preset)
        }

        _uiState.update { it.copy(statusMessage = "Renderizando cadena DSP (EQ, saturador y limitador)...") }
        val (masteredTrack, result) = dspEngine.processTrack(track, settings) { progress ->
            _uiState.update { it.copy(processingProgress = progress) }
        }

        audioPlayer?.setTracks(track, masteredTrack)
        audioPlayer?.isBypassActive = _uiState.value.isBypassActive

        _uiState.update {
            it.copy(
                rawTrack = track,
                masteredTrack = masteredTrack,
                rawAnalysis = preAnalysis,
                masteredAnalysis = result.masteredAnalysis,
                selectedPreset = preset,
                settings = settings,
                result = result,
                isProcessing = false,
                processingProgress = 1.0f,
                statusMessage = "Master listo: ${"%.1f".format(result.masteredAnalysis.integratedLufs)} LUFS (Techo: ${settings.limiterCeilingDb} dBTP)"
            )
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

    private fun reprocessWithSettings(newSettings: MasteringSettings) {
        val raw = _uiState.value.rawTrack ?: return
        viewModelScope.launch(Dispatchers.Default) {
            _uiState.update { it.copy(settings = newSettings) }
            val (mastered, result) = dspEngine.processTrack(raw, newSettings)
            audioPlayer?.updateMasteredTrack(mastered)
            _uiState.update {
                it.copy(
                    masteredTrack = mastered,
                    masteredAnalysis = result.masteredAnalysis,
                    result = result
                )
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
