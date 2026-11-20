package com.example.extractor

import android.net.Uri
import com.example.model.ExtractError
import com.example.model.ExtractResult
import com.example.model.MediaPlatform
import com.example.model.MediaPreview
import com.example.model.MediaType
import com.example.model.QualityOption
import java.util.Locale

/**
 * Modular extractor for public Facebook videos, Reels, and photos.
 */
class FacebookExtractor(private val http: ExtractorHttpHelper) : MediaExtractor {

    override val platform: MediaPlatform = MediaPlatform.FACEBOOK

    override fun canHandle(url: String): Boolean {
        val host = Uri.parse(url).host?.lowercase(Locale.US) ?: return false
        return host == "facebook.com" || host.endsWith(".facebook.com") ||
            host == "fb.watch" || host == "fb.com" || host.endsWith(".fb.com")
    }

    override suspend fun extract(url: String): ExtractResult {
        val candidateUrls = listOf(
            url,
            url.replace("://m.facebook.com", "://www.facebook.com")
                .replace("://mbasic.facebook.com", "://www.facebook.com")
        ).distinct()

        var sawPrivate = false
        var sawRateLimit = false

        for (target in candidateUrls) {
            val (code, body) = try {
                http.fetchText(
                    url = target,
                    userAgent = ExtractorHttpHelper.DESKTOP_UA,
                    headers = mapOf("Sec-Fetch-Mode" to "navigate")
                )
            } catch (_: Exception) {
                continue
            }

            if (code == 429) {
                sawRateLimit = true
                continue
            }
            if (body.isNullOrBlank()) continue

            if (body.contains("This content isn't available", ignoreCase = true) ||
                body.contains("must log in to continue", ignoreCase = true) ||
                body.contains("private_group", ignoreCase = true)
            ) {
                sawPrivate = true
            }

            val preview = parseFacebookHtml(sourceUrl = url, html = body)
            if (preview != null) {
                return ExtractResult.Success(preview)
            }
        }

        return when {
            sawRateLimit -> ExtractResult.Error(ExtractError.RateLimited)
            sawPrivate -> ExtractResult.Error(ExtractError.PrivateOrDeleted)
            else -> ExtractResult.Error(ExtractError.NotFound)
        }
    }

    private suspend fun parseFacebookHtml(sourceUrl: String, html: String): MediaPreview? {
        val hdVideoRegex = Regex(""""(?:playable_url_quality_hd|browser_native_hd_url|hd_src)"\s*:\s*"(https?:[^"]+)"""")
        val sdVideoRegex = Regex(""""(?:playable_url|browser_native_sd_url|sd_src)"\s*:\s*"(https?:[^"]+)"""")

        val hdUrl = hdVideoRegex.find(html)?.groupValues?.getOrNull(1)?.let { http.decodeHtmlEntities(it) }
        val sdUrl = sdVideoRegex.find(html)?.groupValues?.getOrNull(1)?.let { http.decodeHtmlEntities(it) }
        val ogVideo = http.extractMetaContent(html, "og:video") ?: http.extractMetaContent(html, "og:video:url")
        val ogImage = http.extractMetaContent(html, "og:image")

        val options = mutableListOf<QualityOption>()

        if (!hdUrl.isNullOrBlank()) {
            val probed = http.probeMedia(hdUrl, ExtractorHttpHelper.DESKTOP_UA, "https://www.facebook.com/")
            options.add(
                QualityOption(
                    id = "fb_hd",
                    label = "HD 1080p / عالية",
                    downloadUrl = probed?.finalUrl ?: hdUrl,
                    mediaType = MediaType.VIDEO,
                    fileSizeBytes = probed?.contentLength,
                    mimeType = probed?.contentType ?: "video/mp4"
                )
            )
        }

        val fallbackSd = sdUrl ?: ogVideo
        if (!fallbackSd.isNullOrBlank() && fallbackSd != hdUrl) {
            val probed = http.probeMedia(fallbackSd, ExtractorHttpHelper.DESKTOP_UA, "https://www.facebook.com/")
            options.add(
                QualityOption(
                    id = "fb_sd",
                    label = if (hdUrl != null) "SD قياسية" else "فيديو",
                    downloadUrl = probed?.finalUrl ?: fallbackSd,
                    mediaType = MediaType.VIDEO,
                    fileSizeBytes = probed?.contentLength,
                    mimeType = probed?.contentType ?: "video/mp4"
                )
            )
        }

        if (options.isEmpty() && !ogImage.isNullOrBlank()) {
            val probed = http.probeMedia(ogImage, ExtractorHttpHelper.DESKTOP_UA, "https://www.facebook.com/")
            options.add(
                QualityOption(
                    id = "fb_img",
                    label = "صورة عالية الدقة",
                    downloadUrl = probed?.finalUrl ?: ogImage,
                    mediaType = MediaType.IMAGE,
                    fileSizeBytes = probed?.contentLength,
                    mimeType = probed?.contentType ?: "image/jpeg"
                )
            )
        }

        if (options.isEmpty()) return null

        val title = http.extractMetaContent(html, "og:title")
            ?: http.extractMetaContent(html, "twitter:title")
            ?: "وسائط فيسبوك"

        val mediaType = options.first().mediaType
        return MediaPreview(
            sourceUrl = sourceUrl,
            title = title,
            platform = MediaPlatform.FACEBOOK,
            mediaType = mediaType,
            thumbnailUrl = ogImage ?: options.first().downloadUrl,
            qualityOptions = options
        )
    }
}
