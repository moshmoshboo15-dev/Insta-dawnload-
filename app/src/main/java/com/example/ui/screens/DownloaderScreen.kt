package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.components.BatchPreviewCard
import com.example.ui.components.DownloadItemCard
import com.example.ui.components.ErrorBannerCard
import com.example.ui.components.MediaPreviewCard
import com.example.ui.theme.FacebookAccent
import com.example.ui.theme.GoogleAccent
import com.example.ui.theme.InstagramAccent
import com.example.ui.viewmodel.AnalysisState
import com.example.ui.viewmodel.DownloaderViewModel

@Composable
fun DownloaderScreen(
    viewModel: DownloaderViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    var pendingDownloadAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        pendingDownloadAction?.invoke()
        pendingDownloadAction = null
    }

    val legacyStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingDownloadAction?.invoke()
        } else {
            onShowMessage("يرجى منح صلاحية التخزين لحفظ الملفات على أندرويد 9")
        }
        pendingDownloadAction = null
    }

    fun executeWithPermissions(action: () -> Unit) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val hasStorage = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasStorage) {
                pendingDownloadAction = action
                legacyStorageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotif = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasNotif) {
                pendingDownloadAction = action
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        action()
    }

    LaunchedEffect(uiState.messageToast) {
        uiState.messageToast?.let { msg ->
            onShowMessage(msg)
            viewModel.clearToast()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.app_tagline),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Clipboard Banner if detected while input wasn't empty
            item {
                AnimatedVisibility(visible = uiState.clipboardBannerUrl != null) {
                    val clipUrl = uiState.clipboardBannerUrl.orEmpty()
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.clipboard_detected_banner),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = clipUrl,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = {
                                    viewModel.onInputChanged(clipUrl)
                                    viewModel.dismissClipboardBanner()
                                    viewModel.analyzeCurrentInput()
                                },
                                modifier = Modifier.testTag("use_clipboard_button")
                            ) {
                                Text(stringResource(R.string.btn_analyze))
                            }
                        }
                    }
                }
            }

            // URL Input & Mode Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        OutlinedTextField(
                            value = uiState.inputText,
                            onValueChange = viewModel::onInputChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("url_input_field"),
                            label = {
                                Text(
                                    text = if (uiState.isBatchMode) {
                                        stringResource(R.string.batch_links_hint)
                                    } else {
                                        stringResource(R.string.paste_link_hint)
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Link,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (uiState.inputText.isNotEmpty()) {
                                        IconButton(
                                            onClick = viewModel::clearInput,
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("clear_url_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Clear,
                                                contentDescription = stringResource(R.string.btn_clear)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            val clipText = clipboardManager.getText()?.text
                                            viewModel.pasteUrlDirectly(clipText)
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("paste_url_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentPaste,
                                            contentDescription = stringResource(R.string.btn_paste),
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            },
                            singleLine = !uiState.isBatchMode,
                            minLines = if (uiState.isBatchMode) 3 else 1,
                            maxLines = if (uiState.isBatchMode) 6 else 2,
                            keyboardOptions = KeyboardOptions(
                                imeAction = if (uiState.isBatchMode) ImeAction.Default else ImeAction.Search
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    keyboardController?.hide()
                                    viewModel.analyzeCurrentInput()
                                }
                            ),
                            shape = MaterialTheme.shapes.medium,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.batch_mode_label),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Switch(
                                checked = uiState.isBatchMode,
                                onCheckedChange = viewModel::toggleBatchMode,
                                modifier = Modifier.testTag("batch_mode_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val isLoading = uiState.analysisState is AnalysisState.Loading
                        Button(
                            onClick = {
                                keyboardController?.hide()
                                viewModel.analyzeCurrentInput()
                            },
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("analyze_button"),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.btn_analyzing),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.btn_analyze),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }

            // Analysis Result / Error / Supported Platforms
            item {
                AnimatedContent(
                    targetState = uiState.analysisState,
                    label = "analysis_state"
                ) { state ->
                    when (state) {
                        is AnalysisState.Idle -> SupportedPlatformsSection()
                        is AnalysisState.Loading -> {
                            // Loading state shown on button cleanly
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        is AnalysisState.SinglePreview -> {
                            MediaPreviewCard(
                                preview = state.preview,
                                selectedQuality = state.selectedQuality,
                                onSelectQuality = viewModel::selectQuality,
                                onDownloadClick = {
                                    executeWithPermissions { viewModel.startSingleDownload() }
                                }
                            )
                        }

                        is AnalysisState.BatchPreview -> {
                            BatchPreviewCard(
                                items = state.items,
                                onToggleSelection = viewModel::toggleBatchItemSelection,
                                onDownloadAllClick = {
                                    executeWithPermissions { viewModel.startBatchDownload() }
                                }
                            )
                        }

                        is AnalysisState.Error -> {
                            ErrorBannerCard(
                                error = state.error,
                                onRetry = viewModel::analyzeCurrentInput
                            )
                        }
                    }
                }
            }

            // Active Downloads Queue Section
            if (activeDownloads.isNotEmpty()) {
                item {
                    Text(
                        text = "${stringResource(R.string.active_downloads_title)} (${activeDownloads.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                items(activeDownloads, key = { it.id }) { download ->
                    DownloadItemCard(
                        item = download,
                        onPause = { viewModel.pauseDownload(download.id) },
                        onResume = { viewModel.resumeDownload(download.id) },
                        onCancel = { viewModel.cancelDownload(download.id) },
                        onOpen = {},
                        onShare = {},
                        onDelete = { viewModel.cancelDownload(download.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SupportedPlatformsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.supported_platforms_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PlatformTile(
                title = stringResource(R.string.platform_instagram),
                subtitle = "Reels • Stories • منشورات",
                icon = Icons.Rounded.PhotoCamera,
                accent = InstagramAccent
            )
            PlatformTile(
                title = stringResource(R.string.platform_facebook),
                subtitle = "فيديو HD • صور عامة",
                icon = Icons.Rounded.SmartDisplay,
                accent = FacebookAccent
            )
            PlatformTile(
                title = stringResource(R.string.platform_google_images),
                subtitle = "أعلى دقة أصلية",
                icon = Icons.Rounded.ImageSearch,
                accent = GoogleAccent
            )
            PlatformTile(
                title = stringResource(R.string.platform_web_direct),
                subtitle = "MP4 • JPG • صفحات ويب",
                icon = Icons.Rounded.Public,
                accent = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PlatformTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        modifier = Modifier.width(156.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Surface(
                color = accent.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
