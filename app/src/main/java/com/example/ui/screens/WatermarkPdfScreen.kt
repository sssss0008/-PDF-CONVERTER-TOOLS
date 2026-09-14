package com.example.ui.screens

import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.components.PdfTopBar

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatermarkPdfScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val source by viewModel.watermarkPdfSource.collectAsState()
    val watermarkText by viewModel.watermarkText.collectAsState()
    val angle by viewModel.watermarkAngle.collectAsState()
    val fontSize by viewModel.watermarkSize.collectAsState()
    val addPageNumbers by viewModel.watermarkAddPageNumbers.collectAsState()
    val title by viewModel.watermarkDocTitle.collectAsState()

    var selectedColorIndex by remember { mutableStateOf(0) }
    val colorOptions = listOf(
        "Crimson Red" to AndroidColor.argb(80, 211, 47, 47),
        "Slate Gray" to AndroidColor.argb(80, 55, 71, 79),
        "Cobalt Blue" to AndroidColor.argb(80, 25, 118, 210),
        "Forest Green" to AndroidColor.argb(80, 46, 125, 50)
    )

    val watermarkPresets = listOf(
        "CONFIDENTIAL", "DRAFT", "COPY", "OFFICIAL", "SAMPLE", "DO NOT COPY"
    )

    val pickPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.setWatermarkPdfSource(context, uri)
        }
    }

    Scaffold(
        topBar = {
            PdfTopBar(
                title = "Watermark & Numbers",
                onBack = { viewModel.navigateTo(AppScreen.Home) }
            )
        },
        bottomBar = {
            if (source != null) {
                Surface(
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.watermarkColorHex.value = colorOptions[selectedColorIndex].second
                                viewModel.applyWatermark(context)
                            },
                            enabled = watermarkText.isNotBlank() || addPageNumbers,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("apply_watermark_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Apply & Save PDF",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Source Document Selector
            item {
                if (source == null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { pickPdfLauncher.launch(arrayOf("application/pdf")) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = "Select PDF",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Select PDF to Watermark",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Choose a document to protect with watermarks & numbering",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = source?.name ?: "Document.pdf",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${source?.pageCount} pages will be watermarked",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                OutlinedButton(
                                    onClick = { pickPdfLauncher.launch(arrayOf("application/pdf")) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Change")
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = title,
                                onValueChange = { viewModel.watermarkDocTitle.value = it },
                                label = { Text("Output Document Title") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Watermark configuration
            if (source != null) {
                // Live preview simulation card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Live Watermark Preview",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Simulated page sheet
                            Surface(
                                modifier = Modifier
                                    .width(200.dp)
                                    .aspectRatio(0.72f),
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                shadowElevation = 4.dp
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    // Faint dummy content lines
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(modifier = Modifier.fillMaxWidth(0.5f).height(6.dp).background(Color(0xFFE0E0E0)))
                                        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color(0xFFEEEEEE)))
                                        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(Color(0xFFEEEEEE)))
                                        Box(modifier = Modifier.fillMaxWidth(0.8f).height(4.dp).background(Color(0xFFEEEEEE)))
                                        Spacer(modifier = Modifier.weight(1f))
                                        if (addPageNumbers) {
                                            Text(
                                                text = "Page 1 of ${source?.pageCount ?: 1}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, color = Color.Gray),
                                                modifier = Modifier.align(Alignment.CenterHorizontally)
                                            )
                                        }
                                    }

                                    // Watermark text in center
                                    if (watermarkText.isNotBlank()) {
                                        val displayColor = when (selectedColorIndex) {
                                            0 -> Color(0xD9D32F2F)
                                            1 -> Color(0xD937474F)
                                            2 -> Color(0xD91976D2)
                                            else -> Color(0xD92E7D32)
                                        }
                                        Text(
                                            text = watermarkText,
                                            color = displayColor,
                                            fontSize = (fontSize / 2.5f).sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.rotate(angle)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Watermark Text input & Preset chips
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            OutlinedTextField(
                                value = watermarkText,
                                onValueChange = { viewModel.watermarkText.value = it },
                                label = { Text("Watermark Stamp Text") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text("Preset Badges", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(watermarkPresets) { preset ->
                                    AssistChip(
                                        onClick = { viewModel.watermarkText.value = preset },
                                        label = { Text(preset, fontSize = 12.sp) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Customization controls (Angle, Color, Page Numbers)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Stamp Appearance",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Angle
                            Text("Angle: ${angle.toInt()}°", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(-45f, 0f, 45f, 90f).forEach { ang ->
                                    FilterChip(
                                        selected = angle == ang,
                                        onClick = { viewModel.watermarkAngle.value = ang },
                                        label = { Text("${ang.toInt()}°") }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Color Choice
                            Text("Color Palette", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            Spacer(modifier = Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                colorOptions.forEachIndexed { idx, (name, _) ->
                                    FilterChip(
                                        selected = selectedColorIndex == idx,
                                        onClick = { selectedColorIndex = idx },
                                        label = { Text(name) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Font size slider
                            Text("Watermark Size: ${fontSize.toInt()} pt", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            Slider(
                                value = fontSize,
                                onValueChange = { viewModel.watermarkSize.value = it },
                                valueRange = 24f..72f,
                                steps = 5
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Switch: Page Numbers
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Add Page Numbers", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Text("Stamp 'Page X of Y' in bottom center", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = addPageNumbers,
                                    onCheckedChange = { viewModel.watermarkAddPageNumbers.value = it }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
