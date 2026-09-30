@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.harry.launcher

import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import com.harry.launcher.data.repository.AppRepository
import com.harry.launcher.ui.screens.AppDrawer
import com.harry.launcher.ui.screens.HomeScreen
import com.harry.launcher.ui.screens.SettingsSheetContent
import com.harry.launcher.ui.viewmodel.LauncherViewModel
import com.harry.launcher.ui.viewmodel.LauncherViewModelFactory

class MainActivity : ComponentActivity() {

    private val hostId = 1024
    private lateinit var appWidgetHost: AppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager
    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    private val viewModel: LauncherViewModel by viewModels {
        LauncherViewModelFactory(AppRepository(applicationContext))
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
                    onLongPressHome = { viewModel.setHomeScreenMenu(true) },
                    onLaunchApp = { launchApp(it) },
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
                        onLaunchApp = { launchApp(it) },
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
                                        openWallpaperChooser()
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
                                openAppSettings(app.packageName)
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
                            onGridColumnsChange = { viewModel.setGridColumns(it) },
                            onToggleAppLabels = { viewModel.toggleAppLabels(it) },
                            onSetDefaultLauncher = { requestDefaultLauncherRole() },
                            onOpenWallpaper = { openWallpaperChooser() }
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

    private fun launchApp(packageName: String) {
        try {
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(this)
            }
        } catch (_: Exception) {}
    }

    private fun openAppSettings(packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun openWallpaperChooser() {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Choose Wallpaper"))
        } catch (_: Exception) {}
    }

    private fun requestDefaultLauncherRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
