package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioAnalysis
import com.example.model.AudioTrackData

/**
 * Complete Acoustic Analysis card shown when a track is loaded.
 * Displays ITU-R BS.1770-4 / EBU R128 loudness metrics, FFT spectral breakdown,
 * acoustic diagnostic summary, and direct download / copy actions.
 */
@Composable
fun CompleteTrackAnalysisCard(
    track: AudioTrackData?,
    analysis: AudioAnalysis,
    onDownloadAnalysis: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(true) }

    val durationSec = if (track != null && track.sampleRate > 0 && track.channels > 0) {
        (track.samples.size.toFloat() / (track.sampleRate * track.channels))
    } else 0f

    val minSec = (durationSec / 60).toInt()
    val remSec = (durationSec % 60).toInt()
    val formattedDuration = "%d:%02d".format(minSec, remSec)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("complete_track_analysis_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C131F)),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF00E5FF).copy(alpha = 0.4f))
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Title and Track Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF00E5FF).copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ANÁLISIS ACÚSTICO COMPLETO",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (analysis.isSilent) Color(0xFF64748B).copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = if (analysis.isSilent) "SILENCIO" else "BS.1770-4",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (analysis.isSilent) Color(0xFF94A3B8) else Color(0xFF34D399),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = track?.title ?: "Pista en memoria",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF38BDF8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expandir/Colapsar análisis",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }

            // Track Specs Badge
            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SpecBadge(label = "Duración", value = formattedDuration)
                SpecBadge(label = "Sample Rate", value = "${track?.sampleRate ?: 44100} Hz")
                SpecBadge(label = "Canales", value = if ((track?.channels ?: 2) == 1) "Mono" else "Estéreo")
                SpecBadge(
                    label = "Picos",
                    value = if (analysis.hasClipping) "Clip" else if (analysis.isSilent) "Silencio" else "Limpio",
                    valueColor = if (analysis.hasClipping) Color(0xFFFF1744) else Color(0xFF34D399)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider(color = Color(0xFF1E293B), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // 1. Loudness Metrics Grid (ITU-R BS.1770-4 / EBU R128)
                    Text(
                        text = "1. MEDICIONES OBJETIVAS (ITU-R BS.1770-4 / EBU R128)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Row 1: Integrada, Short-Term, Momentary, Sample Peak
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MetricCard(
                            title = "INTEGRADA",
                            value = if (analysis.isSilent) "N/D" else "%.1f".format(analysis.integratedLufs),
                            unit = "LUFS",
                            description = "Sonoridad Global",
                            color = Color(0xFF00E5FF),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "SHORT-TERM",
                            value = if (analysis.isSilent) "N/D" else "%.1f".format(analysis.shortTermMaxLufs),
                            unit = "LUFS",
                            description = "Ventana 3s Máx",
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "MOMENTARY",
                            value = if (analysis.isSilent) "N/D" else "%.1f".format(analysis.momentaryMaxLufs),
                            unit = "LUFS",
                            description = "Ventana 400ms",
                            color = Color(0xFF818CF8),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "SAMPLE PEAK",
                            value = if (analysis.isSilent) "N/D" else "%.2f".format(analysis.samplePeakDbfs),
                            unit = "dBFS",
                            description = "Pico de Muestra",
                            color = Color(0xFFCBD5E1),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Row 2: True Peak, RMS, Factor de Cresta, LRA
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MetricCard(
                            title = "TRUE PEAK",
                            value = if (analysis.isSilent) "N/D" else "%.2f".format(analysis.truePeakDbfs),
                            unit = "dBTP",
                            description = if (analysis.truePeakDbfs >= 0f) "¡Alerta Clip!" else "4x FIR Oversample",
                            color = if (analysis.truePeakDbfs >= 0f) Color(0xFFFF1744) else Color(0xFF10B981),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "NIVEL RMS",
                            value = if (analysis.isSilent) "N/D" else "%.1f".format(analysis.rmsDbfs),
                            unit = "dBFS",
                            description = "Nivel Eficaz",
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "CREST FACTOR",
                            value = if (analysis.isSilent) "N/D" else "%.1f".format(analysis.crestFactorDb),
                            unit = "dB",
                            description = "Peak - RMS",
                            color = Color(0xFFFBBF24),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "RANGO LRA",
                            value = if (analysis.isSilent || analysis.loudnessRangeLu <= 0f) "N/D" else "%.1f".format(analysis.loudnessRangeLu),
                            unit = "LU",
                            description = "EBU Tech 3342",
                            color = Color(0xFFA78BFA),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Real Spectral Energy Distribution (FFT Welch)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "2. DISTRIBUCIÓN ESPECTRAL REAL (FFT WELCH)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Suma: 100%",
                            fontSize = 9.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF080D14),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1E293B))
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            SpectralBar(label = "Sub-Bass (20 - 60 Hz)", energy = analysis.subBassEnergy, color = Color(0xFF818CF8))
                            SpectralBar(label = "Punch Bombo (60 - 250 Hz)", energy = analysis.bassEnergy, color = Color(0xFF38BDF8))
                            SpectralBar(label = "Zona Mud Scoop (250 - 500 Hz)", energy = analysis.lowMidEnergy, color = Color(0xFFF59E0B))
                            SpectralBar(label = "Medios Vocales (500 Hz - 2 kHz)", energy = analysis.midEnergy, color = Color(0xFF10B981))
                            SpectralBar(label = "Pegada Redoblante (2 - 6 kHz)", energy = analysis.highMidEnergy, color = Color(0xFFEC4899))
                            SpectralBar(label = "Aire / Brillo (6 - 20 kHz)", energy = analysis.airEnergy, color = Color(0xFF06B6D4))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Frecuencia Pico Global de Energía:",
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                                Text(
                                    text = analysis.formattedPeakFrequency,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E5FF),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Separate Technical Mastering Recommendations
                    Text(
                        text = "3. RECOMENDACIONES DE MASTERIZACIÓN (SUGERIDAS)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF59E0B),
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF111C2D),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1E3A5F))
                        )
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (analysis.isSilent) {
                                Text(
                                    text = "Señal silenciosa o sin nivel audible. Carga una pista con señal para generar recomendaciones.",
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            } else {
                                if (analysis.truePeakDbfs >= -0.1f) {
                                    RecommendationBullet(
                                        title = "Techo True-Peak",
                                        detail = "Reducir a -0.5 dBTP o -1.0 dBTP para evitar inter-sample distortion en plataformas de streaming."
                                    )
                                } else {
                                    RecommendationBullet(
                                        title = "Techo True-Peak",
                                        detail = "Margen seguro (${"%.2f".format(analysis.truePeakDbfs)} dBTP), cumple estándares de distribución."
                                    )
                                }

                                if (analysis.subBassPercent > 38f) {
                                    RecommendationBullet(
                                        title = "Graves / Sub",
                                        detail = "Alta concentración sub-grave (${"%.1f".format(analysis.subBassPercent)}%). Usar filtro mono-maker en 80-90Hz para focalizar pegada."
                                    )
                                }

                                if (analysis.lowMidPercent > 26f) {
                                    RecommendationBullet(
                                        title = "Limpieza Mud",
                                        detail = "Zona 250-500 Hz dominante (${"%.1f".format(analysis.lowMidPercent)}%). Aplicar corte de -1.5 a -2.0 dB para ganar definición vocal."
                                    )
                                }

                                if (analysis.integratedLufs < -15f) {
                                    RecommendationBullet(
                                        title = "Objetivo Sonoridad",
                                        detail = "Mezcla dinámica (${"%.1f".format(analysis.integratedLufs)} LUFS). Ganancia makeup recomendada de +3 a +6 dB para competir en género urbano."
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action buttons: Download Analysis & Copy Analysis
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onDownloadAnalysis,
                            modifier = Modifier
                                .weight(1.3f)
                                .height(38.dp)
                                .testTag("download_analysis_button"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "DESCARGAR ANÁLISIS (.TXT)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val report = buildString {
                                    appendLine("=== INFORME ACÚSTICO: ${track?.title ?: "Track"} ===")
                                    appendLine("Sonoridad Integrada: ${analysis.formattedIntegratedLufs}")
                                    appendLine("Short-Term Máximo:   ${analysis.formattedShortTermLufs}")
                                    appendLine("Momentary Máximo:    ${analysis.formattedMomentaryLufs}")
                                    appendLine("Sample Peak:         ${analysis.formattedSamplePeak}")
                                    appendLine("True-Peak (4x FIR):  ${analysis.formattedTruePeak}")
                                    appendLine("Nivel RMS:           ${analysis.formattedRms}")
                                    appendLine("Factor de Cresta:    ${analysis.formattedCrestFactor}")
                                    appendLine("Rango LRA (EBU):     ${analysis.formattedLra}")
                                    appendLine("Pico Global Freq:    ${analysis.formattedPeakFrequency}")
                                    appendLine("Correlación Estéreo: ${analysis.formattedStereoCorrelation}")
                                    appendLine("Sub-Bass (20-60Hz):  ${"%.1f".format(analysis.subBassPercent)}%")
                                    appendLine("Punch (60-250Hz):    ${"%.1f".format(analysis.bassPercent)}%")
                                    appendLine("Mud (250-500Hz):     ${"%.1f".format(analysis.lowMidPercent)}%")
                                    appendLine("Mids (500Hz-2kHz):   ${"%.1f".format(analysis.midPercent)}%")
                                    appendLine("High-Mids (2-6kHz):  ${"%.1f".format(analysis.highMidPercent)}%")
                                    appendLine("Air (6-20kHz):       ${"%.1f".format(analysis.airPercent)}%")
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Análisis Acústico", report)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Análisis copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("copy_analysis_button"),
                            shape = RoundedCornerShape(8.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF334155))
                            )
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "COPIAR RESUMEN",
                                fontSize = 10.sp,
                                color = Color(0xFFE2E8F0),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationBullet(title: String, detail: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(4.dp)
                .background(Color(0xFFF59E0B), CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(text = title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFBBF24))
            Text(text = detail, fontSize = 9.sp, color = Color(0xFFCBD5E1), lineHeight = 12.sp)
        }
    }
}

@Composable
private fun SpecBadge(label: String, value: String, valueColor: Color = Color.White) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF0F1723),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1E293B))
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "$label: ", fontSize = 9.sp, color = Color(0xFF64748B))
            Text(text = value, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = valueColor)
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    unit: String,
    description: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF080D14),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1E293B))
        ),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Text(
                text = title,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = color,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = unit,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF64748B)
                )
            }
            Text(
                text = description,
                fontSize = 7.sp,
                color = Color(0xFF475569),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SpectralBar(label: String, energy: Float, color: Color) {
    val percent = (energy * 100f).coerceIn(0f, 100f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 9.sp, color = Color(0xFFCBD5E1))
            Text(
                text = "${percent.toInt()}%",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { (energy).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = color,
            trackColor = Color(0xFF1E293B),
        )
    }
}
