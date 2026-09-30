package com.harry.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class AppModel(
    val label: String,
    val packageName: String,
    val icon: Drawable
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val context = LocalContext.current
            val haptic = LocalHapticFeedback.current
            val scope = rememberCoroutineScope()

            var apps by remember { mutableStateOf<List<AppModel>>(emptyList()) }
            var isLoading by remember { mutableStateOf(true) }

            var isDrawerOpen by remember { mutableStateOf(false) }
            var isSettingsOpen by remember { mutableStateOf(false) }
            var showHomeScreenMenu by remember { mutableStateOf(false) }
            var searchQuery by remember { mutableStateOf("") }
            var selectedAppForMenu by remember { mutableStateOf<AppModel?>(null) }

            // User Customizable Preferences
            var gridColumns by remember { mutableIntStateOf(4) }
            var showAppLabels by remember { mutableStateOf(true) }

            // Asynchronous Background App Loading
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val loaded = loadInstalledApps()
                    withContext(Dispatchers.Main) {
                        apps = loaded
                        isLoading = false
                    }
                }
            }

            BackHandler(enabled = isDrawerOpen || isSettingsOpen) {
                if (isSettingsOpen) {
                    isSettingsOpen = false
                } else {
                    isDrawerOpen = false
                    searchQuery = ""
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showHomeScreenMenu = true
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -30 && !isDrawerOpen) {
                                isDrawerOpen = true
                            } else if (dragAmount > 30 && isDrawerOpen) {
                                isDrawerOpen = false
                                searchQuery = ""
                            }
                        }
                    }
            ) {
                // Home Screen
                HomeScreen(
                    apps = apps,
                    onOpenDrawer = { isDrawerOpen = true },
                    onLaunchApp = { launchApp(it) }
                )

                // High-Speed Animated Drawer
                AnimatedVisibility(
                    visible = isDrawerOpen,
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
                        apps = apps,
                        isLoading = isLoading,
                        gridColumns = gridColumns,
                        showAppLabels = showAppLabels,
                        searchQuery = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onClose = {
                            isDrawerOpen = false
                            searchQuery = ""
                        },
                        onOpenSettings = { isSettingsOpen = true },
                        onLaunchApp = { launchApp(it) },
                        onAppLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedAppForMenu = it
                        }
                    )
                }

                // Long Press on Home Screen Menu
                if (showHomeScreenMenu) {
                    AlertDialog(
                        onDismissRequest = { showHomeScreenMenu = false },
                        title = { Text("Home Options", fontWeight = FontWeight.Bold) },
                        text = {
                            Column {
                                ListItem(
                                    headlineContent = { Text("Launcher Settings") },
                                    leadingContent = { Icon(Icons.Default.Settings, null) },
                                    modifier = Modifier.combinedClickable {
                                        showHomeScreenMenu = false
                                        isSettingsOpen = true
                                    }
                                )
                                ListItem(
                                    headlineContent = { Text("Change Wallpaper") },
                                    leadingContent = { Icon(Icons.Default.Wallpaper, null) },
                                    modifier = Modifier.combinedClickable {
                                        showHomeScreenMenu = false
                                        openWallpaperChooser()
                                    }
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showHomeScreenMenu = false }) {
                                Text("Close")
                            }
                        }
                    )
                }

                // App Info Dialog
                selectedAppForMenu?.let { app ->
                    AlertDialog(
                        onDismissRequest = { selectedAppForMenu = null },
                        title = { Text(text = app.label) },
                        text = { Text("Package: ${app.packageName}") },
                        confirmButton = {
                            Button(onClick = {
                                openAppSettings(app.packageName)
                                selectedAppForMenu = null
                            }) {
                                Text("App Info")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { selectedAppForMenu = null }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                // Nova-Style Launcher Settings Sheet
                if (isSettingsOpen) {
                    ModalBottomSheet(
                        onDismissRequest = { isSettingsOpen = false },
                        containerColor = Color(0xFF1E1E1E)
                    ) {
                        SettingsContent(
                            gridColumns = gridColumns,
                            showAppLabels = showAppLabels,
                            onGridColumnsChange = { gridColumns = it },
                            onToggleAppLabels = { showAppLabels = it },
                            onSetDefaultLauncher = { requestDefaultLauncherRole() },
                            onOpenWallpaper = { openWallpaperChooser() }
                        )
                    }
                }
            }
        }
    }

    private fun loadInstalledApps(): List<AppModel> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val activities: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)

        return activities
            .filter { it.activityInfo != null && it.activityInfo.packageName != packageName }
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

    private fun launchApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
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
        val intent = Intent(Intent.ACTION_SET_WALLPAPER)
        startActivity(Intent.createChooser(intent, "Select Wallpaper"))
    }

    private fun requestDefaultLauncherRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME) && !roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
                startActivity(intent)
                return
            }
        }
        // Fallback for older Android versions
        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
        startActivity(intent)
    }
}

@Composable
fun HomeScreen(
    apps: List<AppModel>,
    onOpenDrawer: () -> Unit,
    onLaunchApp: (String) -> Unit
) {
    val currentTime = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
    val currentDate = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .padding(top = 40.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentTime,
                fontSize = 68.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = currentDate,
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.85f)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Surface(
                onClick = onOpenDrawer,
                shape = RoundedCornerShape(28.dp),
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
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
                    Text(
                        text = "Search apps...",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp
                    )
                }
            }
        }

        // Dock
        val dockApps = remember(apps) {
            apps.filter {
                val pkg = it.packageName.lowercase()
                pkg.contains("dialer") || pkg.contains("chrome") ||
                pkg.contains("messaging") || pkg.contains("camera") ||
                pkg.contains("whatsapp")
            }.take(4)
        }

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
                    AppIcon(
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
fun AppDrawer(
    apps: List<AppModel>,
    isLoading: Boolean,
    gridColumns: Int,
    showAppLabels: Boolean,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onAppLongClick: (AppModel) -> Unit
) {
    val filteredApps = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) apps
        else apps.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

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
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search ${apps.size} apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
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
                    Icon(Icons.Default.Settings, contentDescription = "Launcher Settings", tint = Color.White)
                }

                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close Drawer", tint = Color.White)
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(
                        items = filteredApps,
                        key = { it.packageName }
                    ) { app ->
                        AppIcon(
                            app = app,
                            showLabel = showAppLabels,
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
fun SettingsContent(
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
            Icon(Icons.Default.Wallpaper, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Change Wallpaper")
        }

        Spacer(modifier = Modifier.height(18.dp))
        Divider(color = Color.Gray.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Show App Titles", color = Color.White, fontSize = 16.sp)
            Switch(
                checked = showAppLabels,
                onCheckedChange = onToggleAppLabels
            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
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
