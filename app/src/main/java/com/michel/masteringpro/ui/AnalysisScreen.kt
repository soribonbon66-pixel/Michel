package com.michel.masteringpro.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michel.masteringpro.model.AnalysisIssue
import com.michel.masteringpro.model.AnalysisResult
import com.michel.masteringpro.model.IssueSeverity
import com.michel.masteringpro.model.MasteringSession
import com.michel.masteringpro.model.MasteringSettings
import com.michel.masteringpro.ui.components.ChartLegend
import com.michel.masteringpro.ui.components.DataPill
import com.michel.masteringpro.ui.components.MasteringSlider
import com.michel.masteringpro.ui.components.MetricTile
import com.michel.masteringpro.ui.components.SectionHeading
import com.michel.masteringpro.ui.components.SpectrumChart
import com.michel.masteringpro.ui.components.WaveformChart
import com.michel.masteringpro.ui.theme.MichelColors
import java.util.Locale
import kotlin.math.abs

@Composable
fun AnalysisScreen(
    session: MasteringSession,
    onSettingsChanged: (MasteringSettings) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onLoadAnother: () -> Unit,
    modifier: Modifier = Modifier
) {
    val analysis = session.analysis
    val settings = session.settings
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("ANTES DEL MASTER", color = MichelColors.Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(5.dp))
                Text("ANÁLISIS DEL TRACK CARGADO", color = MichelColors.Text, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(5.dp))
                Text("Medición del audio original · el análisis aparece automáticamente al cargar", color = MichelColors.Muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
            OutlinedButton(onClick = onLoadAnother, shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Default.AudioFile, contentDescription = null, tint = MichelColors.Primary)
            }
        }

        TrackCard(session.track.title, session.track.sampleRate, session.track.channels, session.track.durationSeconds, session.track)

        SectionHeading("Loudness y dinámica", "Valores originales antes de procesar")
        MetricRow {
            MetricTile("Integrated LUFS", lufs(analysis), "EBU R128 · gated", MichelColors.Primary, Modifier.weight(1f))
            MetricTile("True Peak", db(analysis.truePeakDbtp), "dBTP · oversampling 4×", MichelColors.Red, Modifier.weight(1f))
            MetricTile("Loudness Range", number(analysis.loudnessRangeLu, " LU"), "LRA · EBU Tech 3342", MichelColors.Violet, Modifier.weight(1f))
        }
        MetricRow {
            MetricTile("RMS", db(analysis.rmsDbfs), "nivel medio dBFS", MichelColors.Blue, Modifier.weight(1f))
            MetricTile("Crest Factor", number(analysis.crestFactorDb, " dB"), "pico de muestra − RMS", MichelColors.Amber, Modifier.weight(1f))
            MetricTile("Dynamic Range", number(analysis.dynamicRangeDb, " dB"), "True Peak − LUFS", MichelColors.Violet, Modifier.weight(1f))
        }
        MetricRow {
            MetricTile("Clipping", "${analysis.clippingCount}", "${number(analysis.clippingPercent, "%")} muestras > −0.1 dBFS", if (analysis.clippingCount > 0) MichelColors.Red else MichelColors.Green, Modifier.weight(1f))
            MetricTile("Correlación", number(analysis.stereoCorrelation, ""), "fase · −1 a +1", phaseColor(analysis.stereoCorrelation), Modifier.weight(1f))
            MetricTile("Ancho estéreo", db(analysis.stereoWidthDb), "Side / Mid", MichelColors.Blue, Modifier.weight(1f))
        }

        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MichelColors.Surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                SectionHeading("Espectro promedio", "FFT de 4096 puntos · escala logarítmica")
                SpectrumChart(analysis.spectrum, modifier = Modifier.fillMaxWidth().height(190.dp))
                FrequencyLabels()
                Divider(color = MichelColors.Border)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    DataPill("<80 Hz  ${signedDb(analysis.lowBandVsReferenceDb)} dB", if (analysis.hasBassDeficit) MichelColors.Amber else MichelColors.Blue, Modifier.weight(1f))
                    DataPill("200–400 Hz  ${signedDb(analysis.bodyBandVsReferenceDb)} dB", if (analysis.hasBodyExcess) MichelColors.Amber else MichelColors.Blue, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    DataPill("2–5 kHz  ${signedDb(analysis.presenceVsReferenceDb)} dB", if (analysis.hasPresenceExcess) MichelColors.Amber else MichelColors.Blue, Modifier.weight(1f))
                    DataPill(">12 kHz / 1 kHz  ${signedDb(analysis.airVs1kDb)} dB", if (analysis.hasAirDeficit) MichelColors.Amber else MichelColors.Blue, Modifier.weight(1f))
                }
                Text("Las comparaciones de banda son heurísticas espectrales; no sustituyen una escucha en contexto.", color = MichelColors.Faint, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }

        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MichelColors.Surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionHeading("Estéreo, fase y tonalidad", "Indicadores automáticos de la mezcla")
                MetricRow {
                    MetricTile("Mono compatible", number(analysis.monoCompatibilityPercent, "%"), "fold-down vs estéreo", if (analysis.monoCompatibilityPercent < 55) MichelColors.Amber else MichelColors.Green, Modifier.weight(1f), minHeight = 102.dp)
                    MetricTile("BPM estimado", analysis.estimatedBpm?.let { number(it, " BPM") } ?: "N/D", "autocorrelación de envolvente", MichelColors.Blue, Modifier.weight(1f), minHeight = 102.dp)
                    MetricTile("Tonalidad", analysis.estimatedKey, "chromagram / perfil tonal", MichelColors.Violet, Modifier.weight(1f), minHeight = 102.dp)
                }
                if (analysis.channelsOrStereoWarning()) {
                    Text("Una correlación baja puede cancelar contenido al plegar a mono. Comprueba el balance de fase antes de exportar.", color = MichelColors.Amber, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }

        IssuesCard(analysis)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Tune, contentDescription = null, tint = MichelColors.Primary)
            Column {
                Text("AJUSTES SUGERIDOS POR MICHEL", color = MichelColors.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("Derivados del análisis y editables antes de aplicar", color = MichelColors.Muted, fontSize = 12.sp)
            }
        }
        SuggestionNotes(session.suggestion.notes)

        SettingsCard("Ganancia de entrada", "Ajuste antes del EQ y la dinámica") {
            MasteringSlider(
                "Gain",
                settings.gainDb,
                signedDb(settings.gainDb) + " dB",
                -24f..24f
            ) { onSettingsChanged(settings.copy(gainDb = it)) }
        }

        SettingsCard("EQ · tres zonas / cuatro puntos", "RBJ biquad · Low shelf 80 Hz, bells 300 Hz y 3.5 kHz, high shelf 12 kHz") {
            MasteringSlider("Low shelf · 80 Hz", settings.lowEqDb, signedDb(settings.lowEqDb) + " dB", -9f..9f) {
                onSettingsChanged(settings.copy(lowEqDb = it))
            }
            MasteringSlider("Body bell · 300 Hz · Q 1.2", settings.bodyEqDb, signedDb(settings.bodyEqDb) + " dB", -9f..9f) {
                onSettingsChanged(settings.copy(bodyEqDb = it))
            }
            MasteringSlider("Presence bell · 3.5 kHz · Q 1.2", settings.presenceEqDb, signedDb(settings.presenceEqDb) + " dB", -9f..9f) {
                onSettingsChanged(settings.copy(presenceEqDb = it))
            }
            MasteringSlider("Air shelf · 12 kHz", settings.airEqDb, signedDb(settings.airEqDb) + " dB", -9f..9f) {
                onSettingsChanged(settings.copy(airEqDb = it))
            }
        }

        SettingsCard("Compresor feed-forward", "Ataque y release suaves para controlar la envolvente") {
            SettingSwitchRow("Compresor", "Ratio ${number(settings.compressorRatio, ":1")}", settings.compressorEnabled) {
                onSettingsChanged(settings.copy(compressorEnabled = it))
            }
            MasteringSlider("Threshold", settings.compressorThresholdDb, db(settings.compressorThresholdDb), -48f..0f, enabled = settings.compressorEnabled) {
                onSettingsChanged(settings.copy(compressorThresholdDb = it))
            }
            MasteringSlider("Ratio", settings.compressorRatio, number(settings.compressorRatio, ":1"), 1f..10f, enabled = settings.compressorEnabled) {
                onSettingsChanged(settings.copy(compressorRatio = it))
            }
            MasteringSlider("Attack", settings.compressorAttackMs, number(settings.compressorAttackMs, " ms"), 1f..100f, enabled = settings.compressorEnabled) {
                onSettingsChanged(settings.copy(compressorAttackMs = it))
            }
            MasteringSlider("Release", settings.compressorReleaseMs, number(settings.compressorReleaseMs, " ms"), 10f..500f, enabled = settings.compressorEnabled) {
                onSettingsChanged(settings.copy(compressorReleaseMs = it))
            }
        }

        SettingsCard("Limiter brickwall", if (analysis.clippingCount > 0L) "Activado obligatoriamente porque se detectó clipping" else "Protege picos y conserva el techo de True Peak") {
            SettingSwitchRow(
                "Limitador",
                if (analysis.clippingCount > 0L) "No se puede desactivar con clipping detectado" else "Ceiling ajustable",
                settings.limiterEnabled,
                enabled = analysis.clippingCount == 0L
            ) { onSettingsChanged(settings.copy(limiterEnabled = it)) }
            MasteringSlider("Ceiling / threshold", settings.limiterThresholdDb, db(settings.limiterThresholdDb), -6f..0f) {
                onSettingsChanged(settings.copy(limiterThresholdDb = it))
            }
            MasteringSlider("Release", settings.limiterReleaseMs, number(settings.limiterReleaseMs, " ms"), 5f..250f) {
                onSettingsChanged(settings.copy(limiterReleaseMs = it))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(15.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(7.dp))
                Text("RESET A SUGERIDO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth().height(74.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MichelColors.Primary, contentColor = Color(0xFF052116)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp)
        ) {
            Icon(Icons.Default.GraphicEq, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("APLICAR MASTERING", fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 0.4.sp)
                Text("EQ + COMP + LIMITER · normalizar a −14 LUFS", fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun TrackCard(title: String, sampleRate: Int, channels: Int, duration: Float, track: com.michel.masteringpro.model.AudioTrackData) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MichelColors.Surface)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(13.dp), color = MichelColors.Primary.copy(alpha = 0.14f)) {
                    Icon(Icons.Default.AudioFile, contentDescription = null, tint = MichelColors.Primary, modifier = Modifier.padding(11.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = MichelColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${time(duration)} · ${sampleRate / 1000f} kHz · ${if (channels == 1) "Mono" else "Stereo"}", color = MichelColors.Muted, fontSize = 12.sp)
                }
                DataPill("ORIGINAL", MichelColors.Blue)
            }
            WaveformChart(track, modifier = Modifier.fillMaxWidth().height(78.dp))
        }
    }
}

@Composable
private fun MetricRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun IssuesCard(analysis: AnalysisResult) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MichelColors.Surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeading("Problemas detectados", if (analysis.issues.isEmpty()) "No se detectaron alertas importantes" else "Revisa estas señales antes de exportar")
            if (analysis.issues.isEmpty()) {
                Text("✓  Niveles y balance sin avisos automáticos", color = MichelColors.Green, fontSize = 13.sp)
            } else {
                analysis.issues.forEach { IssueRow(it) }
            }
        }
    }
}

@Composable
private fun IssueRow(issue: AnalysisIssue) {
    val tint = when (issue.severity) {
        IssueSeverity.CRITICAL -> MichelColors.Red
        IssueSeverity.WARNING -> MichelColors.Amber
        IssueSeverity.INFO -> MichelColors.Blue
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Surface(shape = RoundedCornerShape(20.dp), color = tint.copy(alpha = 0.15f)) {
            Text(
                when (issue.severity) {
                    IssueSeverity.CRITICAL -> "!"
                    IssueSeverity.WARNING -> "!"
                    IssueSeverity.INFO -> "i"
                },
                color = tint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Text(issue.message, color = tint, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun SuggestionNotes(notes: List<String>) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MichelColors.Primary.copy(alpha = 0.08f))) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            notes.forEach { note ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Text("✦", color = MichelColors.Primary, fontSize = 14.sp)
                    Text(note, color = MichelColors.Text, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MichelColors.Surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, color = MichelColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MichelColors.Muted, fontSize = 11.sp, lineHeight = 15.sp)
            Divider(color = MichelColors.Border)
            content()
        }
    }
}

@Composable
private fun SettingSwitchRow(title: String, description: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MichelColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = MichelColors.Muted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = MichelColors.Background, checkedTrackColor = MichelColors.Primary)
        )
    }
}

@Composable
private fun FrequencyLabels() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("20", "100", "1k", "10k", "20k Hz").forEach { Text(it, color = MichelColors.Faint, fontSize = 10.sp) }
    }
}

private fun AnalysisResult.channelsOrStereoWarning(): Boolean = stereoCorrelation < 0.3f
private fun phaseColor(value: Float): Color = if (value < 0.3f) MichelColors.Red else if (value < 0.7f) MichelColors.Amber else MichelColors.Green
private fun lufs(analysis: AnalysisResult): String = if (analysis.isSilent) "N/D" else number(analysis.integratedLufs, "")
private fun db(value: Float): String = if (value <= -119f) "−∞ dB" else number(value, " dB")
private fun number(value: Float, suffix: String): String = String.format(Locale.US, "%.1f%s", value, suffix)
private fun signedDb(value: Float): String = String.format(Locale.US, "%+.1f", value)
private fun time(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(Locale.US, total / 60, total % 60)
}
