package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Hardware-style A/B Testing module with instant Bypass toggle.
 * Switches seamlessly in real-time between Raw Mix (A) and Mastered DSP (B).
 */
@Composable
fun AbToggleSwitch(
    isBypassActive: Boolean,
    onToggleBypass: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isBypassActive) Color(0xFFF59E0B) else Color(0xFF00E5FF),
        label = "border"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1723)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "A/B Testing",
                        tint = borderColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "MÓDULO DE COMPARACIÓN A/B (BEFORE / AFTER)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2E8F0),
                        letterSpacing = 0.5.sp
                    )
                }

                // Status badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isBypassActive) Color(0x33F59E0B) else Color(0x3300E5FF)
                ) {
                    Text(
                        text = if (isBypassActive) "BYPASS ACTIVO (ORIGINAL)" else "DSP ACTIVO (MASTER)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isBypassActive) Color(0xFFF59E0B) else Color(0xFF00E5FF),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Two-state tactile pill switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF090D14))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(24.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Option A: Raw / Bypass
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isBypassActive) Color(0xFFB45309) else Color.Transparent)
                        .clickable { if (!isBypassActive) onToggleBypass() }
                        .testTag("toggle_raw_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isBypassActive) Color(0xFFFDE047) else Color(0xFF475569))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "A : ORIGINAL (RAW)",
                            fontSize = 11.sp,
                            fontWeight = if (isBypassActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isBypassActive) Color.White else Color(0xFF94A3B8)
                        )
                    }
                }

                // Option B: Master Processed
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (!isBypassActive) Color(0xFF0284C7) else Color.Transparent)
                        .clickable { if (isBypassActive) onToggleBypass() }
                        .testTag("toggle_master_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (!isBypassActive) Color(0xFF00E5FF) else Color(0xFF475569))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "B : MASTERIZADO (WET)",
                            fontSize = 11.sp,
                            fontWeight = if (!isBypassActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (!isBypassActive) Color.White else Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }
    }
}
