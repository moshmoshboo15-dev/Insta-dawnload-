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
 * Modular extractor for Google Images links (imgres, image search results, encrypted-tbn0)
 * extracting the highest available original resolution image.
 */
class GoogleImagesExtractor(private val http: ExtractorHttpHelper) : MediaExtractor {

    override val platform: MediaPlatform = MediaPlatform.GOOGLE_IMAGES

    override fun canHandle(url: String): Boolean {
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase(Locale.US) ?: return false
        val isGoogleHost = host.contains("google.") || host == "goo.gl" || host == "share.google"
        val isGstatic = host.endsWith("gstatic.com")
        if (isGstatic && url.contains("tbn", ignoreCase = true)) return true
        if (!isGoogleHost) return false

        return uri.path?.contains("/imgres", ignoreCase = true) == true ||
            uri.getQueryParameter("imgurl") != null ||
            uri.getQueryParameter("tbm") == "isch" ||
            uri.getQueryParameter("udm") == "2" ||
            host.startsWith("images.google.")
    }

    override suspend fun extract(url: String): ExtractResult {
        val uri = Uri.parse(url)
        val directImgUrl = uri.getQueryParameter("imgurl")?.let { http.decodeUrlSafe(it) }
        val imgRefUrl = uri.getQueryParameter("imgrefurl")?.let { http.decodeUrlSafe(it) }

        val candidates = mutableListOf<QualityOption>()

        if (!directImgUrl.isNullOrBlank() && UrlUtilities.isValidHttpUrl(directImgUrl)) {
            val probed = http.probeMedia(directImgUrl, ExtractorHttpHelper.DESKTOP_UA, imgRefUrl)
            if (probed == null || probed.contentType?.startsWith("image/") == true ||
                http.inferMediaType(directImgUrl, probed.contentType) == MediaType.IMAGE
            ) {
                candidates.add(
                    QualityOption(
                        id = "google_orig",
                        label = "الدقة الأصلية الكاملة",
                        downloadUrl = probed?.finalUrl ?: directImgUrl,
                        mediaType = MediaType.IMAGE,
                        fileSizeBytes = probed?.contentLength,
                        mimeType = probed?.contentType ?: "image/jpeg"
                    )
                )
            }
        }

        // If imgurl wasn't in query params (e.g. Google Images search page or share link), fetch HTML and extract full-res URLs
        if (candidates.isEmpty()) {
            val (code, html) = try {
                http.fetchText(url, ExtractorHttpHelper.DESKTOP_UA)
            } catch (_: Exception) {
                return ExtractResult.Error(ExtractError.NetworkFailure(null))
            }

            if (code == 429) return ExtractResult.Error(ExtractError.RateLimited)

            if (!html.isNullOrBlank()) {
                val foundUrls = mutableListOf<String>()
                // Google Images JSON often stores full-res URLs as ["https://...",width,height]
                Regex("""\["(https?://[^"]+\.(?:jpg|jpeg|png|webp|gif)[^"]*)",\s*(\d+),\s*(\d+)\]""", RegexOption.IGNORE_CASE)
                    .findAll(html)
                    .sortedByDescending {
                        val w = it.groupValues[2].toIntOrNull() ?: 0
                        val h = it.groupValues[3].toIntOrNull() ?: 0
                        w * h
                    }
                    .take(5)
                    .forEach { match ->
                        foundUrls.add(http.decodeHtmlEntities(match.groupValues[1]))
                    }

                http.extractMetaContent(html, "og:image")?.let { foundUrls.add(it) }

                foundUrls.distinct().take(4).forEachIndexed { idx, foundUrl ->
                    if (!foundUrl.contains("encrypted-tbn0.gstatic.com") || foundUrls.size == 1) {
                        val probed = http.probeMedia(foundUrl, ExtractorHttpHelper.DESKTOP_UA)
                        candidates.add(
                            QualityOption(
                                id = "google_res_$idx",
                                label = if (idx == 0) "أعلى جودة متاحة" else "نسخة ${idx + 1}",
                                downloadUrl = probed?.finalUrl ?: foundUrl,
                                mediaType = MediaType.IMAGE,
                                fileSizeBytes = probed?.contentLength,
                                mimeType = probed?.contentType ?: "image/jpeg"
                            )
                        )
                    }
                }
            }
        }

        // Fallback for direct encrypted-tbn0 thumbnail URLs
        if (candidates.isEmpty() && url.contains("gstatic.com")) {
            val probed = http.probeMedia(url, ExtractorHttpHelper.DESKTOP_UA)
            candidates.add(
                QualityOption(
                    id = "google_tbn",
                    label = "صورة Google",
                    downloadUrl = probed?.finalUrl ?: url,
                    mediaType = MediaType.IMAGE,
                    fileSizeBytes = probed?.contentLength,
                    mimeType = probed?.contentType ?: "image/jpeg"
                )
            )
        }

        if (candidates.isEmpty()) {
            return ExtractResult.Error(ExtractError.NotFound)
        }

        return ExtractResult.Success(
            MediaPreview(
                sourceUrl = url,
                title = "صورة من نتائج Google",
                platform = MediaPlatform.GOOGLE_IMAGES,
                mediaType = MediaType.IMAGE,
                thumbnailUrl = candidates.first().downloadUrl,
                qualityOptions = candidates
            )
        )
    }
}
