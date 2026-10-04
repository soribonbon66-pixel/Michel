package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AudioAnalysis
import kotlin.math.sin

/**
 * Real-time 6-Band Frequency Spectrum Analyzer with Peak Hold and comparative A/B glow.
 */
@Composable
fun SpectrumVisualizer(
    rawAnalysis: AudioAnalysis,
    masteredAnalysis: AudioAnalysis,
    isBypassActive: Boolean,
    livePeak: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAnim by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val bandLabels = listOf("Sub (45Hz)", "Bass (100Hz)", "Mud (300Hz)", "Mid (1kHz)", "Bite (3.5k)", "Air (12k)")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(Color(0xFF0D131C), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ESPECTRO DE FRECUENCIAS (RTA EN VIVO)",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (isBypassActive) Color(0xFFF59E0B) else Color(0xFF00E5FF),
                                RoundedCornerShape(4.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isBypassActive) "A: MIX ORIGINAL (RAW)" else "B: MASTER DSP ACTIVO",
                        fontSize = 10.sp,
                        color = if (isBypassActive) Color(0xFFF59E0B) else Color(0xFF00E5FF)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val width = size.width
                val height = size.height
                val barCount = 6
                val barSpacing = 10.dp.toPx()
                val totalSpacing = barSpacing * (barCount - 1)
                val barWidth = (width - totalSpacing) / barCount

                // Grid lines (-12dB, -24dB, -36dB)
                val gridColor = Color(0xFF1E293B)
                for (step in 1..3) {
                    val y = height * (step / 4f)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                    )
                }

                val activeAnalysis = if (isBypassActive) rawAnalysis else masteredAnalysis
                val baseEnergies = listOf(
                    activeAnalysis.subBassEnergy,
                    activeAnalysis.bassEnergy,
                    activeAnalysis.lowMidEnergy,
                    activeAnalysis.midEnergy,
                    activeAnalysis.highMidEnergy,
                    activeAnalysis.airEnergy
                )

                val primaryColor = if (isBypassActive) Color(0xFFF59E0B) else Color(0xFF00E5FF)
                val gradientSecondary = if (isBypassActive) Color(0xFFD97706) else Color(0xFF0284C7)

                for (i in 0 until barCount) {
                    val x = i * (barWidth + barSpacing)
                    val baseRatio = baseEnergies[i] * 2.8f
                    val dynamicBoost = if (isPlaying) (livePeak * 0.4f * (if (i % 2 == 0) pulseAnim else 1f)) else 0f
                    val finalRatio = (baseRatio + dynamicBoost).coerceIn(0.08f, 1.0f)
                    val barHeight = height * finalRatio
                    val y = height - barHeight

                    // Draw main spectrum bar with linear gradient
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                if (finalRatio > 0.85f) Color(0xFFF43F5E) else primaryColor,
                                gradientSecondary,
                                Color(0xFF0F172A)
                            ),
                            startY = y,
                            endY = height
                        ),
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )

                    // Draw peak hold cap line
                    val peakCapY = (y - 3.dp.toPx()).coerceAtLeast(0f)
                    drawRoundRect(
                        color = if (finalRatio > 0.85f) Color(0xFFFF1744) else Color.White,
                        topLeft = Offset(x, peakCapY),
                        size = Size(barWidth, 2.dp.toPx()),
                        cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Frequency band labels underneath
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                bandLabels.forEach { label ->
                    Text(
                        text = label,
                        fontSize = 9.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
