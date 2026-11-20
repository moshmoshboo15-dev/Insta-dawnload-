package com.example

import android.app.Application
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import com.example.data.local.AppDatabase
import com.example.data.local.UserPreferences
import com.example.data.repository.DownloadRepository
import com.example.extractor.DirectMediaExtractor
import com.example.extractor.ExtractorHttpHelper
import com.example.extractor.ExtractorRegistry
import com.example.extractor.FacebookExtractor
import com.example.extractor.GoogleImagesExtractor
import com.example.extractor.InstagramExtractor
import com.example.extractor.WebPageMediaExtractor
import com.example.storage.WhatsAppStatusRepository
import com.example.worker.MediaDownloadWorker
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MediaGrabApp : Application(), ImageLoaderFactory {

    lateinit var okHttpClient: OkHttpClient
        private set
    lateinit var database: AppDatabase
        private set
    lateinit var userPreferences: UserPreferences
        private set
    lateinit var extractorRegistry: ExtractorRegistry
        private set
    lateinit var downloadRepository: DownloadRepository
        private set
    lateinit var whatsAppStatusRepository: WhatsAppStatusRepository
        private set

    override fun onCreate() {
        super.onCreate()
        MediaDownloadWorker.ensureNotificationChannel(this)

        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        database = AppDatabase.getInstance(this)
        userPreferences = UserPreferences(this)

        val httpHelper = ExtractorHttpHelper(okHttpClient)
        extractorRegistry = ExtractorRegistry(
            context = this,
            extractors = listOf(
                InstagramExtractor(httpHelper),
                FacebookExtractor(httpHelper),
                GoogleImagesExtractor(httpHelper),
                DirectMediaExtractor(httpHelper),
                WebPageMediaExtractor(httpHelper)
            )
        )

        downloadRepository = DownloadRepository(this, database.downloadDao())
        whatsAppStatusRepository = WhatsAppStatusRepository(this, database.downloadDao())
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .components {
                add(LocalVideoThumbnailFetcher.Factory())
            }
            .crossfade(true)
            .build()
    }
}

private class LocalVideoThumbnailFetcher(
    private val uri: Uri,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(options.context, uri)
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            DrawableResult(
                drawable = BitmapDrawable(options.context.resources, bitmap),
                isSampled = true,
                dataSource = DataSource.DISK
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != "content" && data.scheme != "file") return null
            val path = data.toString().lowercase()
            val mime = options.context.contentResolver.getType(data)?.lowercase()
            val isVideo = mime?.startsWith("video/") == true ||
                path.endsWith(".mp4") || path.endsWith(".3gp") || path.endsWith(".mkv")
            return if (isVideo) LocalVideoThumbnailFetcher(data, options) else null
        }
    }
}
