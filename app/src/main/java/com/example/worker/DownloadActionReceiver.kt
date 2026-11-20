package com.example.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.MediaGrabApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DownloadActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PAUSE = "com.example.mediagrab.ACTION_PAUSE"
        const val ACTION_CANCEL = "com.example.mediagrab.ACTION_CANCEL"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return
        val app = context.applicationContext as? MediaGrabApp ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_PAUSE -> app.downloadRepository.pauseDownload(downloadId)
                    ACTION_CANCEL -> app.downloadRepository.cancelDownload(downloadId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
