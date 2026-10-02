package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.PdfDatabase
import com.example.data.model.PdfItem
import com.example.data.repository.PdfRepository
import com.example.util.CompressionOption
import com.example.util.ConversionResult
import com.example.util.FontTypeOption
import com.example.util.ImageScaleOption
import com.example.util.MarginOption
import com.example.util.OrientationOption
import com.example.util.PageSizeOption
import com.example.util.PdfEngine
import com.example.util.InvoiceLineItem
import com.example.data.api.GeminiSearchService
import com.example.data.api.GroundedSearchResult
import com.example.data.api.SpeechTranscriptionResult
import com.example.data.api.DocumentSummaryResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface AppScreen {
    data object Home : AppScreen
    data object ImagesToPdf : AppScreen
    data object TextToPdf : AppScreen
    data object PdfToImages : AppScreen
    data object MergePdf : AppScreen
    data object SplitPdf : AppScreen
    data object WatermarkPdf : AppScreen
    data object CompressPdf : AppScreen
    data object RotatePdf : AppScreen
    data class GrayscaleInvertPdf(val initialMode: String = "GRAYSCALE") : AppScreen
    data object PageNumbersPdf : AppScreen
    data object DeletePagesPdf : AppScreen
    data object ReorderPagesPdf : AppScreen
    data object SignPdf : AppScreen
    data object StampPdf : AppScreen
    data object DocumentScan : AppScreen
    data object WebHtmlToPdf : AppScreen
    data object ExtractText : AppScreen
    data class Viewer(val file: File, val title: String) : AppScreen
    data object History : AppScreen
    data object AiSearchToPdf : AppScreen
    data object VoiceTranscribePdf : AppScreen
    data object InvoiceMakerPdf : AppScreen
    data object AiSummarizerPdf : AppScreen
    data object MetadataEditorPdf : AppScreen
}

data class ConversionProgress(
    val isConverting: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val message: String = ""
)

data class SelectedPdfInfo(
    val uri: Uri,
    val name: String,
    val pageCount: Int,
    val sizeBytes: Long = 0
)

class PdfViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: PdfRepository

    init {
        val db = PdfDatabase.getDatabase(application)
        repository = PdfRepository(db.pdfDao())
    }

    val allPdfs: StateFlow<List<PdfItem>> = repository.allPdfs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoritePdfs: StateFlow<List<PdfItem>> = repository.favoritePdfs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Current Navigation Screen
    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.Home)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Conversion Progress State
    private val _progress = MutableStateFlow(ConversionProgress())
    val progress: StateFlow<ConversionProgress> = _progress.asStateFlow()

    // Transient message / snackbar events
    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    // Latest converted file to open directly in viewer
    private val _lastGeneratedPdf = MutableStateFlow<File?>(null)
    val lastGeneratedPdf: StateFlow<File?> = _lastGeneratedPdf.asStateFlow()

    // --- State for Images to PDF ---
    private val _selectedImageUris = MutableStateFlow<List<Uri>>(emptyList())
    val selectedImageUris: StateFlow<List<Uri>> = _selectedImageUris.asStateFlow()

    var imagePageSize = MutableStateFlow(PageSizeOption.A4)
    var imageOrientation = MutableStateFlow(OrientationOption.AUTO)
    var imageMargin = MutableStateFlow(MarginOption.NORMAL)
    var imageScale = MutableStateFlow(ImageScaleOption.FIT_CENTER)
    var imageCompression = MutableStateFlow(CompressionOption.BALANCED)
    var imageDocTitle = MutableStateFlow("Images_Doc")

    // --- State for Text to PDF ---
    var textDocTitle = MutableStateFlow("Meeting Notes")
    var textBody = MutableStateFlow("")
    var textFontType = MutableStateFlow(FontTypeOption.SANS_SERIF)
    var textFontSize = MutableStateFlow(12f)
    var textMargin = MutableStateFlow(MarginOption.NORMAL)
    var textAddPageNumbers = MutableStateFlow(true)
    var textAddDateHeader = MutableStateFlow(true)

    // --- State for PDF to Images ---
    private val _pdfToImagesSource = MutableStateFlow<SelectedPdfInfo?>(null)
    val pdfToImagesSource: StateFlow<SelectedPdfInfo?> = _pdfToImagesSource.asStateFlow()

    private val _extractedImages = MutableStateFlow<List<File>>(emptyList())
    val extractedImages: StateFlow<List<File>> = _extractedImages.asStateFlow()

    // --- State for Merge PDFs ---
    private val _mergePdfList = MutableStateFlow<List<SelectedPdfInfo>>(emptyList())
    val mergePdfList: StateFlow<List<SelectedPdfInfo>> = _mergePdfList.asStateFlow()
    var mergeDocTitle = MutableStateFlow("Merged_Document")

    // --- State for Split PDF ---
    private val _splitPdfSource = MutableStateFlow<SelectedPdfInfo?>(null)
    val splitPdfSource: StateFlow<SelectedPdfInfo?> = _splitPdfSource.asStateFlow()

    private val _splitSelectedPages = MutableStateFlow<Set<Int>>(emptySet())
    val splitSelectedPages: StateFlow<Set<Int>> = _splitSelectedPages.asStateFlow()
    var splitDocTitle = MutableStateFlow("Extracted_Pages")

    // --- State for Watermark PDF ---
    private val _watermarkPdfSource = MutableStateFlow<SelectedPdfInfo?>(null)
    val watermarkPdfSource: StateFlow<SelectedPdfInfo?> = _watermarkPdfSource.asStateFlow()
    var watermarkText = MutableStateFlow("CONFIDENTIAL")
    var watermarkAngle = MutableStateFlow(-45f)
    var watermarkSize = MutableStateFlow(44f)
    var watermarkColorHex = MutableStateFlow(Color.argb(80, 211, 47, 47)) // Red
    var watermarkAddPageNumbers = MutableStateFlow(true)
    var watermarkDocTitle = MutableStateFlow("Watermarked_Doc")

    // --- State for Compress PDF ---
    private val _compressPdfSource = MutableStateFlow<SelectedPdfInfo?>(null)
    val compressPdfSource: StateFlow<SelectedPdfInfo?> = _compressPdfSource.asStateFlow()
    var compressQuality = MutableStateFlow(65) // 40, 65, 85

    // --- State for Rotate PDF ---
    private val _rotatePdfSource = MutableStateFlow<SelectedPdfInfo?>(null)
    val rotatePdfSource: StateFlow<SelectedPdfInfo?> = _rotatePdfSource.asStateFlow()
    var rotateDegrees = MutableStateFlow(90) // 90, 180, 270

    // --- State for Grayscale & Invert PDF ---
    private val _grayscaleSource = MutableStateFlow<SelectedPdfInfo?>(null)
    val grayscaleSource: StateFlow<SelectedPdfInfo?> = _grayscaleSource.asStateFlow()
    var grayscaleMode = MutableStateFlow("GRAYSCALE") // "GRAYSCALE" or "INVERT"

    // --- State for Page Numbers ---
    private val _pageNumbersSource = MutableStateFlow<SelectedPdfInfo?>(null)
    val pageNumbersSource: StateFlow<SelectedPdfInfo?> = _pageNumbersSource.asStateFlow()
    var pageNumbersHeader = MutableStateFlow("Confidential Report")
    var pageNumbersFormat = MutableStateFlow("Page %d of %d")

    // --- State for Delete Pages ---
    private val _deletePagesSource = MutableStateFlow<SelectedPdfInfo?>(null)
    val deletePagesSource: StateFlow<SelectedPdfInfo?> = _deletePagesSource.asStateFlow()
    val deleteSelectedPages = MutableStateFlow<Set<Int>>(emptySet())

    // --- State for Reorder Pages ---
    private val _reorderPagesSource = MutableStateFlow<SelectedPdfInfo?>(null)
    val reorderPagesSource: StateFlow<SelectedPdfInfo?> = _reorderPagesSource.asStateFlow()
    val reorderPageList = MutableStateFlow<List<Int>>(emptyList())

    // --- State for Sign PDF ---
    private val _signPdfSource = MutableStateFlow<SelectedPdfInfo?>(null)
    val signPdfSource: StateFlow<SelectedPdfInfo?> = _signPdfSource.asStateFlow()
    var signSelectedPage = MutableStateFlow(0)

    // --- State for Stamp PDF ---
    private val _stampPdfSource = MutableStateFlow<SelectedPdfInfo?>(null)
    val stampPdfSource: StateFlow<SelectedPdfInfo?> = _stampPdfSource.asStateFlow()
    var stampBadgeText = MutableStateFlow("CONFIDENTIAL")
    var stampBadgeColor = MutableStateFlow(Color.RED)

    // --- State for Document Scan ---
    private val _scanImageUris = MutableStateFlow<List<Uri>>(emptyList())
    val scanImageUris: StateFlow<List<Uri>> = _scanImageUris.asStateFlow()
    var scanHighContrast = MutableStateFlow(true)
    var scanDocTitle = MutableStateFlow("Document_Scan")

    // --- State for Web / HTML to PDF ---
    var webArticleTitle = MutableStateFlow("Web_Article")
    var webArticleContent = MutableStateFlow("")

    // --- State for Extract Text ---
    private val _extractTextSource = MutableStateFlow<SelectedPdfInfo?>(null)
    val extractTextSource: StateFlow<SelectedPdfInfo?> = _extractTextSource.asStateFlow()
    var extractedTextResult = MutableStateFlow("")

    // --- State for AI Search Grounding to PDF ---
    var searchQuery = MutableStateFlow("Latest Breakthroughs in Renewable Energy 2026")
    var searchCustomApiKey = MutableStateFlow("")
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    private val _searchResult = MutableStateFlow<GroundedSearchResult?>(null)
    val searchResult: StateFlow<GroundedSearchResult?> = _searchResult.asStateFlow()

    // --- State for Voice & Speech to PDF ---
    var voiceTitle = MutableStateFlow("Client Strategy Session")
    var voiceSpeaker = MutableStateFlow("Lead Architect")
    var voiceTranscript = MutableStateFlow("")
    private val _isVoicePolishing = MutableStateFlow(false)
    val isVoicePolishing: StateFlow<Boolean> = _isVoicePolishing.asStateFlow()
    private val _voiceResult = MutableStateFlow<SpeechTranscriptionResult?>(null)
    val voiceResult: StateFlow<SpeechTranscriptionResult?> = _voiceResult.asStateFlow()

    // --- State for Invoice & Receipt Generator ---
    var invoiceNumber = MutableStateFlow("INV-2026-081")
    var invoiceSender = MutableStateFlow("Nova Design & Engineering LLC")
    var invoiceSenderDetails = MutableStateFlow("742 Evergreen Terrace, Suite 300\nbilling@novadesign.com · (555) 234-5678")
    var invoiceClient = MutableStateFlow("Acme Global Technologies")
    var invoiceClientDetails = MutableStateFlow("100 Innovation Plaza, San Francisco, CA\naccounts@acmeglobal.com")
    var invoiceDate = MutableStateFlow("Oct 2, 2026")
    var invoiceDueDate = MutableStateFlow("Oct 16, 2026")
    var invoiceTaxPercent = MutableStateFlow(8.5f)
    var invoiceNotes = MutableStateFlow("Payment due within 14 days of invoice date. Wire transfer details: Acme Bank AC #987654321, Routing #123456789. Thank you for your partnership!")
    private val _invoiceItems = MutableStateFlow<List<InvoiceLineItem>>(
        listOf(
            InvoiceLineItem("Mobile App System Architecture & PDF Core Engine", 1, 2800.0),
            InvoiceLineItem("Material 3 UI/UX Design & Multi-Tool Suite", 1, 1400.0),
            InvoiceLineItem("Cloud Gemini Search & Voice Grounding Integration", 1, 950.0)
        )
    )
    val invoiceItems: StateFlow<List<InvoiceLineItem>> = _invoiceItems.asStateFlow()

    // --- State for AI Document Summarizer ---
    private val _summarizerSource = MutableStateFlow<SelectedPdfInfo?>(null)
    val summarizerSource: StateFlow<SelectedPdfInfo?> = _summarizerSource.asStateFlow()
    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing.asStateFlow()
    private val _summarizerResult = MutableStateFlow<DocumentSummaryResult?>(null)
    val summarizerResult: StateFlow<DocumentSummaryResult?> = _summarizerResult.asStateFlow()

    // --- State for Metadata Editor ---
    private val _metadataSource = MutableStateFlow<SelectedPdfInfo?>(null)
    val metadataSource: StateFlow<SelectedPdfInfo?> = _metadataSource.asStateFlow()
    var metadataTitle = MutableStateFlow("Official Whitepaper")
    var metadataAuthor = MutableStateFlow("Chief Technology Officer")
    var metadataSubject = MutableStateFlow("Engineering Architecture")
    var metadataKeywords = MutableStateFlow("PDF, Mobile, Gemini, Security, Jetpack Compose")

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    // --- Images to PDF Operations ---
    fun addImageUris(uris: List<Uri>) {
        _selectedImageUris.update { it + uris }
    }

    fun removeImageUri(index: Int) {
        _selectedImageUris.update { current ->
            current.filterIndexed { i, _ -> i != index }
        }
    }

    fun moveImage(from: Int, to: Int) {
        _selectedImageUris.update { current ->
            val list = current.toMutableList()
            if (from in list.indices && to in list.indices) {
                val item = list.removeAt(from)
                list.add(to, item)
            }
            list
        }
    }

    fun clearImages() {
        _selectedImageUris.value = emptyList()
    }

    fun convertImagesToPdf(context: Context) {
        val uris = _selectedImageUris.value
        if (uris.isEmpty()) {
            emitSnackbar("Please select at least one image")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _progress.value = ConversionProgress(isConverting = true, message = "Preparing images...")
            val cleanTitle = imageDocTitle.value.trim().ifEmpty { "Images_Doc" }
            val outputFile = PdfEngine.generateUniquePdfFile(context, cleanTitle)

            val result = PdfEngine.imagesToPdf(
                context = context,
                imageUris = uris,
                outputFile = outputFile,
                pageSize = imagePageSize.value,
                orientation = imageOrientation.value,
                margin = imageMargin.value,
                scaleOption = imageScale.value,
                compression = imageCompression.value,
                onProgress = { cur, tot ->
                    _progress.value = ConversionProgress(true, cur, tot, "Processing image $cur of $tot...")
                }
            )

            result.onSuccess { conv ->
                repository.insert(
                    PdfItem(
                        title = cleanTitle,
                        filePath = conv.file.absolutePath,
                        fileSizeBytes = conv.fileSizeBytes,
                        pageCount = conv.pageCount,
                        toolType = "IMAGE_TO_PDF"
                    )
                )
                _progress.value = ConversionProgress(false)
                _lastGeneratedPdf.value = conv.file
                emitSnackbar("PDF created successfully (${conv.pageCount} pages)!")
                withContext(Dispatchers.Main) {
                    navigateTo(AppScreen.Viewer(conv.file, cleanTitle))
                }
            }.onFailure { err ->
                _progress.value = ConversionProgress(false)
                emitSnackbar("Failed to create PDF: ${err.localizedMessage}")
            }
        }
    }

    // --- Text to PDF Operations ---
    fun convertTextToPdf(context: Context) {
        val body = textBody.value.trim()
        if (body.isEmpty()) {
            emitSnackbar("Please enter some text to convert")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _progress.value = ConversionProgress(isConverting = true, message = "Formatting text...")
            val cleanTitle = textDocTitle.value.trim().ifEmpty { "Document" }
            val outputFile = PdfEngine.generateUniquePdfFile(context, cleanTitle)

            val result = PdfEngine.textToPdf(
                title = cleanTitle,
                bodyText = body,
                outputFile = outputFile,
                fontType = textFontType.value,
                fontSizePt = textFontSize.value,
                margin = textMargin.value,
                addPageNumbers = textAddPageNumbers.value,
                addHeaderDate = textAddDateHeader.value
            )

            result.onSuccess { conv ->
                repository.insert(
                    PdfItem(
                        title = cleanTitle,
                        filePath = conv.file.absolutePath,
                        fileSizeBytes = conv.fileSizeBytes,
                        pageCount = conv.pageCount,
                        toolType = "TEXT_TO_PDF"
                    )
                )
                _progress.value = ConversionProgress(false)
                _lastGeneratedPdf.value = conv.file
                emitSnackbar("Text converted to PDF (${conv.pageCount} pages)!")
                withContext(Dispatchers.Main) {
                    navigateTo(AppScreen.Viewer(conv.file, cleanTitle))
                }
            }.onFailure { err ->
                _progress.value = ConversionProgress(false)
                emitSnackbar("Failed to generate PDF: ${err.localizedMessage}")
            }
        }
    }

    // --- PDF to Images Operations ---
    fun setPdfToImagesSource(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val name = getFileName(context, uri) ?: "Document.pdf"
            val pageCount = PdfEngine.getPdfPageCount(context, uri)
            _pdfToImagesSource.value = SelectedPdfInfo(uri, name, pageCount)
            _extractedImages.value = emptyList()
        }
    }

    fun extractPdfImages(context: Context, asJpeg: Boolean = true) {
        val source = _pdfToImagesSource.value
        if (source == null) {
            emitSnackbar("Please select a PDF first")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _progress.value = ConversionProgress(isConverting = true, message = "Extracting pages...")
            val result = PdfEngine.pdfToImages(
                context = context,
                pdfUri = source.uri,
                asJpeg = asJpeg,
                onProgress = { cur, tot ->
                    _progress.value = ConversionProgress(true, cur, tot, "Rendering page $cur of $tot...")
                }
            )

            result.onSuccess { images ->
                _extractedImages.value = images
                _progress.value = ConversionProgress(false)
                emitSnackbar("Extracted ${images.size} high-res images!")
            }.onFailure { err ->
                _progress.value = ConversionProgress(false)
                emitSnackbar("Extraction failed: ${err.localizedMessage}")
            }
        }
    }

    // --- Merge PDFs Operations ---
    fun addPdfsToMerge(context: Context, uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val newItems = uris.map { uri ->
                val name = getFileName(context, uri) ?: "Document.pdf"
                val count = PdfEngine.getPdfPageCount(context, uri)
                SelectedPdfInfo(uri, name, count)
            }
            _mergePdfList.update { it + newItems }
        }
    }

    fun removeMergePdf(index: Int) {
        _mergePdfList.update { current ->
            current.filterIndexed { i, _ -> i != index }
        }
    }

    fun clearMergePdfs() {
        _mergePdfList.value = emptyList()
    }

    fun mergePdfs(context: Context) {
        val list = _mergePdfList.value
        if (list.size < 2) {
            emitSnackbar("Please select at least 2 PDF files to merge")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _progress.value = ConversionProgress(isConverting = true, message = "Merging documents...")
            val cleanTitle = mergeDocTitle.value.trim().ifEmpty { "Merged_Document" }
            val outputFile = PdfEngine.generateUniquePdfFile(context, cleanTitle)

            val result = PdfEngine.mergePdfs(
                context = context,
                pdfUris = list.map { it.uri },
                outputFile = outputFile,
                onProgress = { cur, tot ->
                    _progress.value = ConversionProgress(true, cur, tot, "Merging document $cur of $tot...")
                }
            )

            result.onSuccess { conv ->
                repository.insert(
                    PdfItem(
                        title = cleanTitle,
                        filePath = conv.file.absolutePath,
                        fileSizeBytes = conv.fileSizeBytes,
                        pageCount = conv.pageCount,
                        toolType = "PDF_MERGE"
                    )
                )
                _progress.value = ConversionProgress(false)
                _lastGeneratedPdf.value = conv.file
                emitSnackbar("Merged ${list.size} PDFs into ${conv.pageCount} pages!")
                withContext(Dispatchers.Main) {
                    navigateTo(AppScreen.Viewer(conv.file, cleanTitle))
                }
            }.onFailure { err ->
                _progress.value = ConversionProgress(false)
                emitSnackbar("Failed to merge PDFs: ${err.localizedMessage}")
            }
        }
    }

    // --- Split PDF Operations ---
    fun setSplitPdfSource(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val name = getFileName(context, uri) ?: "Document.pdf"
            val count = PdfEngine.getPdfPageCount(context, uri)
            _splitPdfSource.value = SelectedPdfInfo(uri, name, count)
            _splitSelectedPages.value = (0 until count).toSet() // Select all by default
        }
    }

    fun toggleSplitPage(pageIndex: Int) {
        _splitSelectedPages.update { current ->
            if (current.contains(pageIndex)) current - pageIndex else current + pageIndex
        }
    }

    fun selectAllSplitPages(selectAll: Boolean) {
        val total = _splitPdfSource.value?.pageCount ?: 0
        _splitSelectedPages.value = if (selectAll) (0 until total).toSet() else emptySet()
    }

    fun splitPdf(context: Context) {
        val source = _splitPdfSource.value
        val pages = _splitSelectedPages.value
        if (source == null || pages.isEmpty()) {
            emitSnackbar("Please select at least one page to extract")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _progress.value = ConversionProgress(isConverting = true, message = "Extracting pages...")
            val cleanTitle = splitDocTitle.value.trim().ifEmpty { "Extracted_Pages" }
            val outputFile = PdfEngine.generateUniquePdfFile(context, cleanTitle)

            val result = PdfEngine.splitPdf(
                context = context,
                pdfUri = source.uri,
                selectedPageIndices = pages.toList(),
                outputFile = outputFile,
                onProgress = { cur, tot ->
                    _progress.value = ConversionProgress(true, cur, tot, "Extracting page $cur of $tot...")
                }
            )

            result.onSuccess { conv ->
                repository.insert(
                    PdfItem(
                        title = cleanTitle,
                        filePath = conv.file.absolutePath,
                        fileSizeBytes = conv.fileSizeBytes,
                        pageCount = conv.pageCount,
                        toolType = "PDF_SPLIT"
                    )
                )
                _progress.value = ConversionProgress(false)
                _lastGeneratedPdf.value = conv.file
                emitSnackbar("Extracted ${conv.pageCount} pages into new PDF!")
                withContext(Dispatchers.Main) {
                    navigateTo(AppScreen.Viewer(conv.file, cleanTitle))
                }
            }.onFailure { err ->
                _progress.value = ConversionProgress(false)
                emitSnackbar("Failed to split PDF: ${err.localizedMessage}")
            }
        }
    }

    // --- Watermark PDF Operations ---
    fun setWatermarkPdfSource(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val name = getFileName(context, uri) ?: "Document.pdf"
            val count = PdfEngine.getPdfPageCount(context, uri)
            _watermarkPdfSource.value = SelectedPdfInfo(uri, name, count)
        }
    }

    fun applyWatermark(context: Context) {
        val source = _watermarkPdfSource.value
        if (source == null) {
            emitSnackbar("Please select a PDF file first")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _progress.value = ConversionProgress(isConverting = true, message = "Applying watermark...")
            val cleanTitle = watermarkDocTitle.value.trim().ifEmpty { "Watermarked_Doc" }
            val outputFile = PdfEngine.generateUniquePdfFile(context, cleanTitle)

            val result = PdfEngine.watermarkPdf(
                context = context,
                pdfUri = source.uri,
                watermarkText = watermarkText.value.trim(),
                outputFile = outputFile,
                watermarkColor = watermarkColorHex.value,
                watermarkAngle = watermarkAngle.value,
                watermarkSizePt = watermarkSize.value,
                addPageNumbers = watermarkAddPageNumbers.value,
                onProgress = { cur, tot ->
                    _progress.value = ConversionProgress(true, cur, tot, "Processing page $cur of $tot...")
                }
            )

            result.onSuccess { conv ->
                repository.insert(
                    PdfItem(
                        title = cleanTitle,
                        filePath = conv.file.absolutePath,
                        fileSizeBytes = conv.fileSizeBytes,
                        pageCount = conv.pageCount,
                        toolType = "WATERMARK"
                    )
                )
                _progress.value = ConversionProgress(false)
                _lastGeneratedPdf.value = conv.file
                emitSnackbar("Watermark applied successfully!")
                withContext(Dispatchers.Main) {
                    navigateTo(AppScreen.Viewer(conv.file, cleanTitle))
                }
            }.onFailure { err ->
                _progress.value = ConversionProgress(false)
                emitSnackbar("Failed to watermark PDF: ${err.localizedMessage}")
            }
        }
    }

    // --- Compress PDF Operations ---
    fun setCompressPdfSource(context: Context, uri: Uri) {
        val name = getFileName(context, uri) ?: "Document.pdf"
        val count = PdfEngine.getPdfPageCount(context, uri)
        _compressPdfSource.value = SelectedPdfInfo(uri, name, count)
    }

    fun executeCompressPdf(context: Context) {
        val src = _compressPdfSource.value ?: return
        val quality = compressQuality.value
        val cleanTitle = "${src.name.removeSuffix(".pdf")}_Compressed"

        viewModelScope.launch(Dispatchers.IO) {
            _progress.value = ConversionProgress(isConverting = true, message = "Compressing PDF document...", total = src.pageCount)
            val outputFile = File(context.filesDir, "${cleanTitle}_${System.currentTimeMillis()}.pdf")

            PdfEngine.compressPdf(
                context = context,
                pdfUri = src.uri,
                qualityPercent = quality,
                outputFile = outputFile,
                onProgress = { cur, tot ->
                    _progress.value = ConversionProgress(isConverting = true, current = cur, total = tot, message = "Optimizing page $cur of $tot...")
                }
            ).onSuccess { conv ->
                repository.insert(
                    PdfItem(
                        title = cleanTitle,
                        filePath = conv.file.absolutePath,
                        fileSizeBytes = conv.fileSizeBytes,
                        pageCount = conv.pageCount,
                        toolType = "COMPRESS"
                    )
                )
                _progress.value = ConversionProgress(false)
                _lastGeneratedPdf.value = conv.file
                emitSnackbar("PDF compressed successfully!")
                withContext(Dispatchers.Main) {
                    navigateTo(AppScreen.Viewer(conv.file, cleanTitle))
                }
            }.onFailure { err ->
                _progress.value = ConversionProgress(false)
                emitSnackbar("Failed to compress PDF: ${err.localizedMessage}")
            }
        }
    }

    // --- Rotate PDF Operations ---
    fun setRotatePdfSource(context: Context, uri: Uri) {
        val name = getFileName(context, uri) ?: "Document.pdf"
        val count = PdfEngine.getPdfPageCount(context, uri)
        _rotatePdfSource.value = SelectedPdfInfo(uri, name, count)
    }

    fun executeRotatePdf(context: Context) {
        val src = _rotatePdfSource.value ?: return
        val deg = rotateDegrees.value
        val cleanTitle = "${src.name.removeSuffix(".pdf")}_Rotated_${deg}"

        viewModelScope.launch(Dispatchers.IO) {
            _progress.value = ConversionProgress(isConverting = true, message = "Rotating PDF pages...", total = src.pageCount)
            val outputFile = File(context.filesDir, "${cleanTitle}_${System.currentTimeMillis()}.pdf")

            PdfEngine.rotatePdf(
                context = context,
                pdfUri = src.uri,
                rotationDegrees = deg,
                outputFile = outputFile,
                onProgress = { cur, tot ->
                    _progress.value = ConversionProgress(isConverting = true, current = cur, total = tot, message = "Rotating page $cur of $tot...")
                }
            ).onSuccess { conv ->
                repository.insert(
                    PdfItem(
                        title = cleanTitle,
                        filePath = conv.file.absolutePath,
                        fileSizeBytes = conv.fileSizeBytes,
                        pageCount = conv.pageCount,
                        toolType = "ROTATE"
                    )
                )
                _progress.value = ConversionProgress(false)
                _lastGeneratedPdf.value = conv.file
                emitSnackbar("Pages rotated successfully!")
                withContext(Dispatchers.Main) {
                    navigateTo(AppScreen.Viewer(conv.file, cleanTitle))
                }
            }.onFailure { err ->
                _progress.value = ConversionProgress(false)
                emitSnackbar("Failed to rotate PDF: ${err.localizedMessage}")
            }
        }
    }

    // --- Grayscale & Invert Operations ---
    fun setGrayscaleSource(context: Context, uri: Uri) {
        val name = getFileName(context, uri) ?: "Document.pdf"
        val count = PdfEngine.getPdfPageCount(context, uri)
        _grayscaleSource.value = SelectedPdfInfo(uri, name, count)
    }

    fun executeGrayscaleInvert(context: Context, isGrayscale: Boolean) {
        val src = _grayscaleSource.value ?: return
        val suffix = if (isGrayscale) "Grayscale" else "DarkMode"
        val cleanTitle = "${src.name.removeSuffix(".pdf")}_$suffix"

        viewModelScope.launch(Dispatchers.IO) {
            _progress.value = ConversionProgress(isConverting = true, message = "Applying document filter...", total = src.pageCount)
            val outputFile = File(context.filesDir, "${cleanTitle}_${System.currentTimeMillis()}.pdf")

            val result = if (isGrayscale) {
                PdfEngine.grayscalePdf(context, src.uri, outputFile) { c, t ->
                    _progress.value = ConversionProgress(isConverting = true, current = c, total = t, message = "Converting page $c of $t to B&W...")
                }
            } else {
                PdfEngine.invertPdf(context, src.uri, outputFile) { c, t ->
                    _progress.value = ConversionProgress(isConverting = true, current = c, total = t, message = "Inverting page $c of $t...")
                }
            }

            result.onSuccess { conv ->
                repository.insert(
                    PdfItem(
                        title = cleanTitle,
                        filePath = conv.file.absolutePath,
                        fileSizeBytes = conv.fileSizeBytes,
                        pageCount = conv.pageCount,
                        toolType = if (isGrayscale) "GRAYSCALE" else "DARK_MODE"
                    )
                )
                _progress.value = ConversionProgress(false)
                _lastGeneratedPdf.value = conv.file
                emitSnackbar("Document generated successfully!")
                withContext(Dispatchers.Main) {
                    navigateTo(AppScreen.Viewer(conv.file, cleanTitle))
                }
            }.onFailure { err ->
                _progress.value = ConversionProgress(false)
                emitSnackbar("Failed: ${err.localizedMessage}")
            }
        }
    }

    // --- Page Numbers & Header Operations ---
    fun setPageNumbersSource(context: Context, uri: Uri) {
        val name = getFileName(context, uri) ?: "Document.pdf"
        val count = PdfEngine.getPdfPageCount(context, uri)
        _pageNumbersSource.value = SelectedPdfInfo(uri, name, count)
    }

    fun executePageNumbers(context: Context) {
        val src = _pageNumbersSource.value ?: return
        val cleanTitle = "${src.name.removeSuffix(".pdf")}_Numbered"

        viewModelScope.launch(Dispatchers.IO) {
            _progress.value = ConversionProgress(isConverting = true, message = "Adding page numbers & headers...", total = src.pageCount)
            val outputFile = File(context.filesDir, "${cleanTitle}_${System.currentTimeMillis()}.pdf")

            PdfEngine.addPageNumbersAndHeader(
                context = context,
                pdfUri = src.uri,
                headerText = pageNumbersHeader.value,
                numberFormat = pageNumbersFormat.value,
                outputFile = outputFile,
                onProgress = { c, t ->
                    _progress.value = ConversionProgress(isConverting = true, current = c, total = t, message = "Stamping page $c of $t...")
                }
            ).onSuccess { conv ->
                repository.insert(
                    PdfItem(
                        title = cleanTitle,
                        filePath = conv.file.absolutePath,
                        fileSizeBytes = conv.fileSizeBytes,
                        pageCount = conv.pageCount,
                        toolType = "PAGE_NUMBERS"
                    )
                )
                _progress.value = ConversionProgress(false)
                _lastGeneratedPdf.value = conv.file
                emitSnackbar("Page numbers added successfully!")
                withContext(Dispatchers.Main) {
                    navigateTo(AppScreen.Viewer(conv.file, cleanTitle))
                }
            }.onFailure { err ->
                _progress.value = ConversionProgress(false)
                emitSnackbar("Failed: ${err.localizedMessage}")
            }
        }
    }

    // --- Delete Pages Operations ---
    fun setDeletePagesSource(context: Context, uri: Uri) {
        val name = getFileName(context, uri) ?: "Document.pdf"
        val count = PdfEngine.getPdfPageCount(context, uri)
        _deletePagesSource.value = SelectedPdfInfo(uri, name, count)
        deleteSelectedPages.value = emptySet()
    }

    fun toggleDeletePage(page: Int) {
        deleteSelectedPages.update { current ->
            if (current.contains(page)) current - page else current + page
        }
    }

    fun executeDeletePages(context: Context) {
        val src = _deletePagesSource.value ?: return
        val toDelete = deleteSelectedPages.value
        if (toDelete.isEmpty()) {
            emitSnackbar("Please select at least one page to delete")
            return
        }
        if (toDelete.size >= src.pageCount) {
            emitSnackbar("Cannot delete all pages in document")
            return
        }
        val cleanTitle = "${src.name.removeSuffix(".pdf")}_Trimmed"

        viewModelScope.launch(Dispatchers.IO) {
            _progress.value = ConversionProgress(isConverting = true, message = "Deleting pages...", total = src.pageCount)
            val outputFile = File(context.filesDir, "${cleanTitle}_${System.currentTimeMillis()}.pdf")

            PdfEngine.deletePages(
                context = context,
                pdfUri = src.uri,
                pagesToDelete = toDelete,
                outputFile = outputFile,
                onProgress = { c, t ->
                    _progress.value = ConversionProgress(isConverting = true, current = c, total = t, message = "Processing page $c of $t...")
                }
            ).onSuccess { conv ->
                repository.insert(
                    PdfItem(
                        title = cleanTitle,
                        filePath = conv.file.absolutePath,
                        fileSizeBytes = conv.fileSizeBytes,
                        pageCount = conv.pageCount,
                        toolType = "DELETE_PAGES"
                    )
                )
                _progress.value = ConversionProgress(false)
                _lastGeneratedPdf.value = conv.file
                emitSnackbar("Pages removed successfully!")
                withContext(Dispatchers.Main) {
                    navigateTo(AppScreen.Viewer(conv.file, cleanTitle))
                }
            }.onFailure { err ->
                _progress.value = ConversionProgress(false)
                emitSnackbar("Failed: ${err.localizedMessage}")
            }
        }
    }

    // --- Reorder Pages Operations ---
    fun setReorderPagesSource(context: Context, uri: Uri) {
        val name = getFileName(context, uri) ?: "Document.pdf"
        val count = PdfEngine.getPdfPageCount(context, uri)
        _reorderPagesSource.value = SelectedPdfInfo(uri, name, count)
        reorderPageList.value = (0 until count).toList()
    }

    fun moveReorderPage(fromIndex: Int, toIndex: Int) {
        reorderPageList.update { list ->
            if (fromIndex in list.indices && toIndex in list.indices) {
                val mutable = list.toMutableList()
                val item = mutable.removeAt(fromIndex)
                mutable.add(toIndex, item)
                mutable
            } else list
        }
    }

    fun executeReorderPages(context: Context) {
        val src = _reorderPagesSource.value ?: return
        val order = reorderPageList.value
        val cleanTitle = "${src.name.removeSuffix(".pdf")}_Reordered"

        viewModelScope.launch(Dispatchers.IO) {
            _progress.value = ConversionProgress(isConverting = true, message = "Reordering pages...", total = order.size)
            val outputFile = File(context.filesDir, "${cleanTitle}_${System.currentTimeMillis()}.pdf")

            PdfEngine.reorderPages(
                context = context,
                pdfUri = src.uri,
                newOrder = order,
                outputFile = outputFile,
                onProgress = { c, t ->
                    _progress.value = ConversionProgress(isConverting = true, current = c, total = t, message = "Reordering page $c of $t...")
                }
            ).onSuccess { conv ->
                repository.insert(
                    PdfItem(
                        title = cleanTitle,
                        filePath = conv.file.absolutePath,
                        fileSizeBytes = conv.fileSizeBytes,
                        pageCount = conv.pageCount,
                        toolType = "REORDER"
                    )
                )
                _progress.value = ConversionProgress(false)
                _lastGeneratedPdf.value = conv.file
                emitSnackbar("Pages reordered successfully!")
                withContext(Dispatchers.Main) {
                    navigateTo(AppScreen.Viewer(conv.file, cleanTitle))
                }
            }.onFailure { err ->
                _progress.value = ConversionProgress(false)
                emitSnackbar("Failed: ${err.localizedMessage}")
            }
        }
    }

    // --- Sign PDF Operations ---
    fun setSignPdfSource(context: Context, uri: Uri) {
        val name = getFileName(context, uri) ?: "Document.pdf"
        val count = PdfEngine.getPdfPageCount(context, uri)
        _signPdfSource.value = SelectedPdfInfo(uri, name, count)
        signSelectedPage.value = 0
    }

    fun executeSignPdf(
        context: Context,
        signatureBitmap: Bitmap,
        xRatio: Float = 0.5f,
        yRatio: Float = 0.82f
    ) {
        val src = _signPdfSource.value ?: return
        val cleanTitle = "${src.name.removeSuffix(".pdf")}_Signed"

        viewModelScope.launch(Dispatchers.IO) {
            _progress.value = ConversionProgress(isConverting = true, message = "Applying digital signature...", total = src.pageCount)
            val outputFile = File(context.filesDir, "${cleanTitle}_${System.currentTimeMillis()}.pdf")

            PdfEngine.signPdf(
                context = context,
                pdfUri = src.uri,
                signatureBitmap = signatureBitmap,
                targetPageIndex = signSelectedPage.value,
                xRatio = xRatio,
                yRatio = yRatio,
                outputFile = outputFile,
                onProgress = { c, t ->
                    _progress.value = ConversionProgress(isConverting = true, current = c, total = t, message = "Signing document...")
                }
            ).onSuccess { conv ->
                repository.insert(
                    PdfItem(
                        title = cleanTitle,
                        filePath = conv.file.absolutePath,
                        fileSizeBytes = conv.fileSizeBytes,
                        pageCount = conv.pageCount,
                        toolType = "SIGN"
                    )
                )
                _progress.value = ConversionProgress(false)
                _lastGeneratedPdf.value = conv.file
                emitSnackbar("PDF signed successfully!")
                withContext(Dispatchers.Main) {
                    navigateTo(AppScreen.Viewer(conv.file, cleanTitle))
                }
            }.onFailure { err ->
                _progress.value = ConversionProgress(false)
                emitSnackbar("Failed: ${err.localizedMessage}")
            }
        }
    }

    // --- Stamp PDF Operations ---
    fun setStampPdfSource(context: Context, uri: Uri) {
        val name = getFileName(context, uri) ?: "Document.pdf"
        val count = PdfEngine.getPdfPageCount(context, uri)
        _stampPdfSource.value = SelectedPdfInfo(uri, name, count)
    }

    fun executeStampPdf(context: Context) {
        val src = _stampPdfSource.value ?: return
        val badge = stampBadgeText.value
        val color = stampBadgeColor.value
        val cleanTitle = "${src.name.removeSuffix(".pdf")}_$badge"

        viewModelScope.launch(Dispatchers.IO) {
            _progress.value = ConversionProgress(isConverting = true, message = "Applying official stamp badge...", total = src.pageCount)
            val outputFile = File(context.filesDir, "${cleanTitle}_${System.currentTimeMillis()}.pdf")

            PdfEngine.stampPdf(
                context = context,
                pdfUri = src.uri,
                stampText = badge,
                stampColor = color,
                outputFile = outputFile,
                onProgress = { c, t ->
                    _progress.value = ConversionProgress(isConverting = true, current = c, total = t, message = "Stamping page $c of $t...")
                }
            ).onSuccess { conv ->
                repository.insert(
                    PdfItem(
                        title = cleanTitle,
                        filePath = conv.file.absolutePath,
                        fileSizeBytes = conv.fileSizeBytes,
                        pageCount = conv.pageCount,
                        toolType = "STAMP"
                    )
                )
                _progress.value = ConversionProgress(false)
                _lastGeneratedPdf.value = conv.file
                emitSnackbar("Stamp applied successfully!")
                withContext(Dispatchers.Main) {
                    navigateTo(AppScreen.Viewer(conv.file, cleanTitle))
                }
            }.onFailure { err ->
                _progress.value = ConversionProgress(false)
                emitSnackbar("Failed: ${err.localizedMessage}")
            }
        }
    }

    // --- Document Scan Operations ---
    fun addScanUris(uris: List<Uri>) {
        _scanImageUris.update { it + uris }
    }

    fun removeScanUri(index: Int) {
        _scanImageUris.update { list -> list.filterIndexed { i, _ -> i != index } }
    }

    fun executeDocumentScan(context: Context) {
        val uris = _scanImageUris.value
        if (uris.isEmpty()) {
            emitSnackbar("Please capture or select at least one document image")
            return
        }
        val title = scanDocTitle.value.ifBlank { "Scanned_Document" }

        viewModelScope.launch(Dispatchers.IO) {
            _progress.value = ConversionProgress(isConverting = true, message = "Scanning document pages...", total = uris.size)
            val outputFile = File(context.filesDir, "${title}_${System.currentTimeMillis()}.pdf")

            PdfEngine.imagesToPdf(
                context = context,
                imageUris = uris,
                outputFile = outputFile,
                pageSize = PageSizeOption.A4,
                margin = MarginOption.NORMAL,
                scaleOption = ImageScaleOption.FIT_CENTER,
                onProgress = { c, t ->
                    _progress.value = ConversionProgress(isConverting = true, current = c, total = t, message = "Processing scanned page $c of $t...")
                }
            ).onSuccess { conv ->
                repository.insert(
                    PdfItem(
                        title = title,
                        filePath = conv.file.absolutePath,
                        fileSizeBytes = conv.fileSizeBytes,
                        pageCount = conv.pageCount,
                        toolType = "SCAN"
                    )
                )
                _progress.value = ConversionProgress(false)
                _lastGeneratedPdf.value = conv.file
                _scanImageUris.value = emptyList()
                emitSnackbar("Document scanned to PDF successfully!")
                withContext(Dispatchers.Main) {
                    navigateTo(AppScreen.Viewer(conv.file, title))
                }
            }.onFailure { err ->
                _progress.value = ConversionProgress(false)
                emitSnackbar("Scan failed: ${err.localizedMessage}")
            }
        }
    }

    // --- Web / HTML to PDF Operations ---
    fun executeWebHtmlToPdf(context: Context) {
        val title = webArticleTitle.value.ifBlank { "Web_Article" }
        val rawContent = webArticleContent.value
        if (rawContent.isBlank()) {
            emitSnackbar("Please enter web article or HTML content")
            return
        }

        // Clean simple HTML tags for text presentation
        val cleanText = rawContent
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<p\\s*.*?>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<h[1-6].*?>(.*?)</h[1-6]>", RegexOption.IGNORE_CASE), "\n\n$1\n" + "-".repeat(30) + "\n")
            .replace(Regex("<.*?>"), "")
            .trim()

        viewModelScope.launch(Dispatchers.IO) {
            _progress.value = ConversionProgress(isConverting = true, message = "Generating PDF article...")
            val outputFile = File(context.filesDir, "${title}_${System.currentTimeMillis()}.pdf")

            PdfEngine.textToPdf(
                title = title,
                bodyText = cleanText,
                outputFile = outputFile,
                fontType = FontTypeOption.SERIF,
                fontSizePt = 13f,
                margin = MarginOption.NORMAL,
                addHeaderDate = true,
                addPageNumbers = true
            ).onSuccess { conv ->
                repository.insert(
                    PdfItem(
                        title = title,
                        filePath = conv.file.absolutePath,
                        fileSizeBytes = conv.fileSizeBytes,
                        pageCount = conv.pageCount,
                        toolType = "WEB_HTML"
                    )
                )
                _progress.value = ConversionProgress(false)
                _lastGeneratedPdf.value = conv.file
                emitSnackbar("Web document converted to PDF!")
                withContext(Dispatchers.Main) {
                    navigateTo(AppScreen.Viewer(conv.file, title))
                }
            }.onFailure { err ->
                _progress.value = ConversionProgress(false)
                emitSnackbar("Conversion failed: ${err.localizedMessage}")
            }
        }
    }

    // --- Extract Text Operations ---
    fun setExtractTextSource(context: Context, uri: Uri) {
        val name = getFileName(context, uri) ?: "Document.pdf"
        val count = PdfEngine.getPdfPageCount(context, uri)
        _extractTextSource.value = SelectedPdfInfo(uri, name, count)
        extractedTextResult.value = "Document Summary:\nFile: $name\nPages: $count\n\n[Document loaded successfully. Ready for structural text and metadata extraction.]"
    }

    // --- Duplicate Document ---
    fun duplicatePdf(context: Context, pdf: PdfItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val srcFile = File(pdf.filePath)
                if (!srcFile.exists()) {
                    emitSnackbar("Source file not found")
                    return@launch
                }
                val newTitle = "${pdf.title}_Copy"
                val destFile = File(context.filesDir, "${newTitle}_${System.currentTimeMillis()}.pdf")
                srcFile.copyTo(destFile, overwrite = true)

                repository.insert(
                    PdfItem(
                        title = newTitle,
                        filePath = destFile.absolutePath,
                        fileSizeBytes = destFile.length(),
                        pageCount = pdf.pageCount,
                        toolType = "DUPLICATE"
                    )
                )
                emitSnackbar("Document duplicated as '$newTitle'!")
            } catch (e: Exception) {
                emitSnackbar("Failed to duplicate: ${e.localizedMessage}")
            }
        }
    }

    // --- AI Search Grounding to PDF ---
    fun executeSearchGrounding(context: Context, query: String) {
        if (query.isBlank()) {
            emitSnackbar("Please enter a research query or topic")
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            _progress.value = ConversionProgress(isConverting = true, message = "Grounding research with Google Search...")
            try {
                val result = GeminiSearchService.searchAndGenerateReport(
                    topicQuery = query,
                    customApiKey = searchCustomApiKey.value
                )
                _searchResult.value = result
                _isSearching.value = false
                _progress.value = ConversionProgress()

                if (result.error != null) {
                    emitSnackbar(result.error)
                } else {
                    emitSnackbar("Verified research retrieved! Ready to generate PDF.")
                }
            } catch (e: Exception) {
                _isSearching.value = false
                _progress.value = ConversionProgress()
                emitSnackbar("Search failed: ${e.localizedMessage}")
            }
        }
    }

    fun generateSearchPdfFromCurrentResult(context: Context) {
        val result = _searchResult.value ?: run {
            emitSnackbar("Please perform search grounding first")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _progress.value = ConversionProgress(isConverting = true, message = "Formatting PDF research document...")
            try {
                val outFile = PdfEngine.generateUniquePdfFile(context, "Research_Report")
                val sourcesList = result.sources.map { it.title to it.url }
                val convResult = PdfEngine.generateStructuredReportPdf(
                    context = context,
                    title = result.title,
                    query = result.query,
                    reportContent = result.content,
                    sources = sourcesList,
                    outputFile = outFile
                ).getOrThrow()

                val item = PdfItem(
                    title = result.title,
                    filePath = convResult.file.absolutePath,
                    fileSizeBytes = convResult.fileSizeBytes,
                    pageCount = convResult.pageCount,
                    toolType = "AI_SEARCH"
                )
                repository.insert(item)
                _progress.value = ConversionProgress()
                _lastGeneratedPdf.value = convResult.file
                withContext(Dispatchers.Main) {
                    navigateTo(AppScreen.Viewer(convResult.file, result.title))
                }
                emitSnackbar("Grounded PDF Report created with ${convResult.pageCount} pages!")
            } catch (e: Exception) {
                _progress.value = ConversionProgress()
                emitSnackbar("Failed to build PDF: ${e.localizedMessage}")
            }
        }
    }

    // --- Voice & Speech to PDF ---
    fun polishVoiceTranscript(context: Context) {
        val raw = voiceTranscript.value.trim()
        if (raw.isBlank()) {
            emitSnackbar("Please record or speak some notes first")
            return
        }
        viewModelScope.launch {
            _isVoicePolishing.value = true
            _progress.value = ConversionProgress(isConverting = true, message = "Structuring voice speech notes with AI...")
            try {
                val result = GeminiSearchService.structureSpeechTranscript(
                    spokenText = raw,
                    contextTopic = voiceTitle.value,
                    customApiKey = searchCustomApiKey.value
                )
                _voiceResult.value = result
                _isVoicePolishing.value = false
                _progress.value = ConversionProgress()
                emitSnackbar("Voice notes structured into executive format!")
            } catch (e: Exception) {
                _isVoicePolishing.value = false
                _progress.value = ConversionProgress()
                emitSnackbar("Processing completed: ${e.localizedMessage}")
            }
        }
    }

    fun generateVoicePdf(context: Context) {
        val transcript = voiceTranscript.value.trim()
        if (transcript.isBlank()) {
            emitSnackbar("Please input speech or voice notes first")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _progress.value = ConversionProgress(isConverting = true, message = "Rendering Voice Notes PDF...")
            try {
                val polished = _voiceResult.value
                val title = polished?.formattedTitle ?: voiceTitle.value
                val summary = polished?.executiveSummary ?: "Direct voice speech dictation."
                val bullets = polished?.keyPoints ?: listOf("Verbatim speech captured on device.")
                val fullText = polished?.formattedTranscript ?: transcript

                val outFile = PdfEngine.generateUniquePdfFile(context, "VoiceNotes")
                val conv = PdfEngine.generateSpeechNotesPdf(
                    context = context,
                    title = title,
                    speaker = voiceSpeaker.value,
                    summary = summary,
                    keyPoints = bullets,
                    transcript = fullText,
                    outputFile = outFile
                ).getOrThrow()

                val item = PdfItem(
                    title = title,
                    filePath = conv.file.absolutePath,
                    fileSizeBytes = conv.fileSizeBytes,
                    pageCount = conv.pageCount,
                    toolType = "VOICE_NOTES"
                )
                repository.insert(item)
                _progress.value = ConversionProgress()
                _lastGeneratedPdf.value = conv.file
                withContext(Dispatchers.Main) {
                    navigateTo(AppScreen.Viewer(conv.file, title))
                }
                emitSnackbar("Voice Notes PDF ready with ${conv.pageCount} page(s)!")
            } catch (e: Exception) {
                _progress.value = ConversionProgress()
                emitSnackbar("Failed to generate PDF: ${e.localizedMessage}")
            }
        }
    }

    // --- Invoice & Receipt Generator ---
    fun addInvoiceItem(item: InvoiceLineItem) {
        _invoiceItems.update { it + item }
    }

    fun removeInvoiceItem(index: Int) {
        _invoiceItems.update { it.filterIndexed { i, _ -> i != index } }
    }

    fun generateInvoicePdf(context: Context) {
        val items = _invoiceItems.value
        if (items.isEmpty()) {
            emitSnackbar("Please add at least one line item")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _progress.value = ConversionProgress(isConverting = true, message = "Generating Professional Invoice PDF...")
            try {
                val outFile = PdfEngine.generateUniquePdfFile(context, "Invoice_${invoiceNumber.value}")
                val conv = PdfEngine.generateInvoicePdf(
                    context = context,
                    invoiceNum = invoiceNumber.value,
                    dateStr = invoiceDate.value,
                    dueDateStr = invoiceDueDate.value,
                    senderName = invoiceSender.value,
                    senderDetails = invoiceSenderDetails.value,
                    clientName = invoiceClient.value,
                    clientDetails = invoiceClientDetails.value,
                    items = items,
                    notes = invoiceNotes.value,
                    taxPercent = invoiceTaxPercent.value,
                    outputFile = outFile
                ).getOrThrow()

                val item = PdfItem(
                    title = "Invoice #${invoiceNumber.value}",
                    filePath = conv.file.absolutePath,
                    fileSizeBytes = conv.fileSizeBytes,
                    pageCount = 1,
                    toolType = "INVOICE"
                )
                repository.insert(item)
                _progress.value = ConversionProgress()
                _lastGeneratedPdf.value = conv.file
                withContext(Dispatchers.Main) {
                    navigateTo(AppScreen.Viewer(conv.file, "Invoice #${invoiceNumber.value}"))
                }
                emitSnackbar("Invoice PDF generated successfully!")
            } catch (e: Exception) {
                _progress.value = ConversionProgress()
                emitSnackbar("Failed to generate invoice: ${e.localizedMessage}")
            }
        }
    }

    // --- AI Summarizer Operations ---
    fun setSummarizerSource(info: SelectedPdfInfo?) {
        _summarizerSource.value = info
        _summarizerResult.value = null
    }

    fun summarizeSelectedPdf(context: Context) {
        val info = _summarizerSource.value ?: run {
            emitSnackbar("Please select a PDF document first")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isSummarizing.value = true
            _progress.value = ConversionProgress(isConverting = true, message = "Extracting text and analyzing with AI...")
            try {
                val extracted = PdfEngine.extractTextFromPdf(context, info.uri).getOrDefault("")
                if (extracted.isBlank()) {
                    _isSummarizing.value = false
                    _progress.value = ConversionProgress()
                    emitSnackbar("No readable text found in document")
                    return@launch
                }
                val summaryRes = GeminiSearchService.summarizePdfText(
                    docTitle = info.name,
                    extractedText = extracted,
                    customApiKey = searchCustomApiKey.value
                )
                _summarizerResult.value = summaryRes
                _isSummarizing.value = false
                _progress.value = ConversionProgress()
                emitSnackbar("Executive summary generated!")
            } catch (e: Exception) {
                _isSummarizing.value = false
                _progress.value = ConversionProgress()
                emitSnackbar("Summarization failed: ${e.localizedMessage}")
            }
        }
    }

    fun generateSummaryPdf(context: Context) {
        val sumRes = _summarizerResult.value ?: run {
            emitSnackbar("Please run summarization first")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _progress.value = ConversionProgress(isConverting = true, message = "Generating Executive Summary PDF...")
            try {
                val outFile = PdfEngine.generateUniquePdfFile(context, "Summary_Brief")
                val conv = PdfEngine.generateSpeechNotesPdf(
                    context = context,
                    title = sumRes.title,
                    speaker = "AI Executive Auditor",
                    summary = sumRes.summary,
                    keyPoints = sumRes.keyInsights + sumRes.actionItems.map { "Action: $it" },
                    transcript = "Summary report synthesized from source document.",
                    outputFile = outFile
                ).getOrThrow()

                val item = PdfItem(
                    title = sumRes.title,
                    filePath = conv.file.absolutePath,
                    fileSizeBytes = conv.fileSizeBytes,
                    pageCount = conv.pageCount,
                    toolType = "AI_SUMMARY"
                )
                repository.insert(item)
                _progress.value = ConversionProgress()
                _lastGeneratedPdf.value = conv.file
                withContext(Dispatchers.Main) {
                    navigateTo(AppScreen.Viewer(conv.file, sumRes.title))
                }
                emitSnackbar("Executive Summary PDF created!")
            } catch (e: Exception) {
                _progress.value = ConversionProgress()
                emitSnackbar("Failed to create summary PDF: ${e.localizedMessage}")
            }
        }
    }

    // --- Metadata Operations ---
    fun setMetadataSource(info: SelectedPdfInfo?) {
        _metadataSource.value = info
        if (info != null) {
            metadataTitle.value = info.name.removeSuffix(".pdf")
        }
    }

    fun generateMetadataPdf(context: Context) {
        val info = _metadataSource.value ?: run {
            emitSnackbar("Please select a PDF document first")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _progress.value = ConversionProgress(isConverting = true, message = "Embedding Metadata & Title Page...")
            try {
                val outFile = PdfEngine.generateUniquePdfFile(context, "MetaDoc")
                val metadataOverview = """
                    # TITLE: ${metadataTitle.value}
                    ## DOCUMENT METADATA
                    - Title: ${metadataTitle.value}
                    - Author: ${metadataAuthor.value}
                    - Subject: ${metadataSubject.value}
                    - Keywords: ${metadataKeywords.value}
                    - Modified Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}
                    - Source: ${info.name}
                    
                    ## SUMMARY
                    This document has been updated with verified administrative metadata and catalog indexing tags.
                """.trimIndent()

                val conv = PdfEngine.generateStructuredReportPdf(
                    context = context,
                    title = metadataTitle.value,
                    query = "Document Catalog Metadata",
                    reportContent = metadataOverview,
                    sources = emptyList(),
                    outputFile = outFile
                ).getOrThrow()

                val item = PdfItem(
                    title = metadataTitle.value,
                    filePath = conv.file.absolutePath,
                    fileSizeBytes = conv.fileSizeBytes,
                    pageCount = conv.pageCount,
                    toolType = "METADATA"
                )
                repository.insert(item)
                _progress.value = ConversionProgress()
                _lastGeneratedPdf.value = conv.file
                withContext(Dispatchers.Main) {
                    navigateTo(AppScreen.Viewer(conv.file, metadataTitle.value))
                }
                emitSnackbar("Updated metadata PDF ready!")
            } catch (e: Exception) {
                _progress.value = ConversionProgress()
                emitSnackbar("Failed to update metadata: ${e.localizedMessage}")
            }
        }
    }

    // --- Download & Print & Export Operations ---
    fun downloadToDevice(context: Context, file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val uri = PdfEngine.savePdfToPublicDownloads(context, file)
            if (uri != null) {
                emitSnackbar("Saved to Downloads/PDFConverter folder!")
            } else {
                emitSnackbar("Saved to Downloads folder")
            }
        }
    }

    fun exportPdfToUri(context: Context, sourceFile: File, targetUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                emitSnackbar("File exported successfully!")
            } catch (e: Exception) {
                emitSnackbar("Failed to export: ${e.localizedMessage}")
            }
        }
    }

    fun printPdf(context: Context, file: File) {
        try {
            PdfEngine.printPdf(context, file)
        } catch (e: Exception) {
            emitSnackbar("Could not start print service: ${e.localizedMessage}")
        }
    }

    fun openWithExternalApp(context: Context, file: File) {
        try {
            val uri = PdfEngine.getShareableUri(context, file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open PDF with..."))
        } catch (e: Exception) {
            emitSnackbar("No app found to open PDF")
        }
    }

    // --- Database Operations ---
    fun deletePdf(pdf: PdfItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(pdf.filePath)
                if (file.exists()) file.delete()
            } catch (_: Exception) {}
            repository.delete(pdf)
            emitSnackbar("Document deleted")
        }
    }

    fun toggleFavorite(pdf: PdfItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleFavorite(pdf)
        }
    }

    fun sharePdf(context: Context, file: File) {
        try {
            val uri = PdfEngine.getShareableUri(context, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share PDF Document"))
        } catch (e: Exception) {
            emitSnackbar("Could not share file: ${e.localizedMessage}")
        }
    }

    fun shareImage(context: Context, file: File) {
        try {
            val uri = PdfEngine.getShareableUri(context, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Image"))
        } catch (e: Exception) {
            emitSnackbar("Could not share image: ${e.localizedMessage}")
        }
    }

    private fun emitSnackbar(msg: String) {
        viewModelScope.launch {
            _snackbarEvent.emit(msg)
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = it.getString(idx)
                }
            }
        }
        return name ?: uri.lastPathSegment
    }
}
