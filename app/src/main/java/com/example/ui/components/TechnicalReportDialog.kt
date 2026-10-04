package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.MasteringResult

/**
 * Technical mastering report dialog displaying exact metrics:
 * Target LUFS achieved, EQ bands modified, gain reduction applied, dynamic range changes, and anti-clipping log.
 */
@Composable
fun TechnicalReportDialog(
    result: MasteringResult?,
    onDismiss: () -> Unit
) {
    if (result == null) return

    val orig = result.originalAnalysis
    val mast = result.masteredAnalysis
    val settings = result.appliedSettings

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .testTag("technical_report_dialog"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1723)),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF00E5FF)))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "INFORME TÉCNICO DSP",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Preset: ${settings.presetType.title}",
                            fontSize = 12.sp,
                            color = Color(0xFF00E5FF)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_report_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color(0xFF94A3B8))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFF1E293B))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Metric 1: Loudness & Target LUFS
                    ReportSectionCard(
                        title = "1. SONORIDAD INTEGRADA (ITU-R BS.1770)",
                        icon = Icons.Default.Speed
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricColumn("LUFS Original", "%.1f LUFS".format(orig.integratedLufs), Color(0xFF94A3B8))
                            MetricColumn("Objetivo Preset", "%.1f LUFS".format(settings.presetType.targetLufs), Color(0xFFFBBF24))
                            MetricColumn("Master Logrado", "%.1f LUFS".format(mast.integratedLufs), Color(0xFF00E5FF))
                        }
                    }

                    // Metric 2: True Peak & Clipping Prevention
                    ReportSectionCard(
                        title = "2. PREVENCIÓN DE CLIPPING & TECHO TRUE-PEAK",
                        icon = Icons.Default.Shield
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricColumn("Pico Original", "%.2f dBFS".format(orig.truePeakDbfs), if (orig.truePeakDbfs >= 0f) Color(0xFFFF1744) else Color(0xFF94A3B8))
                            MetricColumn("Techo Ceiling", "%.2f dBTP".format(settings.limiterCeilingDb), Color(0xFF00E5FF))
                            MetricColumn("Picos Frenados", "${result.peakPreventedClips} intersamples", Color(0xFF10B981))
                        }
                    }

                    // Metric 3: Dynamics & Limiter Gain Reduction
                    ReportSectionCard(
                        title = "3. REDUCCIÓN DE GANANCIA & DINÁMICA (GR)",
                        icon = Icons.Default.Tune
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricColumn("Makeup Gain", "+%.1f dB".format(settings.makeupGainDb), Color(0xFF00E5FF))
                            MetricColumn("Max Gain Reduction", "-%.1f dB".format(result.maxGainReductionDb), Color(0xFFF59E0B))
                            MetricColumn("Release Adaptativo", "${settings.limiterReleaseMs.toInt()} ms", Color(0xFF94A3B8))
                        }
                    }

                    // Metric 4: EQ Bands Modified
                    ReportSectionCard(
                        title = "4. BANDAS ECUALIZADAS PARAMÉTRICAMENTE",
                        icon = Icons.Default.GraphicEq
                    ) {
                        settings.getAllBands().forEach { band ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${band.name} (${band.frequencyHz.toInt()} Hz, Q=${"%.1f".format(band.qFactor)})",
                                    fontSize = 11.sp,
                                    color = Color(0xFFE2E8F0)
                                )
                                Text(
                                    text = (if (band.gainDb >= 0) "+" else "") + "%.1f dB".format(band.gainDb),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (band.gainDb > 0) Color(0xFF00E5FF) else if (band.gainDb < 0) Color(0xFFF59E0B) else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }

                    // Metric 5: Auto-calibration decision log
                    if (settings.autoAdjustmentLog.isNotEmpty()) {
                        ReportSectionCard(
                            title = "5. DECISIONES DEL ALGORITMO AUTO-AJUSTABLE",
                            icon = Icons.Default.AutoFixHigh
                        ) {
                            settings.autoAdjustmentLog.forEach { logItem ->
                                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                    Text("• ", color = Color(0xFF00E5FF), fontSize = 11.sp)
                                    Text(
                                        text = logItem,
                                        fontSize = 10.sp,
                                        color = Color(0xFFCBD5E1),
                                        lineHeight = 14.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("ENTENDIDO", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ReportSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B1017), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                letterSpacing = 0.5.sp
            )
        }
        content()
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    valueColor: Color
) {
    Column {
        Text(text = label, fontSize = 10.sp, color = Color(0xFF64748B))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}
