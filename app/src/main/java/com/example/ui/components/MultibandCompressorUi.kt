package com.example.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CompressorBand

/**
 * High-precision Multiband Compressor UI controller.
 * Features ultra-fine scrubbing sliders, micro-stepping nudge controls (0.1 dB),
 * and dynamic numerical telemetry for Gain, Threshold, and Ratio.
 */
@Composable
fun MultibandCompressorUi(
    bands: List<CompressorBand>,
    selectedBandId: String,
    onSelectBand: (String) -> Unit,
    onGainChanged: (bandId: String, gainDb: Float) -> Unit,
    onThresholdChanged: (bandId: String, thresholdDb: Float) -> Unit,
    onRatioChanged: (bandId: String, ratio: Float) -> Unit,
    onNudgeGain: (bandId: String, deltaDb: Float) -> Unit,
    onNudgeThreshold: (bandId: String, deltaDb: Float) -> Unit,
    onNudgeRatio: (bandId: String, deltaRatio: Float) -> Unit,
    onToggleBypass: (bandId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeBand = bands.find { it.id == selectedBandId } ?: bands.firstOrNull() ?: return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("multiband_compressor_card"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1017)),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1E293B))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "COMPRESOR MULTIBANDA DE ALTA PRECISIÓN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (activeBand.isBypassed) Color(0x33F59E0B) else Color(0x3310B981)
                ) {
                    Text(
                        text = if (activeBand.isBypassed) "BANDA BYPASS" else "BANDA ACTIVA",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeBand.isBypassed) Color(0xFFF59E0B) else Color(0xFF10B981),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Band Selector Tabs (Low, Mid, High)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                bands.forEach { band ->
                    val isSelected = band.id == selectedBandId
                    BandSelectorPill(
                        band = band,
                        isSelected = isSelected,
                        onSelect = { onSelectBand(band.id) },
                        onToggleBypass = { onToggleBypass(band.id) },
                        modifier = Modifier.weight(1f).testTag("comp_band_${band.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Active Band Telemetry Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111827), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = activeBand.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Rango: ${activeBand.frequencyRange}",
                        fontSize = 10.sp,
                        color = Color(0xFF00E5FF)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TelemetryValue("GAIN", (if (activeBand.gainDb >= 0) "+" else "") + "%.1f dB".format(activeBand.gainDb), Color(0xFF00E5FF))
                    TelemetryValue("THRESH", "%.1f dB".format(activeBand.thresholdDb), Color(0xFFFBBF24))
                    TelemetryValue("RATIO", "%.1f:1".format(activeBand.ratio), Color(0xFFF43F5E))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 1. HIGH-PRECISION GAIN SLIDER (-12.0 dB to +12.0 dB)
            PrecisionSliderGroup(
                label = "Output Make-up Gain",
                unit = "dB",
                value = activeBand.gainDb,
                valueRange = -12.0f..12.0f,
                formattedValue = (if (activeBand.gainDb >= 0) "+" else "") + "%.1f dB".format(activeBand.gainDb),
                accentColor = Color(0xFF00E5FF),
                onValueChange = { onGainChanged(activeBand.id, it) },
                onNudgeFineMinus = { onNudgeGain(activeBand.id, -0.1f) },
                onNudgeFinePlus = { onNudgeGain(activeBand.id, 0.1f) },
                onNudgeCoarseMinus = { onNudgeGain(activeBand.id, -0.5f) },
                onNudgeCoarsePlus = { onNudgeGain(activeBand.id, 0.5f) },
                testTagPrefix = "gain"
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFF1E293B))

            // 2. HIGH-PRECISION THRESHOLD SLIDER (-40.0 dB to 0.0 dB)
            PrecisionSliderGroup(
                label = "Compression Threshold",
                unit = "dB",
                value = activeBand.thresholdDb,
                valueRange = -40.0f..0.0f,
                formattedValue = "%.1f dB".format(activeBand.thresholdDb),
                accentColor = Color(0xFFFBBF24),
                onValueChange = { onThresholdChanged(activeBand.id, it) },
                onNudgeFineMinus = { onNudgeThreshold(activeBand.id, -0.1f) },
                onNudgeFinePlus = { onNudgeThreshold(activeBand.id, 0.1f) },
                onNudgeCoarseMinus = { onNudgeThreshold(activeBand.id, -1.0f) },
                onNudgeCoarsePlus = { onNudgeThreshold(activeBand.id, 1.0f) },
                testTagPrefix = "threshold"
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFF1E293B))

            // 3. HIGH-PRECISION RATIO SLIDER (1.0:1 to 20.0:1)
            PrecisionSliderGroup(
                label = "Compression Ratio",
                unit = ":1",
                value = activeBand.ratio,
                valueRange = 1.0f..20.0f,
                formattedValue = "%.1f:1".format(activeBand.ratio),
                accentColor = Color(0xFFF43F5E),
                onValueChange = { onRatioChanged(activeBand.id, it) },
                onNudgeFineMinus = { onNudgeRatio(activeBand.id, -0.1f) },
                onNudgeFinePlus = { onNudgeRatio(activeBand.id, 0.1f) },
                onNudgeCoarseMinus = { onNudgeRatio(activeBand.id, -0.5f) },
                onNudgeCoarsePlus = { onNudgeRatio(activeBand.id, 0.5f) },
                testTagPrefix = "ratio"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Fast ratio quick-select buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    Pair("2.0:1", 2.0f),
                    Pair("3.5:1", 3.5f),
                    Pair("6.0:1", 6.0f),
                    Pair("10:1", 10.0f)
                ).forEach { (lbl, rVal) ->
                    OutlinedButton(
                        onClick = { onRatioChanged(activeBand.id, rVal) },
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (kotlin.math.abs(activeBand.ratio - rVal) < 0.15f) Color(0xFF1E293B) else Color.Transparent
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (kotlin.math.abs(activeBand.ratio - rVal) < 0.15f) Color(0xFFF43F5E) else Color(0xFF334155)
                            )
                        )
                    ) {
                        Text(
                            text = lbl,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (kotlin.math.abs(activeBand.ratio - rVal) < 0.15f) Color.White else Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BandSelectorPill(
    band: CompressorBand,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleBypass: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .border(
                1.5.dp,
                if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E293B),
                RoundedCornerShape(8.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF132238) else Color(0xFF0D141F)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = band.id.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF94A3B8)
                )

                // Bypass dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (band.isBypassed) Color(0xFFF59E0B) else Color(0xFF10B981))
                        .clickable(onClick = onToggleBypass)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = band.frequencyRange,
                fontSize = 9.sp,
                color = Color(0xFF64748B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PrecisionSliderGroup(
    label: String,
    unit: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    formattedValue: String,
    accentColor: Color,
    onValueChange: (Float) -> Unit,
    onNudgeFineMinus: () -> Unit,
    onNudgeFinePlus: () -> Unit,
    onNudgeCoarseMinus: () -> Unit,
    onNudgeCoarsePlus: () -> Unit,
    testTagPrefix: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color(0xFFCBD5E1),
                fontWeight = FontWeight.Medium
            )

            // High Precision Value Badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF1E293B),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(accentColor.copy(alpha = 0.5f))
                )
            ) {
                Text(
                    text = formattedValue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // High precision continuous slider
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor.copy(alpha = 0.8f),
                inactiveTrackColor = Color(0xFF1E293B)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .testTag("${testTagPrefix}_slider")
        )

        // Micro-stepping fine nudge buttons (-0.5, -0.1, +0.1, +0.5)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NudgeButton(label = "-0.5", onClick = onNudgeCoarseMinus, modifier = Modifier.weight(1f).testTag("${testTagPrefix}_coarse_minus"))
            NudgeButton(label = "-0.1", onClick = onNudgeFineMinus, modifier = Modifier.weight(1f).testTag("${testTagPrefix}_fine_minus"))
            NudgeButton(label = "+0.1", onClick = onNudgeFinePlus, modifier = Modifier.weight(1f).testTag("${testTagPrefix}_fine_plus"))
            NudgeButton(label = "+0.5", onClick = onNudgeCoarsePlus, modifier = Modifier.weight(1f).testTag("${testTagPrefix}_coarse_plus"))
        }
    }
}

@Composable
private fun NudgeButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(28.dp),
        shape = RoundedCornerShape(6.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color(0xFF131B26),
            contentColor = Color(0xFF94A3B8)
        )
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun TelemetryValue(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.End) {
        Text(text = label, fontSize = 8.sp, color = Color(0xFF64748B))
        Text(text = value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, fontFamily = FontFamily.Monospace)
    }
}
