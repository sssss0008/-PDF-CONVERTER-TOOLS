package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.SelectedPdfInfo
import com.example.ui.components.PdfTopBar
import com.example.ui.theme.CrimsonPrimary

@Composable
fun PdfFilePickerCard(
    pdfInfo: SelectedPdfInfo?,
    onSelectPdf: () -> Unit,
    label: String = "Select PDF Document",
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onSelectPdf),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (pdfInfo != null) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (pdfInfo != null) CrimsonPrimary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (pdfInfo != null) Icons.Default.Description else Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = if (pdfInfo != null) CrimsonPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (pdfInfo != null) {
                    Text(
                        text = pdfInfo.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${pdfInfo.pageCount} ${if (pdfInfo.pageCount == 1) "page" else "pages"} detected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tap to choose file from device",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = onSelectPdf,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (pdfInfo != null) "Change" else "Browse")
            }
        }
    }
}

// -------------------------------------------------------------
// 1. COMPRESS PDF SCREEN
// -------------------------------------------------------------
@Composable
fun CompressPdfScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val source by viewModel.compressPdfSource.collectAsState()
    val quality by viewModel.compressQuality.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.setCompressPdfSource(context, it) }
    }

    Scaffold(
        topBar = {
            PdfTopBar(
                title = "Compress PDF",
                onBack = { viewModel.navigateTo(AppScreen.Home) }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PdfFilePickerCard(
                pdfInfo = source,
                onSelectPdf = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                label = "Select PDF to Compress"
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Compression Level",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    listOf(
                        Triple(40, "Extreme Compression (40%)", "Maximum file reduction for email & uploads"),
                        Triple(65, "Balanced Compression (65%)", "Ideal blend of compact size and crisp clarity"),
                        Triple(85, "Light Compression (85%)", "Preserves high-res images with modest reduction")
                    ).forEach { (valQual, title, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.compressQuality.value = valQual }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (quality == valQual),
                                onClick = { viewModel.compressQuality.value = valQual }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { viewModel.executeCompressPdf(context) },
                enabled = source != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("compress_pdf_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.Compress, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Compress Document", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// -------------------------------------------------------------
// 2. ROTATE PDF SCREEN
// -------------------------------------------------------------
@Composable
fun RotatePdfScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val source by viewModel.rotatePdfSource.collectAsState()
    val degrees by viewModel.rotateDegrees.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.setRotatePdfSource(context, it) }
    }

    Scaffold(
        topBar = {
            PdfTopBar(
                title = "Rotate PDF Pages",
                onBack = { viewModel.navigateTo(AppScreen.Home) }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PdfFilePickerCard(
                pdfInfo = source,
                onSelectPdf = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                label = "Select PDF to Rotate"
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Rotation Angle",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(
                            Pair(90, "90° CW"),
                            Pair(180, "180° Flip"),
                            Pair(270, "270° CW")
                        ).forEach { (deg, label) ->
                            val isSelected = degrees == deg
                            OutlinedButton(
                                onClick = { viewModel.rotateDegrees.value = deg },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                )
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(imageVector = Icons.Default.RotateRight, contentDescription = null)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { viewModel.executeRotatePdf(context) },
                enabled = source != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("rotate_pdf_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.RotateRight, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Rotate Document", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// -------------------------------------------------------------
// 3. GRAYSCALE & INVERT (DARK MODE) SCREEN
// -------------------------------------------------------------
@Composable
fun GrayscaleInvertScreen(
    initialMode: String,
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val source by viewModel.grayscaleSource.collectAsState()
    var isGrayscale by remember { mutableStateOf(initialMode == "GRAYSCALE") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.setGrayscaleSource(context, it) }
    }

    Scaffold(
        topBar = {
            PdfTopBar(
                title = if (isGrayscale) "Grayscale B&W PDF" else "Invert Dark Mode PDF",
                onBack = { viewModel.navigateTo(AppScreen.Home) }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PdfFilePickerCard(
                pdfInfo = source,
                onSelectPdf = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                label = "Select PDF Document"
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Conversion Mode",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = isGrayscale,
                            onClick = { isGrayscale = true },
                            label = { Text("Ink Saver B&W") },
                            leadingIcon = { Icon(Icons.Default.FormatColorReset, contentDescription = null) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = !isGrayscale,
                            onClick = { isGrayscale = false },
                            label = { Text("Night Dark Mode") },
                            leadingIcon = { Icon(Icons.Default.DarkMode, contentDescription = null) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isGrayscale)
                            "Converts all colored images and illustrations to monochrome grayscale, significantly reducing printer ink consumption."
                        else
                            "Inverts light backgrounds into high-contrast black and light text, ideal for night-time reading without eye fatigue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { viewModel.executeGrayscaleInvert(context, isGrayscale) },
                enabled = source != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = if (isGrayscale) Icons.Default.FormatColorReset else Icons.Default.DarkMode,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isGrayscale) "Apply B&W Filter" else "Generate Dark Mode PDF", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// -------------------------------------------------------------
// 4. PAGE NUMBERS & HEADER SCREEN
// -------------------------------------------------------------
@Composable
fun PageNumbersScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val source by viewModel.pageNumbersSource.collectAsState()
    var headerText by remember { mutableStateOf(viewModel.pageNumbersHeader.value) }
    var selectedFormat by remember { mutableStateOf(viewModel.pageNumbersFormat.value) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.setPageNumbersSource(context, it) }
    }

    Scaffold(
        topBar = {
            PdfTopBar(
                title = "Page Numbering & Header",
                onBack = { viewModel.navigateTo(AppScreen.Home) }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PdfFilePickerCard(
                pdfInfo = source,
                onSelectPdf = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                label = "Select PDF Document"
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Header & Footer Options",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = headerText,
                        onValueChange = {
                            headerText = it
                            viewModel.pageNumbersHeader.value = it
                        },
                        label = { Text("Top Header Title (Optional)") },
                        placeholder = { Text("e.g. Confidential Report 2026") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Footer Number Format",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    listOf(
                        Pair("Page %d of %d", "Page 1 of 10"),
                        Pair("- %d -", "- 1 -"),
                        Pair("Page %d", "Page 1")
                    ).forEach { (format, sample) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFormat = format
                                    viewModel.pageNumbersFormat.value = format
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedFormat == format,
                                onClick = {
                                    selectedFormat = format
                                    viewModel.pageNumbersFormat.value = format
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(sample, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { viewModel.executePageNumbers(context) },
                enabled = source != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.FormatListNumbered, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Page Numbers", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// -------------------------------------------------------------
// 5. DELETE / TRIM PAGES SCREEN
// -------------------------------------------------------------
@Composable
fun DeletePagesScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val source by viewModel.deletePagesSource.collectAsState()
    val selectedToDelete by viewModel.deleteSelectedPages.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.setDeletePagesSource(context, it) }
    }

    Scaffold(
        topBar = {
            PdfTopBar(
                title = "Remove PDF Pages",
                onBack = { viewModel.navigateTo(AppScreen.Home) }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PdfFilePickerCard(
                pdfInfo = source,
                onSelectPdf = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                label = "Select PDF Document"
            )

            if (source != null) {
                Text(
                    text = "Tap the pages you wish to delete (${selectedToDelete.size} selected for removal):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 65.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(source!!.pageCount) { pageIdx ->
                        val isMarked = selectedToDelete.contains(pageIdx)
                        Box(
                            modifier = Modifier
                                .size(65.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isMarked) Color(0xFFFFCDD2)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .border(
                                    width = if (isMarked) 2.dp else 1.dp,
                                    color = if (isMarked) Color.Red else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { viewModel.toggleDeletePage(pageIdx) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "P ${pageIdx + 1}",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (isMarked) Color.Red else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                if (isMarked) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Deleted",
                                        tint = Color.Red,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Button(
                onClick = { viewModel.executeDeletePages(context) },
                enabled = source != null && selectedToDelete.isNotEmpty() && selectedToDelete.size < (source?.pageCount ?: 0),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete ${selectedToDelete.size} Pages", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// -------------------------------------------------------------
// 6. REORDER PAGES SCREEN
// -------------------------------------------------------------
@Composable
fun ReorderPagesScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val source by viewModel.reorderPagesSource.collectAsState()
    val orderList by viewModel.reorderPageList.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.setReorderPagesSource(context, it) }
    }

    Scaffold(
        topBar = {
            PdfTopBar(
                title = "Reorder PDF Pages",
                onBack = { viewModel.navigateTo(AppScreen.Home) }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PdfFilePickerCard(
                pdfInfo = source,
                onSelectPdf = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                label = "Select PDF Document"
            )

            if (source != null) {
                Text(
                    text = "Adjust page order using up and down arrows:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(orderList) { index, originalPageIdx ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = CircleShape,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${index + 1}",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = "Original Page ${originalPageIdx + 1}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = { viewModel.moveReorderPage(index, index - 1) },
                                    enabled = index > 0
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up")
                                }

                                IconButton(
                                    onClick = { viewModel.moveReorderPage(index, index + 1) },
                                    enabled = index < orderList.lastIndex
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down")
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Button(
                onClick = { viewModel.executeReorderPages(context) },
                enabled = source != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.SwapVert, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Reordered PDF", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// -------------------------------------------------------------
// 7. SIGN PDF SCREEN (Interactive Canvas Signature)
// -------------------------------------------------------------
@Composable
fun SignPdfScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val source by viewModel.signPdfSource.collectAsState()
    val targetPage by viewModel.signSelectedPage.collectAsState()

    val lines = remember { mutableStateListOf<List<Offset>>() }
    var currentLine by remember { mutableStateOf<List<Offset>>(emptyList()) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.setSignPdfSource(context, it) }
    }

    Scaffold(
        topBar = {
            PdfTopBar(
                title = "Sign PDF Document",
                onBack = { viewModel.navigateTo(AppScreen.Home) }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PdfFilePickerCard(
                pdfInfo = source,
                onSelectPdf = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                label = "Select PDF to Sign"
            )

            if (source != null && source!!.pageCount > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Target Page:", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(source!!.pageCount) { idx ->
                            FilterChip(
                                selected = targetPage == idx,
                                onClick = { viewModel.signSelectedPage.value = idx },
                                label = { Text("${idx + 1}") }
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Draw Signature",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        TextButton(onClick = {
                            lines.clear()
                            currentLine = emptyList()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFAFAFA))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentLine = listOf(offset)
                                    },
                                    onDrag = { change, _ ->
                                        currentLine = currentLine + change.position
                                    },
                                    onDragEnd = {
                                        if (currentLine.isNotEmpty()) {
                                            lines.add(currentLine)
                                            currentLine = emptyList()
                                        }
                                    }
                                )
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Baseline guide
                            drawLine(
                                color = Color.LightGray,
                                start = Offset(20f, size.height * 0.75f),
                                end = Offset(size.width - 20f, size.height * 0.75f),
                                strokeWidth = 1f
                            )

                            // Render all finished lines
                            lines.forEach { line ->
                                if (line.size > 1) {
                                    val path = Path().apply {
                                        moveTo(line.first().x, line.first().y)
                                        for (i in 1 until line.size) {
                                            lineTo(line[i].x, line[i].y)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color.Black,
                                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                }
                            }

                            // Render currently dragging line
                            if (currentLine.size > 1) {
                                val path = Path().apply {
                                    moveTo(currentLine.first().x, currentLine.first().y)
                                    for (i in 1 until currentLine.size) {
                                        lineTo(currentLine[i].x, currentLine[i].y)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = Color.Black,
                                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    // Render signature lines to Bitmap
                    val bmpW = 600
                    val bmpH = 300
                    val sigBmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                    val canvas = AndroidCanvas(sigBmp)
                    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.BLACK
                        style = AndroidPaint.Style.STROKE
                        strokeWidth = 8f
                        strokeCap = AndroidPaint.Cap.ROUND
                        strokeJoin = AndroidPaint.Join.ROUND
                    }

                    lines.forEach { line ->
                        if (line.size > 1) {
                            val path = android.graphics.Path().apply {
                                moveTo(line.first().x * (bmpW / 350f), line.first().y * (bmpH / 180f))
                                for (i in 1 until line.size) {
                                    lineTo(line[i].x * (bmpW / 350f), line[i].y * (bmpH / 180f))
                                }
                            }
                            canvas.drawPath(path, paint)
                        }
                    }

                    viewModel.executeSignPdf(context, sigBmp)
                },
                enabled = source != null && lines.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.Draw, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Document", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// -------------------------------------------------------------
// 8. STAMP BADGE SCREEN
// -------------------------------------------------------------
@Composable
fun StampPdfScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val source by viewModel.stampPdfSource.collectAsState()
    var selectedBadge by remember { mutableStateOf(viewModel.stampBadgeText.value) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.setStampPdfSource(context, it) }
    }

    val badges = listOf(
        Pair("CONFIDENTIAL", android.graphics.Color.rgb(211, 47, 47)),
        Pair("APPROVED", android.graphics.Color.rgb(46, 125, 50)),
        Pair("DRAFT", android.graphics.Color.rgb(239, 108, 0)),
        Pair("OFFICIAL", android.graphics.Color.rgb(21, 101, 192)),
        Pair("COPY", android.graphics.Color.rgb(97, 97, 97)),
        Pair("URGENT", android.graphics.Color.rgb(194, 24, 91))
    )

    Scaffold(
        topBar = {
            PdfTopBar(
                title = "Stamp Badges",
                onBack = { viewModel.navigateTo(AppScreen.Home) }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PdfFilePickerCard(
                pdfInfo = source,
                onSelectPdf = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                label = "Select PDF to Stamp"
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Choose Official Stamp Badge",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.height(200.dp)
                    ) {
                        items(badges) { (badge, colorInt) ->
                            val isSelected = selectedBadge == badge
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) Color(colorInt) else MaterialTheme.colorScheme.outlineVariant
                                ),
                                color = if (isSelected) Color(colorInt).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .clickable {
                                        selectedBadge = badge
                                        viewModel.stampBadgeText.value = badge
                                        viewModel.stampBadgeColor.value = colorInt
                                    }
                                    .padding(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = badge,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(colorInt)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { viewModel.executeStampPdf(context) },
                enabled = source != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.Verified, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Apply '$selectedBadge' Stamp", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// -------------------------------------------------------------
// 9. DOCUMENT SCAN SCREEN
// -------------------------------------------------------------
@Composable
fun DocumentScanScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scanUris by viewModel.scanImageUris.collectAsState()
    var docTitle by remember { mutableStateOf(viewModel.scanDocTitle.value) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.addScanUris(uris)
    }

    Scaffold(
        topBar = {
            PdfTopBar(
                title = "Document Scanner",
                onBack = { viewModel.navigateTo(AppScreen.Home) }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = docTitle,
                onValueChange = {
                    docTitle = it
                    viewModel.scanDocTitle.value = it
                },
                label = { Text("Document Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

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
                        Text(
                            text = "Scanned Pages (${scanUris.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Pages")
                        }
                    }

                    if (scanUris.isEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No document pages captured yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyColumn(
                            modifier = Modifier.height(200.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(scanUris) { idx, uri ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Page ${idx + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = { viewModel.removeScanUri(idx) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { viewModel.executeDocumentScan(context) },
                enabled = scanUris.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Convert Scans to PDF", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// -------------------------------------------------------------
// 10. WEB / HTML TO PDF SCREEN
// -------------------------------------------------------------
@Composable
fun WebHtmlToPdfScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(viewModel.webArticleTitle.value) }
    var content by remember { mutableStateOf(viewModel.webArticleContent.value) }

    Scaffold(
        topBar = {
            PdfTopBar(
                title = "Web & HTML to PDF",
                onBack = { viewModel.navigateTo(AppScreen.Home) }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    viewModel.webArticleTitle.value = it
                },
                label = { Text("Article Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Web Content or HTML Text",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )

                TextButton(onClick = {
                    val sample = "<h1>Annual Research Summary</h1>\n<p>This report documents strategic initiatives and research findings compiled across quarters.</p>\n<h2>Key Highlights</h2>\n<p>- Accelerated document workflow efficiency by 85%.</p>\n<p>- Local client-side security ensuring privacy.</p>"
                    content = sample
                    viewModel.webArticleContent.value = sample
                }) {
                    Text("Paste Sample")
                }
            }

            OutlinedTextField(
                value = content,
                onValueChange = {
                    content = it
                    viewModel.webArticleContent.value = it
                },
                placeholder = { Text("Paste HTML or web article text here...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(14.dp)
            )

            Button(
                onClick = { viewModel.executeWebHtmlToPdf(context) },
                enabled = content.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Default.Description, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate PDF Document", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// -------------------------------------------------------------
// 11. EXTRACT TEXT & METADATA SCREEN
// -------------------------------------------------------------
@Composable
fun ExtractTextScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val source by viewModel.extractTextSource.collectAsState()
    val extractedText by viewModel.extractedTextResult.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.setExtractTextSource(context, it) }
    }

    Scaffold(
        topBar = {
            PdfTopBar(
                title = "Extract Text & Structure",
                onBack = { viewModel.navigateTo(AppScreen.Home) }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PdfFilePickerCard(
                pdfInfo = source,
                onSelectPdf = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                label = "Select PDF to Extract"
            )

            if (source != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Document Text & Metadata",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )

                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("PDF Text", extractedText)
                                clipboard.setPrimaryClip(clip)
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyColumn(modifier = Modifier.weight(1f)) {
                            item {
                                Text(
                                    text = extractedText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
