package com.michel.masteringpro.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michel.masteringpro.model.AudioTrackData
import com.michel.masteringpro.model.SpectrumData
import com.michel.masteringpro.ui.theme.MichelColors
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

@Composable
fun SectionHeading(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(title, color = MichelColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = MichelColors.Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
fun MetricTile(
    title: String,
    value: String,
    detail: String,
    accent: Color = MichelColors.Primary,
    modifier: Modifier = Modifier,
    minHeight: Dp = 112.dp
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MichelColors.Surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(minHeight)
                .padding(horizontal = 13.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                title.uppercase(),
                color = MichelColors.Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.65.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Column {
                Text(value, color = accent, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(detail, color = MichelColors.Faint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun DataPill(text: String, tint: Color = MichelColors.Blue, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.28f), CircleShape)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).background(tint, CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(text, color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun MasteringSlider(
    label: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = if (enabled) MichelColors.Text else MichelColors.Faint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(valueLabel, color = if (enabled) MichelColors.Primary else MichelColors.Faint, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = MichelColors.Primary,
                activeTrackColor = MichelColors.Primary,
                inactiveTrackColor = MichelColors.Border,
                disabledThumbColor = MichelColors.Faint,
                disabledActiveTrackColor = MichelColors.Faint
            )
        )
    }
}

@Composable
fun WaveformChart(
    first: AudioTrackData,
    second: AudioTrackData? = null,
    modifier: Modifier = Modifier,
    firstColor: Color = MichelColors.Blue,
    secondColor: Color = MichelColors.Primary
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0xFF08131D))
    ) {
        val centerY = size.height / 2f
        drawLine(MichelColors.Border.copy(alpha = 0.7f), Offset(0f, centerY), Offset(size.width, centerY), 1f)
        val barCount = (size.width / 3.2f).toInt().coerceIn(48, 256)
        val barWidth = size.width / barCount
        for (bar in 0 until barCount) {
            val startFraction = bar.toFloat() / barCount
            val endFraction = (bar + 1f) / barCount
            val ampA = amplitude(first, startFraction, endFraction)
            val x = (bar + 0.5f) * barWidth
            val extentA = (ampA * size.height * 0.46f).coerceAtLeast(1f)
            drawLine(firstColor.copy(alpha = if (second == null) 0.88f else 0.46f), Offset(x, centerY - extentA), Offset(x, centerY + extentA), strokeWidth = 1.35f, cap = StrokeCap.Round)
            if (second != null) {
                val ampB = amplitude(second, startFraction, endFraction)
                val extentB = (ampB * size.height * 0.46f).coerceAtLeast(1f)
                drawLine(secondColor.copy(alpha = 0.88f), Offset(x, centerY - extentB), Offset(x, centerY + extentB), strokeWidth = 1.15f, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
fun SpectrumChart(
    first: SpectrumData,
    second: SpectrumData? = null,
    modifier: Modifier = Modifier,
    firstColor: Color = MichelColors.Blue,
    secondColor: Color = MichelColors.Primary
) {
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0xFF08131D))
    ) {
        val maxDb = 0f
        val minDb = -72f
        for (db in -60..0 step 20) {
            val y = ((maxDb - db) / (maxDb - minDb)) * size.height
            drawLine(MichelColors.Border.copy(alpha = 0.48f), Offset(0f, y), Offset(size.width, y), 1f)
        }
        val maxFrequency = min(20_000.0, first.sampleRate / 2.0).coerceAtLeast(100.0)
        val logMin = ln(20.0)
        val logMax = ln(maxFrequency)
        drawSpectrum(first, firstColor, size.width, size.height, minDb, logMin, logMax, alpha = if (second == null) 0.9f else 0.55f)
        if (second != null) drawSpectrum(second, secondColor, size.width, size.height, minDb, logMin, logMax, alpha = 0.95f)
    }
}

@Composable
fun ChartLegend(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(18.dp).height(3.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, color = MichelColors.Muted, fontSize = 11.sp)
    }
}

private fun amplitude(track: AudioTrackData, startFraction: Float, endFraction: Float): Float {
    val start = (startFraction * track.frameCount).toInt().coerceIn(0, track.frameCount)
    val end = (endFraction * track.frameCount).toInt().coerceIn(start + 1, track.frameCount.coerceAtLeast(1))
    if (track.frameCount == 0) return 0f
    var peak = 0f
    val boundedEnd = min(end, track.frameCount)
    val stride = ((boundedEnd - start) / 256).coerceAtLeast(1)
    for (frame in start until boundedEnd step stride) {
        val offset = frame * track.channels
        peak = max(peak, abs(track.samples[offset]))
        if (track.channels > 1) peak = max(peak, abs(track.samples[offset + 1]))
    }
    return peak.coerceIn(0f, 1f)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpectrum(
    spectrum: SpectrumData,
    color: Color,
    width: Float,
    height: Float,
    minDb: Float,
    logMin: Double,
    logMax: Double,
    alpha: Float
) {
    val bins = spectrum.magnitudesDb.size
    if (bins < 2) return
    val fftSize = 2 * (bins - 1)
    val maxFrequency = min(20_000.0, spectrum.sampleRate / 2.0).coerceAtLeast(100.0)
    val path = Path()
    var started = false
    for (bin in 1 until bins) {
        val frequency = bin.toDouble() * spectrum.sampleRate / fftSize
        if (frequency < 20.0 || frequency > maxFrequency) continue
        val x = ((ln(frequency) - logMin) / (logMax - logMin)).toFloat() * width
        val normalized = ((spectrum.magnitudesDb[bin] - minDb) / -minDb).coerceIn(0f, 1f)
        val y = (1f - normalized) * height
        if (!started) {
            path.moveTo(x, y)
            started = true
        } else {
            path.lineTo(x, y)
        }
    }
    if (started) drawPath(path, color.copy(alpha = alpha), style = Stroke(width = 2f))
}
