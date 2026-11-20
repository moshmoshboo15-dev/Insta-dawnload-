package com.example.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.model.DownloadStatus
import com.example.model.MediaPlatform
import com.example.model.MediaType
import com.example.model.WhatsAppStatusItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

class WhatsAppStatusRepository(
    private val context: Context,
    private val downloadDao: DownloadDao
) {

    fun buildInitialUri(isBusiness: Boolean): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val docId = if (isBusiness) {
            "primary:Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses"
        } else {
            "primary:Android/media/com.whatsapp/WhatsApp/Media/.Statuses"
        }
        return DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            docId
        )
    }

    fun persistTreePermission(treeUri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        runCatching {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        }
    }

    fun hasValidPermission(treeUriString: String?): Boolean {
        if (treeUriString.isNullOrBlank()) return false
        val uri = Uri.parse(treeUriString)
        val hasPersisted = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        if (!hasPersisted) return false
        val doc = DocumentFile.fromTreeUri(context, uri)
        return doc != null && doc.exists() && doc.canRead()
    }

    suspend fun loadStatuses(treeUriString: String): List<WhatsAppStatusItem> = withContext(Dispatchers.IO) {
        val rootUri = Uri.parse(treeUriString)
        val dir = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext emptyList()
        if (!dir.exists() || !dir.canRead()) return@withContext emptyList()

        dir.listFiles()
            .asSequence()
            .filter { file ->
                file.isFile &&
                    !file.name.isNullOrBlank() &&
                    !file.name.equals(".nomedia", ignoreCase = true)
            }
            .mapNotNull { file ->
                val name = file.name ?: return@mapNotNull null
                val lower = name.lowercase(Locale.US)
                val mime = file.type?.lowercase(Locale.US)
                val mediaType = when {
                    mime?.startsWith("video/") == true ||
                        lower.endsWith(".mp4") || lower.endsWith(".3gp") || lower.endsWith(".mkv") -> MediaType.VIDEO
                    mime?.startsWith("image/") == true ||
                        lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                        lower.endsWith(".png") || lower.endsWith(".webp") -> MediaType.IMAGE
                    else -> null
                } ?: return@mapNotNull null

                WhatsAppStatusItem(
                    uriString = file.uri.toString(),
                    name = name,
                    mediaType = mediaType,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    mimeType = mime ?: if (mediaType == MediaType.VIDEO) "video/mp4" else "image/jpeg"
                )
            }
            .sortedByDescending { it.lastModified }
            .toList()
    }

    suspend fun saveStatusToGallery(item: WhatsAppStatusItem): Result<Uri> = withContext(Dispatchers.IO) {
        val uri = Uri.parse(item.uriString)
        val input = context.contentResolver.openInputStream(uri)
            ?: return@withContext Result.failure(IllegalStateException("تعذر قراءة ملف الحالة"))

        val saveResult = input.use { stream ->
            MediaStoreSaver.saveStreamToGallery(
                context = context,
                inputStream = stream,
                fileName = "WA_Status_${item.name}",
                mediaType = item.mediaType,
                mimeType = item.mimeType
            )
        }

        saveResult.onSuccess { savedUri ->
            downloadDao.upsert(
                DownloadEntity(
                    id = UUID.randomUUID().toString(),
                    title = "حالة واتساب • ${item.name}",
                    fileName = "WA_Status_${item.name}",
                    sourceUrl = item.uriString,
                    downloadUrl = item.uriString,
                    thumbnailUrl = savedUri.toString(),
                    platform = MediaPlatform.WHATSAPP.name,
                    mediaType = item.mediaType.name,
                    qualityLabel = "الأصلية",
                    mimeType = item.mimeType,
                    status = DownloadStatus.COMPLETED.name,
                    downloadedBytes = item.sizeBytes,
                    totalBytes = item.sizeBytes,
                    savedUri = savedUri.toString()
                )
            )
        }
        saveResult
    }
}
