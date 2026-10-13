package com.michel.masteringpro.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.michel.masteringpro.model.MasteringUiState
import com.michel.masteringpro.ui.components.DataPill
import com.michel.masteringpro.ui.theme.MichelColors
import com.michel.masteringpro.viewmodel.MasteringViewModel
import java.util.Locale

@Composable
fun MichelApp(viewModel: MasteringViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val openAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.loadAudio(context, uri)
        }
    }
    val loadAudio = { openAudio.launch(arrayOf("audio/*")) }
    BackHandler(enabled = state is MasteringUiState.Processed) { viewModel.editSettings() }

    Scaffold(containerColor = MichelColors.Background) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
        ) {
            AppHeader()
            Divider(color = MichelColors.Border.copy(alpha = 0.8f))
            when (val current = state) {
                MasteringUiState.Empty -> EmptyScreen(onLoad = loadAudio)
                is MasteringUiState.Loading -> LoadingScreen(current.message)
                is MasteringUiState.Analyzed -> AnalysisScreen(
                    session = current.session,
                    onSettingsChanged = { updated -> viewModel.updateSettings { updated } },
                    onReset = viewModel::resetToSuggested,
                    onApply = viewModel::applyMastering,
                    onLoadAnother = loadAudio
                )
                is MasteringUiState.Processing -> ProcessingScreen(current)
                is MasteringUiState.Processed -> ResultScreen(
                    state = current,
                    onListenOriginal = { viewModel.playPreview(master = false) },
                    onListenMaster = { viewModel.playPreview(master = true) },
                    onTogglePlayback = viewModel::togglePlayback,
                    onExport = viewModel::exportMasterAgain,
                    onShare = {
                        val uri = current.fileUri
                        if (uri != null) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/wav"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            runCatching {
                                context.startActivity(Intent.createChooser(shareIntent, "Compartir master de Michel"))
                            }
                        }
                    },
                    onEdit = viewModel::editSettings
                )
                is MasteringUiState.Error -> ErrorScreen(current.message, onLoad = loadAudio)
            }
        }
    }
}

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 19.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(15.dp), color = MichelColors.Primary) {
            androidx.compose.material3.Icon(
                Icons.Default.GraphicEq,
                contentDescription = null,
                tint = MichelColors.Background,
                modifier = Modifier.padding(10.dp).size(23.dp)
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 11.dp)) {
            Text("MICHEL", color = MichelColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
            Text("MASTERING PRO  ·  AUDIO LAB", color = MichelColors.Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
        }
        DataPill("100% OFFLINE", MichelColors.Primary)
    }
}

@Composable
private fun EmptyScreen(onLoad: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MichelColors.Surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Surface(shape = CircleShape, color = MichelColors.Primary.copy(alpha = 0.12f)) {
                    androidx.compose.material3.Icon(Icons.Default.MusicNote, contentDescription = null, tint = MichelColors.Primary, modifier = Modifier.padding(17.dp).size(32.dp))
                }
                Spacer(Modifier.height(23.dp))
                Text("TU TRACK.\nTU MASTER.", color = MichelColors.Text, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(12.dp))
                Text("Michel analiza y masteriza tu mezcla directamente en el dispositivo. Sin cuentas, sin subidas y sin servidor.", color = MichelColors.Muted, fontSize = 14.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = onLoad,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MichelColors.Primary, contentColor = MichelColors.Background)
                ) {
                    androidx.compose.material3.Icon(Icons.Default.AudioFile, contentDescription = null)
                    Spacer(Modifier.size(9.dp))
                    Text("CARGAR AUDIO", fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                }
                Spacer(Modifier.height(13.dp))
                Text("MP3 · WAV · AAC · M4A · FLAC (según decoder del dispositivo)", color = MichelColors.Faint, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
        Spacer(Modifier.height(22.dp))
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MichelColors.Elevated)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StepRow("01", "ANALIZAR", "LUFS · True Peak · FFT · fase")
                StepRow("02", "AJUSTAR", "Sugerencias editables")
                StepRow("03", "MASTERIZAR", "A/B · WAV 32-bit float")
            }
        }
    }
}

@Composable
private fun StepRow(number: String, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(number, color = MichelColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(end = 12.dp))
        Column {
            Text(title, color = MichelColors.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
            Text(detail, color = MichelColors.Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = MichelColors.Primary)
        Spacer(Modifier.height(22.dp))
        Text(message, color = MichelColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("El audio permanece en este dispositivo.", color = MichelColors.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ProcessingScreen(state: MasteringUiState.Processing) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = CircleShape, color = MichelColors.Primary.copy(alpha = 0.12f)) {
            androidx.compose.material3.Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MichelColors.Primary, modifier = Modifier.padding(19.dp).size(34.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text("PROCESANDO MASTER…", color = MichelColors.Text, fontSize = 19.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text("Gain → EQ → compresor → limiter → normalización LUFS", color = MichelColors.Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        LinearProgressIndicator(
            progress = state.progress.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth().height(7.dp).background(Color.Transparent, RoundedCornerShape(10.dp)),
            color = MichelColors.Primary,
            trackColor = MichelColors.Border
        )
        Spacer(Modifier.height(8.dp))
        Text("${(state.progress * 100).toInt()}%", color = MichelColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Text(state.session.track.title, color = MichelColors.Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ErrorScreen(message: String, onLoad: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("NO SE PUDO COMPLETAR", color = MichelColors.Red, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(9.dp))
        Text(message, color = MichelColors.Muted, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 19.sp)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onLoad, colors = ButtonDefaults.buttonColors(containerColor = MichelColors.Primary, contentColor = MichelColors.Background)) {
            androidx.compose.material3.Icon(Icons.Default.AudioFile, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("ELEGIR OTRO AUDIO", fontWeight = FontWeight.Bold)
        }
    }
}
