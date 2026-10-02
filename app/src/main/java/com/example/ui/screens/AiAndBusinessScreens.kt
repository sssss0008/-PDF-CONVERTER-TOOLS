package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.AppScreen
import com.example.ui.PdfViewModel
import com.example.ui.SelectedPdfInfo
import com.example.ui.components.PdfTopBar
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.AccentTeal
import com.example.ui.theme.CrimsonPrimary
import com.example.ui.theme.CrimsonPrimaryDark
import com.example.util.InvoiceLineItem
import com.example.util.PdfEngine
import java.util.Locale

// -------------------------------------------------------------
// 1. AI Google Search Grounding to PDF Screen
// -------------------------------------------------------------
@Composable
fun AiSearchToPdfScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("Breakthroughs in Clean Energy & Storage 2026") }
    var customApiKey by remember { mutableStateOf("") }
    var showApiKeyInput by remember { mutableStateOf(false) }

    val isSearching by viewModel.isSearching.collectAsState()
    val searchResult by viewModel.searchResult.collectAsState()

    val sampleQueries = listOf(
        "Renewable Energy 2026",
        "Artificial Intelligence Trends",
        "Electric Vehicles Outlook",
        "Space Exploration Milestones",
        "Biotech & Longevity Medicine"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("ai_search_to_pdf_screen")
    ) {
        PdfTopBar(
            title = "AI Google Search to PDF",
            onBack = { viewModel.navigateTo(AppScreen.Home) }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card with Gradient
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(CrimsonPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.TravelExplore, contentDescription = null, tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Google Search Grounding",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                                Text(
                                    "Model: gemini-3.5-flash with live web citations",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Enter any subject or research query. Gemini searches the live web for verified facts and compiles a multi-page, formatted PDF report with citations and executive analysis.",
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Input Query
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Research Topic or Inquiry") },
                    placeholder = { Text("e.g. Next-Gen Nuclear Fusion Technology") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_query_input"),
                    shape = RoundedCornerShape(14.dp),
                    maxLines = 2
                )
            }

            // Suggested Topics Chips
            item {
                Column {
                    Text(
                        "Popular Topics:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sampleQueries.take(3).forEach { sample ->
                            FilterChip(
                                selected = query == sample,
                                onClick = { query = sample },
                                label = { Text(sample, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // API Key Toggle (Optional user override)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showApiKeyInput = !showApiKeyInput }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (showApiKeyInput) "Hide Custom API Key (Optional)" else "Enter Custom Gemini API Key (Optional)",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                AnimatedVisibility(visible = showApiKeyInput) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = customApiKey,
                            onValueChange = {
                                customApiKey = it
                                viewModel.searchCustomApiKey.value = it
                            },
                            label = { Text("Gemini API Key") },
                            placeholder = { Text("AI Studio automatically injects this from Secrets") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Text(
                            "Keys in AI Studio are securely provided via Secrets panel. If left empty, BuildConfig is used.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Search Grounding Action Button
            item {
                Button(
                    onClick = {
                        viewModel.searchCustomApiKey.value = customApiKey
                        viewModel.executeSearchGrounding(context, query)
                    },
                    enabled = query.isNotBlank() && !isSearching,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("execute_search_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Searching Google & Synthesizing...")
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ground with Google Search", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            // Results Card & Actions
            item {
                searchResult?.let { res ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    res.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (res.isLiveGrounding) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                                ) {
                                    Text(
                                        if (res.isLiveGrounding) "Google Verified" else "Synthesized",
                                        color = if (res.isLiveGrounding) Color(0xFF2E7D32) else Color(0xFFE65100),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                res.content.take(350) + "...",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )

                            if (res.sources.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Grounded Sources (${res.sources.size}):",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentBlue
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                res.sources.take(3).forEach { s ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp), tint = AccentBlue)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            s.title,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(12.dp))

                            // Generate PDF from Result
                            Button(
                                onClick = {
                                    viewModel.generateSearchPdfFromCurrentResult(context)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("generate_report_pdf_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generate Full PDF Report", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 2. Voice & Speech to PDF Screen (Transcribe Audio & Dictation)
// -------------------------------------------------------------
@Composable
fun VoiceTranscribeScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("Meeting Voice Notes") }
    var speaker by remember { mutableStateOf("Project Lead") }
    var transcript by remember { mutableStateOf(viewModel.voiceTranscript.value) }

    val isVoicePolishing by viewModel.isVoicePolishing.collectAsState()
    val voiceResult by viewModel.voiceResult.collectAsState()

    // Android Speech Recognizer integration
    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    fun startListening() {
        if (speechRecognizer == null) {
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now to transcribe notes...")
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { isListening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { isListening = false }
            override fun onError(error: Int) { isListening = false }
            override fun onResults(results: Bundle?) {
                isListening = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spoke = matches[0]
                    transcript = if (transcript.isBlank()) spoke else "$transcript $spoke"
                    viewModel.voiceTranscript.value = transcript
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    // Update partial preview if needed
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(intent)
        isListening = true
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        isListening = false
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startListening()
        }
    }

    // Mic Pulsing Animation
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("voice_transcribe_screen")
    ) {
        PdfTopBar(
            title = "Voice & Speech to PDF",
            onBack = { viewModel.navigateTo(AppScreen.Home) }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Mic Action
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (isListening) "Listening... Speak now" else "Tap Mic to Dictate Audio Notes",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Record through microphone or type notes. Supports AI executive formatting.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Mic Button
                        Box(
                            modifier = Modifier
                                .scale(pulseScale)
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(if (isListening) CrimsonPrimary else Color(0xFF1A237E))
                                .clickable {
                                    if (isListening) {
                                        stopListening()
                                    } else {
                                        val hasPermission = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasPermission) {
                                            startListening()
                                        } else {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = "Microphone",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            if (isListening) "TAP TO STOP" else "TAP TO RECORD",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isListening) CrimsonPrimary else Color(0xFF1A237E)
                        )
                    }
                }
            }

            // Title & Speaker
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            viewModel.voiceTitle.value = it
                        },
                        label = { Text("Document Title") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = speaker,
                        onValueChange = {
                            speaker = it
                            viewModel.voiceSpeaker.value = it
                        },
                        label = { Text("Speaker") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                }
            }

            // Transcript Box
            item {
                OutlinedTextField(
                    value = transcript,
                    onValueChange = {
                        transcript = it
                        viewModel.voiceTranscript.value = it
                    },
                    label = { Text("Spoken Transcript / Voice Notes") },
                    placeholder = { Text("Live microphone speech or paste audio notes here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .testTag("transcript_input"),
                    shape = RoundedCornerShape(14.dp)
                )
            }

            // AI Polish Action
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.voiceTitle.value = title
                            viewModel.voiceSpeaker.value = speaker
                            viewModel.voiceTranscript.value = transcript
                            viewModel.polishVoiceTranscript(context)
                        },
                        enabled = transcript.isNotBlank() && !isVoicePolishing,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isVoicePolishing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AI Structure", fontSize = 13.sp)
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.voiceTitle.value = title
                            viewModel.voiceSpeaker.value = speaker
                            viewModel.voiceTranscript.value = transcript
                            viewModel.generateVoicePdf(context)
                        },
                        enabled = transcript.isNotBlank(),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("create_voice_pdf_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A237E))
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create Voice PDF", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Polished Preview Card
            item {
                voiceResult?.let { res ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CrimsonPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Structured Preview", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Executive Summary:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text(res.executiveSummary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Key Highlights:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            res.keyPoints.forEach { pt ->
                                Text("• $pt", fontSize = 12.sp, modifier = Modifier.padding(vertical = 1.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 3. Invoice & Receipt Generator Screen
// -------------------------------------------------------------
@Composable
fun InvoiceMakerScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var invNum by remember { mutableStateOf(viewModel.invoiceNumber.value) }
    var senderName by remember { mutableStateOf(viewModel.invoiceSender.value) }
    var senderDetails by remember { mutableStateOf(viewModel.invoiceSenderDetails.value) }
    var clientName by remember { mutableStateOf(viewModel.invoiceClient.value) }
    var clientDetails by remember { mutableStateOf(viewModel.invoiceClientDetails.value) }
    var dateStr by remember { mutableStateOf(viewModel.invoiceDate.value) }
    var dueDateStr by remember { mutableStateOf(viewModel.invoiceDueDate.value) }
    var taxPercent by remember { mutableStateOf(viewModel.invoiceTaxPercent.value.toString()) }
    var notes by remember { mutableStateOf(viewModel.invoiceNotes.value) }

    val items by viewModel.invoiceItems.collectAsState()

    var newItemDesc by remember { mutableStateOf("") }
    var newItemQty by remember { mutableStateOf("1") }
    var newItemRate by remember { mutableStateOf("100.00") }
    var showAddItemDialog by remember { mutableStateOf(false) }

    val subtotal = items.sumOf { it.qty * it.unitPrice }
    val taxRateFloat = taxPercent.toFloatOrNull() ?: 0f
    val taxAmount = subtotal * (taxRateFloat / 100.0)
    val grandTotal = subtotal + taxAmount

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("invoice_maker_screen")
    ) {
        PdfTopBar(
            title = "Invoice & Receipt Maker",
            onBack = { viewModel.navigateTo(AppScreen.Home) }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // General Info Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Invoice Details", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            OutlinedTextField(
                                value = invNum,
                                onValueChange = {
                                    invNum = it
                                    viewModel.invoiceNumber.value = it
                                },
                                label = { Text("Invoice #") },
                                singleLine = true,
                                modifier = Modifier.width(150.dp),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = dateStr,
                                onValueChange = {
                                    dateStr = it
                                    viewModel.invoiceDate.value = it
                                },
                                label = { Text("Date") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = dueDateStr,
                                onValueChange = {
                                    dueDateStr = it
                                    viewModel.invoiceDueDate.value = it
                                },
                                label = { Text("Due Date") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            // Sender & Client
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("From (Your Business)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = senderName,
                            onValueChange = {
                                senderName = it
                                viewModel.invoiceSender.value = it
                            },
                            label = { Text("Business Name") },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = senderDetails,
                            onValueChange = {
                                senderDetails = it
                                viewModel.invoiceSenderDetails.value = it
                            },
                            label = { Text("Address / Contact") },
                            shape = RoundedCornerShape(10.dp),
                            maxLines = 3
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bill To (Client)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = clientName,
                            onValueChange = {
                                clientName = it
                                viewModel.invoiceClient.value = it
                            },
                            label = { Text("Client Name") },
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = clientDetails,
                            onValueChange = {
                                clientDetails = it
                                viewModel.invoiceClientDetails.value = it
                            },
                            label = { Text("Address / Contact") },
                            shape = RoundedCornerShape(10.dp),
                            maxLines = 3
                        )
                    }
                }
            }

            // Line Items Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Line Items (${items.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Button(
                        onClick = { showAddItemDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Item", fontSize = 12.sp)
                    }
                }
            }

            // Add Item Form
            if (showAddItemDialog) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("New Item", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = newItemDesc,
                                onValueChange = { newItemDesc = it },
                                label = { Text("Description") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = newItemQty,
                                    onValueChange = { newItemQty = it },
                                    label = { Text("Qty") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                OutlinedTextField(
                                    value = newItemRate,
                                    onValueChange = { newItemRate = it },
                                    label = { Text("Rate ($)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1.5f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                OutlinedButton(onClick = { showAddItemDialog = false }) {
                                    Text("Cancel")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (newItemDesc.isNotBlank()) {
                                            val q = newItemQty.toIntOrNull() ?: 1
                                            val r = newItemRate.toDoubleOrNull() ?: 0.0
                                            viewModel.addInvoiceItem(InvoiceLineItem(newItemDesc, q, r))
                                            newItemDesc = ""
                                            showAddItemDialog = false
                                        }
                                    }
                                ) {
                                    Text("Add to List")
                                }
                            }
                        }
                    }
                }
            }

            // Line Items List
            itemsIndexed(items) { index, item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.description, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                "${item.qty} × $${String.format(Locale.US, "%.2f", item.unitPrice)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "$${String.format(Locale.US, "%.2f", item.qty * item.unitPrice)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        IconButton(onClick = { viewModel.removeInvoiceItem(index) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Item", tint = Color.Red, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Calculation Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Subtotal:", fontSize = 13.sp)
                            Text(String.format(Locale.US, "$%.2f", subtotal), fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Tax %: ", fontSize = 13.sp)
                                OutlinedTextField(
                                    value = taxPercent,
                                    onValueChange = {
                                        taxPercent = it
                                        viewModel.invoiceTaxPercent.value = it.toFloatOrNull() ?: 0f
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.width(70.dp).height(46.dp),
                                    shape = RoundedCornerShape(6.dp)
                                )
                            }
                            Text(String.format(Locale.US, "$%.2f", taxAmount), fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Grand Total Due:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                String.format(Locale.US, "$%.2f", grandTotal),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = CrimsonPrimary
                            )
                        }
                    }
                }
            }

            // Notes and Generate Button
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = {
                        notes = it
                        viewModel.invoiceNotes.value = it
                    },
                    label = { Text("Payment Instructions & Terms") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 3
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        viewModel.invoiceNumber.value = invNum
                        viewModel.invoiceSender.value = senderName
                        viewModel.invoiceSenderDetails.value = senderDetails
                        viewModel.invoiceClient.value = clientName
                        viewModel.invoiceClientDetails.value = clientDetails
                        viewModel.invoiceDate.value = dateStr
                        viewModel.invoiceDueDate.value = dueDateStr
                        viewModel.invoiceTaxPercent.value = taxRateFloat
                        viewModel.invoiceNotes.value = notes
                        viewModel.generateInvoicePdf(context)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("generate_invoice_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate & Download Invoice PDF", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 4. AI Document Summarizer Screen
// -------------------------------------------------------------
@Composable
fun AiSummarizerScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val summarizerSource by viewModel.summarizerSource.collectAsState()
    val isSummarizing by viewModel.isSummarizing.collectAsState()
    val summaryResult by viewModel.summarizerResult.collectAsState()

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = "Document_${System.currentTimeMillis()}.pdf"
            val pageCount = PdfEngine.getPdfPageCount(context, uri)
            viewModel.setSummarizerSource(SelectedPdfInfo(uri, name, pageCount))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("ai_summarizer_screen")
    ) {
        PdfTopBar(
            title = "AI PDF Summarizer",
            onBack = { viewModel.navigateTo(AppScreen.Home) }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Source Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Select Document to Summarize", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (summarizerSource != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = CrimsonPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(summarizerSource!!.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("${summarizerSource!!.pageCount} pages", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        } else {
                            Text("Pick any PDF from your device or app library to analyze.", fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { pdfPicker.launch(arrayOf("application/pdf")) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (summarizerSource != null) "Change PDF" else "Pick PDF File")
                        }
                    }
                }
            }

            // Summarize Action
            item {
                Button(
                    onClick = { viewModel.summarizeSelectedPdf(context) },
                    enabled = summarizerSource != null && !isSummarizing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("run_summarizer_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    if (isSummarizing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Synthesizing Executive Summary...")
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Summarize with Gemini AI", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Results Card
            item {
                summaryResult?.let { res ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(res.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Executive Overview:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text(res.summary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)

                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Key Insights:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            res.keyInsights.forEach { ins ->
                                Text("• $ins", fontSize = 12.sp, modifier = Modifier.padding(vertical = 1.dp))
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Action Items:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            res.actionItems.forEach { act ->
                                Text("✓ $act", fontSize = 12.sp, color = AccentBlue, modifier = Modifier.padding(vertical = 1.dp))
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.generateSummaryPdf(context) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("export_summary_pdf_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export Executive Brief as PDF", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 5. PDF Metadata & Title Editor Screen
// -------------------------------------------------------------
@Composable
fun MetadataEditorScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val metadataSource by viewModel.metadataSource.collectAsState()
    var title by remember { mutableStateOf(viewModel.metadataTitle.value) }
    var author by remember { mutableStateOf(viewModel.metadataAuthor.value) }
    var subject by remember { mutableStateOf(viewModel.metadataSubject.value) }
    var keywords by remember { mutableStateOf(viewModel.metadataKeywords.value) }

    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = "Document_${System.currentTimeMillis()}.pdf"
            val count = PdfEngine.getPdfPageCount(context, uri)
            viewModel.setMetadataSource(SelectedPdfInfo(uri, name, count))
            title = name.removeSuffix(".pdf")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("metadata_editor_screen")
    ) {
        PdfTopBar(
            title = "PDF Metadata & Catalog Editor",
            onBack = { viewModel.navigateTo(AppScreen.Home) }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Select Document", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (metadataSource != null) {
                            Text("Current: ${metadataSource!!.name} (${metadataSource!!.pageCount} pages)", fontSize = 13.sp)
                        } else {
                            Text("Pick a PDF to inspect and update its catalog metadata tags.", fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (metadataSource != null) "Change PDF" else "Pick PDF")
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        viewModel.metadataTitle.value = it
                    },
                    label = { Text("Document Title") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = author,
                    onValueChange = {
                        author = it
                        viewModel.metadataAuthor.value = it
                    },
                    label = { Text("Author / Creator") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = subject,
                    onValueChange = {
                        subject = it
                        viewModel.metadataSubject.value = it
                    },
                    label = { Text("Subject / Department") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = keywords,
                    onValueChange = {
                        keywords = it
                        viewModel.metadataKeywords.value = it
                    },
                    label = { Text("Keywords (comma separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            item {
                Button(
                    onClick = {
                        viewModel.metadataTitle.value = title
                        viewModel.metadataAuthor.value = author
                        viewModel.metadataSubject.value = subject
                        viewModel.metadataKeywords.value = keywords
                        viewModel.generateMetadataPdf(context)
                    },
                    enabled = metadataSource != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_metadata_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Document with Updated Metadata", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
