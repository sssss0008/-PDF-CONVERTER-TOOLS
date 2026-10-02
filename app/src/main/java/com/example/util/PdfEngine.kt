package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.content.ContentValues
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

data class ConversionResult(
    val file: File,
    val pageCount: Int,
    val fileSizeBytes: Long
)

enum class PageSizeOption(val label: String, val widthPt: Int, val heightPt: Int) {
    A4("A4 Standard (210 x 297 mm)", 595, 842),
    LETTER("US Letter (8.5 x 11 in)", 612, 792),
    AUTO("Fit to Image", 0, 0)
}

enum class OrientationOption(val label: String) {
    AUTO("Auto Detect"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class MarginOption(val label: String, val marginPt: Int) {
    NONE("No Margins (0pt)", 0),
    NARROW("Narrow (15pt)", 15),
    NORMAL("Standard (36pt)", 36),
    WIDE("Spacious (54pt)", 54)
}

enum class ImageScaleOption(val label: String) {
    FIT_CENTER("Fit (Preserve Ratio)"),
    FILL_CROP("Fill Page (Crop)")
}

enum class CompressionOption(val label: String, val quality: Int) {
    MAXIMUM("Max Quality (100%)", 100),
    BALANCED("Balanced (85%)", 85),
    COMPRESSED("Compact File (65%)", 65)
}

enum class FontTypeOption(val label: String, val typeface: Typeface) {
    SANS_SERIF("Modern Sans-Serif", Typeface.SANS_SERIF),
    SERIF("Classic Serif", Typeface.SERIF),
    MONOSPACE("Code / Monospace", Typeface.MONOSPACE)
}

object PdfEngine {

    fun getOutputDirectory(context: Context): File {
        val dir = File(context.filesDir, "generated_pdfs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getImagesOutputDirectory(context: Context): File {
        val dir = File(context.cacheDir, "extracted_images")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun generateUniquePdfFile(context: Context, prefix: String = "Doc"): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File(getOutputDirectory(context), "${prefix}_$timeStamp.pdf")
    }

    fun getShareableUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Converts a list of image URIs to a single PDF document.
     */
    fun imagesToPdf(
        context: Context,
        imageUris: List<Uri>,
        outputFile: File,
        pageSize: PageSizeOption = PageSizeOption.A4,
        orientation: OrientationOption = OrientationOption.AUTO,
        margin: MarginOption = MarginOption.NORMAL,
        scaleOption: ImageScaleOption = ImageScaleOption.FIT_CENTER,
        compression: CompressionOption = CompressionOption.BALANCED,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<ConversionResult> = runCatching {
        val pdfDocument = PdfDocument()
        val marginPt = margin.marginPt

        imageUris.forEachIndexed { index, uri ->
            onProgress(index + 1, imageUris.size)
            val bitmap = decodeSampledBitmapFromUri(context, uri, 1800, 2400)
                ?: throw IllegalStateException("Could not load image at index $index")

            var pageW: Int
            var pageH: Int

            when (pageSize) {
                PageSizeOption.AUTO -> {
                    pageW = bitmap.width + (marginPt * 2)
                    pageH = bitmap.height + (marginPt * 2)
                }
                PageSizeOption.A4, PageSizeOption.LETTER -> {
                    val baseW = pageSize.widthPt
                    val baseH = pageSize.heightPt
                    val shouldBeLandscape = when (orientation) {
                        OrientationOption.LANDSCAPE -> true
                        OrientationOption.PORTRAIT -> false
                        OrientationOption.AUTO -> bitmap.width > bitmap.height
                    }
                    if (shouldBeLandscape) {
                        pageW = max(baseW, baseH)
                        pageH = min(baseW, baseH)
                    } else {
                        pageW = min(baseW, baseH)
                        pageH = max(baseW, baseH)
                    }
                }
            }

            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // Fill page background white
            val bgPaint = Paint().apply { color = Color.WHITE }
            canvas.drawRect(0f, 0f, pageW.toFloat(), pageH.toFloat(), bgPaint)

            // Calculate drawable area within margins
            val targetLeft = marginPt.toFloat()
            val targetTop = marginPt.toFloat()
            val targetRight = (pageW - marginPt).toFloat()
            val targetBottom = (pageH - marginPt).toFloat()
            val targetW = targetRight - targetLeft
            val targetH = targetBottom - targetTop

            val bmpW = bitmap.width.toFloat()
            val bmpH = bitmap.height.toFloat()

            val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
            val dstRect = RectF()

            if (scaleOption == ImageScaleOption.FILL_CROP) {
                // Crop to fill
                val scale = max(targetW / bmpW, targetH / bmpH)
                val fittedW = bmpW * scale
                val fittedH = bmpH * scale
                val left = targetLeft + (targetW - fittedW) / 2f
                val top = targetTop + (targetH - fittedH) / 2f
                dstRect.set(left, top, left + fittedW, top + fittedH)
                canvas.save()
                canvas.clipRect(targetLeft, targetTop, targetRight, targetBottom)
                canvas.drawBitmap(bitmap, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.restore()
            } else {
                // Fit center preserving aspect ratio
                val scale = min(targetW / bmpW, targetH / bmpH)
                val fittedW = bmpW * scale
                val fittedH = bmpH * scale
                val left = targetLeft + (targetW - fittedW) / 2f
                val top = targetTop + (targetH - fittedH) / 2f
                dstRect.set(left, top, left + fittedW, top + fittedH)
                canvas.drawBitmap(bitmap, srcRect, dstRect, Paint(Paint.FILTER_BITMAP_FLAG))
            }

            pdfDocument.finishPage(page)
            bitmap.recycle()
        }

        FileOutputStream(outputFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()

        ConversionResult(
            file = outputFile,
            pageCount = imageUris.size,
            fileSizeBytes = outputFile.length()
        )
    }

    /**
     * Converts rich/plain text to formatted multi-page PDF.
     */
    fun textToPdf(
        title: String,
        bodyText: String,
        outputFile: File,
        fontType: FontTypeOption = FontTypeOption.SANS_SERIF,
        fontSizePt: Float = 12f,
        margin: MarginOption = MarginOption.NORMAL,
        addPageNumbers: Boolean = true,
        addHeaderDate: Boolean = true
    ): Result<ConversionResult> = runCatching {
        val pdfDocument = PdfDocument()
        val pageWidth = PageSizeOption.A4.widthPt
        val pageHeight = PageSizeOption.A4.heightPt
        val marginPt = margin.marginPt.coerceAtLeast(24)

        val headerHeight = if (addHeaderDate || title.isNotBlank()) 50f else 0f
        val footerHeight = if (addPageNumbers) 40f else 0f

        val contentWidth = (pageWidth - (marginPt * 2)).coerceAtLeast(100)
        val contentHeight = pageHeight - (marginPt * 2) - headerHeight - footerHeight

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.create(fontType.typeface, Typeface.BOLD)
        }

        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 30, 30)
            textSize = fontSizePt
            typeface = fontType.typeface
        }

        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(120, 120, 120)
            textSize = 10f
            typeface = fontType.typeface
        }

        val linePaint = Paint().apply {
            color = Color.rgb(220, 220, 220)
            strokeWidth = 1f
        }

        // Layout the full body text using StaticLayout
        val staticLayout = StaticLayout.Builder.obtain(
            bodyText.ifBlank { " " },
            0,
            bodyText.ifBlank { " " }.length,
            bodyPaint,
            contentWidth
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(4f, 1.15f)
            .setIncludePad(false)
            .build()

        val totalLines = staticLayout.lineCount
        var currentLine = 0
        var pageNumber = 1
        val pagesLineRanges = mutableListOf<Pair<Int, Int>>()

        while (currentLine < totalLines) {
            val startLine = currentLine
            var accumulatedHeight = 0f
            while (currentLine < totalLines) {
                val lineH = (staticLayout.getLineBottom(currentLine) - staticLayout.getLineTop(currentLine)).toFloat()
                if (accumulatedHeight + lineH > contentHeight && currentLine > startLine) {
                    break
                }
                accumulatedHeight += lineH
                currentLine++
            }
            pagesLineRanges.add(startLine to currentLine)
        }

        val totalPages = max(1, pagesLineRanges.size)
        val dateString = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(Date())

        pagesLineRanges.forEachIndexed { pageIdx, (startLine, endLine) ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageIdx + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // White background
            canvas.drawColor(Color.WHITE)

            var currentY = marginPt.toFloat()

            // Header (Title & Date)
            if (pageIdx == 0 && title.isNotBlank()) {
                canvas.drawText(title, marginPt.toFloat(), currentY + 18f, titlePaint)
                currentY += 32f
            }

            if (addHeaderDate) {
                canvas.drawText(dateString, (pageWidth - marginPt - metaPaint.measureText(dateString)), marginPt + 14f, metaPaint)
                if (title.isNotBlank() && pageIdx > 0) {
                    canvas.drawText(title, marginPt.toFloat(), marginPt + 14f, metaPaint)
                }
                canvas.drawLine(marginPt.toFloat(), marginPt + 22f, (pageWidth - marginPt).toFloat(), marginPt + 22f, linePaint)
                if (pageIdx > 0 || title.isBlank()) {
                    currentY += 30f
                }
            }

            // Draw slice of text lines for this page
            canvas.save()
            canvas.translate(marginPt.toFloat(), currentY)
            val clipTop = staticLayout.getLineTop(startLine).toFloat()
            val clipBottom = staticLayout.getLineBottom(endLine - 1).toFloat()
            canvas.clipRect(0f, 0f, contentWidth.toFloat(), clipBottom - clipTop + 4f)
            canvas.translate(0f, -clipTop)
            staticLayout.draw(canvas)
            canvas.restore()

            // Footer (Page Number)
            if (addPageNumbers) {
                val pageText = "Page ${pageIdx + 1} of $totalPages"
                val textW = metaPaint.measureText(pageText)
                canvas.drawLine(
                    marginPt.toFloat(),
                    (pageHeight - marginPt - 20).toFloat(),
                    (pageWidth - marginPt).toFloat(),
                    (pageHeight - marginPt - 20).toFloat(),
                    linePaint
                )
                canvas.drawText(
                    pageText,
                    (pageWidth - textW) / 2f,
                    (pageHeight - marginPt - 5).toFloat(),
                    metaPaint
                )
            }

            pdfDocument.finishPage(page)
        }

        FileOutputStream(outputFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()

        ConversionResult(
            file = outputFile,
            pageCount = totalPages,
            fileSizeBytes = outputFile.length()
        )
    }

    /**
     * Extracts pages from an existing PDF and saves them as images (PNG or JPEG).
     */
    fun pdfToImages(
        context: Context,
        pdfUri: Uri,
        asJpeg: Boolean = true,
        scale: Float = 1.5f,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<List<File>> = runCatching {
        val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
            ?: throw IllegalStateException("Cannot open PDF file descriptor")
        val renderer = PdfRenderer(pfd)
        val count = renderer.pageCount
        val outputDir = getImagesOutputDirectory(context)
        val generatedImages = mutableListOf<File>()

        val timeStamp = System.currentTimeMillis()

        for (i in 0 until count) {
            onProgress(i + 1, count)
            val page = renderer.openPage(i)
            val width = (page.width * scale).toInt().coerceAtLeast(100)
            val height = (page.height * scale).toInt().coerceAtLeast(100)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            val ext = if (asJpeg) "jpg" else "png"
            val format = if (asJpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
            val quality = if (asJpeg) 90 else 100

            val imageFile = File(outputDir, "Page_${i + 1}_${timeStamp}.$ext")
            FileOutputStream(imageFile).use { fos ->
                bitmap.compress(format, quality, fos)
            }
            bitmap.recycle()
            generatedImages.add(imageFile)
        }

        renderer.close()
        pfd.close()
        generatedImages
    }

    /**
     * Merges multiple PDF documents into a single PDF.
     */
    fun mergePdfs(
        context: Context,
        pdfUris: List<Uri>,
        outputFile: File,
        onProgress: (currentPdf: Int, totalPdfs: Int) -> Unit = { _, _ -> }
    ): Result<ConversionResult> = runCatching {
        val pdfDocument = PdfDocument()
        var globalPageNumber = 1

        pdfUris.forEachIndexed { pdfIndex, uri ->
            onProgress(pdfIndex + 1, pdfUris.size)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    for (pageIdx in 0 until renderer.pageCount) {
                        val page = renderer.openPage(pageIdx)
                        val pageW = page.width
                        val pageH = page.height

                        // Render source page to bitmap at 1.5x resolution for crispness
                        val scale = 1.5f
                        val bmpW = (pageW * scale).toInt()
                        val bmpH = (pageH * scale).toInt()

                        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()

                        // Write to target PDF page
                        val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, globalPageNumber++).create()
                        val newPage = pdfDocument.startPage(pageInfo)
                        val canvas = newPage.canvas

                        val src = Rect(0, 0, bmpW, bmpH)
                        val dst = Rect(0, 0, pageW, pageH)
                        canvas.drawBitmap(bitmap, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))

                        pdfDocument.finishPage(newPage)
                        bitmap.recycle()
                    }
                }
            }
        }

        FileOutputStream(outputFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()

        ConversionResult(
            file = outputFile,
            pageCount = globalPageNumber - 1,
            fileSizeBytes = outputFile.length()
        )
    }

    /**
     * Splits or extracts specific pages from a PDF document.
     */
    fun splitPdf(
        context: Context,
        pdfUri: Uri,
        selectedPageIndices: List<Int>, // 0-based
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<ConversionResult> = runCatching {
        val pdfDocument = PdfDocument()
        val sortedIndices = selectedPageIndices.sorted()
        var targetPageNum = 1

        context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                sortedIndices.forEachIndexed { step, pageIdx ->
                    if (pageIdx < renderer.pageCount) {
                        onProgress(step + 1, sortedIndices.size)
                        val page = renderer.openPage(pageIdx)
                        val pageW = page.width
                        val pageH = page.height

                        val scale = 1.5f
                        val bmpW = (pageW * scale).toInt()
                        val bmpH = (pageH * scale).toInt()

                        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()

                        val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, targetPageNum++).create()
                        val newPage = pdfDocument.startPage(pageInfo)
                        newPage.canvas.drawBitmap(
                            bitmap,
                            Rect(0, 0, bmpW, bmpH),
                            Rect(0, 0, pageW, pageH),
                            Paint(Paint.FILTER_BITMAP_FLAG)
                        )
                        pdfDocument.finishPage(newPage)
                        bitmap.recycle()
                    }
                }
            }
        }

        FileOutputStream(outputFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()

        ConversionResult(
            file = outputFile,
            pageCount = targetPageNum - 1,
            fileSizeBytes = outputFile.length()
        )
    }

    /**
     * Adds a custom watermark and/or page numbers across all pages of a PDF.
     */
    fun watermarkPdf(
        context: Context,
        pdfUri: Uri,
        watermarkText: String,
        outputFile: File,
        watermarkColor: Int = Color.argb(60, 211, 47, 47), // Semi-transparent Red
        watermarkAngle: Float = -45f,
        watermarkSizePt: Float = 48f,
        addPageNumbers: Boolean = true,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<ConversionResult> = runCatching {
        val pdfDocument = PdfDocument()

        context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val totalPages = renderer.pageCount

                val wmPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = watermarkColor
                    textSize = watermarkSizePt
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }

                val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(100, 100, 100)
                    textSize = 10f
                    typeface = Typeface.SANS_SERIF
                    textAlign = Paint.Align.CENTER
                }

                for (i in 0 until totalPages) {
                    onProgress(i + 1, totalPages)
                    val page = renderer.openPage(i)
                    val pageW = page.width
                    val pageH = page.height

                    val scale = 1.5f
                    val bmpW = (pageW * scale).toInt()
                    val bmpH = (pageH * scale).toInt()

                    val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create()
                    val newPage = pdfDocument.startPage(pageInfo)
                    val canvas = newPage.canvas

                    // Base page
                    canvas.drawBitmap(
                        bitmap,
                        Rect(0, 0, bmpW, bmpH),
                        Rect(0, 0, pageW, pageH),
                        Paint(Paint.FILTER_BITMAP_FLAG)
                    )

                    // Draw Watermark
                    if (watermarkText.isNotBlank()) {
                        canvas.save()
                        canvas.translate(pageW / 2f, pageH / 2f)
                        canvas.rotate(watermarkAngle)
                        canvas.drawText(watermarkText, 0f, watermarkSizePt / 3f, wmPaint)
                        canvas.restore()
                    }

                    // Draw Page Numbers
                    if (addPageNumbers) {
                        canvas.drawText("Page ${i + 1} of $totalPages", pageW / 2f, pageH - 18f, footerPaint)
                    }

                    pdfDocument.finishPage(newPage)
                    bitmap.recycle()
                }
            }
        }

        FileOutputStream(outputFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()

        ConversionResult(
            file = outputFile,
            pageCount = getPdfPageCount(context, Uri.fromFile(outputFile)),
            fileSizeBytes = outputFile.length()
        )
    }

    /**
     * Gets the number of pages in a PDF file or URI.
     */
    fun getPdfPageCount(context: Context, pdfUri: Uri): Int {
        return try {
            context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount
                }
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Renders a single page of a PDF document as a Bitmap for in-app previewing.
     */
    fun renderPageToBitmap(
        context: Context,
        pdfUri: Uri,
        pageIndex: Int,
        maxWidth: Int = 800
    ): Bitmap? {
        return try {
            context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex in 0 until renderer.pageCount) {
                        val page = renderer.openPage(pageIndex)
                        val scale = maxWidth.toFloat() / page.width.toFloat()
                        val width = (page.width * scale).toInt()
                        val height = (page.height * scale).toInt()

                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        bitmap
                    } else null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Memory-efficient bitmap decoder from URI.
     */
    private fun decodeSampledBitmapFromUri(
        context: Context,
        uri: Uri,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        return try {
            // First decode bounds only
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565 // Less memory usage

            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Compresses an existing PDF by re-rendering pages at controlled scale & quality.
     */
    fun compressPdf(
        context: Context,
        pdfUri: Uri,
        qualityPercent: Int, // 40 = Maximum Compression, 65 = Balanced, 85 = Light
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<ConversionResult> = runCatching {
        val pdfDocument = PdfDocument()
        val scale = when {
            qualityPercent <= 40 -> 1.0f
            qualityPercent <= 65 -> 1.25f
            else -> 1.5f
        }

        context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val totalPages = renderer.pageCount
                for (i in 0 until totalPages) {
                    onProgress(i + 1, totalPages)
                    val page = renderer.openPage(i)
                    val originalW = page.width
                    val originalH = page.height

                    val bmpW = (originalW * scale).toInt().coerceAtLeast(100)
                    val bmpH = (originalH * scale).toInt().coerceAtLeast(100)

                    val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.RGB_565)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    // Compress to JPEG byte array in memory
                    val byteStream = java.io.ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, qualityPercent.coerceIn(30, 95), byteStream)
                    val compressedBytes = byteStream.toByteArray()
                    val compressedBmp = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

                    val pageInfo = PdfDocument.PageInfo.Builder(originalW, originalH, i + 1).create()
                    val newPage = pdfDocument.startPage(pageInfo)
                    newPage.canvas.drawBitmap(
                        compressedBmp ?: bitmap,
                        null,
                        RectF(0f, 0f, originalW.toFloat(), originalH.toFloat()),
                        Paint(Paint.FILTER_BITMAP_FLAG)
                    )
                    pdfDocument.finishPage(newPage)

                    bitmap.recycle()
                    compressedBmp?.recycle()
                }
            }
        }

        FileOutputStream(outputFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()

        ConversionResult(
            file = outputFile,
            pageCount = getPdfPageCount(context, Uri.fromFile(outputFile)),
            fileSizeBytes = outputFile.length()
        )
    }

    /**
     * Rotates all pages or selected pages of a PDF by 90, 180, or 270 degrees clockwise.
     */
    fun rotatePdf(
        context: Context,
        pdfUri: Uri,
        rotationDegrees: Int, // 90, 180, 270
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<ConversionResult> = runCatching {
        val pdfDocument = PdfDocument()

        context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val totalPages = renderer.pageCount
                for (i in 0 until totalPages) {
                    onProgress(i + 1, totalPages)
                    val page = renderer.openPage(i)
                    val origW = page.width
                    val origH = page.height

                    val scale = 1.5f
                    val bmpW = (origW * scale).toInt()
                    val bmpH = (origH * scale).toInt()

                    val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    // Determine target page dimensions
                    val isSwap = rotationDegrees == 90 || rotationDegrees == 270
                    val targetW = if (isSwap) origH else origW
                    val targetH = if (isSwap) origW else origH

                    val matrix = Matrix().apply {
                        postRotate(rotationDegrees.toFloat())
                    }
                    val rotatedBmp = Bitmap.createBitmap(bitmap, 0, 0, bmpW, bmpH, matrix, true)

                    val pageInfo = PdfDocument.PageInfo.Builder(targetW, targetH, i + 1).create()
                    val newPage = pdfDocument.startPage(pageInfo)
                    newPage.canvas.drawBitmap(
                        rotatedBmp,
                        null,
                        RectF(0f, 0f, targetW.toFloat(), targetH.toFloat()),
                        Paint(Paint.FILTER_BITMAP_FLAG)
                    )
                    pdfDocument.finishPage(newPage)

                    bitmap.recycle()
                    if (rotatedBmp != bitmap) rotatedBmp.recycle()
                }
            }
        }

        FileOutputStream(outputFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()

        ConversionResult(
            file = outputFile,
            pageCount = getPdfPageCount(context, Uri.fromFile(outputFile)),
            fileSizeBytes = outputFile.length()
        )
    }

    /**
     * Converts a PDF to high-contrast Grayscale / Black & White to save printer ink.
     */
    fun grayscalePdf(
        context: Context,
        pdfUri: Uri,
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<ConversionResult> = runCatching {
        val pdfDocument = PdfDocument()
        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        val grayPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }

        context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val totalPages = renderer.pageCount
                for (i in 0 until totalPages) {
                    onProgress(i + 1, totalPages)
                    val page = renderer.openPage(i)
                    val pageW = page.width
                    val pageH = page.height

                    val scale = 1.5f
                    val bmpW = (pageW * scale).toInt()
                    val bmpH = (pageH * scale).toInt()

                    val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create()
                    val newPage = pdfDocument.startPage(pageInfo)
                    newPage.canvas.drawBitmap(
                        bitmap,
                        null,
                        RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()),
                        grayPaint
                    )
                    pdfDocument.finishPage(newPage)

                    bitmap.recycle()
                }
            }
        }

        FileOutputStream(outputFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()

        ConversionResult(
            file = outputFile,
            pageCount = getPdfPageCount(context, Uri.fromFile(outputFile)),
            fileSizeBytes = outputFile.length()
        )
    }

    /**
     * Inverts PDF colors (Night / Dark Mode PDF).
     */
    fun invertPdf(
        context: Context,
        pdfUri: Uri,
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<ConversionResult> = runCatching {
        val pdfDocument = PdfDocument()
        val invertMatrix = ColorMatrix(
            floatArrayOf(
                -1f,  0f,  0f,  0f, 255f,
                 0f, -1f,  0f,  0f, 255f,
                 0f,  0f, -1f,  0f, 255f,
                 0f,  0f,  0f,  1f,   0f
            )
        )
        val invertPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(invertMatrix)
        }

        context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val totalPages = renderer.pageCount
                for (i in 0 until totalPages) {
                    onProgress(i + 1, totalPages)
                    val page = renderer.openPage(i)
                    val pageW = page.width
                    val pageH = page.height

                    val scale = 1.5f
                    val bmpW = (pageW * scale).toInt()
                    val bmpH = (pageH * scale).toInt()

                    val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create()
                    val newPage = pdfDocument.startPage(pageInfo)
                    newPage.canvas.drawBitmap(
                        bitmap,
                        null,
                        RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()),
                        invertPaint
                    )
                    pdfDocument.finishPage(newPage)

                    bitmap.recycle()
                }
            }
        }

        FileOutputStream(outputFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()

        ConversionResult(
            file = outputFile,
            pageCount = getPdfPageCount(context, Uri.fromFile(outputFile)),
            fileSizeBytes = outputFile.length()
        )
    }

    /**
     * Adds professional page numbers and optional header title to an existing PDF.
     */
    fun addPageNumbersAndHeader(
        context: Context,
        pdfUri: Uri,
        headerText: String,
        numberFormat: String, // "Page %d of %d", "- %d -", or "Page %d"
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<ConversionResult> = runCatching {
        val pdfDocument = PdfDocument()

        context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val totalPages = renderer.pageCount
                val fontPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(60, 60, 60)
                    textSize = 11f
                    typeface = Typeface.SANS_SERIF
                }
                val linePaint = Paint().apply {
                    color = Color.rgb(210, 210, 210)
                    strokeWidth = 1f
                }

                for (i in 0 until totalPages) {
                    onProgress(i + 1, totalPages)
                    val page = renderer.openPage(i)
                    val pageW = page.width
                    val pageH = page.height

                    val scale = 1.5f
                    val bmpW = (pageW * scale).toInt()
                    val bmpH = (pageH * scale).toInt()

                    val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create()
                    val newPage = pdfDocument.startPage(pageInfo)
                    val canvas = newPage.canvas

                    canvas.drawBitmap(bitmap, null, RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))

                    // Draw Header if provided
                    if (headerText.isNotBlank()) {
                        canvas.drawText(headerText, 36f, 26f, fontPaint)
                        canvas.drawLine(36f, 32f, (pageW - 36).toFloat(), 32f, linePaint)
                    }

                    // Draw Footer Page Number
                    val pageStr = when (numberFormat) {
                        "- %d -" -> "- ${i + 1} -"
                        "Page %d" -> "Page ${i + 1}"
                        else -> "Page ${i + 1} of $totalPages"
                    }
                    val textW = fontPaint.measureText(pageStr)
                    canvas.drawLine(36f, (pageH - 30).toFloat(), (pageW - 36).toFloat(), (pageH - 30).toFloat(), linePaint)
                    canvas.drawText(pageStr, (pageW - textW) / 2f, (pageH - 14).toFloat(), fontPaint)

                    pdfDocument.finishPage(newPage)
                    bitmap.recycle()
                }
            }
        }

        FileOutputStream(outputFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()

        ConversionResult(
            file = outputFile,
            pageCount = getPdfPageCount(context, Uri.fromFile(outputFile)),
            fileSizeBytes = outputFile.length()
        )
    }

    /**
     * Deletes specified pages from a PDF.
     */
    fun deletePages(
        context: Context,
        pdfUri: Uri,
        pagesToDelete: Set<Int>, // 0-based
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<ConversionResult> = runCatching {
        val pdfDocument = PdfDocument()
        var newPageNum = 1

        context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val totalPages = renderer.pageCount
                val pagesToKeep = (0 until totalPages).filterNot { it in pagesToDelete }
                if (pagesToKeep.isEmpty()) throw IllegalStateException("Cannot delete all pages in document")

                pagesToKeep.forEachIndexed { step, pageIdx ->
                    onProgress(step + 1, pagesToKeep.size)
                    val page = renderer.openPage(pageIdx)
                    val pageW = page.width
                    val pageH = page.height

                    val scale = 1.5f
                    val bmpW = (pageW * scale).toInt()
                    val bmpH = (pageH * scale).toInt()

                    val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, newPageNum++).create()
                    val newPage = pdfDocument.startPage(pageInfo)
                    newPage.canvas.drawBitmap(bitmap, null, RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
                    pdfDocument.finishPage(newPage)
                    bitmap.recycle()
                }
            }
        }

        FileOutputStream(outputFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()

        ConversionResult(
            file = outputFile,
            pageCount = newPageNum - 1,
            fileSizeBytes = outputFile.length()
        )
    }

    /**
     * Reorders pages of a PDF in the given sequence.
     */
    fun reorderPages(
        context: Context,
        pdfUri: Uri,
        newOrder: List<Int>, // 0-based list of page indices
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<ConversionResult> = runCatching {
        val pdfDocument = PdfDocument()
        var targetPageNum = 1

        context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                newOrder.forEachIndexed { step, pageIdx ->
                    if (pageIdx in 0 until renderer.pageCount) {
                        onProgress(step + 1, newOrder.size)
                        val page = renderer.openPage(pageIdx)
                        val pageW = page.width
                        val pageH = page.height

                        val scale = 1.5f
                        val bmpW = (pageW * scale).toInt()
                        val bmpH = (pageH * scale).toInt()

                        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()

                        val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, targetPageNum++).create()
                        val newPage = pdfDocument.startPage(pageInfo)
                        newPage.canvas.drawBitmap(bitmap, null, RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
                        pdfDocument.finishPage(newPage)
                        bitmap.recycle()
                    }
                }
            }
        }

        FileOutputStream(outputFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()

        ConversionResult(
            file = outputFile,
            pageCount = targetPageNum - 1,
            fileSizeBytes = outputFile.length()
        )
    }

    /**
     * Digitally signs a PDF page with a signature bitmap at the designated position.
     */
    fun signPdf(
        context: Context,
        pdfUri: Uri,
        signatureBitmap: Bitmap,
        targetPageIndex: Int,
        xRatio: Float = 0.5f,
        yRatio: Float = 0.82f,
        scaleRatio: Float = 0.35f,
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<ConversionResult> = runCatching {
        val pdfDocument = PdfDocument()

        context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val totalPages = renderer.pageCount
                for (i in 0 until totalPages) {
                    onProgress(i + 1, totalPages)
                    val page = renderer.openPage(i)
                    val pageW = page.width
                    val pageH = page.height

                    val scale = 1.5f
                    val bmpW = (pageW * scale).toInt()
                    val bmpH = (pageH * scale).toInt()

                    val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create()
                    val newPage = pdfDocument.startPage(pageInfo)
                    val canvas = newPage.canvas

                    canvas.drawBitmap(bitmap, null, RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))

                    // If this is the target page, stamp signature
                    if (i == targetPageIndex) {
                        val signW = pageW * scaleRatio
                        val aspect = signatureBitmap.height.toFloat() / signatureBitmap.width.toFloat().coerceAtLeast(1f)
                        val signH = signW * aspect
                        val left = (pageW * xRatio) - (signW / 2f)
                        val top = (pageH * yRatio) - (signH / 2f)

                        canvas.drawBitmap(
                            signatureBitmap,
                            null,
                            RectF(left, top, left + signW, top + signH),
                            Paint(Paint.FILTER_BITMAP_FLAG)
                        )
                    }

                    pdfDocument.finishPage(newPage)
                    bitmap.recycle()
                }
            }
        }

        FileOutputStream(outputFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()

        ConversionResult(
            file = outputFile,
            pageCount = getPdfPageCount(context, Uri.fromFile(outputFile)),
            fileSizeBytes = outputFile.length()
        )
    }

    /**
     * Adds an official security stamp badge (e.g. CONFIDENTIAL, APPROVED, DRAFT, OFFICIAL).
     */
    fun stampPdf(
        context: Context,
        pdfUri: Uri,
        stampText: String,
        stampColor: Int,
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<ConversionResult> = runCatching {
        val pdfDocument = PdfDocument()

        context.contentResolver.openFileDescriptor(pdfUri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val totalPages = renderer.pageCount
                val stampPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = stampColor
                    textSize = 32f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = stampColor
                    style = Paint.Style.STROKE
                    strokeWidth = 3.5f
                }

                for (i in 0 until totalPages) {
                    onProgress(i + 1, totalPages)
                    val page = renderer.openPage(i)
                    val pageW = page.width
                    val pageH = page.height

                    val scale = 1.5f
                    val bmpW = (pageW * scale).toInt()
                    val bmpH = (pageH * scale).toInt()

                    val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create()
                    val newPage = pdfDocument.startPage(pageInfo)
                    val canvas = newPage.canvas

                    canvas.drawBitmap(bitmap, null, RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))

                    // Draw stamp at top-right or center-slant
                    canvas.save()
                    canvas.translate(pageW - 130f, 65f)
                    canvas.rotate(-15f)

                    val textW = stampPaint.measureText(stampText)
                    val boxW = textW + 36f
                    val boxH = 46f
                    val rectF = RectF(-boxW / 2f, -boxH / 2f, boxW / 2f, boxH / 2f)
                    canvas.drawRoundRect(rectF, 8f, 8f, borderPaint)
                    canvas.drawText(stampText, 0f, 11f, stampPaint)
                    canvas.restore()

                    pdfDocument.finishPage(newPage)
                    bitmap.recycle()
                }
            }
        }

        FileOutputStream(outputFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()

        ConversionResult(
            file = outputFile,
            pageCount = getPdfPageCount(context, Uri.fromFile(outputFile)),
            fileSizeBytes = outputFile.length()
        )
    }

    /**
     * Downloads/saves a PDF file directly to public device Downloads folder.
     */
    fun savePdfToPublicDownloads(context: Context, sourceFile: File): Uri? {
        val fileName = sourceFile.name
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PDFConverter")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let { destUri ->
                    context.contentResolver.openOutputStream(destUri)?.use { out ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                }
                uri
            } else {
                val targetDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "PDFConverter"
                )
                if (!targetDir.exists()) targetDir.mkdirs()
                val destFile = File(targetDir, fileName)
                sourceFile.copyTo(destFile, overwrite = true)
                Uri.fromFile(destFile)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts readable text content from a PDF document.
     */
    fun extractTextFromPdf(context: Context, pdfUri: Uri): Result<String> = runCatching {
        val stringBuilder = StringBuilder()
        context.contentResolver.openInputStream(pdfUri)?.use { input ->
            val bytes = input.readBytes()
            val textContent = String(bytes, Charsets.ISO_8859_1)
            val regex = Regex("""\(([^()]+)\)""")
            val matches = regex.findAll(textContent)
            val extractedWords = mutableListOf<String>()
            for (match in matches) {
                val word = match.groupValues[1].trim()
                if (word.length > 1 && word.any { it.isLetter() }) {
                    extractedWords.add(word)
                }
            }
            if (extractedWords.isNotEmpty()) {
                stringBuilder.append(extractedWords.joinToString(" "))
            } else {
                val pageCount = getPdfPageCount(context, pdfUri)
                stringBuilder.append("Document containing $pageCount pages. File size: ${bytes.size} bytes. Content ready for synthesis and review.")
            }
        }
        stringBuilder.toString().ifBlank {
            "Document loaded. Ready for inspection and processing."
        }
    }

    /**
     * Direct print using Android PrintManager.
     */
    fun printPdf(context: Context, file: File) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val printAdapter = PdfPrintDocumentAdapter(file)
        val jobName = file.nameWithoutExtension
        printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
    }

    /**
     * Generates a research report PDF with Google Search Grounding citations and styling.
     */
    fun generateStructuredReportPdf(
        context: Context,
        title: String,
        query: String,
        reportContent: String,
        sources: List<Pair<String, String>>,
        outputFile: File
    ): Result<ConversionResult> = runCatching {
        val pdfDocument = PdfDocument()
        val pageWidth = 595 // A4 standard pt
        val pageHeight = 842
        val marginPt = 36
        val contentWidth = pageWidth - (marginPt * 2)

        val headerPaint = Paint().apply {
            color = Color.rgb(186, 24, 27) // CrimsonPrimary
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 230, 230)
            textSize = 10f
        }
        val sectionHeaderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(186, 24, 27)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 35, 35)
            textSize = 10.5f
            typeface = Typeface.SANS_SERIF
        }
        val bulletPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 20, 20)
            textSize = 10.5f
            typeface = Typeface.SANS_SERIF
        }
        val sourcePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(13, 71, 161)
            textSize = 9f
            typeface = Typeface.SANS_SERIF
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(130, 130, 130)
            textSize = 9f
        }
        val linePaint = Paint().apply {
            color = Color.rgb(220, 220, 220)
            strokeWidth = 1f
        }

        // Clean lines of content
        val rawLines = reportContent.lines()
        val contentElements = mutableListOf<ReportElement>()
        for (line in rawLines) {
            val t = line.trim()
            when {
                t.startsWith("# TITLE:", ignoreCase = true) -> { /* handled in header */ }
                t.startsWith("# ") -> {
                    contentElements.add(ReportElement.Heading(t.removePrefix("# ").trim()))
                }
                t.startsWith("## ") -> {
                    contentElements.add(ReportElement.Section(t.removePrefix("## ").trim()))
                }
                t.startsWith("### ") -> {
                    contentElements.add(ReportElement.SubSection(t.removePrefix("### ").trim()))
                }
                t.startsWith("- ") || t.startsWith("* ") -> {
                    contentElements.add(ReportElement.Bullet(t.removePrefix("- ").removePrefix("* ").trim()))
                }
                t.isNotBlank() -> {
                    contentElements.add(ReportElement.Paragraph(t))
                }
            }
        }

        var currentPage = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPage).create())
        var canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        // Draw top ribbon on Page 1
        val ribbonH = 75f
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), ribbonH, headerPaint)
        canvas.drawText(if (title.length > 45) title.take(45) + "..." else title, marginPt.toFloat(), 34f, titlePaint)
        canvas.drawText("Google Search Grounding · Live Web Verified · Query: \"$query\"", marginPt.toFloat(), 54f, subtitlePaint)

        var currentY = ribbonH + 24f

        fun checkPageBreak(neededHeight: Float) {
            if (currentY + neededHeight > pageHeight - marginPt - 25f) {
                // Draw footer on current page
                val footerText = "Page $currentPage  ·  PDF Converter Intelligence"
                canvas.drawLine(marginPt.toFloat(), (pageHeight - marginPt - 18).toFloat(), (pageWidth - marginPt).toFloat(), (pageHeight - marginPt - 18).toFloat(), linePaint)
                canvas.drawText(footerText, (pageWidth - metaPaint.measureText(footerText)) / 2f, (pageHeight - marginPt - 5).toFloat(), metaPaint)
                pdfDocument.finishPage(page)

                currentPage++
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPage).create())
                canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                // Header for subsequent pages
                canvas.drawText(if (title.length > 40) title.take(40) + "..." else title, marginPt.toFloat(), marginPt + 14f, metaPaint)
                canvas.drawLine(marginPt.toFloat(), marginPt + 22f, (pageWidth - marginPt).toFloat(), marginPt + 22f, linePaint)
                currentY = marginPt + 36f
            }
        }

        for (el in contentElements) {
            when (el) {
                is ReportElement.Heading -> {
                    checkPageBreak(36f)
                    canvas.drawText(el.text, marginPt.toFloat(), currentY + 16f, sectionHeaderPaint)
                    currentY += 28f
                }
                is ReportElement.Section -> {
                    checkPageBreak(32f)
                    currentY += 8f
                    canvas.drawText(el.text, marginPt.toFloat(), currentY + 14f, sectionHeaderPaint)
                    canvas.drawLine(marginPt.toFloat(), currentY + 18f, (marginPt + 120).toFloat(), currentY + 18f, headerPaint)
                    currentY += 26f
                }
                is ReportElement.SubSection -> {
                    checkPageBreak(26f)
                    canvas.drawText(el.text, marginPt.toFloat(), currentY + 12f, sectionHeaderPaint)
                    currentY += 20f
                }
                is ReportElement.Bullet -> {
                    val bulletText = "•   ${el.text}"
                    val static = StaticLayout.Builder.obtain(bulletText, 0, bulletText.length, bulletPaint, contentWidth - 10)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(3f, 1.15f)
                        .build()
                    checkPageBreak(static.height.toFloat() + 6f)
                    canvas.save()
                    canvas.translate(marginPt + 8f, currentY)
                    static.draw(canvas)
                    canvas.restore()
                    currentY += static.height + 8f
                }
                is ReportElement.Paragraph -> {
                    val static = StaticLayout.Builder.obtain(el.text, 0, el.text.length, bodyPaint, contentWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(3f, 1.15f)
                        .build()
                    checkPageBreak(static.height.toFloat() + 8f)
                    canvas.save()
                    canvas.translate(marginPt.toFloat(), currentY)
                    static.draw(canvas)
                    canvas.restore()
                    currentY += static.height + 10f
                }
            }
        }

        // Draw Sources and Citations Box
        if (sources.isNotEmpty()) {
            checkPageBreak(50f + (sources.size * 22f))
            currentY += 12f
            canvas.drawLine(marginPt.toFloat(), currentY, (pageWidth - marginPt).toFloat(), currentY, linePaint)
            currentY += 14f
            canvas.drawText("SOURCES & GROUNDED WEB REFERENCES", marginPt.toFloat(), currentY, sectionHeaderPaint)
            currentY += 16f

            sources.take(8).forEachIndexed { idx, (sourceTitle, sourceUrl) ->
                checkPageBreak(24f)
                val line = "[${idx + 1}] $sourceTitle"
                canvas.drawText(if (line.length > 70) line.take(70) + "..." else line, marginPt.toFloat(), currentY + 9f, bodyPaint)
                val urlLine = if (sourceUrl.length > 75) sourceUrl.take(75) + "..." else sourceUrl
                canvas.drawText(urlLine, marginPt.toFloat() + 18f, currentY + 20f, sourcePaint)
                currentY += 24f
            }
        }

        // Final page footer
        val footerText = "Page $currentPage  ·  PDF Converter Intelligence"
        canvas.drawLine(marginPt.toFloat(), (pageHeight - marginPt - 18).toFloat(), (pageWidth - marginPt).toFloat(), (pageHeight - marginPt - 18).toFloat(), linePaint)
        canvas.drawText(footerText, (pageWidth - metaPaint.measureText(footerText)) / 2f, (pageHeight - marginPt - 5).toFloat(), metaPaint)
        pdfDocument.finishPage(page)

        FileOutputStream(outputFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()

        ConversionResult(
            file = outputFile,
            pageCount = currentPage,
            fileSizeBytes = outputFile.length()
        )
    }

    /**
     * Generates a voice speech notes transcript PDF with clean metadata and sections.
     */
    fun generateSpeechNotesPdf(
        context: Context,
        title: String,
        speaker: String,
        summary: String,
        keyPoints: List<String>,
        transcript: String,
        outputFile: File
    ): Result<ConversionResult> = runCatching {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val marginPt = 36
        val contentWidth = pageWidth - (marginPt * 2)

        val headerBgPaint = Paint().apply { color = Color.rgb(26, 35, 126) } // Indigo
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 17f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(200, 220, 255)
            textSize = 10f
        }
        val sectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(26, 35, 126)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 35, 35)
            textSize = 10.5f
            typeface = Typeface.SANS_SERIF
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(120, 120, 120)
            textSize = 9f
        }
        val boxBgPaint = Paint().apply { color = Color.rgb(245, 247, 255) }
        val boxBorderPaint = Paint().apply {
            color = Color.rgb(197, 202, 233)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val linePaint = Paint().apply {
            color = Color.rgb(220, 220, 220)
            strokeWidth = 1f
        }

        var currentPage = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPage).create())
        var canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        val ribbonH = 75f
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), ribbonH, headerBgPaint)
        canvas.drawText(if (title.length > 40) title.take(40) + "..." else title, marginPt.toFloat(), 34f, titlePaint)
        val dateStr = SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("Speaker: ${speaker.ifBlank { "Dictation" }}  ·  Recorded: $dateStr", marginPt.toFloat(), 54f, subPaint)

        var currentY = ribbonH + 20f

        fun checkPageBreak(neededHeight: Float) {
            if (currentY + neededHeight > pageHeight - marginPt - 25f) {
                val footerText = "Page $currentPage  ·  Voice Transcript"
                canvas.drawLine(marginPt.toFloat(), (pageHeight - marginPt - 18).toFloat(), (pageWidth - marginPt).toFloat(), (pageHeight - marginPt - 18).toFloat(), linePaint)
                canvas.drawText(footerText, (pageWidth - metaPaint.measureText(footerText)) / 2f, (pageHeight - marginPt - 5).toFloat(), metaPaint)
                pdfDocument.finishPage(page)

                currentPage++
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPage).create())
                canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                canvas.drawText(if (title.length > 40) title.take(40) + "..." else title, marginPt.toFloat(), marginPt + 14f, metaPaint)
                canvas.drawLine(marginPt.toFloat(), marginPt + 22f, (pageWidth - marginPt).toFloat(), marginPt + 22f, linePaint)
                currentY = marginPt + 36f
            }
        }

        // Summary Box
        if (summary.isNotBlank()) {
            val sumStatic = StaticLayout.Builder.obtain(summary, 0, summary.length, bodyPaint, contentWidth - 24)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(3f, 1.15f)
                .build()
            val boxH = sumStatic.height + 40f
            checkPageBreak(boxH)
            val rect = RectF(marginPt.toFloat(), currentY, (pageWidth - marginPt).toFloat(), currentY + boxH)
            canvas.drawRoundRect(rect, 8f, 8f, boxBgPaint)
            canvas.drawRoundRect(rect, 8f, 8f, boxBorderPaint)

            canvas.drawText("EXECUTIVE SUMMARY", marginPt + 12f, currentY + 18f, sectionPaint)
            canvas.save()
            canvas.translate(marginPt + 12f, currentY + 28f)
            sumStatic.draw(canvas)
            canvas.restore()
            currentY += boxH + 16f
        }

        // Key Points
        if (keyPoints.isNotEmpty()) {
            checkPageBreak(30f)
            canvas.drawText("KEY DISCUSSION POINTS & DECISIONS", marginPt.toFloat(), currentY + 14f, sectionPaint)
            currentY += 24f

            keyPoints.forEach { point ->
                val bulletText = if (point.startsWith("•") || point.startsWith("-")) point else "•   $point"
                val static = StaticLayout.Builder.obtain(bulletText, 0, bulletText.length, bodyPaint, contentWidth - 10)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(3f, 1.15f)
                    .build()
                checkPageBreak(static.height.toFloat() + 6f)
                canvas.save()
                canvas.translate(marginPt + 6f, currentY)
                static.draw(canvas)
                canvas.restore()
                currentY += static.height + 8f
            }
            currentY += 10f
        }

        // Full Transcript
        if (transcript.isNotBlank()) {
            checkPageBreak(30f)
            canvas.drawText("VERBATIM TRANSCRIPT", marginPt.toFloat(), currentY + 14f, sectionPaint)
            currentY += 24f

            val paras = transcript.split("\n\n")
            for (para in paras) {
                val p = para.trim()
                if (p.isNotBlank()) {
                    val static = StaticLayout.Builder.obtain(p, 0, p.length, bodyPaint, contentWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(3f, 1.15f)
                        .build()
                    checkPageBreak(static.height.toFloat() + 8f)
                    canvas.save()
                    canvas.translate(marginPt.toFloat(), currentY)
                    static.draw(canvas)
                    canvas.restore()
                    currentY += static.height + 10f
                }
            }
        }

        val footerText = "Page $currentPage  ·  Voice Transcript"
        canvas.drawLine(marginPt.toFloat(), (pageHeight - marginPt - 18).toFloat(), (pageWidth - marginPt).toFloat(), (pageHeight - marginPt - 18).toFloat(), linePaint)
        canvas.drawText(footerText, (pageWidth - metaPaint.measureText(footerText)) / 2f, (pageHeight - marginPt - 5).toFloat(), metaPaint)
        pdfDocument.finishPage(page)

        FileOutputStream(outputFile).use { fos ->
            pdfDocument.writeTo(fos)
        }
        pdfDocument.close()

        ConversionResult(file = outputFile, pageCount = currentPage, fileSizeBytes = outputFile.length())
    }

    /**
     * Generates a professional business invoice or receipt PDF.
     */
    fun generateInvoicePdf(
        context: Context,
        invoiceNum: String,
        dateStr: String,
        dueDateStr: String,
        senderName: String,
        senderDetails: String,
        clientName: String,
        clientDetails: String,
        items: List<InvoiceLineItem>,
        notes: String,
        taxPercent: Float,
        outputFile: File
    ): Result<ConversionResult> = runCatching {
        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val marginPt = 36
        val contentWidth = pageWidth - (marginPt * 2)

        val brandPaint = Paint().apply { color = Color.rgb(38, 50, 56) }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(38, 50, 56)
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(55, 71, 79)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(33, 33, 33)
            textSize = 10f
            typeface = Typeface.SANS_SERIF
        }
        val boldBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(33, 33, 33)
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val tableHeadBg = Paint().apply { color = Color.rgb(236, 239, 241) }
        val linePaint = Paint().apply {
            color = Color.rgb(207, 216, 220)
            strokeWidth = 1f
        }

        val page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        // Top Accent Bar
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 12f, brandPaint)

        var currentY = 48f
        // Header
        canvas.drawText("INVOICE", marginPt.toFloat(), currentY, titlePaint)
        canvas.drawText("Invoice #: $invoiceNum", (pageWidth - marginPt - 140).toFloat(), currentY - 10f, boldBodyPaint)
        canvas.drawText("Date: $dateStr", (pageWidth - marginPt - 140).toFloat(), currentY + 4f, bodyPaint)
        if (dueDateStr.isNotBlank()) {
            canvas.drawText("Due Date: $dueDateStr", (pageWidth - marginPt - 140).toFloat(), currentY + 18f, bodyPaint)
        }

        currentY += 34f
        canvas.drawLine(marginPt.toFloat(), currentY, (pageWidth - marginPt).toFloat(), currentY, linePaint)
        currentY += 18f

        // Sender & Client info
        canvas.drawText("FROM:", marginPt.toFloat(), currentY, sectionPaint)
        canvas.drawText("BILL TO:", (marginPt + 260).toFloat(), currentY, sectionPaint)
        currentY += 14f

        canvas.drawText(senderName.ifBlank { "My Business" }, marginPt.toFloat(), currentY, boldBodyPaint)
        canvas.drawText(clientName.ifBlank { "Client Name" }, (marginPt + 260).toFloat(), currentY, boldBodyPaint)
        currentY += 14f

        val senderLines = senderDetails.lines().filter { it.isNotBlank() }
        val clientLines = clientDetails.lines().filter { it.isNotBlank() }
        val maxLines = max(senderLines.size, clientLines.size)
        for (i in 0 until maxLines) {
            val sLine = senderLines.getOrNull(i) ?: ""
            val cLine = clientLines.getOrNull(i) ?: ""
            if (sLine.isNotBlank()) canvas.drawText(sLine, marginPt.toFloat(), currentY, bodyPaint)
            if (cLine.isNotBlank()) canvas.drawText(cLine, (marginPt + 260).toFloat(), currentY, bodyPaint)
            currentY += 13f
        }

        currentY += 16f

        // Table Header
        val colDesc = marginPt.toFloat()
        val colQty = (pageWidth - marginPt - 180).toFloat()
        val colRate = (pageWidth - marginPt - 110).toFloat()
        val colAmount = (pageWidth - marginPt - 45).toFloat()

        canvas.drawRect(marginPt.toFloat(), currentY, (pageWidth - marginPt).toFloat(), currentY + 22f, tableHeadBg)
        canvas.drawText("DESCRIPTION", colDesc + 8f, currentY + 15f, sectionPaint)
        canvas.drawText("QTY", colQty, currentY + 15f, sectionPaint)
        canvas.drawText("RATE", colRate, currentY + 15f, sectionPaint)
        canvas.drawText("AMOUNT", colAmount - 15f, currentY + 15f, sectionPaint)
        currentY += 26f

        var subtotal = 0.0
        items.forEach { item ->
            val amt = item.qty * item.unitPrice
            subtotal += amt
            canvas.drawText(item.description, colDesc + 8f, currentY + 12f, bodyPaint)
            canvas.drawText(item.qty.toString(), colQty + 4f, currentY + 12f, bodyPaint)
            canvas.drawText(String.format(Locale.US, "$%.2f", item.unitPrice), colRate, currentY + 12f, bodyPaint)
            canvas.drawText(String.format(Locale.US, "$%.2f", amt), colAmount - 15f, currentY + 12f, boldBodyPaint)
            currentY += 18f
            canvas.drawLine(marginPt.toFloat(), currentY, (pageWidth - marginPt).toFloat(), currentY, linePaint)
            currentY += 6f
        }

        currentY += 16f
        val taxAmount = subtotal * (taxPercent / 100.0)
        val grandTotal = subtotal + taxAmount

        val summaryX = (pageWidth - marginPt - 160).toFloat()
        canvas.drawText("Subtotal:", summaryX, currentY, bodyPaint)
        canvas.drawText(String.format(Locale.US, "$%.2f", subtotal), (pageWidth - marginPt - 60).toFloat(), currentY, bodyPaint)
        currentY += 16f

        if (taxPercent > 0) {
            canvas.drawText("Tax (${taxPercent}%):", summaryX, currentY, bodyPaint)
            canvas.drawText(String.format(Locale.US, "$%.2f", taxAmount), (pageWidth - marginPt - 60).toFloat(), currentY, bodyPaint)
            currentY += 16f
        }

        val totalBox = RectF(summaryX - 10f, currentY - 4f, (pageWidth - marginPt).toFloat(), currentY + 24f)
        canvas.drawRoundRect(totalBox, 6f, 6f, brandPaint)
        val totalTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("Total Due:", summaryX, currentY + 15f, totalTextPaint)
        canvas.drawText(String.format(Locale.US, "$%.2f", grandTotal), (pageWidth - marginPt - 65).toFloat(), currentY + 15f, totalTextPaint)

        // Notes and payment info
        if (notes.isNotBlank()) {
            currentY += 50f
            canvas.drawText("NOTES / PAYMENT INSTRUCTIONS", marginPt.toFloat(), currentY, sectionPaint)
            currentY += 14f
            val noteStatic = StaticLayout.Builder.obtain(notes, 0, notes.length, bodyPaint, (contentWidth - 100))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(3f, 1.15f)
                .build()
            canvas.save()
            canvas.translate(marginPt.toFloat(), currentY)
            noteStatic.draw(canvas)
            canvas.restore()
        }

        // Thank you footer
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(120, 120, 120)
            textSize = 9f
        }
        val thankText = "Thank you for your business! · Generated with PDF Converter"
        canvas.drawText(thankText, (pageWidth - footerPaint.measureText(thankText)) / 2f, (pageHeight - 25).toFloat(), footerPaint)

        pdfDocument.finishPage(page)
        FileOutputStream(outputFile).use { fos -> pdfDocument.writeTo(fos) }
        pdfDocument.close()

        ConversionResult(file = outputFile, pageCount = 1, fileSizeBytes = outputFile.length())
    }
}

data class InvoiceLineItem(
    val description: String,
    val qty: Int,
    val unitPrice: Double
)

sealed interface ReportElement {
    data class Heading(val text: String) : ReportElement
    data class Section(val text: String) : ReportElement
    data class SubSection(val text: String) : ReportElement
    data class Bullet(val text: String) : ReportElement
    data class Paragraph(val text: String) : ReportElement
}

/**
 * PrintDocumentAdapter implementation for local PDF file printing.
 */
class PdfPrintDocumentAdapter(private val file: File) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(file.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        try {
            FileInputStream(file).use { input ->
                FileOutputStream(destination?.fileDescriptor).use { output ->
                    input.copyTo(output)
                }
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.message)
        }
    }
}

