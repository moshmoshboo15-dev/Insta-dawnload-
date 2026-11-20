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
 * Modular extractor for public Instagram posts, Reels, and Stories.
 */
class InstagramExtractor(private val http: ExtractorHttpHelper) : MediaExtractor {

    override val platform: MediaPlatform = MediaPlatform.INSTAGRAM

    override fun canHandle(url: String): Boolean {
        val host = Uri.parse(url).host?.lowercase(Locale.US) ?: return false
        return host == "instagram.com" || host.endsWith(".instagram.com") || host == "instagr.am"
    }

    override suspend fun extract(url: String): ExtractResult {
        val cleanUrl = url.substringBefore('?')
        val isStory = cleanUrl.contains("/stories/", ignoreCase = true)

        // 1) Try standard page fetch & embed page (for posts/reels/tv)
        val candidates = buildList {
            if (!isStory) {
                val base = cleanUrl.trimEnd('/')
                add("$base/embed/captioned/")
                add("$base/?__a=1&__d=dis")
            }
            add(url)
        }

        var sawPrivateOrLogin = false
        var sawRateLimit = false

        for (target in candidates) {
            val (code, body) = try {
                http.fetchText(
                    url = target,
                    userAgent = ExtractorHttpHelper.MOBILE_UA,
                    headers = mapOf("Referer" to "https://www.instagram.com/")
                )
            } catch (_: Exception) {
                continue
            }

            if (code == 429) {
                sawRateLimit = true
                continue
            }
            if (body.isNullOrBlank()) continue

            if (body.contains("This account is private", ignoreCase = true) ||
                body.contains("\"is_private\":true", ignoreCase = true) ||
                body.contains("Sorry, this page isn't available", ignoreCase = true)
            ) {
                sawPrivateOrLogin = true
            }

            val parsed = parseInstagramPayload(sourceUrl = url, payload = body)
            if (parsed != null) {
                return ExtractResult.Success(parsed)
            }

            if (body.contains("login_and_signup_page", ignoreCase = true) ||
                body.contains("/accounts/login/", ignoreCase = true)
            ) {
                sawPrivateOrLogin = true
            }
        }

        return when {
            sawRateLimit -> ExtractResult.Error(ExtractError.RateLimited)
            sawPrivateOrLogin -> ExtractResult.Error(ExtractError.PrivateOrDeleted)
            else -> ExtractResult.Error(ExtractError.NotFound)
        }
    }

    private suspend fun parseInstagramPayload(sourceUrl: String, payload: String): MediaPreview? {
        val videoUrls = mutableListOf<String>()
        val imageUrls = mutableListOf<String>()

        // OpenGraph tags
        http.extractMetaContent(payload, "og:video")?.let { videoUrls.add(it) }
        http.extractMetaContent(payload, "og:video:secure_url")?.let { videoUrls.add(it) }
        http.extractMetaContent(payload, "og:image")?.let { imageUrls.add(it) }

        // Embedded JSON video_url / display_url / video_versions
        Regex(""""video_url"\s*:\s*"(https?:[^"]+)"""").findAll(payload).forEach {
            videoUrls.add(http.decodeHtmlEntities(it.groupValues[1]))
        }
        Regex(""""video_versions"\s*:\s*\[\s*\{[^}]*"url"\s*:\s*"(https?:[^"]+)"""").findAll(payload).forEach {
            videoUrls.add(http.decodeHtmlEntities(it.groupValues[1]))
        }
        Regex(""""display_url"\s*:\s*"(https?:[^"]+)"""").findAll(payload).forEach {
            imageUrls.add(http.decodeHtmlEntities(it.groupValues[1]))
        }
        Regex("""<video[^>]+src=["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE).findAll(payload).forEach {
            videoUrls.add(http.decodeHtmlEntities(it.groupValues[1]))
        }

        val cleanVideos = videoUrls.map { it.trim() }.filter { it.startsWith("http") }.distinct()
        val cleanImages = imageUrls.map { it.trim() }.filter { it.startsWith("http") }.distinct()

        if (cleanVideos.isEmpty() && cleanImages.isEmpty()) return null

        val title = http.extractMetaContent(payload, "og:title")
            ?: "منشور إنستغرام"

        val options = mutableListOf<QualityOption>()
        cleanVideos.forEachIndexed { idx, vUrl ->
            val probed = http.probeMedia(vUrl, ExtractorHttpHelper.MOBILE_UA, "https://www.instagram.com/")
            options.add(
                QualityOption(
                    id = "ig_vid_$idx",
                    label = if (idx == 0) "فيديو HD" else "فيديو جودة ${idx + 1}",
                    downloadUrl = probed?.finalUrl ?: vUrl,
                    mediaType = MediaType.VIDEO,
                    fileSizeBytes = probed?.contentLength,
                    mimeType = probed?.contentType ?: "video/mp4"
                )
            )
        }
        if (options.isEmpty()) {
            cleanImages.forEachIndexed { idx, iUrl ->
                val probed = http.probeMedia(iUrl, ExtractorHttpHelper.MOBILE_UA, "https://www.instagram.com/")
                options.add(
                    QualityOption(
                        id = "ig_img_$idx",
                        label = if (idx == 0) "صورة عالية الدقة" else "صورة ${idx + 1}",
                        downloadUrl = probed?.finalUrl ?: iUrl,
                        mediaType = MediaType.IMAGE,
                        fileSizeBytes = probed?.contentLength,
                        mimeType = probed?.contentType ?: "image/jpeg"
                    )
                )
            }
        }

        val primaryType = if (cleanVideos.isNotEmpty()) MediaType.VIDEO else MediaType.IMAGE
        val thumb = cleanImages.firstOrNull() ?: cleanVideos.firstOrNull()

        return MediaPreview(
            sourceUrl = sourceUrl,
            title = title,
            platform = MediaPlatform.INSTAGRAM,
            mediaType = primaryType,
            thumbnailUrl = thumb,
            qualityOptions = options
        )
    }
}
