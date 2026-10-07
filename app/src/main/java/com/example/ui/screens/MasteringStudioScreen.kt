package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
                        onClick = {
                            val isOldSchool = uiState.selectedPreset != PresetType.DEMBOW_OSCURO
                            viewModel.loadDemoBeat(!isOldSchool)
                        },
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

            // 1. Presets Selector & Auto-calibration
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PRESETS INTELIGENTES DE GÉNERO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 1.sp
                    )

                    // Auto-Calibrate button
                    FilledTonalButton(
                        onClick = { viewModel.autoCalibrate() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color(0xFF00E5FF)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp).testTag("auto_calibrate_button")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AUTO-CALIBRAR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(PresetType.values()) { preset ->
                        val isSelected = uiState.selectedPreset == preset
                        PresetCard(
                            preset = preset,
                            isSelected = isSelected,
                            onSelect = { viewModel.selectPreset(preset) },
                            modifier = Modifier.testTag("preset_${preset.name.lowercase()}")
                        )
                    }
                }
            }

            // 2. Real-time A/B Testing module with instant bypass toggle
            AbToggleSwitch(
                isBypassActive = uiState.isBypassActive,
                onToggleBypass = { viewModel.toggleBypass() },
                modifier = Modifier.testTag("ab_switch_container")
            )

            // Custom External Profile (.txt / .json) & Acoustic Analysis Export
            CustomProfileCard(
                isCustomProfileActive = uiState.isCustomProfileActive,
                customProfileName = uiState.customProfileName,
                onImportProfile = { profilePickerLauncher.launch("*/*") },
                onExportAnalysis = { viewModel.exportAnalysisAndParameters(context) },
                onDownloadTemplate = { viewModel.downloadProfileTemplate(context) }
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
                targetLufs = uiState.selectedPreset.targetLufs,
                isBypass = uiState.isBypassActive,
                peakL = uiState.livePeakL,
                peakR = uiState.livePeakR
            )

            // 5. Quick Hardware Sliders Rack
            MasteringSlidersRack(
                settings = uiState.settings,
                onSubGainChanged = { gain -> viewModel.updateEqBandGain("sub", gain) },
                onSnareGainChanged = { gain -> viewModel.updateEqBandGain("snare", gain) },
                onSaturationChanged = { drive -> viewModel.updateSaturationDrive(drive) },
                onLimiterCeilingChanged = { ceil -> viewModel.updateLimiterCeiling(ceil) },
                onAutoCorrect = { viewModel.autoCorrectCurrentMaster() }
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
                        Text("¡Master Renderizado!", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "Archivo guardado exitosamente en Música/ReggaetonMasterDSP:",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = export.fileName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/wav"
                                putExtra(Intent.EXTRA_STREAM, export.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartir Master WAV"))
                            viewModel.dismissExportState()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Compartir")
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
private fun PresetCard(
    preset: PresetType,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(180.dp)
            .clickable(onClick = onSelect)
            .border(
                1.5.dp,
                if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E293B),
                RoundedCornerShape(10.dp)
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF132238) else Color(0xFF0E141E)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = preset.targetLufs.toString() + " LUFS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xFFFBBF24)
                )
                if (isSelected) {
                    Box(modifier = Modifier.size(6.dp).background(Color(0xFF00E5FF), CircleShape))
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = preset.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = preset.subtitle,
                fontSize = 10.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
                    title = "RANGO DINÁMICO",
                    value = "%.1f dB".format(active.dynamicRangeDb),
                    subText = "Crest Factor",
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
    onSnareGainChanged: (Float) -> Unit,
    onSaturationChanged: (Float) -> Unit,
    onLimiterCeilingChanged: (Float) -> Unit,
    onAutoCorrect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1723)),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1E293B)))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CONTROLES RÁPIDOS DE HARDWARE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.sp
                )
                FilledTonalButton(
                    onClick = onAutoCorrect,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF1E293B),
                        contentColor = Color(0xFF38BDF8)
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(26.dp).testTag("auto_correct_master_button")
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AUTO-CORREGIR", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 1. Sub-Bass 40-60Hz
            SliderParamRow(
                label = "Sub-Bass 45-60Hz",
                valueText = "${if (settings.subBassBand.gainDb >= 0) "+" else ""}%.1f dB".format(settings.subBassBand.gainDb),
                value = settings.subBassBand.gainDb,
                valueRange = -6.0f..12.0f,
                onValueChange = onSubGainChanged
            )

            // 2. Snare Presence / Attack 3.5kHz
            SliderParamRow(
                label = "Pegada / Redoblante (3.5 kHz)",
                valueText = "${if (settings.snarePresenceBand.gainDb >= 0) "+" else ""}%.1f dB".format(settings.snarePresenceBand.gainDb),
                value = settings.snarePresenceBand.gainDb,
                valueRange = -6.0f..12.0f,
                onValueChange = onSnareGainChanged
            )

            // 3. Tube Saturation Drive
            SliderParamRow(
                label = "Saturación Armónica Analógica",
                valueText = "${(settings.saturationDrive.coerceIn(0f, 1f) * 100).toInt()}%",
                value = settings.saturationDrive,
                valueRange = 0.0f..1.0f,
                onValueChange = onSaturationChanged
            )

            // 4. Limiter Ceiling
            SliderParamRow(
                label = "Techo Limitador True-Peak",
                valueText = "%.2f dBTP".format(settings.limiterCeilingDb),
                value = settings.limiterCeilingDb,
                valueRange = -3.0f..0.0f,
                onValueChange = onLimiterCeilingChanged
            )
        }
    }
}

@Composable
private fun SliderParamRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    var localValue by remember(value) { mutableStateOf(value.coerceIn(valueRange.start, valueRange.endInclusive)) }

    LaunchedEffect(value) {
        localValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 11.sp, color = Color(0xFFCBD5E1))
            Text(valueText, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
        }
        Slider(
            value = localValue.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = {
                localValue = it.coerceIn(valueRange.start, valueRange.endInclusive)
            },
            onValueChangeFinished = {
                onValueChange(localValue.coerceIn(valueRange.start, valueRange.endInclusive))
            },
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF00E5FF),
                activeTrackColor = Color(0xFF0284C7),
                inactiveTrackColor = Color(0xFF1E293B)
            ),
            modifier = Modifier.fillMaxWidth()
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
