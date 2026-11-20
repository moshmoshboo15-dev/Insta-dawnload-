package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.MediaGrabApp
import com.example.R
import com.example.extractor.UrlUtilities
import com.example.model.DownloadStatus
import com.example.storage.MediaStoreSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MediaDownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val CHANNEL_ID = "mediagrab_downloads"

        fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.notif_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = context.getString(R.string.notif_channel_desc)
                        setShowBadge(false)
                    }
                    manager.createNotificationChannel(channel)
                }
            }
        }
    }

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return@withContext Result.failure()
        val app = applicationContext as MediaGrabApp
        val dao = app.database.downloadDao()
        val entity = dao.getById(downloadId) ?: return@withContext Result.failure()

        ensureNotificationChannel(applicationContext)
        val notifId = downloadId.hashCode()

        val partDir = File(applicationContext.cacheDir, "downloads").apply {
            if (!exists()) mkdirs()
        }
        val partFile = File(partDir, "$downloadId.part")

        var existingBytes = if (partFile.exists()) partFile.length() else 0L
        if (existingBytes > 0L && entity.totalBytes > 0L && existingBytes >= entity.totalBytes) {
            // File already fully downloaded to .part, finalize save
            return@withContext finalizeToGallery(app, entity.id, entity.fileName, partFile, notifId)
        }

        if (!MediaStoreSaver.hasSufficientStorage(applicationContext, (entity.totalBytes - existingBytes).coerceAtLeast(0L))) {
            dao.updateStatus(
                id = downloadId,
                status = DownloadStatus.FAILED.name,
                errorMessage = applicationContext.getString(R.string.error_insufficient_storage)
            )
            postCompletionNotification(notifId, entity.title, success = false)
            return@withContext Result.failure()
        }

        try {
            setForeground(createForegroundInfo(notifId, downloadId, entity.title, 0, existingBytes, entity.totalBytes))
        } catch (_: Exception) {
            // Foreground promotion may fail if app is restricted in background; continue worker safely
        }

        dao.updateProgress(
            id = downloadId,
            status = DownloadStatus.DOWNLOADING.name,
            downloadedBytes = existingBytes,
            totalBytes = entity.totalBytes
        )

        try {
            val reqBuilder = Request.Builder()
                .url(entity.downloadUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                )
            if (existingBytes > 0L) {
                reqBuilder.header("Range", "bytes=$existingBytes-")
            }

            app.okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    val errorMsg = when (response.code) {
                        401, 403, 404, 410 -> applicationContext.getString(R.string.error_private_or_deleted)
                        429 -> applicationContext.getString(R.string.error_rate_limited)
                        else -> applicationContext.getString(R.string.error_generic)
                    }
                    dao.updateStatus(downloadId, DownloadStatus.FAILED.name, errorMsg)
                    postCompletionNotification(notifId, entity.title, success = false)
                    return@withContext Result.failure()
                }

                val isPartial = response.code == 206
                if (!isPartial) {
                    existingBytes = 0L
                }

                val body = response.body ?: throw IOException("استجابة فارغة من الخادم")
                val contentLength = body.contentLength()
                val totalBytes = when {
                    isPartial && contentLength > 0L -> existingBytes + contentLength
                    contentLength > 0L -> contentLength
                    else -> entity.totalBytes
                }

                if (totalBytes > 0L && !MediaStoreSaver.hasSufficientStorage(applicationContext, totalBytes - existingBytes)) {
                    dao.updateStatus(
                        downloadId,
                        DownloadStatus.FAILED.name,
                        applicationContext.getString(R.string.error_insufficient_storage)
                    )
                    postCompletionNotification(notifId, entity.title, success = false)
                    return@withContext Result.failure()
                }

                var downloaded = existingBytes
                val buffer = ByteArray(16 * 1024)
                var lastUpdateMs = 0L

                body.byteStream().use { input ->
                    FileOutputStream(partFile, isPartial).use { output ->
                        while (true) {
                            if (isStopped) {
                                // Respect pause or cancel immediately
                                val current = dao.getById(downloadId)
                                if (current?.statusEnum == DownloadStatus.CANCELLED) {
                                    partFile.delete()
                                } else {
                                    dao.updateProgress(
                                        id = downloadId,
                                        status = DownloadStatus.PAUSED.name,
                                        downloadedBytes = downloaded,
                                        totalBytes = totalBytes
                                    )
                                }
                                notificationManager.cancel(notifId)
                                return@withContext Result.success()
                            }

                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            val now = System.currentTimeMillis()
                            if (now - lastUpdateMs >= 400L) {
                                lastUpdateMs = now
                                val pct = if (totalBytes > 0L) ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100) else -1
                                dao.updateProgress(
                                    id = downloadId,
                                    status = DownloadStatus.DOWNLOADING.name,
                                    downloadedBytes = downloaded,
                                    totalBytes = totalBytes
                                )
                                notificationManager.notify(
                                    notifId,
                                    buildProgressNotification(downloadId, entity.title, pct, downloaded, totalBytes)
                                )
                            }
                        }
                        output.flush()
                    }
                }

                dao.updateProgress(
                    id = downloadId,
                    status = DownloadStatus.DOWNLOADING.name,
                    downloadedBytes = downloaded,
                    totalBytes = if (totalBytes > 0L) totalBytes else downloaded
                )
            }

            finalizeToGallery(app, downloadId, entity.fileName, partFile, notifId)
        } catch (e: IOException) {
            if (isStopped) {
                notificationManager.cancel(notifId)
                return@withContext Result.success()
            }
            dao.updateStatus(
                id = downloadId,
                status = DownloadStatus.FAILED.name,
                errorMessage = applicationContext.getString(R.string.error_no_internet)
            )
            postCompletionNotification(notifId, entity.title, success = false)
            Result.failure()
        } catch (e: Exception) {
            dao.updateStatus(
                id = downloadId,
                status = DownloadStatus.FAILED.name,
                errorMessage = e.localizedMessage ?: applicationContext.getString(R.string.error_generic)
            )
            postCompletionNotification(notifId, entity.title, success = false)
            Result.failure()
        }
    }

    private suspend fun finalizeToGallery(
        app: MediaGrabApp,
        downloadId: String,
        fileName: String,
        partFile: File,
        notifId: Int
    ): Result {
        val dao = app.database.downloadDao()
        val entity = dao.getById(downloadId) ?: return Result.failure()
        val finalSize = partFile.length()

        val saveResult = MediaStoreSaver.saveFileToGallery(
            context = applicationContext,
            sourceFile = partFile,
            fileName = fileName,
            mediaType = entity.mediaTypeEnum,
            mimeType = entity.mimeType
        )

        partFile.delete()

        return saveResult.fold(
            onSuccess = { savedUri ->
                dao.markCompleted(
                    id = downloadId,
                    status = DownloadStatus.COMPLETED.name,
                    downloadedBytes = finalSize,
                    totalBytes = finalSize,
                    savedUri = savedUri.toString()
                )
                postCompletionNotification(notifId, entity.title, success = true)
                Result.success()
            },
            onFailure = { err ->
                dao.updateStatus(
                    id = downloadId,
                    status = DownloadStatus.FAILED.name,
                    errorMessage = err.localizedMessage ?: applicationContext.getString(R.string.error_generic)
                )
                postCompletionNotification(notifId, entity.title, success = false)
                Result.failure()
            }
        )
    }

    private fun createForegroundInfo(
        notifId: Int,
        downloadId: String,
        title: String,
        progressPct: Int,
        downloadedBytes: Long,
        totalBytes: Long
    ): ForegroundInfo {
        val notification = buildProgressNotification(downloadId, title, progressPct, downloadedBytes, totalBytes)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notifId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notifId, notification)
        }
    }

    private fun buildProgressNotification(
        downloadId: String,
        title: String,
        progressPct: Int,
        downloadedBytes: Long,
        totalBytes: Long
    ): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            applicationContext,
            downloadId.hashCode(),
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = PendingIntent.getBroadcast(
            applicationContext,
            downloadId.hashCode() + 1,
            Intent(applicationContext, DownloadActionReceiver::class.java).apply {
                action = DownloadActionReceiver.ACTION_PAUSE
                putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, downloadId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = PendingIntent.getBroadcast(
            applicationContext,
            downloadId.hashCode() + 2,
            Intent(applicationContext, DownloadActionReceiver::class.java).apply {
                action = DownloadActionReceiver.ACTION_CANCEL
                putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, downloadId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val progressText = if (totalBytes > 0L) {
            "${UrlUtilities.formatBytes(downloadedBytes)} / ${UrlUtilities.formatBytes(totalBytes)} ($progressPct%)"
        } else {
            UrlUtilities.formatBytes(downloadedBytes)
        }

        return NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(applicationContext.getString(R.string.notif_downloading, title))
            .setContentText(progressText)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPct.coerceAtLeast(0), progressPct < 0)
            .addAction(0, applicationContext.getString(R.string.btn_pause), pauseIntent)
            .addAction(0, applicationContext.getString(R.string.btn_cancel), cancelIntent)
            .build()
    }

    private fun postCompletionNotification(notifId: Int, title: String, success: Boolean) {
        val openIntent = PendingIntent.getActivity(
            applicationContext,
            notifId + 10,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (success) {
            applicationContext.getString(R.string.notif_completed, title)
        } else {
            applicationContext.getString(R.string.notif_failed, title)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(
                if (success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error
            )
            .setContentTitle("MediaGrab")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(notifId + 1000, notification)
    }
}
