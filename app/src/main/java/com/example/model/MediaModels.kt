package com.example.model

enum class MediaPlatform(val displayNameAr: String) {
    INSTAGRAM("إنستغرام"),
    FACEBOOK("فيسبوك"),
    GOOGLE_IMAGES("صور Google"),
    DIRECT("رابط مباشر"),
    WEB_PAGE("صفحة ويب"),
    WHATSAPP("حالات واتساب")
}

enum class MediaType(val displayNameAr: String) {
    VIDEO("فيديو"),
    IMAGE("صورة")
}

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

data class QualityOption(
    val id: String,
    val label: String,
    val downloadUrl: String,
    val mediaType: MediaType,
    val fileSizeBytes: Long? = null,
    val mimeType: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

data class MediaPreview(
    val sourceUrl: String,
    val title: String,
    val platform: MediaPlatform,
    val mediaType: MediaType,
    val thumbnailUrl: String?,
    val qualityOptions: List<QualityOption>,
    val author: String? = null
)

sealed class ExtractError {
    data object InvalidUrl : ExtractError()
    data object PrivateOrDeleted : ExtractError()
    data object NoInternet : ExtractError()
    data object NotFound : ExtractError()
    data object RateLimited : ExtractError()
    data class NetworkFailure(val detail: String?) : ExtractError()
}

sealed class ExtractResult {
    data class Success(val preview: MediaPreview) : ExtractResult()
    data class Error(val error: ExtractError) : ExtractResult()
}

data class WhatsAppStatusItem(
    val uriString: String,
    val name: String,
    val mediaType: MediaType,
    val sizeBytes: Long,
    val lastModified: Long,
    val mimeType: String,
    val isSaved: Boolean = false
)
