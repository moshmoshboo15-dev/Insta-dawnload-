package com.example.extractor

import android.net.Uri
import com.example.model.ExtractError
import com.example.model.ExtractResult
import com.example.model.MediaPlatform
import com.example.model.MediaPreview
import com.example.model.QualityOption

/**
 * Modular extractor for direct image or video file links.
 */
class DirectMediaExtractor(private val http: ExtractorHttpHelper) : MediaExtractor {

    override val platform: MediaPlatform = MediaPlatform.DIRECT

    override fun canHandle(url: String): Boolean {
        return http.inferMediaType(url, null) != null
    }

    override suspend fun extract(url: String): ExtractResult {
        val probed = http.probeMedia(url, ExtractorHttpHelper.DESKTOP_UA)
        val mediaType = http.inferMediaType(probed?.finalUrl ?: url, probed?.contentType)
            ?: return ExtractResult.Error(ExtractError.NotFound)

        val finalUrl = probed?.finalUrl ?: url
        val fileName = Uri.parse(finalUrl).lastPathSegment?.takeIf { it.isNotBlank() }
            ?: if (mediaType.name == "VIDEO") "ملف فيديو مباشر" else "ملف صورة مباشر"

        return ExtractResult.Success(
            MediaPreview(
                sourceUrl = url,
                title = fileName,
                platform = MediaPlatform.DIRECT,
                mediaType = mediaType,
                thumbnailUrl = finalUrl,
                qualityOptions = listOf(
                    QualityOption(
                        id = "direct_orig",
                        label = "الجودة الأصلية",
                        downloadUrl = finalUrl,
                        mediaType = mediaType,
                        fileSizeBytes = probed?.contentLength,
                        mimeType = probed?.contentType
                    )
                )
            )
        )
    }
}
