package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.DownloadEntity
import com.example.model.DownloadStatus
import com.example.ui.components.DownloadItemCard
import com.example.ui.viewmodel.HistoryFilter
import com.example.ui.viewmodel.HistoryViewModel

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var deleteTarget by remember { mutableStateOf<DownloadEntity?>(null) }

    fun openMedia(entity: DownloadEntity) {
        val uriStr = entity.savedUri ?: return
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.parse(uriStr),
                    entity.mimeType ?: if (entity.mediaType == "VIDEO") "video/*" else "image/*"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, entity.title))
        } catch (_: Exception) {
            onShowMessage("تعذر فتح الملف؛ تأكد من وجود عارض وسائط مثبت")
        }
    }

    fun shareMedia(entity: DownloadEntity) {
        val uriStr = entity.savedUri ?: return
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = entity.mimeType ?: if (entity.mediaType == "VIDEO") "video/*" else "image/*"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(uriStr))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.btn_share)))
        } catch (_: Exception) {
            onShowMessage("تعذر مشاركة الملف")
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (downloads.any {
                        it.statusEnum == DownloadStatus.COMPLETED ||
                            it.statusEnum == DownloadStatus.FAILED ||
                            it.statusEnum == DownloadStatus.CANCELLED
                    }
                ) {
                    IconButton(
                        onClick = viewModel::clearFinished,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = "مسح السجل المكتمل",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == HistoryFilter.ALL,
                    onClick = { viewModel.setFilter(HistoryFilter.ALL) },
                    label = { Text(stringResource(R.string.filter_all)) },
                    modifier = Modifier.testTag("filter_all")
                )
                FilterChip(
                    selected = selectedFilter == HistoryFilter.ACTIVE,
                    onClick = { viewModel.setFilter(HistoryFilter.ACTIVE) },
                    label = { Text(stringResource(R.string.filter_active)) },
                    modifier = Modifier.testTag("filter_active")
                )
                FilterChip(
                    selected = selectedFilter == HistoryFilter.COMPLETED,
                    onClick = { viewModel.setFilter(HistoryFilter.COMPLETED) },
                    label = { Text(stringResource(R.string.filter_completed)) },
                    modifier = Modifier.testTag("filter_completed")
                )
                FilterChip(
                    selected = selectedFilter == HistoryFilter.PAUSED,
                    onClick = { viewModel.setFilter(HistoryFilter.PAUSED) },
                    label = { Text(stringResource(R.string.filter_paused)) },
                    modifier = Modifier.testTag("filter_paused")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (downloads.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.history_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.history_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(downloads, key = { it.id }) { item ->
                        DownloadItemCard(
                            item = item,
                            onPause = { viewModel.pause(item.id) },
                            onResume = { viewModel.resume(item.id) },
                            onCancel = { viewModel.cancel(item.id) },
                            onOpen = { openMedia(item) },
                            onShare = { shareMedia(item) },
                            onDelete = { deleteTarget = item }
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف التنزيل") },
            text = {
                Text(
                    if (target.statusEnum == DownloadStatus.COMPLETED && target.savedUri != null) {
                        "هل تريد حذف العنصر من السجل فقط أم حذفه من المعرض أيضاً؟"
                    } else {
                        "هل تريد إزالة هذا التنزيل؟"
                    }
                )
            },
            confirmButton = {
                if (target.statusEnum == DownloadStatus.COMPLETED && target.savedUri != null) {
                    TextButton(
                        onClick = {
                            viewModel.delete(target, deleteFileFromDevice = true)
                            deleteTarget = null
                        },
                        modifier = Modifier.testTag("confirm_delete_device")
                    ) {
                        Text("حذف من السجل والجهاز", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(
                        onClick = {
                            viewModel.delete(target, deleteFileFromDevice = false)
                            deleteTarget = null
                        },
                        modifier = Modifier.testTag("confirm_delete_record")
                    ) {
                        Text("حذف", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                Row {
                    if (target.statusEnum == DownloadStatus.COMPLETED && target.savedUri != null) {
                        TextButton(
                            onClick = {
                                viewModel.delete(target, deleteFileFromDevice = false)
                                deleteTarget = null
                            }
                        ) {
                            Text("من السجل فقط")
                        }
                    }
                    TextButton(onClick = { deleteTarget = null }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            }
        )
    }
}
