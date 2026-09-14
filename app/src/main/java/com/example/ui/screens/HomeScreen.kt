package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatColorReset
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PdfItem
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.components.ToolCard
import com.example.ui.components.formatFileSize
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.CrimsonPrimaryDark
import java.io.File

data class ToolDefinition(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconBgColor: Color,
    val category: String,
    val badge: String? = null,
    val onClick: (PdfViewModel, () -> Unit) -> Unit
)

@Composable
fun HomeScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allPdfs by viewModel.allPdfs.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    // File picker for "Open & View Any PDF"
    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val tempFile = File(context.cacheDir, "preview_${System.currentTimeMillis()}.pdf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                viewModel.navigateTo(AppScreen.Viewer(tempFile, tempFile.name))
            } catch (e: Exception) {
                // Ignore failure
            }
        }
    }

    val totalPages = allPdfs.sumOf { it.pageCount }
    val totalBytes = allPdfs.sumOf { it.fileSizeBytes }

    // Full catalog of 20+ PDF Converter tools
    val allTools = remember {
        listOf(
            // 1. Convert & Scan
            ToolDefinition(
                id = "images_to_pdf",
                title = "Images to PDF",
                subtitle = "Convert photos & receipts",
                icon = Icons.Default.Collections,
                iconBgColor = CrimsonPrimary,
                category = "Convert",
                badge = "Popular",
                onClick = { vm, _ -> vm.navigateTo(AppScreen.ImagesToPdf) }
            ),
            ToolDefinition(
                id = "document_scan",
                title = "Document Scanner",
                subtitle = "Scan pages into crisp PDF",
                icon = Icons.Default.AutoFixHigh,
                iconBgColor = Color(0xFFC2185B),
                category = "Convert",
                badge = "New",
                onClick = { vm, _ -> vm.navigateTo(AppScreen.DocumentScan) }
            ),
            ToolDefinition(
                id = "text_to_pdf",
                title = "Text to PDF",
                subtitle = "Format rich documents & notes",
                icon = Icons.Default.TextFields,
                iconBgColor = AccentBlue,
                category = "Convert",
                onClick = { vm, _ -> vm.navigateTo(AppScreen.TextToPdf) }
            ),
            ToolDefinition(
                id = "web_to_pdf",
                title = "Web & HTML to PDF",
                subtitle = "Convert articles & web code",
                icon = Icons.Default.Language,
                iconBgColor = Color(0xFF00897B),
                category = "Convert",
                onClick = { vm, _ -> vm.navigateTo(AppScreen.WebHtmlToPdf) }
            ),
            ToolDefinition(
                id = "pdf_to_images",
                title = "PDF to Images",
                subtitle = "Extract high-res JPG/PNG",
                icon = Icons.Default.Image,
                iconBgColor = AccentTeal,
                category = "Convert",
                onClick = { vm, _ -> vm.navigateTo(AppScreen.PdfToImages) }
            ),

            // 2. Organize Pages
            ToolDefinition(
                id = "merge_pdf",
                title = "Merge PDFs",
                subtitle = "Combine multiple documents",
                icon = Icons.Default.Merge,
                iconBgColor = Color(0xFFE65100),
                category = "Organize",
                badge = "Essential",
                onClick = { vm, _ -> vm.navigateTo(AppScreen.MergePdf) }
            ),
            ToolDefinition(
                id = "split_pdf",
                title = "Split PDF",
                subtitle = "Extract pages or ranges",
                icon = Icons.Default.CallSplit,
                iconBgColor = Color(0xFF6A1B9A),
                category = "Organize",
                onClick = { vm, _ -> vm.navigateTo(AppScreen.SplitPdf) }
            ),
            ToolDefinition(
                id = "delete_pages",
                title = "Delete Pages",
                subtitle = "Remove unwanted pages",
                icon = Icons.Default.Delete,
                iconBgColor = Color(0xFFD32F2F),
                category = "Organize",
                onClick = { vm, _ -> vm.navigateTo(AppScreen.DeletePagesPdf) }
            ),
            ToolDefinition(
                id = "reorder_pages",
                title = "Reorder Pages",
                subtitle = "Rearrange sequence of pages",
                icon = Icons.Default.SwapVert,
                iconBgColor = Color(0xFF00ACC1),
                category = "Organize",
                onClick = { vm, _ -> vm.navigateTo(AppScreen.ReorderPagesPdf) }
            ),
            ToolDefinition(
                id = "rotate_pdf",
                title = "Rotate Pages",
                subtitle = "Fix orientation 90°, 180°",
                icon = Icons.Default.RotateRight,
                iconBgColor = Color(0xFF43A047),
                category = "Organize",
                onClick = { vm, _ -> vm.navigateTo(AppScreen.RotatePdf) }
            ),

            // 3. Optimize & Style
            ToolDefinition(
                id = "compress_pdf",
                title = "Compress PDF",
                subtitle = "Reduce size for email & uploads",
                icon = Icons.Default.Compress,
                iconBgColor = Color(0xFF1E88E5),
                category = "Optimize",
                badge = "Save Space",
                onClick = { vm, _ -> vm.navigateTo(AppScreen.CompressPdf) }
            ),
            ToolDefinition(
                id = "grayscale_pdf",
                title = "Ink-Saver B&W",
                subtitle = "Monochrome print optimization",
                icon = Icons.Default.FormatColorReset,
                iconBgColor = Color(0xFF546E7A),
                category = "Optimize",
                onClick = { vm, _ -> vm.navigateTo(AppScreen.GrayscaleInvertPdf("GRAYSCALE")) }
            ),
            ToolDefinition(
                id = "dark_mode_pdf",
                title = "Invert Dark Mode",
                subtitle = "Night reading mode for eyes",
                icon = Icons.Default.DarkMode,
                iconBgColor = Color(0xFF263238),
                category = "Optimize",
                onClick = { vm, _ -> vm.navigateTo(AppScreen.GrayscaleInvertPdf("INVERT")) }
            ),
            ToolDefinition(
                id = "page_numbers",
                title = "Page Numbers",
                subtitle = "Stamp footers & headers",
                icon = Icons.Default.FormatListNumbered,
                iconBgColor = Color(0xFF8E24AA),
                category = "Optimize",
                onClick = { vm, _ -> vm.navigateTo(AppScreen.PageNumbersPdf) }
            ),
            ToolDefinition(
                id = "watermark_pdf",
                title = "Watermark Text",
                subtitle = "Custom diagonal text branding",
                icon = Icons.Default.PictureAsPdf,
                iconBgColor = Color(0xFF00838F),
                category = "Optimize",
                onClick = { vm, _ -> vm.navigateTo(AppScreen.WatermarkPdf) }
            ),

            // 4. Sign & Security
            ToolDefinition(
                id = "sign_pdf",
                title = "Sign PDF",
                subtitle = "Draw digital signature on page",
                icon = Icons.Default.Draw,
                iconBgColor = Color(0xFF3949AB),
                category = "Security",
                badge = "Top Pick",
                onClick = { vm, _ -> vm.navigateTo(AppScreen.SignPdf) }
            ),
            ToolDefinition(
                id = "stamp_badges",
                title = "Stamp Badges",
                subtitle = "APPROVED, CONFIDENTIAL, etc.",
                icon = Icons.Default.Verified,
                iconBgColor = Color(0xFFD81B60),
                category = "Security",
                onClick = { vm, _ -> vm.navigateTo(AppScreen.StampPdf) }
            ),
            ToolDefinition(
                id = "extract_text",
                title = "Extract Text",
                subtitle = "Inspect text & page structure",
                icon = Icons.Default.Description,
                iconBgColor = Color(0xFF689F38),
                category = "Security",
                onClick = { vm, _ -> vm.navigateTo(AppScreen.ExtractText) }
            ),
            ToolDefinition(
                id = "open_any_pdf",
                title = "Open Any PDF",
                subtitle = "Built-in reader & page viewer",
                icon = Icons.Default.Visibility,
                iconBgColor = Color(0xFF455A64),
                category = "Security",
                onClick = { _, pick -> pick() }
            ),
            ToolDefinition(
                id = "my_library",
                title = "My Library",
                subtitle = "${allPdfs.size} saved documents",
                icon = Icons.Default.Folder,
                iconBgColor = Color(0xFF2E7D32),
                category = "Security",
                onClick = { vm, _ -> vm.navigateTo(AppScreen.History) }
            )
        )
    }

    // Filter tools by search query and category
    val filteredTools = remember(searchQuery, selectedCategory, allPdfs.size) {
        allTools.filter { tool ->
            val matchesCategory = selectedCategory == "All" || tool.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() ||
                    tool.title.contains(searchQuery, ignoreCase = true) ||
                    tool.subtitle.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    val categories = listOf(
        Pair("All", "All (${allTools.size})"),
        Pair("Convert", "Convert & Scan"),
        Pair("Organize", "Organize Pages"),
        Pair("Optimize", "Optimize & Style"),
        Pair("Security", "Sign & Badges")
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Hero Header with Gradient & Live Stats
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                CrimsonPrimaryDark,
                                CrimsonPrimary
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "PDF Converter",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "20+ All-in-One Offline Document Tools",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White.copy(alpha = 0.88f)
                                )
                            )
                        }

                        IconButton(
                            onClick = { viewModel.navigateTo(AppScreen.History) },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "History",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Quick Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatPill(
                            label = "Documents",
                            value = "${allPdfs.size}",
                            modifier = Modifier.weight(1f)
                        )
                        StatPill(
                            label = "Pages Created",
                            value = "$totalPages",
                            modifier = Modifier.weight(1f)
                        )
                        StatPill(
                            label = "Storage",
                            value = formatFileSize(totalBytes),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Search Bar
        item {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search tools (scan, compress, sign, rotate...)") },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = CrimsonPrimary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CrimsonPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )
        }

        // Category Filter Chips
        item {
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { (catKey, catLabel) ->
                    FilterChip(
                        selected = selectedCategory == catKey,
                        onClick = { selectedCategory = catKey },
                        label = { Text(catLabel, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        }

        // Quick Actions Strip (when no active text search)
        if (searchQuery.isBlank()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            QuickActionChip(
                                icon = Icons.Default.AutoFixHigh,
                                label = "Scan Doc",
                                color = Color(0xFFC2185B),
                                onClick = { viewModel.navigateTo(AppScreen.DocumentScan) }
                            )
                        }
                        item {
                            QuickActionChip(
                                icon = Icons.Default.Collections,
                                label = "Images to PDF",
                                color = CrimsonPrimary,
                                onClick = { viewModel.navigateTo(AppScreen.ImagesToPdf) }
                            )
                        }
                        item {
                            QuickActionChip(
                                icon = Icons.Default.Compress,
                                label = "Compress",
                                color = Color(0xFF1E88E5),
                                onClick = { viewModel.navigateTo(AppScreen.CompressPdf) }
                            )
                        }
                        item {
                            QuickActionChip(
                                icon = Icons.Default.Draw,
                                label = "Sign PDF",
                                color = Color(0xFF3949AB),
                                onClick = { viewModel.navigateTo(AppScreen.SignPdf) }
                            )
                        }
                        item {
                            QuickActionChip(
                                icon = Icons.Default.Merge,
                                label = "Merge",
                                color = Color(0xFFE65100),
                                onClick = { viewModel.navigateTo(AppScreen.MergePdf) }
                            )
                        }
                    }
                }
            }
        }

        // Section Title
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "Search Results (${filteredTools.size})" else "All PDF Tools",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = "${filteredTools.size} Tools",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Grid of 20+ Tools (chunked into pairs)
        val toolPairs = filteredTools.chunked(2)
        items(toolPairs) { pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ToolCard(
                    title = pair[0].title,
                    subtitle = pair[0].subtitle,
                    icon = pair[0].icon,
                    iconBgColor = pair[0].iconBgColor,
                    badgeText = pair[0].badge,
                    onClick = { pair[0].onClick(viewModel) { openPdfLauncher.launch(arrayOf("application/pdf")) } },
                    modifier = Modifier.weight(1f)
                )

                if (pair.size > 1) {
                    ToolCard(
                        title = pair[1].title,
                        subtitle = pair[1].subtitle,
                        icon = pair[1].icon,
                        iconBgColor = pair[1].iconBgColor,
                        badgeText = pair[1].badge,
                        onClick = { pair[1].onClick(viewModel) { openPdfLauncher.launch(arrayOf("application/pdf")) } },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Recent Documents Section
        if (allPdfs.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Documents",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Text(
                        text = "See All",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = CrimsonPrimary,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.clickable { viewModel.navigateTo(AppScreen.History) }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(allPdfs.take(6)) { pdf ->
                        RecentPdfCard(
                            pdf = pdf,
                            onOpen = {
                                viewModel.navigateTo(AppScreen.Viewer(File(pdf.filePath), pdf.title))
                            },
                            onDownload = {
                                viewModel.downloadToDevice(context, File(pdf.filePath))
                            },
                            onShare = {
                                viewModel.sharePdf(context, File(pdf.filePath))
                            },
                            onToggleFavorite = {
                                viewModel.toggleFavorite(pdf)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionChip(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = color)
        }
    }
}

@Composable
fun StatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.18f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RecentPdfCard(
    pdf: PdfItem,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(230.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onOpen),
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
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CrimsonPrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = CrimsonPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Row {
                    IconButton(onClick = onDownload, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = if (pdf.isFavorite) Icons.Default.Star else Icons.Default.StarOutline,
                            contentDescription = "Favorite",
                            tint = if (pdf.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = pdf.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${pdf.pageCount} ${if (pdf.pageCount == 1) "page" else "pages"} · ${formatFileSize(pdf.fileSizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = formatTimestamp(pdf.timestamp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
