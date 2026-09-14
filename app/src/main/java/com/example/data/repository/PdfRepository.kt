package com.example.data.repository

import com.example.data.db.PdfDao
import com.example.data.model.PdfItem
import kotlinx.coroutines.flow.Flow

class PdfRepository(private val pdfDao: PdfDao) {
    val allPdfs: Flow<List<PdfItem>> = pdfDao.getAllPdfs()
    val favoritePdfs: Flow<List<PdfItem>> = pdfDao.getFavoritePdfs()

    suspend fun getPdfById(id: Long): PdfItem? = pdfDao.getPdfById(id)

    suspend fun insert(pdf: PdfItem): Long = pdfDao.insertPdf(pdf)

    suspend fun update(pdf: PdfItem) = pdfDao.updatePdf(pdf)

    suspend fun delete(pdf: PdfItem) = pdfDao.deletePdf(pdf)

    suspend fun deleteById(id: Long) = pdfDao.deletePdfById(id)

    suspend fun toggleFavorite(pdf: PdfItem) {
        pdfDao.updatePdf(pdf.copy(isFavorite = !pdf.isFavorite))
    }
}
