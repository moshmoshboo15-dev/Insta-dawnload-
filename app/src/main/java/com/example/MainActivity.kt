package com.example

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.navigation.MediaGrabRoute
import com.example.ui.screens.DownloaderScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WhatsAppScreen
import com.example.ui.theme.MediaGrabTheme
import com.example.ui.viewmodel.DownloaderViewModel
import com.example.ui.viewmodel.HistoryViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.WhatsAppViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val downloaderViewModel: DownloaderViewModel by viewModels()
    private val historyViewModel: HistoryViewModel by viewModels()
    private val whatsAppViewModel: WhatsAppViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MediaGrabTheme(themeMode = themeMode) {
                    MediaGrabRoot(
                        downloaderViewModel = downloaderViewModel,
                        historyViewModel = historyViewModel,
                        whatsAppViewModel = whatsAppViewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            checkClipboardAutoPaste()
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                downloaderViewModel.onSharedTextReceived(sharedText)
            }
        }
    }

    private fun checkClipboardAutoPaste() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        if (!clipboard.hasPrimaryClip()) return
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount <= 0) return
        val text = clip.getItemAt(0).coerceToText(this)?.toString()
        downloaderViewModel.handleClipboardCandidate(text)
    }
}

private data class NavDestinationItem(
    val route: MediaGrabRoute,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
private fun MediaGrabRoot(
    downloaderViewModel: DownloaderViewModel,
    historyViewModel: HistoryViewModel,
    whatsAppViewModel: WhatsAppViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val destinations = remember {
        listOf(
            NavDestinationItem(
                route = MediaGrabRoute.Download,
                labelRes = R.string.nav_download,
                selectedIcon = Icons.Rounded.Download,
                unselectedIcon = Icons.Outlined.Download,
                testTag = "nav_download"
            ),
            NavDestinationItem(
                route = MediaGrabRoute.History,
                labelRes = R.string.nav_history,
                selectedIcon = Icons.Rounded.History,
                unselectedIcon = Icons.Outlined.History,
                testTag = "nav_history"
            ),
            NavDestinationItem(
                route = MediaGrabRoute.WhatsApp,
                labelRes = R.string.nav_whatsapp,
                selectedIcon = Icons.Rounded.PhotoLibrary,
                unselectedIcon = Icons.Outlined.PhotoLibrary,
                testTag = "nav_whatsapp"
            ),
            NavDestinationItem(
                route = MediaGrabRoute.Settings,
                labelRes = R.string.nav_settings,
                selectedIcon = Icons.Rounded.Settings,
                unselectedIcon = Icons.Outlined.Settings,
                testTag = "nav_settings"
            )
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    fun showMessage(msg: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun navigateTo(route: MediaGrabRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        destinations.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            val label = stringResource(item.labelRes)
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        destinations.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            val label = stringResource(item.labelRes)
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = label
                                    )
                                },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = MediaGrabRoute.Download,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable<MediaGrabRoute.Download> {
                        DownloaderScreen(
                            viewModel = downloaderViewModel,
                            onShowMessage = ::showMessage
                        )
                    }
                    composable<MediaGrabRoute.History> {
                        HistoryScreen(
                            viewModel = historyViewModel,
                            onShowMessage = ::showMessage
                        )
                    }
                    composable<MediaGrabRoute.WhatsApp> {
                        WhatsAppScreen(
                            viewModel = whatsAppViewModel,
                            onShowMessage = ::showMessage
                        )
                    }
                    composable<MediaGrabRoute.Settings> {
                        SettingsScreen(viewModel = settingsViewModel)
                    }
                }
            }
        }
    }
}
