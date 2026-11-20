package com.example.extractor

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.model.ExtractError
import com.example.model.ExtractResult
import java.io.IOException

/**
 * Dispatches link analysis to modular platform extractors.
 * Adding or updating a platform requires editing only its dedicated extractor module.
 */
class ExtractorRegistry(
    private val context: Context,
    private val extractors: List<MediaExtractor>
) {

    suspend fun extract(rawInput: String): ExtractResult {
        val url = UrlUtilities.extractFirstUrl(rawInput)
            ?: return ExtractResult.Error(ExtractError.InvalidUrl)

        if (!UrlUtilities.isValidHttpUrl(url)) {
            return ExtractResult.Error(ExtractError.InvalidUrl)
        }

        if (!isNetworkAvailable()) {
            return ExtractResult.Error(ExtractError.NoInternet)
        }

        val matching = extractors.filter { it.canHandle(url) }
        for (extractor in matching) {
            try {
                val result = extractor.extract(url)
                if (result is ExtractResult.Success) {
                    return result
                }
                // If a dedicated social extractor explicitly reports PrivateOrDeleted or RateLimited, return immediately
                if (result is ExtractResult.Error &&
                    (result.error is ExtractError.PrivateOrDeleted || result.error is ExtractError.RateLimited) &&
                    extractor !is WebPageMediaExtractor
                ) {
                    return result
                }
            } catch (io: IOException) {
                if (!isNetworkAvailable()) {
                    return ExtractResult.Error(ExtractError.NoInternet)
                }
                return ExtractResult.Error(ExtractError.NetworkFailure(io.localizedMessage))
            } catch (e: Exception) {
                if (extractor == matching.last()) {
                    return ExtractResult.Error(ExtractError.NetworkFailure(e.localizedMessage))
                }
            }
        }

        return ExtractResult.Error(ExtractError.NotFound)
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
