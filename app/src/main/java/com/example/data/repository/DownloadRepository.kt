package com.example.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.extractor.UrlUtilities
import com.example.model.DownloadStatus
import com.example.model.MediaPreview
import com.example.model.QualityOption
import com.example.storage.MediaStoreSaver
import com.example.worker.MediaDownloadWorker
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID

class DownloadRepository(
    private val context: Context,
    private val downloadDao: DownloadDao
) {
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    val downloads: Flow<List<DownloadEntity>> = downloadDao.observeAll()

    suspend fun enqueueDownload(
        preview: MediaPreview,
        quality: QualityOption
    ): String {
        val id = UUID.randomUUID().toString()
        val fileName = UrlUtilities.sanitizeFileName(
            raw = preview.title,
            mediaType = quality.mediaType,
            mimeType = quality.mimeType
        )
        val entity = DownloadEntity(
            id = id,
            title = preview.title,
            fileName = fileName,
            sourceUrl = preview.sourceUrl,
            downloadUrl = quality.downloadUrl,
            thumbnailUrl = preview.thumbnailUrl,
            platform = preview.platform.name,
            mediaType = quality.mediaType.name,
            qualityLabel = quality.label,
            mimeType = quality.mimeType,
            status = DownloadStatus.QUEUED.name,
            downloadedBytes = 0L,
            totalBytes = quality.fileSizeBytes ?: 0L
        )
        downloadDao.upsert(entity)
        enqueueWorker(id)
        return id
    }

    suspend fun pauseDownload(id: String) {
        downloadDao.updateStatus(id, DownloadStatus.PAUSED.name)
        workManager.cancelUniqueWork(uniqueWorkName(id))
    }

    suspend fun resumeDownload(id: String) {
        downloadDao.updateStatus(id, DownloadStatus.QUEUED.name, errorMessage = null)
        enqueueWorker(id)
    }

    suspend fun cancelDownload(id: String) {
        downloadDao.updateStatus(id, DownloadStatus.CANCELLED.name)
        workManager.cancelUniqueWork(uniqueWorkName(id))
        File(context.cacheDir, "downloads/$id.part").delete()
    }

    suspend fun deleteDownload(entity: DownloadEntity, deleteFileFromDevice: Boolean) {
        workManager.cancelUniqueWork(uniqueWorkName(entity.id))
        File(context.cacheDir, "downloads/${entity.id}.part").delete()
        if (deleteFileFromDevice) {
            MediaStoreSaver.deleteFromMediaStore(context, entity.savedUri)
        }
        downloadDao.deleteById(entity.id)
    }

    suspend fun clearFinishedHistory() {
        downloadDao.clearFinished()
    }

    private fun enqueueWorker(downloadId: String) {
        val inputData = Data.Builder()
            .putString(MediaDownloadWorker.KEY_DOWNLOAD_ID, downloadId)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<MediaDownloadWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName(downloadId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun uniqueWorkName(id: String) = "download_$id"
}
