package com.michel.masteringpro.model

import android.net.Uri

sealed interface MasteringUiState {
    data object Empty : MasteringUiState
    data class Loading(val message: String = "Cargando y analizando el track…") : MasteringUiState
    data class Analyzed(val session: MasteringSession) : MasteringUiState
    data class Processing(val session: MasteringSession, val progress: Float) : MasteringUiState
    data class Processed(
        val session: MasteringSession,
        val masteredTrack: AudioTrackData,
        val resultAnalysis: AnalysisResult,
        val fileUri: Uri? = null,
        val fileName: String? = null,
        val exportError: String? = null,
        val isExporting: Boolean = false,
        val isPlaying: Boolean = false,
        val isMasterPreview: Boolean = false
    ) : MasteringUiState
    data class Error(val message: String) : MasteringUiState
}
