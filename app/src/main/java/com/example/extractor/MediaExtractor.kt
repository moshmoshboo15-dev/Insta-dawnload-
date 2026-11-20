package com.example.extractor

import com.example.model.ExtractResult
import com.example.model.MediaPlatform
import com.example.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.Locale

interface MediaExtractor {
    val platform: MediaPlatform
    fun canHandle(url: String): Boolean
    suspend fun extract(url: String): ExtractResult
}

data class ProbedMedia(
    val finalUrl: String,
    val contentType: String?,
    val contentLength: Long?
)

class ExtractorHttpHelper(val client: OkHttpClient) {

    companion object {
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }

    suspend fun fetchText(
        url: String,
        userAgent: String = DESKTOP_UA,
        headers: Map<String, String> = emptyMap()
    ): Pair<Int, String?> = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9,ar;q=0.8")
        headers.forEach { (k, v) -> builder.header(k, v) }

        client.newCall(builder.build()).execute().use { response ->
            val body = response.body?.string()
            response.code to body
        }
    }

    suspend fun probeMedia(
        url: String,
        userAgent: String = DESKTOP_UA,
        referer: String? = null
    ): ProbedMedia? = withContext(Dispatchers.IO) {
        try {
            val headBuilder = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", userAgent)
            if (referer != null) headBuilder.header("Referer", referer)

            client.newCall(headBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    val ct = resp.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase(Locale.US)
                    val len = resp.header("Content-Length")?.toLongOrNull()
                    return@withContext ProbedMedia(resp.request.url.toString(), ct, len)
                }
            }

            // Fallback Range GET 0-0 for servers rejecting HEAD
            val getBuilder = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", userAgent)
                .header("Range", "bytes=0-0")
            if (referer != null) getBuilder.header("Referer", referer)

            client.newCall(getBuilder.build()).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 206) {
                    val ct = resp.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase(Locale.US)
                    val contentRange = resp.header("Content-Range")
                    val totalFromRange = contentRange?.substringAfterLast('/')?.toLongOrNull()
                    val len = totalFromRange ?: resp.header("Content-Length")?.toLongOrNull()
                    return@withContext ProbedMedia(resp.request.url.toString(), ct, len)
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun inferMediaType(url: String, contentType: String?): MediaType? {
        if (contentType != null) {
            if (contentType.startsWith("video/")) return MediaType.VIDEO
            if (contentType.startsWith("image/")) return MediaType.IMAGE
        }
        val cleanPath = url.substringBefore('?').substringBefore('#').lowercase(Locale.US)
        return when {
            cleanPath.endsWith(".mp4") || cleanPath.endsWith(".mov") ||
                cleanPath.endsWith(".webm") || cleanPath.endsWith(".mkv") ||
                cleanPath.endsWith(".m4v") || cleanPath.endsWith(".avi") -> MediaType.VIDEO
            cleanPath.endsWith(".jpg") || cleanPath.endsWith(".jpeg") ||
                cleanPath.endsWith(".png") || cleanPath.endsWith(".webp") ||
                cleanPath.endsWith(".gif") || cleanPath.endsWith(".bmp") -> MediaType.IMAGE
            else -> null
        }
    }

    fun extractMetaContent(html: String, propertyOrName: String): String? {
        val pattern = Regex(
            """<meta\s+[^>]*(?:property|name)\s*=\s*["']${Regex.escape(propertyOrName)}["'][^>]*content\s*=\s*["']([^"']+)["'][^>]*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val reversePattern = Regex(
            """<meta\s+[^>]*content\s*=\s*["']([^"']+)["'][^>]*(?:property|name)\s*=\s*["']${Regex.escape(propertyOrName)}["'][^>]*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val raw = pattern.find(html)?.groupValues?.getOrNull(1)
            ?: reversePattern.find(html)?.groupValues?.getOrNull(1)
        return raw?.let { decodeHtmlEntities(it).trim() }?.takeIf { it.isNotBlank() }
    }

    fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("\\u0026", "&")
            .replace("\\u002F", "/")
            .replace("\\/", "/")
    }

    fun decodeUrlSafe(value: String): String {
        return try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: Exception) {
            value
        }
    }
}
