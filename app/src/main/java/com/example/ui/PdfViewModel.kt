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
    data class Viewer(val file: File, val title: String) : AppScreen
    data object History : AppScreen
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
