package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Card for importing custom parameter files (.txt / .json), exporting acoustic analysis reports,
 * and downloading editable parameter templates.
 */
@Composable
fun CustomProfileCard(
    isCustomProfileActive: Boolean,
    customProfileName: String?,
    onImportProfile: () -> Unit,
    onExportAnalysis: () -> Unit,
    onDownloadTemplate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("custom_profile_card"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1723)),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isCustomProfileActive) Color(0xFF00E5FF) else Color(0xFF1E293B)
            )
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header with custom profile badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = if (isCustomProfileActive) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PERFILES & ARCHIVOS (.TXT / .JSON)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isCustomProfileActive) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF1E293B)
                ) {
                    Text(
                        text = if (isCustomProfileActive) "ARCHIVO APLICADO" else "AJUSTES MANUALES (PLANO)",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCustomProfileActive) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Aplica ajustes al máster subiendo un archivo con tus presets (.txt o .json), o descarga la plantilla editable para crear el tuyo.",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button 1: Load Profile File
                Button(
                    onClick = onImportProfile,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(38.dp)
                        .testTag("load_profile_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCustomProfileActive) Color(0xFF0284C7) else Color(0xFF1E293B),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "CARGAR .TXT/.JSON",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Button 2: Export Analysis & Parameters
                FilledTonalButton(
                    onClick = onExportAnalysis,
                    modifier = Modifier
                        .weight(1.4f)
                        .height(38.dp)
                        .testTag("export_analysis_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF132032),
                        contentColor = Color(0xFF38BDF8)
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "DESCARGAR ANÁLISIS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Button 3: Download Template
            OutlinedButton(
                onClick = onDownloadTemplate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .testTag("download_template_button"),
                shape = RoundedCornerShape(8.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF334155))
                ),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "DESCARGAR PLANTILLA EDITABLE (.TXT)",
                    fontSize = 10.sp,
                    color = Color(0xFFCBD5E1),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
