package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.DownloadStatus
import com.example.model.MediaPlatform
import com.example.model.MediaType

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val fileName: String,
    val sourceUrl: String,
    val downloadUrl: String,
    val thumbnailUrl: String?,
    val platform: String = MediaPlatform.DIRECT.name,
    val mediaType: String = MediaType.IMAGE.name,
    val qualityLabel: String = "الأصلية",
    val mimeType: String? = null,
    val status: String = DownloadStatus.QUEUED.name,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val savedUri: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val statusEnum: DownloadStatus
        get() = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.QUEUED)

    val platformEnum: MediaPlatform
        get() = runCatching { MediaPlatform.valueOf(platform) }.getOrDefault(MediaPlatform.WEB_PAGE)

    val mediaTypeEnum: MediaType
        get() = runCatching { MediaType.valueOf(mediaType) }.getOrDefault(MediaType.IMAGE)

    val progressFraction: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}
