@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.harry.launcher

import android.app.role.RoleManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==========================================
// 1. DATA LAYER & PERSISTENCE
// ==========================================

@Immutable
data class AppModel(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

class AppRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    suspend fun loadInstalledApps(): List<AppModel> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val activities: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)

        activities
            .filter { it.activityInfo != null && it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map {
                AppModel(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(pm)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun getSavedWidgetIds(): List<Int> {
        val raw = prefs.getString("saved_widget_ids", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { it.toIntOrNull() }
    }

    fun saveWidgetIds(ids: List<Int>) {
        prefs.edit().putString("saved_widget_ids", ids.joinToString(",")).apply()
    }
}

// ==========================================
// 2. STATE & VIEWMODEL LAYER
// ==========================================

data class LauncherUiState(
    val allApps: List<AppModel> = emptyList(),
    val filteredApps: List<AppModel> = emptyList(),
    val dockApps: List<AppModel> = emptyList(),
    val widgetIds: List<Int> = emptyList(),
    val searchQuery: String = "",
    val isDrawerOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val showHomeScreenMenu: Boolean = false,
    val selectedAppForMenu: AppModel? = null,
    val gridColumns: Int = 4,
    val showAppLabels: Boolean = true,
    val isLoading: Boolean = true
)

class LauncherViewModel(private val repository: AppRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    init {
        loadApps()
        loadWidgets()
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val apps = repository.loadInstalledApps()

            val dock = apps.filter {
                val pkg = it.packageName.lowercase()
                pkg.contains("dialer") || pkg.contains("chrome") ||
                pkg.contains("messaging") || pkg.contains("camera") ||
                pkg.contains("whatsapp")
            }.take(4)

            _uiState.update {
                it.copy(
                    allApps = apps,
                    filteredApps = apps,
                    dockApps = dock,
                    isLoading = false
                )
            }
        }
    }

    private fun loadWidgets() {
        val saved = repository.getSavedWidgetIds()
        _uiState.update { it.copy(widgetIds = saved) }
    }

    fun addWidget(id: Int) {
        val updated = _uiState.value.widgetIds + id
        _uiState.update { it.copy(widgetIds = updated) }
        repository.saveWidgetIds(updated)
    }

    fun removeWidget(id: Int) {
        val updated = _uiState.value.widgetIds.filter { it != id }
        _uiState.update { it.copy(widgetIds = updated) }
        repository.saveWidgetIds(updated)
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state ->
            val filtered = if (query.isBlank()) {
                state.allApps
            } else {
                state.allApps.filter { it.label.contains(query, ignoreCase = true) }
            }
            state.copy(searchQuery = query, filteredApps = filtered)
        }
    }

    fun setDrawerOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isDrawerOpen = isOpen, searchQuery = if (!isOpen) "" else it.searchQuery) }
    }

    fun setSettingsOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isSettingsOpen = isOpen) }
    }

    fun setHomeScreenMenu(isOpen: Boolean) {
        _uiState.update { it.copy(showHomeScreenMenu = isOpen) }
    }

    fun setSelectedAppForMenu(app: AppModel?) {
        _uiState.update { it.copy(selectedAppForMenu = app) }
    }

    fun setGridColumns(columns: Int) {
        _uiState.update { it.copy(gridColumns = columns) }
    }

    fun toggleAppLabels(show: Boolean) {
        _uiState.update { it.copy(showAppLabels = show) }
    }
}

class LauncherViewModelFactory(private val repository: AppRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LauncherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LauncherViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// ==========================================
// 3. MAIN ACTIVITY & WIDGET HOST
// ==========================================

class MainActivity : ComponentActivity() {

    private val hostId = 1024
    private lateinit var appWidgetHost: AppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager
    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    private val viewModel: LauncherViewModel by viewModels {
        LauncherViewModelFactory(AppRepository(applicationContext))
    }

    // Handles widget configuration (e.g. KWGT widget selection or settings)
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

    // Handles widget picker dialog
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
            val haptic = LocalHapticFeedback.current

            BackHandler(enabled = state.isDrawerOpen || state.isSettingsOpen) {
                if (state.isSettingsOpen) viewModel.setSettingsOpen(false)
                else viewModel.setDrawerOpen(false)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.setHomeScreenMenu(true)
                        })
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -30 && !state.isDrawerOpen) viewModel.setDrawerOpen(true)
                            else if (dragAmount > 30 && state.isDrawerOpen) viewModel.setDrawerOpen(false)
                        }
                    }
            ) {
                HomeScreen(
                    dockApps = state.dockApps,
                    widgetIds = state.widgetIds,
                    appWidgetHost = appWidgetHost,
                    appWidgetManager = appWidgetManager,
                    onOpenDrawer = { viewModel.setDrawerOpen(true) },
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
                    ) + fadeIn(animationSpec = tween(140)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(120))
                ) {
                    AppDrawer(
                        state = state,
                        onQueryChange = { viewModel.onSearchQueryChange(it) },
                        onClose = { viewModel.setDrawerOpen(false) },
                        onOpenSettings = { viewModel.setSettingsOpen(true) },
                        onLaunchApp = { launchApp(it) },
                        onAppLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.setSelectedAppForMenu(it)
                        }
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
        } catch (e: Exception) {
            // Failsafe against picker launch denials
        }
    }

    private fun launchApp(packageName: String) {
        try {
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(this)
            }
        } catch (e: Exception) {}
    }

    private fun openAppSettings(packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {}
    }

    private fun openWallpaperChooser() {
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Choose Wallpaper"))
        } catch (e: Exception) {}
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
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}

// ==========================================
// 4. UI SCREENS & WIDGET HOST CONTAINER
// ==========================================

@Composable
fun HomeScreen(
    dockApps: List<AppModel>,
    widgetIds: List<Int>,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    onOpenDrawer: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onRemoveWidget: (Int) -> Unit
) {
    val currentTime = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
    val currentDate = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Digital Clock and Search
        Column(
            modifier = Modifier
                .padding(top = 32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = currentTime, fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(text = currentDate, fontSize = 17.sp, color = Color.White.copy(alpha = 0.85f))

            Spacer(modifier = Modifier.height(20.dp))

            Surface(
                onClick = onOpenDrawer,
                shape = RoundedCornerShape(28.dp),
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .height(52.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Search apps...", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
                }
            }
        }

        // Widgets Scroll Area (KWGT, Weather, Clocks)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items = widgetIds, key = { it }) { id ->
                WidgetHostItem(
                    widgetId = id,
                    appWidgetHost = appWidgetHost,
                    appWidgetManager = appWidgetManager,
                    onRemove = { onRemoveWidget(id) }
                )
            }
        }

        // Bottom Dock
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                dockApps.forEach { app ->
                    AppIconItem(
                        app = app,
                        showLabel = false,
                        onClick = { onLaunchApp(app.packageName) }
                    )
                }

                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier
                        .size(50.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(Icons.Default.Apps, contentDescription = "Open Drawer", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun WidgetHostItem(
    widgetId: Int,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    onRemove: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val info = remember(widgetId) { appWidgetManager.getAppWidgetInfo(widgetId) }
    val haptic = LocalHapticFeedback.current

    if (info != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showDeleteConfirm = true
                    }
                )
        ) {
            AndroidView(
                factory = { ctx ->
                    appWidgetHost.createView(ctx, widgetId, info).apply {
                        setAppWidget(widgetId, info)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Widget Options") },
                text = { Text("Remove this widget from the home screen?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirm = false
                            onRemove()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Remove")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun AppDrawer(
    state: LauncherUiState,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onAppLongClick: (AppModel) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF5141414))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = state.searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search ${state.allApps.size} apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF262626),
                        unfocusedContainerColor = Color(0xFF1E1E1E),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                }

                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(state.gridColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(
                        items = state.filteredApps,
                        key = { it.packageName },
                        contentType = { "app_tile" }
                    ) { app ->
                        AppIconItem(
                            app = app,
                            showLabel = state.showAppLabels,
                            onClick = { onLaunchApp(app.packageName) },
                            onLongClick = { onAppLongClick(app) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppIconItem(
    app: AppModel,
    showLabel: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 4.dp)
    ) {
        Image(
            painter = rememberDrawablePainter(drawable = app.icon),
            contentDescription = app.label,
            modifier = Modifier.size(50.dp)
        )

        if (showLabel) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = app.label,
                fontSize = 11.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun SettingsSheetContent(
    gridColumns: Int,
    showAppLabels: Boolean,
    onGridColumnsChange: (Int) -> Unit,
    onToggleAppLabels: (Boolean) -> Unit,
    onSetDefaultLauncher: () -> Unit,
    onOpenWallpaper: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text("Launcher Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = onSetDefaultLauncher,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Home, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Set as Default Launcher")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onOpenWallpaper,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Image, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Change Wallpaper")
        }

        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Show App Titles", color = Color.White, fontSize = 16.sp)
            Switch(checked = showAppLabels, onCheckedChange = onToggleAppLabels)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Grid Layout", color = Color.White, fontSize = 16.sp)
            Row {
                FilterChip(
                    selected = gridColumns == 4,
                    onClick = { onGridColumnsChange(4) },
                    label = { Text("4 Cols") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterChip(
                    selected = gridColumns == 5,
                    onClick = { onGridColumnsChange(5) },
                    label = { Text("5 Cols") }
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
