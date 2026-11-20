package com.example.extractor

import com.example.model.ExtractError
import com.example.model.ExtractResult
import com.example.model.MediaPlatform
import com.example.model.MediaPreview
import com.example.model.MediaType
import com.example.model.QualityOption
import java.net.URI

/**
 * Fallback modular extractor for any general web page:
 * extracts OpenGraph media, HTML5 <video>/<source>, and high-res <img> / srcset candidates.
 */
class WebPageMediaExtractor(private val http: ExtractorHttpHelper) : MediaExtractor {

    override val platform: MediaPlatform = MediaPlatform.WEB_PAGE

    override fun canHandle(url: String): Boolean = UrlUtilities.isValidHttpUrl(url)

    override suspend fun extract(url: String): ExtractResult {
        // First check if the URL itself redirects to or serves raw image/video bytes
        val headProbe = http.probeMedia(url, ExtractorHttpHelper.DESKTOP_UA)
        val directType = http.inferMediaType(headProbe?.finalUrl ?: url, headProbe?.contentType)
        if (directType != null && headProbe?.contentType?.contains("html") != true) {
            val finalUrl = headProbe?.finalUrl ?: url
            return ExtractResult.Success(
                MediaPreview(
                    sourceUrl = url,
                    title = "ملف وسائط مباشر",
                    platform = MediaPlatform.DIRECT,
                    mediaType = directType,
                    thumbnailUrl = finalUrl,
                    qualityOptions = listOf(
                        QualityOption(
                            id = "direct_probe",
                            label = "الجودة الأصلية",
                            downloadUrl = finalUrl,
                            mediaType = directType,
                            fileSizeBytes = headProbe?.contentLength,
                            mimeType = headProbe?.contentType
                        )
                    )
                )
            )
        }

        val (code, html) = try {
            http.fetchText(url, ExtractorHttpHelper.DESKTOP_UA)
        } catch (e: Exception) {
            return ExtractResult.Error(ExtractError.NetworkFailure(e.localizedMessage))
        }

        if (code == 401 || code == 403 || code == 404) {
            return ExtractResult.Error(ExtractError.PrivateOrDeleted)
        }
        if (code == 429) {
            return ExtractResult.Error(ExtractError.RateLimited)
        }
        if (html.isNullOrBlank()) {
            return ExtractResult.Error(ExtractError.NotFound)
        }

        val baseUri = try {
            URI(url)
        } catch (_: Exception) {
            null
        }

        fun resolve(candidate: String?): String? {
            if (candidate.isNullOrBlank()) return null
            val cleaned = http.decodeHtmlEntities(candidate).trim()
            if (cleaned.startsWith("data:", ignoreCase = true)) return null
            return try {
                if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) cleaned
                else baseUri?.resolve(cleaned)?.toString()
            } catch (_: Exception) {
                null
            }
        }

        val videoCandidates = mutableListOf<String>()
        val imageCandidates = mutableListOf<String>()

        resolve(http.extractMetaContent(html, "og:video"))?.let { videoCandidates.add(it) }
        resolve(http.extractMetaContent(html, "og:video:secure_url"))?.let { videoCandidates.add(it) }
        resolve(http.extractMetaContent(html, "twitter:player:stream"))?.let { videoCandidates.add(it) }

        Regex("""<(?:video|source)[^>]+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .forEach { match ->
                resolve(match.groupValues[1])?.let { videoCandidates.add(it) }
            }

        resolve(http.extractMetaContent(html, "og:image"))?.let { imageCandidates.add(it) }
        resolve(http.extractMetaContent(html, "twitter:image"))?.let { imageCandidates.add(it) }

        // Parse srcset for highest resolution image
        Regex("""srcset\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .take(10)
            .forEach { match ->
                val bestFromSrcset = match.groupValues[1]
                    .split(',')
                    .mapNotNull { entry ->
                        val parts = entry.trim().split(Regex("\\s+"))
                        val u = resolve(parts.firstOrNull()) ?: return@mapNotNull null
                        val weight = parts.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull() ?: 1
                        u to weight
                    }
                    .maxByOrNull { it.second }
                    ?.first
                if (bestFromSrcset != null) imageCandidates.add(bestFromSrcset)
            }

        Regex("""<img[^>]+src\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .findAll(html)
            .take(12)
            .forEach { match ->
                val resolved = resolve(match.groupValues[1])
                if (resolved != null && !resolved.contains("favicon", ignoreCase = true) &&
                    !resolved.endsWith(".svg", ignoreCase = true)
                ) {
                    imageCandidates.add(resolved)
                }
            }

        val options = mutableListOf<QualityOption>()
        videoCandidates.distinct().take(4).forEachIndexed { idx, vUrl ->
            val probed = http.probeMedia(vUrl, ExtractorHttpHelper.DESKTOP_UA, url)
            options.add(
                QualityOption(
                    id = "web_vid_$idx",
                    label = if (idx == 0) "فيديو (الأفضل)" else "فيديو ${idx + 1}",
                    downloadUrl = probed?.finalUrl ?: vUrl,
                    mediaType = MediaType.VIDEO,
                    fileSizeBytes = probed?.contentLength,
                    mimeType = probed?.contentType ?: "video/mp4"
                )
            )
        }

        imageCandidates.distinct().take(5).forEachIndexed { idx, iUrl ->
            val probed = http.probeMedia(iUrl, ExtractorHttpHelper.DESKTOP_UA, url)
            options.add(
                QualityOption(
                    id = "web_img_$idx",
                    label = if (idx == 0 && videoCandidates.isEmpty()) "صورة عالية الدقة" else "صورة ${idx + 1}",
                    downloadUrl = probed?.finalUrl ?: iUrl,
                    mediaType = MediaType.IMAGE,
                    fileSizeBytes = probed?.contentLength,
                    mimeType = probed?.contentType ?: "image/jpeg"
                )
            )
        }

        if (options.isEmpty()) {
            return ExtractResult.Error(ExtractError.NotFound)
        }

        val pageTitle = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.getOrNull(1)?.let { http.decodeHtmlEntities(it).trim() }
            ?.takeIf { it.isNotBlank() }
            ?: http.extractMetaContent(html, "og:title")
            ?: "وسائط من صفحة ويب"

        val primaryType = options.first().mediaType
        val thumb = imageCandidates.firstOrNull() ?: options.first().downloadUrl

        return ExtractResult.Success(
            MediaPreview(
                sourceUrl = url,
                title = pageTitle,
                platform = MediaPlatform.WEB_PAGE,
                mediaType = primaryType,
                thumbnailUrl = thumb,
                qualityOptions = options
            )
        )
    }
}
