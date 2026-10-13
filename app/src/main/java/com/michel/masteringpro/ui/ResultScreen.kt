package com.michel.masteringpro.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michel.masteringpro.model.AnalysisResult
import com.michel.masteringpro.model.MasteringUiState
import com.michel.masteringpro.ui.components.ChartLegend
import com.michel.masteringpro.ui.components.SectionHeading
import com.michel.masteringpro.ui.components.SpectrumChart
import com.michel.masteringpro.ui.components.WaveformChart
import com.michel.masteringpro.ui.theme.MichelColors
import java.util.Locale
import kotlin.math.abs

@Composable
fun ResultScreen(
    state: MasteringUiState.Processed,
    onListenOriginal: () -> Unit,
    onListenMaster: () -> Unit,
    onTogglePlayback: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val before = state.session.analysis
    val after = state.resultAnalysis
    val lufsOk = !after.isSilent && abs(after.integratedLufs + 14f) <= 0.5f
    val peakOk = after.truePeakDbtp < -1f

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        Column {
            Text("MICHEL MASTERING PRO  /  A–B", color = MichelColors.Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(5.dp))
            Text("RESULTADO TRAS APLICAR CAMBIOS", color = MichelColors.Text, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(5.dp))
            Text("Análisis comparativo del track original y el master renderizado.", color = MichelColors.Muted, fontSize = 13.sp)
        }

        TargetValidationCard(lufsOk, peakOk, after)

        ComparisonCard(before, after)

        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MichelColors.Surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeading("Waveform · A / B", "Original en gris, master procesado en verde")
                WaveformChart(
                    first = state.session.track,
                    second = state.masteredTrack,
                    modifier = Modifier.fillMaxWidth().height(104.dp),
                    firstColor = Color(0xFFA9B5C0),
                    secondColor = MichelColors.Primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    ChartLegend(Color(0xFFA9B5C0), "Original A")
                    ChartLegend(MichelColors.Primary, "Master B")
                }
            }
        }

        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MichelColors.Surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeading("Espectro comparativo", "FFT promedio antes / después")
                SpectrumChart(
                    first = before.spectrum,
                    second = after.spectrum,
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                    firstColor = Color(0xFFA9B5C0),
                    secondColor = MichelColors.Primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    ChartLegend(Color(0xFFA9B5C0), "Antes")
                    ChartLegend(MichelColors.Primary, "Después")
                }
            }
        }

        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MichelColors.Surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading("Escucha A/B instantánea", "Cambia la fuente durante la reproducción para comparar al mismo tiempo del track")
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onListenOriginal,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!state.isMasterPreview) MichelColors.Blue else MichelColors.Elevated,
                            contentColor = if (!state.isMasterPreview) MichelColors.Background else MichelColors.Text
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (state.isPlaying && !state.isMasterPreview) "ORIGINAL A" else "ESCUCHAR A", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onListenMaster,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isMasterPreview) MichelColors.Primary else MichelColors.Elevated,
                            contentColor = if (state.isMasterPreview) MichelColors.Background else MichelColors.Text
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(if (state.isPlaying && state.isMasterPreview) "MASTER B" else "ESCUCHAR B", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(onClick = onTogglePlayback, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text(if (state.isPlaying) "PAUSAR" else "REANUDAR", fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MichelColors.Surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MichelColors.Primary)
                    Spacer(Modifier.width(8.dp))
                    Text("WAV MASTERIZADO", color = MichelColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    state.fileName?.let { "$it\nMúsica / Michel" } ?: "El audio se procesó; todavía no se pudo guardar el WAV.",
                    color = MichelColors.Muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                if (state.exportError != null) Text("Aviso al exportar: ${state.exportError}", color = MichelColors.Amber, fontSize = 12.sp)
                if (state.isExporting) Text("Exportando WAV…", color = MichelColors.Blue, fontSize = 12.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onExport,
                enabled = !state.isExporting,
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MichelColors.Primary, contentColor = MichelColors.Background)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (state.fileUri == null) "EXPORTAR WAV" else "GUARDAR OTRA COPIA", fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            OutlinedButton(
                onClick = onShare,
                enabled = state.fileUri != null,
                modifier = Modifier.weight(0.72f).height(54.dp),
                shape = RoundedCornerShape(15.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("COMPARTIR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp)) {
            Icon(Icons.Default.Tune, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("VOLVER A EDITAR AJUSTES", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun TargetValidationCard(lufsOk: Boolean, peakOk: Boolean, after: AnalysisResult) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (lufsOk && peakOk) MichelColors.Primary.copy(alpha = 0.12f) else MichelColors.Amber.copy(alpha = 0.08f))
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Objetivo EBU R128", color = MichelColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("−14 LUFS ±0.5 · True Peak < −1 dBTP", color = MichelColors.Muted, fontSize = 12.sp)
            Divider(color = MichelColors.Border)
            ValidationRow("Sonoridad ${number(after.integratedLufs, " LUFS")}", lufsOk)
            ValidationRow("True Peak ${number(after.truePeakDbtp, " dBTP")}", peakOk)
            if (!lufsOk || !peakOk) {
                Text("La seguridad de pico tiene prioridad: una mezcla con gran crest factor puede quedar por debajo de −14 LUFS para respetar el techo de −1 dBTP.", color = MichelColors.Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
    }
}

@Composable
private fun ValidationRow(label: String, passed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (passed) "✓" else "!", color = if (passed) MichelColors.Green else MichelColors.Amber, fontSize = 17.sp, fontWeight = FontWeight.Black)
        Text(label, color = if (passed) MichelColors.Green else MichelColors.Amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ComparisonCard(before: AnalysisResult, after: AnalysisResult) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MichelColors.Surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SectionHeading("Before / After", "Mediciones lado a lado · valores recalcados en el PCM masterizado")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("MÉTRICA", color = MichelColors.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("ANTES", color = Color(0xFFA9B5C0), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.85f))
                Text("DESPUÉS", color = MichelColors.Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.85f))
            }
            Divider(color = MichelColors.Border)
            ComparisonRow("Integrated LUFS", metricLufs(before), metricLufs(after))
            ComparisonRow("True Peak", number(before.truePeakDbtp, " dBTP"), number(after.truePeakDbtp, " dBTP"))
            ComparisonRow("LRA", number(before.loudnessRangeLu, " LU"), number(after.loudnessRangeLu, " LU"))
            ComparisonRow("RMS", number(before.rmsDbfs, " dBFS"), number(after.rmsDbfs, " dBFS"))
            ComparisonRow("Clipping", "${before.clippingCount} (${number(before.clippingPercent, "%")})", "${after.clippingCount} (${number(after.clippingPercent, "%")})")
        }
    }
}

@Composable
private fun ComparisonRow(label: String, before: String, after: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MichelColors.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(before, color = MichelColors.Text, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.85f))
        Text(after, color = MichelColors.Primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.85f))
    }
}

private fun metricLufs(result: AnalysisResult): String = if (result.isSilent) "N/D" else number(result.integratedLufs, " LUFS")
private fun number(value: Float, suffix: String): String = String.format(Locale.US, "%.1f%s", value, suffix)
