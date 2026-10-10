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
 * Displays ITU-R BS.1770 / EBU R128 loudness metrics, spectral balance breakdown,
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
                                text = "ANÁLISIS COMPLETO DEL TRACK",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "MEDIDO",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF34D399),
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
                    value = if (analysis.hasClipping) "Clip" else "Limpio",
                    valueColor = if (analysis.hasClipping) Color(0xFFFF1744) else Color(0xFF34D399)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Divider(color = Color(0xFF1E293B), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    // 1. Loudness Metrics Grid (ITU-R BS.1770)
                    Text(
                        text = "1. MEDICIONES DE SONORIDAD (ITU-R BS.1770 / EBU R128)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            title = "INTEGRADA",
                            value = "%.1f".format(analysis.integratedLufs),
                            unit = "LUFS",
                            description = "Sonoridad Global",
                            color = Color(0xFF00E5FF),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "SHORT-TERM",
                            value = "%.1f".format(analysis.shortTermMaxLufs),
                            unit = "LUFS",
                            description = "Ventana Máx (3s)",
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "TRUE PEAK",
                            value = "%.2f".format(analysis.truePeakDbfs),
                            unit = "dBTP",
                            description = if (analysis.truePeakDbfs >= 0f) "¡Clip Detectado!" else "Seguro",
                            color = if (analysis.truePeakDbfs >= 0f) Color(0xFFFF1744) else Color(0xFF10B981),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "DINÁMICA",
                            value = "%.1f".format(analysis.dynamicRangeDb),
                            unit = "dB",
                            description = "Crest Factor PLR",
                            color = Color(0xFFFBBF24),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Spectral Energy Distribution
                    Text(
                        text = "2. DISTRIBUCIÓN DE ENERGÍA ESPECTRAL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 0.5.sp
                    )

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
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Frecuencia Fundamental Dominante:",
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                                Text(
                                    text = "${analysis.dominantFrequencyHz.toInt()} Hz",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E5FF)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Acoustic Diagnostic Summary
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF111C2D),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1E3A5F))
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val diagText = buildString {
                                append("Sonoridad: ${"%.1f".format(analysis.integratedLufs)} LUFS. ")
                                if (analysis.integratedLufs < -16.0f) {
                                    append("Mezcla con amplio margen de ganancia. ")
                                } else if (analysis.integratedLufs > -10.0f) {
                                    append("Nivel de sonoridad alto. ")
                                }
                                if (analysis.hasClipping) {
                                    append("Alerta: Se detectaron muestras al límite de 0 dBTP. ")
                                } else {
                                    append("Picos limpios sin clipping digital. ")
                                }
                                append("Sub-grave centrado en ${analysis.dominantFrequencyHz.toInt()} Hz.")
                            }
                            Text(
                                text = diagText,
                                fontSize = 10.sp,
                                color = Color(0xFFE2E8F0),
                                lineHeight = 14.sp
                            )
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
                                    appendLine("=== ANÁLISIS ACÚSTICO: ${track?.title ?: "Track"} ===")
                                    appendLine("Sonoridad Integrada: ${"%.2f".format(analysis.integratedLufs)} LUFS")
                                    appendLine("Short-Term Máximo:   ${"%.2f".format(analysis.shortTermMaxLufs)} LUFS")
                                    appendLine("Nivel True-Peak:     ${"%.2f".format(analysis.truePeakDbfs)} dBTP")
                                    appendLine("Rango Dinámico:      ${"%.2f".format(analysis.dynamicRangeDb)} dB")
                                    appendLine("Frecuencia Clave:    ${analysis.dominantFrequencyHz.toInt()} Hz")
                                    appendLine("Sub-Bass (20-60Hz):  ${(analysis.subBassEnergy * 100).toInt()}%")
                                    appendLine("Punch (60-250Hz):    ${(analysis.bassEnergy * 100).toInt()}%")
                                    appendLine("Mud (250-500Hz):     ${(analysis.lowMidEnergy * 100).toInt()}%")
                                    appendLine("Mids (500Hz-2kHz):   ${(analysis.midEnergy * 100).toInt()}%")
                                    appendLine("High-Mids (2-6kHz):  ${(analysis.highMidEnergy * 100).toInt()}%")
                                    appendLine("Air (6-20kHz):       ${(analysis.airEnergy * 100).toInt()}%")
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
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecBadge(label: String, value: String, valueColor: Color = Color(0xFFE2E8F0)) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFF131D2E)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF090E17),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1E293B))
        )
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = color,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = unit,
                    fontSize = 8.sp,
                    color = color.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
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
    val pct = (energy.coerceIn(0f, 1f) * 100).toInt()
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 9.sp, color = Color(0xFFCBD5E1))
            Text(text = "$pct%", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { energy.coerceIn(0.01f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = color,
            trackColor = Color(0xFF1E293B)
        )
    }
}
