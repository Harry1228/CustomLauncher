package com.harry.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
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
        val apps = loadInstalledApps()

        setContent {
            var isDrawerOpen by remember { mutableStateOf(false) }
            var searchQuery by remember { mutableStateOf("") }
            var selectedAppForMenu by remember { mutableStateOf<AppModel?>(null) }

            BackHandler(enabled = isDrawerOpen) {
                isDrawerOpen = false
                searchQuery = ""
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, dragAmount ->
                            if (dragAmount < -25 && !isDrawerOpen) {
                                isDrawerOpen = true // Swipe UP opens drawer
                            } else if (dragAmount > 25 && isDrawerOpen) {
                                isDrawerOpen = false // Swipe DOWN closes drawer
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

                // Nova Style Slide-Up Drawer
                AnimatedVisibility(
                    visible = isDrawerOpen,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    AppDrawer(
                        apps = apps,
                        searchQuery = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onClose = {
                            isDrawerOpen = false
                            searchQuery = ""
                        },
                        onLaunchApp = { launchApp(it) },
                        onAppLongClick = { selectedAppForMenu = it }
                    )
                }

                // App Info Dialog on Long-press
                selectedAppForMenu?.let { app ->
                    AlertDialog(
                        onDismissRequest = { selectedAppForMenu = null },
                        title = { Text(text = app.label) },
                        text = { Text("Open system app settings for ${app.label}?") },
                        confirmButton = {
                            TextButton(onClick = {
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
            .distinctBy { it.activityInfo.packageName } // Prevents duplicate key crashes
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
        } catch (e: Exception) {
            // Failsafe against unlaunchable system packages
        }
    }

    private fun openAppSettings(packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Failsafe
        }
    }
}

@Composable
fun HomeScreen(
    apps: List<AppModel>,
    onOpenDrawer: () -> Unit,
    onLaunchApp: (String) -> Unit
) {
    val currentTime = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }
    val currentDate = remember {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Clock & Search Bar
        Column(
            modifier = Modifier
                .padding(top = 40.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentTime,
                fontSize = 64.sp,
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
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Search apps...",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp
                    )
                }
            }
        }

        // Bottom Dock
        val dockApps = remember(apps) {
            apps.filter {
                val pkg = it.packageName.lowercase()
                pkg.contains("dialer") || pkg.contains("chrome") ||
                pkg.contains("messaging") || pkg.contains("camera") ||
                pkg.contains("whatsapp")
            }.take(4)
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.Black.copy(alpha = 0.4f),
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
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = "Open Drawer",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun AppDrawer(
    apps: List<AppModel>,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
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
            .background(Color(0xF0121212))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search ${apps.size} apps...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    },
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
                        focusedContainerColor = Color(0xFF2C2C2C),
                        unfocusedContainerColor = Color(0xFF222222),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredApps) { app ->
                    AppIcon(
                        app = app,
                        showLabel = true,
                        onClick = { onLaunchApp(app.packageName) },
                        onLongClick = { onAppLongClick(app) }
                    )
                }
            }
        }
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
            .width(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 6.dp)
    ) {
        Image(
            painter = rememberDrawablePainter(drawable = app.icon),
            contentDescription = app.label,
            modifier = Modifier.size(52.dp)
        )

        if (showLabel) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = app.label,
                fontSize = 11.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
            )
        }
    }
}
