@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.harry.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.harry.launcher.data.iconpack.IconPackManager
import com.harry.launcher.data.repository.AppRepository
import com.harry.launcher.data.source.PackageMonitor
import com.harry.launcher.ui.screens.AppDrawer
import com.harry.launcher.ui.screens.HomeScreen
import com.harry.launcher.ui.screens.SettingsSheetContent
import com.harry.launcher.ui.viewmodel.LauncherViewModel
import com.harry.launcher.ui.viewmodel.LauncherViewModelFactory
import com.harry.launcher.util.SystemActionDispatcher

class MainActivity : ComponentActivity() {

    private val hostId = 1024
    private lateinit var appWidgetHost: AppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var systemDispatcher: SystemActionDispatcher
    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    private val viewModel: LauncherViewModel by viewModels {
        val iconPackManager = IconPackManager(applicationContext)
        val repository = AppRepository(applicationContext, iconPackManager)
        val packageMonitor = PackageMonitor(applicationContext)
        LauncherViewModelFactory(repository, packageMonitor)
    }

    private val configureWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
                ?: pendingWidgetId
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                viewModel.addWidget(id)
            }
        } else {
            if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                appWidgetHost.deleteAppWidgetId(pendingWidgetId)
            }
        }
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private val pickWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
                ?: pendingWidgetId
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val info: AppWidgetProviderInfo? = appWidgetManager.getAppWidgetInfo(id)
                if (info?.configure != null) {
                    val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                        component = info.configure
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    }
                    pendingWidgetId = id
                    configureWidgetLauncher.launch(configIntent)
                } else {
                    viewModel.addWidget(id)
                    pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                }
            }
        } else {
            if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                appWidgetHost.deleteAppWidgetId(pendingWidgetId)
            }
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.setBackgroundDrawableResource(android.R.color.transparent)

        systemDispatcher = SystemActionDispatcher(applicationContext)
        appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        appWidgetHost = AppWidgetHost(applicationContext, hostId)

        setContent {
            val state by viewModel.uiState.collectAsState()

            BackHandler(enabled = state.isDrawerOpen || state.isSettingsOpen) {
                if (state.isSettingsOpen) viewModel.setSettingsOpen(false)
                else viewModel.setDrawerOpen(false)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                HomeScreen(
                    dockApps = state.dockApps,
                    widgetIds = state.widgetIds,
                    appWidgetHost = appWidgetHost,
                    appWidgetManager = appWidgetManager,
                    onOpenDrawer = { viewModel.setDrawerOpen(true) },
                    onSwipeDown = { systemDispatcher.expandNotificationShade() },
                    onDoubleTap = { /* Prepared for screen lock / shortcut action */ },
                    onLongPressHome = { viewModel.setHomeScreenMenu(true) },
                    onLaunchApp = { systemDispatcher.launchAppSafely(it) },
                    onRemoveWidget = { id ->
                        appWidgetHost.deleteAppWidgetId(id)
                        viewModel.removeWidget(id)
                    }
                )

                AnimatedVisibility(
                    visible = state.isDrawerOpen,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(120)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(100))
                ) {
                    AppDrawer(
                        state = state,
                        onQueryChange = { viewModel.onSearchQueryChange(it) },
                        onClose = { viewModel.setDrawerOpen(false) },
                        onOpenSettings = { viewModel.setSettingsOpen(true) },
                        onLaunchApp = { systemDispatcher.launchAppSafely(it) },
                        onAppLongClick = { viewModel.setSelectedAppForMenu(it) }
                    )
                }

                if (state.showHomeScreenMenu) {
                    AlertDialog(
                        onDismissRequest = { viewModel.setHomeScreenMenu(false) },
                        title = { Text("Home Screen Options", fontWeight = FontWeight.Bold) },
                        text = {
                            Column {
                                ListItem(
                                    headlineContent = { Text("Add Widget") },
                                    leadingContent = { Icon(Icons.Default.Widgets, contentDescription = null) },
                                    modifier = Modifier.clickable {
                                        viewModel.setHomeScreenMenu(false)
                                        launchWidgetPicker()
                                    }
                                )
                                ListItem(
                                    headlineContent = { Text("Launcher Settings") },
                                    leadingContent = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    modifier = Modifier.clickable {
                                        viewModel.setHomeScreenMenu(false)
                                        viewModel.setSettingsOpen(true)
                                    }
                                )
                                ListItem(
                                    headlineContent = { Text("Change Wallpaper") },
                                    leadingContent = { Icon(Icons.Default.Image, contentDescription = null) },
                                    modifier = Modifier.clickable {
                                        viewModel.setHomeScreenMenu(false)
                                        systemDispatcher.openWallpaperPickerSafely()
                                    }
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { viewModel.setHomeScreenMenu(false) }) { Text("Close") }
                        }
                    )
                }

                state.selectedAppForMenu?.let { app ->
                    AlertDialog(
                        onDismissRequest = { viewModel.setSelectedAppForMenu(null) },
                        title = { Text(text = app.label) },
                        text = { Text("Package: ${app.packageName}") },
                        confirmButton = {
                            Button(onClick = {
                                systemDispatcher.openAppSettingsSafely(app.packageName)
                                viewModel.setSelectedAppForMenu(null)
                            }) { Text("App Info") }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.setSelectedAppForMenu(null) }) { Text("Cancel") }
                        }
                    )
                }

                if (state.isSettingsOpen) {
                    ModalBottomSheet(
                        onDismissRequest = { viewModel.setSettingsOpen(false) },
                        containerColor = Color(0xFF1E1E1E)
                    ) {
                        SettingsSheetContent(
                            gridColumns = state.gridColumns,
                            showAppLabels = state.showAppLabels,
                            availableIconPacks = state.availableIconPacks,
                            selectedIconPack = state.selectedIconPack,
                            onGridColumnsChange = { viewModel.setGridColumns(it) },
                            onToggleAppLabels = { viewModel.toggleAppLabels(it) },
                            onSelectIconPack = { viewModel.applyIconPack(it) },
                            onSetDefaultLauncher = { systemDispatcher.openDefaultLauncherSettings() },
                            onOpenWallpaper = { systemDispatcher.openWallpaperPickerSafely() }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appWidgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        appWidgetHost.stopListening()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        viewModel.onTrimMemory(level)
    }

    private fun launchWidgetPicker() {
        try {
            val newId = appWidgetHost.allocateAppWidgetId()
            pendingWidgetId = newId
            val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newId)
            }
            pickWidgetLauncher.launch(pickIntent)
        } catch (_: Exception) {}
    }
}
