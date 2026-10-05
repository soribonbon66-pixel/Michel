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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Visualizer for real-time LUFS Loudness Data (ITU-R BS.1770 / EBU R128).
 * Displays a calibrated horizontal loudness bar or circular gauge with target pins and platform compliance status.
 */
@Composable
fun LufsMeterBar(
    currentLufs: Float,
    targetLufs: Float = -8.5f,
    shortTermLufs: Float = currentLufs,
    isMastered: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Normalization bounds: -36.0 LUFS (min) to -4.0 LUFS (max)
    val minLufs = -36.0f
    val maxLufs = -4.0f
    val lufsRange = maxLufs - minLufs

    val normalizedCurrent = ((currentLufs - minLufs) / lufsRange).coerceIn(0f, 1f)
    val normalizedTarget = ((targetLufs - minLufs) / lufsRange).coerceIn(0f, 1f)
    val normalizedShortTerm = ((shortTermLufs - minLufs) / lufsRange).coerceIn(0f, 1f)

    val animatedFill by animateFloatAsState(
        targetValue = normalizedCurrent,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
        label = "lufsFill"
    )

    val animatedShortTerm by animateFloatAsState(
        targetValue = normalizedShortTerm,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 400f),
        label = "lufsShortTerm"
    )

    // Loudness Category & Color Indicator
    val (statusLabel, statusColor) = when {
        currentLufs < -22f -> "MEZCLA MUY BAJA" to Color(0xFF38BDF8)
        currentLufs < -16f -> "RANGO DINÁMICO AMPLIO" to Color(0xFF00E5FF)
        currentLufs < -12f -> "ESTÁNDAR STREAMING (-14 LUFS)" to Color(0xFF10B981)
        currentLufs < -6.5f -> "HIT URBANO / CLUB READY" to Color(0xFFFBBF24)
        else -> "COMPETITIVO EXTREMO / HOT" to Color(0xFFFF5252)
    }

    val deltaLufs = currentLufs - targetLufs
    val deltaText = if (deltaLufs >= 0f) "+${"%.1f".format(deltaLufs)}" else "%.1f".format(deltaLufs)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0F18), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
            .padding(12.dp)
            .testTag("lufs_meter_bar")
    ) {
        // Header with active status and integrated readout
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SONORIDAD LUFS INTEGRADA",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                if (isMastered) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF64748B).copy(alpha = 0.2f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (isMastered) "MASTER (B)" else "RAW (A)",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMastered) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                        )
                    }
                }
                Text(
                    text = statusLabel,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }

            // Big Numeric LUFS Readout
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "%.1f".format(currentLufs),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = " LUFS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(
                            if (kotlin.math.abs(deltaLufs) <= 1.0f) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFFBBF24).copy(alpha = 0.15f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Δ $deltaText LU",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (kotlin.math.abs(deltaLufs) <= 1.0f) Color(0xFF10B981) else Color(0xFFFBBF24)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal Calibrated Loudness Bar Canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .testTag("lufs_horizontal_track")
        ) {
            val width = size.width
            val height = size.height

            // 1. Dark background rail
            drawRoundRect(
                color = Color(0xFF111827),
                size = size,
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // 2. Zone indicators (subtle background shading)
            // Streaming optimal zone: -16 to -12 LUFS
            val stStartX = width * ((-16f - minLufs) / lufsRange)
            val stEndX = width * ((-12f - minLufs) / lufsRange)
            drawRect(
                color = Color(0xFF10B981).copy(alpha = 0.15f),
                topLeft = Offset(stStartX, 0f),
                size = Size(stEndX - stStartX, height)
            )

            // Urban/Club target zone: -12 to -7 LUFS
            val clubStartX = width * ((-12f - minLufs) / lufsRange)
            val clubEndX = width * ((-7f - minLufs) / lufsRange)
            drawRect(
                color = Color(0xFFFBBF24).copy(alpha = 0.15f),
                topLeft = Offset(clubStartX, 0f),
                size = Size(clubEndX - clubStartX, height)
            )

            // 3. Active Filled Meter with dynamic studio gradient
            val fillWidth = width * animatedFill
            if (fillWidth > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF0284C7), // -36 LUFS (Deep Cyan)
                            Color(0xFF00E5FF), // -24 LUFS (Light Blue)
                            Color(0xFF10B981), // -14 LUFS (Streaming Green)
                            Color(0xFFFBBF24), // -9 LUFS (Perreo Gold)
                            Color(0xFFFF5252)  // -5 LUFS (Hot Red)
                        ),
                        startX = 0f,
                        endX = width
                    ),
                    size = Size(fillWidth, height),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }

            // 4. Short-term peak hold line
            val shortTermX = width * animatedShortTerm
            if (shortTermX > 0f && shortTermX <= width) {
                drawLine(
                    color = Color.White.copy(alpha = 0.9f),
                    start = Offset(shortTermX, 0f),
                    end = Offset(shortTermX, height),
                    strokeWidth = 2.dp.toPx()
                )
            }

            // 5. Target marker needle with triangular top marker
            val targetX = width * normalizedTarget
            drawLine(
                color = Color(0xFFFFD600),
                start = Offset(targetX, 0f),
                end = Offset(targetX, height),
                strokeWidth = 2.5.dp.toPx()
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Scale markings with standard LUFS anchors
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val markers = listOf(
                "-36" to "-36",
                "-24" to "-24",
                "-18" to "-18",
                "-14" to "-14 [Spotify]",
                "-9" to "-9 [Club]",
                "-7" to "-7",
                "-4" to "-4"
            )

            markers.forEach { (lufsVal, label) ->
                val isTargetAnchor = lufsVal == targetLufs.toInt().toString()
                Text(
                    text = label,
                    fontSize = 8.sp,
                    fontWeight = if (isTargetAnchor) FontWeight.Black else FontWeight.Normal,
                    color = if (isTargetAnchor) Color(0xFFFFD600) else Color(0xFF64748B)
                )
            }
        }
    }
}
