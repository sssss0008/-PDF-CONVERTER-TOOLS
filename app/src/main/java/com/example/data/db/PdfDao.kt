package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.PdfItem
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDao {
    @Query("SELECT * FROM pdf_items ORDER BY timestamp DESC")
    fun getAllPdfs(): Flow<List<PdfItem>>

    @Query("SELECT * FROM pdf_items WHERE id = :id")
    suspend fun getPdfById(id: Long): PdfItem?

    @Query("SELECT * FROM pdf_items WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavoritePdfs(): Flow<List<PdfItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPdf(pdf: PdfItem): Long

    @Update
    suspend fun updatePdf(pdf: PdfItem)

    @Delete
    suspend fun deletePdf(pdf: PdfItem)

    @Query("DELETE FROM pdf_items WHERE id = :id")
    suspend fun deletePdfById(id: Long)
}
