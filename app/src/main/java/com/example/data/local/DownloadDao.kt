package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadEntity)

    @Query("UPDATE downloads SET status = :status, errorMessage = :errorMessage, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(
        id: String,
        status: String,
        errorMessage: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        "UPDATE downloads SET status = :status, downloadedBytes = :downloadedBytes, " +
            "totalBytes = :totalBytes, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateProgress(
        id: String,
        status: String,
        downloadedBytes: Long,
        totalBytes: Long,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        "UPDATE downloads SET status = :status, downloadedBytes = :downloadedBytes, " +
            "totalBytes = :totalBytes, savedUri = :savedUri, errorMessage = NULL, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun markCompleted(
        id: String,
        status: String,
        downloadedBytes: Long,
        totalBytes: Long,
        savedUri: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun clearFinished()
}
