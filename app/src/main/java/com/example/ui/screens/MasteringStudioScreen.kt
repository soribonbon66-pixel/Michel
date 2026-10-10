package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.PresetType
import com.example.ui.components.*
import com.example.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasteringStudioScreen(
    viewModel: MasteringViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // File picker launcher
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.loadAudioFromUri(context, uri)
        }
    }

    // Custom profile (.txt / .json) picker launcher
    val profilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.loadProfileFromUri(context, uri)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFF070B11),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "REGGAETON MASTER",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF00E5FF)
                            ) {
                                Text(
                                    text = "DSP",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF070B11),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = uiState.rawTrack?.title ?: "Sin pista cargada",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    // Demo Beat loader button
                    IconButton(
                        onClick = { viewModel.loadDemoBeat() },
                        modifier = Modifier.testTag("load_demo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Cargar beat demo",
                            tint = Color(0xFF00E5FF)
                        )
                    }

                    // Open file picker
                    IconButton(
                        onClick = { audioPickerLauncher.launch("audio/*") },
                        modifier = Modifier.testTag("load_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Cargar archivo de audio",
                            tint = Color.White
                        )
                    }

                    // Technical Report dialog toggle
                    IconButton(
                        onClick = { viewModel.toggleReportDialog(true) },
                        modifier = Modifier.testTag("show_report_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = "Informe Técnico",
                            tint = Color(0xFFFBBF24)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0C121B),
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            // Prominent [Download / Export WAV] action bar
            Surface(
                color = Color(0xFF0C121B),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1E293B)))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Button(
                        onClick = { viewModel.exportMasterWav(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("download_master_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0284C7)
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DESCARGAR MASTER WAV (HI-FI)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Processing status banner
            if (uiState.isProcessing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = Color(0xFF00E5FF),
                    trackColor = Color(0xFF1E293B)
                )
            }

            // Status ribbon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111827), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (uiState.isProcessing) Color(0xFFFBBF24) else Color(0xFF10B981), CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = uiState.statusMessage,
                    fontSize = 11.sp,
                    color = Color(0xFFE2E8F0),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 1. Análisis Completo del Track (aparece al cargar pista o demo)
            CompleteTrackAnalysisCard(
                track = uiState.rawTrack,
                analysis = uiState.rawAnalysis,
                onDownloadAnalysis = { viewModel.exportAnalysisAndParameters(context) }
            )

            // 2. Cargar Presets desde Archivo (.txt / .json) o Descargar Plantilla
            CustomProfileCard(
                isCustomProfileActive = uiState.isCustomProfileActive,
                customProfileName = uiState.customProfileName,
                onImportProfile = { profilePickerLauncher.launch("*/*") },
                onExportAnalysis = { viewModel.exportAnalysisAndParameters(context) },
                onDownloadTemplate = { viewModel.downloadProfileTemplate(context) }
            )

            // 3. Comparación A/B en Vivo (Master vs Bypass Raw)
            AbToggleSwitch(
                isBypassActive = uiState.isBypassActive,
                onToggleBypass = { viewModel.toggleBypass() },
                modifier = Modifier.testTag("ab_switch_container")
            )

            // 3. Tab Switcher (Spectrum Visualizer vs EQ Curve vs Multiband Compressor)
            TabRow(
                selectedTabIndex = when (uiState.activeTab) {
                    StudioTab.SPECTRUM -> 0
                    StudioTab.EQ_CURVE -> 1
                    StudioTab.DYNAMICS -> 2
                    StudioTab.REPORT -> 0
                },
                containerColor = Color(0xFF0D131C),
                contentColor = Color(0xFF00E5FF),
                divider = {},
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
            ) {
                Tab(
                    selected = uiState.activeTab == StudioTab.SPECTRUM,
                    onClick = { viewModel.setActiveTab(StudioTab.SPECTRUM) },
                    text = { Text("ESPECTRO RTA", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = uiState.activeTab == StudioTab.EQ_CURVE,
                    onClick = { viewModel.setActiveTab(StudioTab.EQ_CURVE) },
                    text = { Text("CURVA EQ (5B)", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = uiState.activeTab == StudioTab.DYNAMICS,
                    onClick = { viewModel.setActiveTab(StudioTab.DYNAMICS) },
                    text = { Text("COMPRESOR MB", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
            }

            // Visualizer view
            when (uiState.activeTab) {
                StudioTab.SPECTRUM -> {
                    SpectrumVisualizer(
                        rawAnalysis = uiState.rawAnalysis,
                        masteredAnalysis = uiState.masteredAnalysis,
                        isBypassActive = uiState.isBypassActive,
                        livePeak = maxOf(uiState.livePeakL, uiState.livePeakR),
                        isPlaying = uiState.isPlaying,
                        modifier = Modifier.testTag("spectrum_visualizer")
                    )
                }
                StudioTab.EQ_CURVE -> {
                    ParametricEqCurve(
                        settings = uiState.settings,
                        onBandGainChanged = { id, gain -> viewModel.updateEqBandGain(id, gain) },
                        modifier = Modifier.testTag("parametric_eq_curve")
                    )
                }
                StudioTab.DYNAMICS -> {
                    MultibandCompressorUi(
                        bands = uiState.settings.multibandBands,
                        selectedBandId = uiState.selectedCompressorBandId,
                        onSelectBand = { viewModel.selectCompressorBand(it) },
                        onGainChanged = { id, gain -> viewModel.updateCompressorGain(id, gain) },
                        onThresholdChanged = { id, th -> viewModel.updateCompressorThreshold(id, th) },
                        onRatioChanged = { id, r -> viewModel.updateCompressorRatio(id, r) },
                        onNudgeGain = { id, delta -> viewModel.nudgeCompressorGain(id, delta) },
                        onNudgeThreshold = { id, delta -> viewModel.nudgeCompressorThreshold(id, delta) },
                        onNudgeRatio = { id, delta -> viewModel.nudgeCompressorRatio(id, delta) },
                        onToggleBypass = { viewModel.toggleCompressorBandBypass(it) },
                        modifier = Modifier.testTag("multiband_compressor_view")
                    )
                }
                else -> {}
            }

            // 4. Studio Meters & LUFS Metrics Card
            StudioMetersRack(
                rawAnalysis = uiState.rawAnalysis,
                masteredAnalysis = uiState.masteredAnalysis,
                targetLufs = -14.0f,
                isBypass = uiState.isBypassActive,
                peakL = uiState.livePeakL,
                peakR = uiState.livePeakR
            )

            // 5. Controles de Hardware y Sliders Manuales (Sin presets)
            MasteringSlidersRack(
                settings = uiState.settings,
                onSubGainChanged = { gain -> viewModel.updateEqBandGain("sub", gain) },
                onKickGainChanged = { gain -> viewModel.updateEqBandGain("kick", gain) },
                onMudGainChanged = { gain -> viewModel.updateEqBandGain("mud", gain) },
                onSnareGainChanged = { gain -> viewModel.updateEqBandGain("snare", gain) },
                onAirGainChanged = { gain -> viewModel.updateEqBandGain("air", gain) },
                onSaturationChanged = { drive -> viewModel.updateSaturationDrive(drive) },
                onStereoWidthChanged = { width -> viewModel.updateStereoWidth(width) },
                onMonoSubChanged = { freq -> viewModel.updateMonoSubFreq(freq) },
                onMakeupGainChanged = { gain -> viewModel.updateMakeupGain(gain) },
                onLimiterCeilingChanged = { ceil -> viewModel.updateLimiterCeiling(ceil) },
                onResetToFlat = { viewModel.resetToFlatSettings() }
            )

            // 6. Playback Controller
            PlaybackControlsCard(
                isPlaying = uiState.isPlaying,
                isLooping = uiState.isLooping,
                currentSec = uiState.playbackCurrentSec,
                totalSec = uiState.playbackTotalSec,
                progress = uiState.playbackProgress,
                onPlayPause = { viewModel.togglePlayPause() },
                onSeek = { p -> viewModel.seek(p) },
                onToggleLoop = { viewModel.toggleLoop() }
            )

            // 7. Technical report summary card snippet
            uiState.result?.let { res ->
                TechnicalSummaryCard(
                    result = res,
                    onOpenFullReport = { viewModel.toggleReportDialog(true) }
                )
            }
        }
    }

    // Full Technical Report Dialog
    if (uiState.showReportDialog) {
        TechnicalReportDialog(
            result = uiState.result,
            onDismiss = { viewModel.toggleReportDialog(false) }
        )
    }

    // Export Result Dialog
    when (val export = uiState.exportState) {
        is ExportState.Exporting -> {
            AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text("Renderizando Audio Hi-Fi...", color = Color.White) },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(color = Color(0xFF00E5FF))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Aplicando dither TPDF a 16-bit 44.1kHz...", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }
                },
                containerColor = Color(0xFF0F1723)
            )
        }
        is ExportState.Success -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissExportState() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(export.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = export.description,
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = export.fileName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )

                        if (export.previewContent != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF090D14),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1E293B))
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 180.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .verticalScroll(rememberScrollState())
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = export.previewContent,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8),
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (export.previewContent != null) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Mastering Data", export.previewContent)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Texto copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copiar", fontSize = 11.sp)
                            }
                        }
                        Button(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = export.mimeType
                                    putExtra(Intent.EXTRA_STREAM, export.uri)
                                    if (export.previewContent != null) {
                                        putExtra(Intent.EXTRA_TEXT, export.previewContent)
                                    }
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Compartir ${export.fileName}"))
                                viewModel.dismissExportState()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compartir", fontSize = 12.sp)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissExportState() }) {
                        Text("Cerrar", color = Color(0xFF94A3B8))
                    }
                },
                containerColor = Color(0xFF0F1723)
            )
        }
        is ExportState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.dismissExportState() },
                title = { Text("Error al Exportar", color = Color(0xFFEF4444)) },
                text = { Text(export.message, color = Color(0xFFCBD5E1)) },
                confirmButton = {
                    Button(onClick = { viewModel.dismissExportState() }) {
                        Text("OK")
                    }
                },
                containerColor = Color(0xFF0F1723)
            )
        }
        ExportState.Idle -> {}
    }
}

@Composable
private fun StudioMetersRack(
    rawAnalysis: com.example.model.AudioAnalysis,
    masteredAnalysis: com.example.model.AudioAnalysis,
    targetLufs: Float,
    isBypass: Boolean,
    peakL: Float,
    peakR: Float
) {
    val active = if (isBypass) rawAnalysis else masteredAnalysis

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1723)),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1E293B)))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Real-Time LUFS Horizontal Gauge Meter Bar
            LufsMeterBar(
                currentLufs = active.integratedLufs,
                targetLufs = targetLufs,
                shortTermLufs = active.shortTermMaxLufs,
                isMastered = !isBypass
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "MEDICIONES DE DINÁMICA & PICOS",
                fontSize = 10.sp,
                color = Color(0xFF94A3B8),
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Integrated LUFS
                LoudnessPill(
                    title = "INTEGRATED",
                    value = "%.1f LUFS".format(active.integratedLufs),
                    subText = "Meta: -8.5 LUFS",
                    accentColor = Color(0xFF00E5FF)
                )

                // Short Term LUFS
                LoudnessPill(
                    title = "SHORT-TERM",
                    value = "%.1f LUFS".format(active.shortTermMaxLufs),
                    subText = "Pico ventana 3s",
                    accentColor = Color(0xFF38BDF8)
                )

                // True Peak dBTP
                LoudnessPill(
                    title = "TRUE PEAK",
                    value = "%.2f dBTP".format(active.truePeakDbfs),
                    subText = if (active.truePeakDbfs >= 0f) "CLIP DETECTADO" else "Seguro anti-clip",
                    accentColor = if (active.truePeakDbfs >= 0f) Color(0xFFFF1744) else Color(0xFF10B981)
                )

                // Crest Factor PLR
                LoudnessPill(
                    title = "FACTOR CRESTA",
                    value = "%.1f dB".format(active.crestFactorDb),
                    subText = "Pk - RMS (PLR)",
                    accentColor = Color(0xFFFBBF24)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stereo VU Meter
            StereoVuMeter(peakL = peakL, peakR = peakR, isClipping = active.hasClipping)
        }
    }
}

@Composable
private fun LoudnessPill(
    title: String,
    value: String,
    subText: String,
    accentColor: Color
) {
    Column {
        Text(text = title, fontSize = 9.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accentColor)
        Text(text = subText, fontSize = 8.sp, color = Color(0xFF475569))
    }
}

@Composable
private fun MasteringSlidersRack(
    settings: com.example.model.MasteringSettings,
    onSubGainChanged: (Float) -> Unit,
    onKickGainChanged: (Float) -> Unit,
    onMudGainChanged: (Float) -> Unit,
    onSnareGainChanged: (Float) -> Unit,
    onAirGainChanged: (Float) -> Unit,
    onSaturationChanged: (Float) -> Unit,
    onStereoWidthChanged: (Float) -> Unit,
    onMonoSubChanged: (Float) -> Unit,
    onMakeupGainChanged: (Float) -> Unit,
    onLimiterCeilingChanged: (Float) -> Unit,
    onResetToFlat: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1723)),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1E293B)))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header with Reset button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AJUSTES MANUALES CON SLIDERS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Modifica los parámetros para moldear el sonido",
                        fontSize = 9.sp,
                        color = Color(0xFF64748B)
                    )
                }

                // Reset to flat button
                OutlinedButton(
                    onClick = onResetToFlat,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF334155))
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp).testTag("reset_flat_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("PLANO (0 dB)", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Section A: Ecualizador 5 Bandas
            Text(
                text = "ECUALIZADOR DE 5 BANDAS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))

            SliderParamRow(
                label = "Sub-Bass Redondo (50 Hz)",
                value = settings.subBassBand.gainDb,
                valueRange = -6.0f..12.0f,
                formatter = { "${if (it >= 0) "+" else ""}%.1f dB".format(it) },
                onValueChange = onSubGainChanged,
                testTag = "sub_gain_slider"
            )

            SliderParamRow(
                label = "Pegada de Bombo / Kick (85 Hz)",
                value = settings.kickPunchBand.gainDb,
                valueRange = -6.0f..12.0f,
                formatter = { "${if (it >= 0) "+" else ""}%.1f dB".format(it) },
                onValueChange = onKickGainChanged,
                testTag = "kick_gain_slider"
            )

            SliderParamRow(
                label = "Limpieza Mud Scoop (280 Hz)",
                value = settings.mudCutBand.gainDb,
                valueRange = -6.0f..6.0f,
                formatter = { "${if (it >= 0) "+" else ""}%.1f dB".format(it) },
                onValueChange = onMudGainChanged,
                testTag = "mud_gain_slider"
            )

            SliderParamRow(
                label = "Mordida de Redoblante (3.2 kHz)",
                value = settings.snarePresenceBand.gainDb,
                valueRange = -6.0f..12.0f,
                formatter = { "${if (it >= 0) "+" else ""}%.1f dB".format(it) },
                onValueChange = onSnareGainChanged,
                testTag = "snare_gain_slider"
            )

            SliderParamRow(
                label = "Brillo y Aire Air Shelf (11 kHz)",
                value = settings.airShelfBand.gainDb,
                valueRange = -6.0f..12.0f,
                formatter = { "${if (it >= 0) "+" else ""}%.1f dB".format(it) },
                onValueChange = onAirGainChanged,
                testTag = "air_gain_slider"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Section B: Color Analógico & Estéreo
            Text(
                text = "COLOR ANALÓGICO & ESPACIO ESTÉREO",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFBBF24),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))

            SliderParamRow(
                label = "Saturación Armónica Analógica",
                value = settings.saturationDrive,
                valueRange = 0.0f..1.0f,
                formatter = { "${(it * 100).toInt()}%" },
                onValueChange = onSaturationChanged,
                testTag = "saturation_slider"
            )

            SliderParamRow(
                label = "Apertura de Imagen Estéreo",
                value = settings.stereoWidthFactor,
                valueRange = 0.5f..2.0f,
                formatter = { "%.2fx".format(it) },
                onValueChange = onStereoWidthChanged,
                testTag = "stereo_width_slider"
            )

            SliderParamRow(
                label = "Corte Sub en Mono (Mono-Maker)",
                value = settings.monoSubFreqHz,
                valueRange = 20.0f..150.0f,
                formatter = { "${it.toInt()} Hz" },
                onValueChange = onMonoSubChanged,
                testTag = "mono_sub_slider"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Section C: Limitador True-Peak
            Text(
                text = "LIMITADOR INTELIGENTE TRUE-PEAK",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF38BDF8),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))

            SliderParamRow(
                label = "Ganancia Makeup de Entrada",
                value = settings.makeupGainDb,
                valueRange = 0.0f..12.0f,
                formatter = { "+%.1f dB".format(it) },
                onValueChange = onMakeupGainChanged,
                testTag = "makeup_gain_slider"
            )

            SliderParamRow(
                label = "Techo de Seguridad True-Peak",
                value = settings.limiterCeilingDb,
                valueRange = -3.0f..0.0f,
                formatter = { "%.2f dBTP".format(it) },
                onValueChange = onLimiterCeilingChanged,
                testTag = "limiter_ceiling_slider"
            )
        }
    }
}

@Composable
private fun SliderParamRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    formatter: (Float) -> String,
    onValueChange: (Float) -> Unit,
    testTag: String = ""
) {
    val safeStart = if (value.isNaN()) valueRange.start else value.coerceIn(valueRange.start, valueRange.endInclusive)
    var localValue by remember(value) { mutableStateOf(safeStart) }

    LaunchedEffect(value) {
        val safe = if (value.isNaN()) valueRange.start else value.coerceIn(valueRange.start, valueRange.endInclusive)
        localValue = safe
    }

    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 11.sp, color = Color(0xFFCBD5E1))
            Text(
                text = formatter(localValue),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF),
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
        Slider(
            value = localValue.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = {
                val clamped = if (it.isNaN()) valueRange.start else it.coerceIn(valueRange.start, valueRange.endInclusive)
                localValue = clamped
            },
            onValueChangeFinished = {
                val clamped = localValue.coerceIn(valueRange.start, valueRange.endInclusive)
                try {
                    onValueChange(clamped)
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            },
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF00E5FF),
                activeTrackColor = Color(0xFF0284C7),
                inactiveTrackColor = Color(0xFF1E293B)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier)
        )
    }
}

@Composable
private fun PlaybackControlsCard(
    isPlaying: Boolean,
    isLooping: Boolean,
    currentSec: Float,
    totalSec: Float,
    progress: Float,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleLoop: () -> Unit
) {
    var localProgress by remember(progress) { mutableStateOf(progress.coerceIn(0f, 1f)) }
    var isUserDragging by remember { mutableStateOf(false) }

    LaunchedEffect(progress) {
        if (!isUserDragging) {
            localProgress = progress.coerceIn(0f, 1f)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1723)),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1E293B)))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Slider seekbar with non-conflicting drag lifecycle
            Slider(
                value = localProgress.coerceIn(0f, 1f),
                onValueChange = {
                    isUserDragging = true
                    localProgress = it.coerceIn(0f, 1f)
                },
                onValueChangeFinished = {
                    isUserDragging = false
                    onSeek(localProgress.coerceIn(0f, 1f))
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF00E5FF),
                    inactiveTrackColor = Color(0xFF1E293B)
                ),
                modifier = Modifier.fillMaxWidth().testTag("playback_seekbar")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time label
                Text(
                    text = "${formatTime(currentSec)} / ${formatTime(totalSec)}",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )

                // Controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Loop
                    IconButton(
                        onClick = onToggleLoop,
                        modifier = Modifier.size(36.dp).testTag("loop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Repetir",
                            tint = if (isLooping) Color(0xFF00E5FF) else Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Play / Pause FAB style button
                    FilledIconButton(
                        onClick = onPlayPause,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFF00E5FF)
                        ),
                        modifier = Modifier.size(44.dp).testTag("play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            tint = Color(0xFF070B11),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TechnicalSummaryCard(
    result: com.example.model.MasteringResult,
    onOpenFullReport: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenFullReport),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B121C)),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1E3A8A)))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("INFORME TÉCNICO GENERADO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ajuste: ${"%.1f".format(result.originalAnalysis.integratedLufs)} LUFS → ${"%.1f".format(result.masteredAnalysis.integratedLufs)} LUFS | GR Máx: -${"%.1f".format(result.maxGainReductionDb)} dB | ${result.peakPreventedClips} clips evitados",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF00E5FF))
        }
    }
}

private fun formatTime(seconds: Float): String {
    val secInt = seconds.toInt().coerceAtLeast(0)
    val mins = secInt / 60
    val secs = secInt % 60
    return "%02d:%02d".format(mins, secs)
}
