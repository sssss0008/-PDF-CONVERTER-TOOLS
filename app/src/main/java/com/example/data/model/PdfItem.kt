package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_items")
data class PdfItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val toolType: String, // "IMAGE_TO_PDF", "TEXT_TO_PDF", "PDF_MERGE", "PDF_SPLIT", "WATERMARK"
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)
