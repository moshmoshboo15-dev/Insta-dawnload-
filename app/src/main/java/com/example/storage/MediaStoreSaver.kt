package com.example.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.example.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object MediaStoreSaver {

    private const val APP_FOLDER = "MediaGrab"
    private const val MIN_SAFETY_BUFFER_BYTES = 16L * 1024L * 1024L // 16 MB buffer

    fun hasSufficientStorage(context: Context, requiredBytes: Long): Boolean {
        val checkBytes = (if (requiredBytes > 0L) requiredBytes else 0L) + MIN_SAFETY_BUFFER_BYTES
        val internalStat = StatFs(context.cacheDir.absolutePath)
        if (internalStat.availableBytes < checkBytes) return false

        val externalDir = context.getExternalFilesDir(null)
        if (externalDir != null) {
            val externalStat = StatFs(externalDir.absolutePath)
            if (externalStat.availableBytes < checkBytes) return false
        }
        return true
    }

    suspend fun saveFileToGallery(
        context: Context,
        sourceFile: File,
        fileName: String,
        mediaType: MediaType,
        mimeType: String?
    ): Result<Uri> = withContext(Dispatchers.IO) {
        FileInputStream(sourceFile).use { input ->
            saveStreamToGallery(context, input, fileName, mediaType, mimeType)
        }
    }

    suspend fun saveStreamToGallery(
        context: Context,
        inputStream: InputStream,
        fileName: String,
        mediaType: MediaType,
        mimeType: String?
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val resolvedMime = mimeType ?: if (mediaType == MediaType.VIDEO) "video/mp4" else "image/jpeg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = if (mediaType == MediaType.VIDEO) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }
                val baseDir = if (mediaType == MediaType.VIDEO) {
                    Environment.DIRECTORY_MOVIES
                } else {
                    Environment.DIRECTORY_PICTURES
                }

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, resolvedMime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "$baseDir/$APP_FOLDER")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val itemUri = resolver.insert(collection, values)
                    ?: return@withContext Result.failure(IllegalStateException("فشل إنشاء ملف في المعرض"))

                try {
                    resolver.openOutputStream(itemUri)?.use { out ->
                        inputStream.copyTo(out)
                    } ?: throw IllegalStateException("تعذر فتح مسار الكتابة في المعرض")

                    val publishValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(itemUri, publishValues, null, null)
                    Result.success(itemUri)
                } catch (e: Exception) {
                    resolver.delete(itemUri, null, null)
                    Result.failure(e)
                }
            } else {
                @Suppress("DEPRECATION")
                val rootDir = Environment.getExternalStoragePublicDirectory(
                    if (mediaType == MediaType.VIDEO) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                )
                val targetFolder = File(rootDir, APP_FOLDER).apply {
                    if (!exists()) mkdirs()
                }
                var outFile = File(targetFolder, fileName)
                if (outFile.exists()) {
                    val base = fileName.substringBeforeLast('.', fileName)
                    val ext = fileName.substringAfterLast('.', "")
                    outFile = File(targetFolder, "${base}_${System.currentTimeMillis()}.$ext")
                }

                outFile.outputStream().use { out ->
                    inputStream.copyTo(out)
                }

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(outFile.absolutePath),
                    arrayOf(resolvedMime),
                    null
                )
                Result.success(Uri.fromFile(outFile))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFromMediaStore(context: Context, uriString: String?): Boolean =
        withContext(Dispatchers.IO) {
            if (uriString.isNullOrBlank()) return@withContext false
            try {
                val uri = Uri.parse(uriString)
                when (uri.scheme) {
                    "content" -> context.contentResolver.delete(uri, null, null) > 0
                    "file" -> uri.path?.let { File(it).delete() } ?: false
                    else -> false
                }
            } catch (_: Exception) {
                false
            }
        }
}
