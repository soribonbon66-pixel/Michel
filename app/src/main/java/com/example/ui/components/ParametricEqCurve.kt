package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dsp.BiquadFilter
import com.example.model.MasteringSettings
import kotlin.math.*

/**
 * 5-Band Parametric Equalizer Curve visualizer with interactive frequency nodes.
 */
@Composable
fun ParametricEqCurve(
    settings: MasteringSettings,
    onBandGainChanged: (bandId: String, newGainDb: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val bands = settings.getAllBands()
    var selectedBandId by remember { mutableStateOf("sub") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0D131C), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CURVA DE RESPUESTA PARAMÉTRICA (5 BANDAS)",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                letterSpacing = 1.sp
            )
            Text(
                text = if (settings.isAutoCalibrated) "Auto-Ajustado para ${settings.presetType.title}" else "Preset Base",
                fontSize = 10.sp,
                color = Color(0xFF00E5FF)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Dynamic Frequency Response Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .testTag("eq_curve_canvas")
                .pointerInput(bands) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        // Dragging vertically adjusts gain of currently selected band
                        val activeBand = bands.find { it.id == selectedBandId } ?: return@detectDragGestures
                        val deltaGain = -dragAmount.y * 0.15f
                        val newGain = (activeBand.gainDb + deltaGain).coerceIn(-12f, 12f)
                        onBandGainChanged(activeBand.id, newGain)
                    }
                }
        ) {
            val width = size.width
            val height = size.height

            // dB grid lines: +12, +6, 0, -6, -12
            val minDb = -12f
            val maxDb = 12f
            val zeroY = height * 0.5f

            // Zero line
            drawLine(
                color = Color(0xFF334155),
                start = Offset(0f, zeroY),
                end = Offset(width, zeroY),
                strokeWidth = 1.5f
            )

            // Grid lines for +6 and -6
            val plus6Y = height * 0.25f
            val minus6Y = height * 0.75f
            drawLine(
                color = Color(0xFF1E293B),
                start = Offset(0f, plus6Y),
                end = Offset(width, plus6Y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
            )
            drawLine(
                color = Color(0xFF1E293B),
                start = Offset(0f, minus6Y),
                end = Offset(width, minus6Y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
            )

            // Frequency vertical grid lines (50Hz, 100Hz, 1kHz, 5kHz, 10kHz)
            val freqGrid = listOf(50f, 100f, 500f, 1000f, 5000f, 10000f)
            freqGrid.forEach { f ->
                val normX = (log10(f / 20f) / log10(20000f / 20f)).toFloat().coerceIn(0f, 1f)
                val x = normX * width
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f
                )
            }

            // Create temporary filters to calculate the cumulative curve
            val sampleRate = 44100f
            val fSub = BiquadFilter(BiquadFilter.Type.LOW_SHELF, settings.subBassBand.frequencyHz, settings.subBassBand.gainDb, settings.subBassBand.qFactor, sampleRate)
            val fKick = BiquadFilter(BiquadFilter.Type.PEAK, settings.kickPunchBand.frequencyHz, settings.kickPunchBand.gainDb, settings.kickPunchBand.qFactor, sampleRate)
            val fMud = BiquadFilter(BiquadFilter.Type.PEAK, settings.mudCutBand.frequencyHz, settings.mudCutBand.gainDb, settings.mudCutBand.qFactor, sampleRate)
            val fSnare = BiquadFilter(BiquadFilter.Type.PEAK, settings.snarePresenceBand.frequencyHz, settings.snarePresenceBand.gainDb, settings.snarePresenceBand.qFactor, sampleRate)
            val fAir = BiquadFilter(BiquadFilter.Type.HIGH_SHELF, settings.airShelfBand.frequencyHz, settings.airShelfBand.gainDb, settings.airShelfBand.qFactor, sampleRate)

            val curvePath = Path()
            val fillPath = Path()
            val steps = 120

            for (step in 0..steps) {
                val normX = step.toFloat() / steps
                val freq = 20.0 * 10.0.pow(normX.toDouble() * log10(20000.0 / 20.0))
                val totalGain = fSub.getGainDbAt(freq.toFloat()) +
                        fKick.getGainDbAt(freq.toFloat()) +
                        fMud.getGainDbAt(freq.toFloat()) +
                        fSnare.getGainDbAt(freq.toFloat()) +
                        fAir.getGainDbAt(freq.toFloat())

                val clampedGain = totalGain.coerceIn(minDb, maxDb)
                val x = normX * width
                val y = zeroY - (clampedGain / maxDb) * (height * 0.45f)

                if (step == 0) {
                    curvePath.moveTo(x, y)
                    fillPath.moveTo(x, zeroY)
                    fillPath.lineTo(x, y)
                } else {
                    curvePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(width, zeroY)
            fillPath.close()

            // Fill gradient under curve
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0x3300E5FF),
                        Color(0x0500E5FF),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = height
                )
            )

            // Draw primary cyan curve line
            drawPath(
                path = curvePath,
                color = Color(0xFF00E5FF),
                style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Draw band interactive anchor nodes
            bands.forEach { band ->
                val normX = (log10(band.frequencyHz / 20f) / log10(20000f / 20f)).coerceIn(0f, 1f)
                val nodeX = normX * width
                val nodeY = zeroY - (band.gainDb.coerceIn(minDb, maxDb) / maxDb) * (height * 0.45f)
                val isSelected = band.id == selectedBandId

                // Glow ring
                drawCircle(
                    color = if (isSelected) Color(0x6600E5FF) else Color(0x22FFFFFF),
                    radius = if (isSelected) 10.dp.toPx() else 6.dp.toPx(),
                    center = Offset(nodeX, nodeY)
                )

                // Solid center
                drawCircle(
                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                    radius = if (isSelected) 5.dp.toPx() else 3.5.dp.toPx(),
                    center = Offset(nodeX, nodeY)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Band selector pills and mini +/- gain controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            bands.forEach { band ->
                val isSelected = band.id == selectedBandId
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedBandId = band.id },
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = band.name.split(" ").first(),
                                fontSize = 10.sp,
                                color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF94A3B8)
                            )
                            Text(
                                text = (if (band.gainDb >= 0) "+" else "") + "%.1f".format(band.gainDb) + " dB",
                                fontSize = 9.sp,
                                color = if (isSelected) Color.White else Color(0xFF64748B)
                            )
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1E293B),
                        containerColor = Color(0xFF131B26)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = Color(0xFF1E293B),
                        selectedBorderColor = Color(0xFF00E5FF)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("band_chip_${band.id}")
                )
            }
        }
    }
}
