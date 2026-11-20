package com.example.extractor

import android.net.Uri
import android.webkit.MimeTypeMap
import com.example.model.MediaType
import java.util.Locale

object UrlUtilities {

    private val urlRegex = Regex(
        """https?://[^\s<>"'\]\)]+""",
        RegexOption.IGNORE_CASE
    )

    fun extractFirstUrl(rawText: String?): String? {
        if (rawText.isNullOrBlank()) return null
        val match = urlRegex.find(rawText.trim()) ?: return null
        return cleanTrailingPunctuation(match.value)
    }

    fun extractAllUrls(rawText: String?): List<String> {
        if (rawText.isNullOrBlank()) return emptyList()
        return urlRegex.findAll(rawText)
            .map { cleanTrailingPunctuation(it.value) }
            .filter { isValidHttpUrl(it) }
            .distinct()
            .toList()
    }

    fun isValidHttpUrl(url: String): Boolean {
        return try {
            val uri = Uri.parse(url.trim())
            val scheme = uri.scheme?.lowercase(Locale.US)
            (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    private fun cleanTrailingPunctuation(url: String): String {
        var cleaned = url.trim()
        while (cleaned.endsWith(".") || cleaned.endsWith(",") || cleaned.endsWith(";") || cleaned.endsWith("!")) {
            cleaned = cleaned.dropLast(1)
        }
        return cleaned
    }

    fun sanitizeFileName(raw: String, mediaType: MediaType, mimeType: String? = null): String {
        val extFromMime = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        val defaultExt = if (mediaType == MediaType.VIDEO) "mp4" else "jpg"
        val ext = (extFromMime ?: defaultExt).lowercase(Locale.US)
        val cleanBase = raw
            .replace(Regex("[^a-zA-Z0-9\\u0600-\\u06FF._-]"), "_")
            .trim('_')
            .take(48)
            .ifBlank { "mediagrab_${System.currentTimeMillis()}" }
        return if (cleanBase.endsWith(".$ext", ignoreCase = true)) {
            cleanBase
        } else {
            val baseNoExt = cleanBase.substringBeforeLast('.', cleanBase)
            "${baseNoExt}.$ext"
        }
    }

    fun formatBytes(bytes: Long?): String {
        if (bytes == null || bytes <= 0L) return "غير معروف"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}
