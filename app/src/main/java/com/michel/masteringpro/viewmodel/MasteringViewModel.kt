package com.michel.masteringpro.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.michel.masteringpro.audio.AudioAnalyzer
import com.michel.masteringpro.audio.AudioLoader
import com.michel.masteringpro.audio.AudioPreviewPlayer
import com.michel.masteringpro.audio.AudioProcessor
import com.michel.masteringpro.audio.SuggestionEngine
import com.michel.masteringpro.audio.WavWriter
import com.michel.masteringpro.model.AnalysisResult
import com.michel.masteringpro.model.AudioTrackData
import com.michel.masteringpro.model.MasteringSession
import com.michel.masteringpro.model.MasteringSettings
import com.michel.masteringpro.model.MasteringUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MasteringViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<MasteringUiState>(MasteringUiState.Empty)
    val uiState: StateFlow<MasteringUiState> = _uiState.asStateFlow()

    private val previewPlayer = AudioPreviewPlayer { onPreviewFinished() }

    fun loadAudio(context: Context, uri: Uri) {
        previewPlayer.stop()
        _uiState.value = MasteringUiState.Loading()
        viewModelScope.launch {
            try {
                val track = withContext(Dispatchers.IO) { AudioLoader.load(context.applicationContext, uri) }
                _uiState.value = MasteringUiState.Loading("Midiendo LUFS, True Peak, dinámica y espectro…")
                val analysis = withContext(Dispatchers.Default) { AudioAnalyzer.analyze(track) }
                val suggestion = SuggestionEngine.suggest(analysis)
                _uiState.value = MasteringUiState.Analyzed(
                    MasteringSession(track = track, analysis = analysis, suggestion = suggestion)
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                _uiState.value = MasteringUiState.Error(
                    throwable.localizedMessage ?: "No se pudo cargar o analizar el audio"
                )
            }
        }
    }

    fun updateSettings(transform: (MasteringSettings) -> MasteringSettings) {
        val current = sessionForEditing() ?: return
        var updated = transform(current.settings).sanitized()
        if (current.analysis.clippingCount > 0L) updated = updated.copy(limiterEnabled = true)
        _uiState.value = MasteringUiState.Analyzed(current.copy(settings = updated))
    }

    fun resetToSuggested() {
        val current = sessionForEditing() ?: return
        _uiState.value = MasteringUiState.Analyzed(current.copy(settings = current.suggestion.settings))
    }

    fun editSettings() {
        val state = _uiState.value
        if (state is MasteringUiState.Processed) {
            previewPlayer.stop()
            _uiState.value = MasteringUiState.Analyzed(state.session)
        }
    }

    fun applyMastering() {
        val session = sessionForEditing() ?: return
        previewPlayer.stop()
        _uiState.value = MasteringUiState.Processing(session, 0f)
        viewModelScope.launch {
            try {
                val mastered = withContext(Dispatchers.Default) {
                    AudioProcessor.process(session.track, session.settings) { progress ->
                        _uiState.update { current ->
                            if (current is MasteringUiState.Processing) current.copy(progress = progress * 0.82f) else current
                        }
                    }
                }
                _uiState.update { current ->
                    if (current is MasteringUiState.Processing) current.copy(progress = 0.86f) else current
                }
                val resultAnalysis = withContext(Dispatchers.Default) { AudioAnalyzer.analyze(mastered) }
                _uiState.update { current ->
                    if (current is MasteringUiState.Processing) current.copy(progress = 0.95f) else current
                }
                val exported = runCatching { WavWriter.export(getApplication(), mastered) }
                val audioFile = exported.getOrNull()
                val fileError = exported.exceptionOrNull()?.localizedMessage
                _uiState.value = MasteringUiState.Processed(
                    session = session,
                    masteredTrack = mastered,
                    resultAnalysis = resultAnalysis,
                    fileUri = audioFile?.uri,
                    fileName = audioFile?.fileName,
                    exportError = fileError
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                _uiState.value = MasteringUiState.Error(
                    throwable.localizedMessage ?: "No se pudo completar el mastering"
                )
            }
        }
    }

    fun playPreview(master: Boolean) {
        val state = _uiState.value as? MasteringUiState.Processed ?: return
        if (previewPlayer.hasSession()) {
            previewPlayer.selectMaster(master)
            if (!state.isPlaying) previewPlayer.resume()
            _uiState.update { current ->
                if (current is MasteringUiState.Processed) current.copy(isPlaying = true, isMasterPreview = master) else current
            }
        } else {
            previewPlayer.play(state.session.track, state.masteredTrack, master)
            _uiState.update { current ->
                if (current is MasteringUiState.Processed) current.copy(isPlaying = true, isMasterPreview = master) else current
            }
        }
    }

    fun togglePlayback() {
        val state = _uiState.value as? MasteringUiState.Processed ?: return
        if (state.isPlaying) {
            previewPlayer.pause()
            _uiState.update { current ->
                if (current is MasteringUiState.Processed) current.copy(isPlaying = false) else current
            }
        } else if (previewPlayer.hasSession()) {
            previewPlayer.resume()
            _uiState.update { current ->
                if (current is MasteringUiState.Processed) current.copy(isPlaying = true) else current
            }
        } else {
            playPreview(state.isMasterPreview)
        }
    }

    fun stopPlayback() {
        previewPlayer.stop()
        _uiState.update { current ->
            if (current is MasteringUiState.Processed) current.copy(isPlaying = false) else current
        }
    }

    fun exportMasterAgain() {
        val state = _uiState.value as? MasteringUiState.Processed ?: return
        if (state.isExporting) return
        _uiState.value = state.copy(isExporting = true, exportError = null)
        viewModelScope.launch {
            val exported = runCatching { WavWriter.export(getApplication(), state.masteredTrack) }
            _uiState.update { current ->
                if (current is MasteringUiState.Processed) {
                    val result = exported.getOrNull()
                    current.copy(
                        isExporting = false,
                        fileUri = result?.uri ?: current.fileUri,
                        fileName = result?.fileName ?: current.fileName,
                        exportError = exported.exceptionOrNull()?.localizedMessage
                    )
                } else current
            }
        }
    }

    private fun sessionForEditing(): MasteringSession? = when (val state = _uiState.value) {
        is MasteringUiState.Analyzed -> state.session
        is MasteringUiState.Processed -> state.session
        else -> null
    }

    private fun onPreviewFinished() {
        _uiState.update { current ->
            if (current is MasteringUiState.Processed) current.copy(isPlaying = false) else current
        }
    }

    override fun onCleared() {
        previewPlayer.close()
        super.onCleared()
    }

    private fun MasteringSettings.sanitized(): MasteringSettings = copy(
        gainDb = gainDb.coerceIn(-24f, 24f),
        lowEqDb = lowEqDb.coerceIn(-9f, 9f),
        bodyEqDb = bodyEqDb.coerceIn(-9f, 9f),
        presenceEqDb = presenceEqDb.coerceIn(-9f, 9f),
        airEqDb = airEqDb.coerceIn(-9f, 9f),
        compressorThresholdDb = compressorThresholdDb.coerceIn(-48f, 0f),
        compressorRatio = compressorRatio.coerceIn(1f, 12f),
        compressorAttackMs = compressorAttackMs.coerceIn(0.1f, 100f),
        compressorReleaseMs = compressorReleaseMs.coerceIn(10f, 1_000f),
        limiterThresholdDb = limiterThresholdDb.coerceIn(-6f, 0f),
        limiterReleaseMs = limiterReleaseMs.coerceIn(5f, 500f),
        targetLufs = -14f
    )
}
