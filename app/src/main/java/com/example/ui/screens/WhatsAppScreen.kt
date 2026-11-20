package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.extractor.UrlUtilities
import com.example.model.MediaType
import com.example.model.WhatsAppStatusItem
import com.example.ui.components.StatusMediaViewerDialog
import com.example.ui.theme.WhatsAppAccent
import com.example.ui.viewmodel.StatusFilter
import com.example.ui.viewmodel.WhatsAppViewModel

@Composable
fun WhatsAppScreen(
    viewModel: WhatsAppViewModel,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var previewItem by remember { mutableStateOf<WhatsAppStatusItem?>(null) }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        viewModel.onFolderGranted(treeUri)
    }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            onShowMessage(msg)
            viewModel.clearMessage()
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
        ) {
            // Header + App Switcher
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.whatsapp_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (uiState.hasFolderAccess) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = viewModel::refreshStatuses,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("whatsapp_refresh_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = stringResource(R.string.whatsapp_refresh)
                                )
                            }
                            TextButton(
                                onClick = { safLauncher.launch(viewModel.getInitialUriForCurrentTab()) },
                                modifier = Modifier.testTag("whatsapp_change_folder_button")
                            ) {
                                Text(stringResource(R.string.whatsapp_change_folder))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !uiState.isBusinessTab,
                        onClick = { viewModel.switchApp(isBusiness = false) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        modifier = Modifier.testTag("tab_whatsapp_regular")
                    ) {
                        Text(stringResource(R.string.whatsapp_regular))
                    }
                    SegmentedButton(
                        selected = uiState.isBusinessTab,
                        onClick = { viewModel.switchApp(isBusiness = true) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        modifier = Modifier.testTag("tab_whatsapp_business")
                    ) {
                        Text(stringResource(R.string.whatsapp_business))
                    }
                }
            }

            if (!uiState.hasFolderAccess) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, WhatsAppAccent.copy(alpha = 0.45f)),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.widthIn(max = 460.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                color = WhatsAppAccent.copy(alpha = 0.16f),
                                shape = CircleShape,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.FolderSpecial,
                                        contentDescription = null,
                                        tint = WhatsAppAccent,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.whatsapp_guide_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.whatsapp_guide_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = { safLauncher.launch(viewModel.getInitialUriForCurrentTab()) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WhatsAppAccent,
                                    contentColor = Color(0xFF042214)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("grant_whatsapp_saf_button"),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(
                                    text = stringResource(R.string.whatsapp_grant_access),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            } else {
                // Filter & Save All Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.filter == StatusFilter.ALL,
                            onClick = { viewModel.setFilter(StatusFilter.ALL) },
                            label = { Text(stringResource(R.string.filter_all)) }
                        )
                        FilterChip(
                            selected = uiState.filter == StatusFilter.IMAGES,
                            onClick = { viewModel.setFilter(StatusFilter.IMAGES) },
                            label = { Text("صور") }
                        )
                        FilterChip(
                            selected = uiState.filter == StatusFilter.VIDEOS,
                            onClick = { viewModel.setFilter(StatusFilter.VIDEOS) },
                            label = { Text("فيديو") }
                        )
                    }

                    if (uiState.filteredItems.isNotEmpty()) {
                        OutlinedButton(
                            onClick = viewModel::saveAllStatuses,
                            modifier = Modifier.testTag("whatsapp_save_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.whatsapp_save_all))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    uiState.filteredItems.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.whatsapp_empty_folder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 115.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.filteredItems, key = { it.uriString }) { item ->
                                val isSaved = item.uriString in uiState.savedUris
                                StatusGridTile(
                                    item = item,
                                    isSaved = isSaved,
                                    onClick = { previewItem = item },
                                    onSaveClick = { viewModel.saveStatus(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    previewItem?.let { current ->
        StatusMediaViewerDialog(
            item = current,
            isSaved = current.uriString in uiState.savedUris,
            onSaveClick = { viewModel.saveStatus(current) },
            onDismiss = { previewItem = null }
        )
    }
}

@Composable
private fun StatusGridTile(
    item: WhatsAppStatusItem,
    isSaved: Boolean,
    onClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(0.8f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .testTag("status_tile_${item.name}")
    ) {
        AsyncImage(
            model = Uri.parse(item.uriString),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (item.mediaType == MediaType.VIDEO) {
            Surface(
                color = Color.Black.copy(alpha = 0.62f),
                shape = CircleShape,
                modifier = Modifier
                    .size(38.dp)
                    .align(Alignment.Center)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.media_type_video),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Surface(
            color = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
        ) {
            Text(
                text = UrlUtilities.formatBytes(item.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Surface(
            onClick = { if (!isSaved) onSaveClick() },
            color = if (isSaved) WhatsAppAccent else MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .minimumInteractiveComponentSize()
                .size(40.dp)
                .testTag("save_status_${item.name}")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isSaved) Icons.Rounded.Check else Icons.Rounded.Download,
                    contentDescription = stringResource(R.string.whatsapp_save),
                    tint = if (isSaved) Color(0xFF042214) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
