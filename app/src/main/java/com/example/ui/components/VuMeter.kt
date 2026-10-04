package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.log10

/**
 * Dual Studio Peak & RMS VU Meter with dB indicators and Clip LED warning.
 */
@Composable
fun StereoVuMeter(
    peakL: Float,
    peakR: Float,
    isClipping: Boolean = false,
    modifier: Modifier = Modifier
) {
    val animatedL by animateFloatAsState(
        targetValue = peakL.coerceIn(0f, 1.2f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
        label = "meterL"
    )
    val animatedR by animateFloatAsState(
        targetValue = peakR.coerceIn(0f, 1.2f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
        label = "meterR"
    )

    Column(
        modifier = modifier
            .background(Color(0xFF0D131C), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "VU METER MASTER L / R",
                fontSize = 10.sp,
                color = Color(0xFF94A3B8),
                letterSpacing = 1.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (isClipping || peakL > 0.98f || peakR > 0.98f) Color(0xFFFF1744) else Color(0xFF1E293B),
                            RoundedCornerShape(4.dp)
                        )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "CLIP",
                    fontSize = 9.sp,
                    color = if (isClipping || peakL > 0.98f || peakR > 0.98f) Color(0xFFFF1744) else Color(0xFF64748B)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Left Channel Bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("L", fontSize = 10.sp, color = Color(0xFF94A3B8), modifier = Modifier.width(14.dp))
            VuBar(ratio = animatedL, modifier = Modifier.weight(1f).height(10.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Right Channel Bar
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("R", fontSize = 10.sp, color = Color(0xFF94A3B8), modifier = Modifier.width(14.dp))
            VuBar(ratio = animatedR, modifier = Modifier.weight(1f).height(10.dp))
        }

        Spacer(modifier = Modifier.height(4.dp))

        // dB Scale markers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("-36", "-24", "-18", "-12", "-6", "-3", "0", "+3").forEach { db ->
                Text(db, fontSize = 8.sp, color = Color(0xFF64748B))
            }
        }
    }
}

@Composable
private fun VuBar(
    ratio: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        // Dark background track
        drawRoundRect(
            color = Color(0xFF131B26),
            size = size,
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )

        // Filled segment
        val fillWidth = (width * ratio.coerceIn(0f, 1f))
        if (fillWidth > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF00E5FF),
                        Color(0xFF10B981),
                        Color(0xFFFBBF24),
                        Color(0xFFF43F5E)
                    ),
                    startX = 0f,
                    endX = width
                ),
                size = Size(fillWidth, height),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }

        // Draw 0dB mark line at 85% width
        val zeroX = width * 0.85f
        drawLine(
            color = Color(0xFFEF4444),
            start = Offset(zeroX, 0f),
            end = Offset(zeroX, height),
            strokeWidth = 1.5f
        )
    }
}
