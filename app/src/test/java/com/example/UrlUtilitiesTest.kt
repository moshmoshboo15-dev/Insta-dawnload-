package com.example

import com.example.extractor.DirectMediaExtractor
import com.example.extractor.ExtractorHttpHelper
import com.example.extractor.FacebookExtractor
import com.example.extractor.GoogleImagesExtractor
import com.example.extractor.InstagramExtractor
import com.example.extractor.UrlUtilities
import com.example.model.MediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UrlUtilitiesTest {

    private val httpHelper = ExtractorHttpHelper(OkHttpClient())

    @Test
    fun extractUrlsFromShareTextAndBatch() {
        val shared = "شاهد هذا المقطع الرائع! https://www.instagram.com/reel/ABC123/?igsh=xyz."
        assertEquals("https://www.instagram.com/reel/ABC123/?igsh=xyz", UrlUtilities.extractFirstUrl(shared))

        val batch = """
            https://example.com/photo.jpg
            https://fb.watch/xyz123/
            not-a-url
            https://example.com/photo.jpg
        """.trimIndent()
        val urls = UrlUtilities.extractAllUrls(batch)
        assertEquals(2, urls.size)
        assertEquals("https://example.com/photo.jpg", urls[0])
        assertEquals("https://fb.watch/xyz123/", urls[1])
    }

    @Test
    fun sanitizeFileNameProducesValidExtension() {
        val name = UrlUtilities.sanitizeFileName("فيديو جديد: 2026!", MediaType.VIDEO, "video/mp4")
        assertTrue(name.endsWith(".mp4"))
        assertFalse(name.contains(":"))
    }

    @Test
    fun platformExtractorsMatchCorrectDomains() {
        val ig = InstagramExtractor(httpHelper)
        val fb = FacebookExtractor(httpHelper)
        val google = GoogleImagesExtractor(httpHelper)
        val direct = DirectMediaExtractor(httpHelper)

        assertTrue(ig.canHandle("https://www.instagram.com/reel/C8abc123/"))
        assertFalse(ig.canHandle("https://www.facebook.com/watch/?v=123"))

        assertTrue(fb.canHandle("https://fb.watch/abc123/"))
        assertTrue(fb.canHandle("https://m.facebook.com/story.php?id=1"))

        assertTrue(google.canHandle("https://www.google.com/imgres?imgurl=https://site.com/pic.jpg"))
        assertTrue(direct.canHandle("https://cdn.site.com/video.mp4?token=123"))
    }
}
