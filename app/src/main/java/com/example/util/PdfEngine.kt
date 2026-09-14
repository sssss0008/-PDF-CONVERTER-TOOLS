package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
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
}
