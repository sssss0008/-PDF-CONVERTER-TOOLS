package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.components.PdfTopBar
import com.example.util.FontTypeOption
import com.example.util.MarginOption

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextToPdfScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val title by viewModel.textDocTitle.collectAsState()
    val body by viewModel.textBody.collectAsState()
    val fontType by viewModel.textFontType.collectAsState()
    val fontSize by viewModel.textFontSize.collectAsState()
    val margin by viewModel.textMargin.collectAsState()
    val addPageNumbers by viewModel.textAddPageNumbers.collectAsState()
    val addDateHeader by viewModel.textAddDateHeader.collectAsState()

    val wordCount = if (body.isBlank()) 0 else body.trim().split("\\s+".toRegex()).size
    val charCount = body.length

    val templates = listOf(
        "Meeting Notes" to "Meeting Notes\n\nAttendees:\n- Alice\n- Bob\n- Charlie\n\nAgenda & Key Discussion Points:\n1. Q3 Roadmap Review and Milestone Tracking\n2. Design system token synchronization\n3. Release timeline and test coverage\n\nAction Items:\n[ ] Finalize PDF engine implementation\n[ ] Perform end-to-end integration testing\n[ ] Publish release candidate build",
        "Formal Letter" to "To: Department Header\nSubject: Formal Request\n\nDear Sir/Madam,\n\nI am writing to formally submit our project deliverables for your review and consideration.\nAll required benchmarks, specifications, and performance targets have been thoroughly tested.\n\nThank you for your time and guidance.\n\nSincerely,\nProject Lead",
        "Project Proposal" to "Project Executive Summary\n\n1. Objective\nCreate an intuitive, all-in-one document management utility enabling high-fidelity conversion, merging, page splitting, and watermarking.\n\n2. Key Capabilities\n- Offline-first execution\n- Native rendering\n- Zero latency\n\n3. Conclusion\nReady for immediate deployment."
    )

    Scaffold(
        topBar = {
            PdfTopBar(
                title = "Text to PDF",
                onBack = { viewModel.navigateTo(AppScreen.Home) },
                actions = {
                    if (body.isNotBlank() || title.isNotBlank()) {
                        IconButton(onClick = {
                            viewModel.textDocTitle.value = ""
                            viewModel.textBody.value = ""
                        }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                }
            )
        },
        bottomBar = {
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
                        onClick = { viewModel.convertTextToPdf(context) },
                        enabled = body.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("convert_text_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Generate PDF Document",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
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
            // Document Title
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { viewModel.textDocTitle.value = it },
                    label = { Text("Document Title / Heading") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Quick Starter Templates
            item {
                Column {
                    Text(
                        text = "Quick Starters",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(templates) { (templateTitle, templateContent) ->
                            AssistChip(
                                onClick = {
                                    viewModel.textDocTitle.value = templateTitle
                                    viewModel.textBody.value = templateContent
                                },
                                label = { Text(templateTitle) }
                            )
                        }
                    }
                }
            }

            // Text Editor Area
            item {
                Column {
                    OutlinedTextField(
                        value = body,
                        onValueChange = { viewModel.textBody.value = it },
                        label = { Text("Document Content") },
                        placeholder = { Text("Type or paste your text here...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$wordCount words · $charCount characters",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Formatting & Typography Card
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
                                text = "Typography & Layout",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Font Style
                        Text("Font Family", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FontTypeOption.entries.forEach { option ->
                                FilterChip(
                                    selected = fontType == option,
                                    onClick = { viewModel.textFontType.value = option },
                                    label = { Text(option.label) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Font Size
                        Text("Font Size: ${fontSize.toInt()} pt", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(10f, 12f, 14f, 16f, 18f).forEach { size ->
                                FilterChip(
                                    selected = fontSize == size,
                                    onClick = { viewModel.textFontSize.value = size },
                                    label = { Text("${size.toInt()} pt") },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.FormatSize, contentDescription = null)
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Margins
                        Text("Page Margins", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(MarginOption.NARROW, MarginOption.NORMAL, MarginOption.WIDE).forEach { opt ->
                                FilterChip(
                                    selected = margin == opt,
                                    onClick = { viewModel.textMargin.value = opt },
                                    label = { Text(opt.label) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Switch: Page Numbers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Page Numbers", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text("Display 'Page X of Y' in footer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = addPageNumbers,
                                onCheckedChange = { viewModel.textAddPageNumbers.value = it }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Switch: Date Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Header Date Stamp", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                Text("Include creation date and divider", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = addDateHeader,
                                onCheckedChange = { viewModel.textAddDateHeader.value = it }
                            )
                        }
                    }
                }
            }
        }
    }
}
