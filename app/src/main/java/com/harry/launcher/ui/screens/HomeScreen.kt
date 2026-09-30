package com.harry.launcher.ui.screens

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harry.launcher.data.model.AppModel
import com.harry.launcher.ui.components.AppIconItem
import com.harry.launcher.ui.components.WidgetHostItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    dockApps: List<AppModel>,
    widgetIds: List<Int>,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    onOpenDrawer: () -> Unit,
    onSwipeDown: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPressHome: () -> Unit,
    onLaunchApp: (String) -> Unit,
    onRemoveWidget: (Int) -> Unit
) {
    val currentTime = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
    val currentDate = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()) }
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .draggable(
                state = rememberDraggableState { delta ->
                    when {
                        delta < -12f -> onOpenDrawer() // Swipe Up -> Open Drawer
                        delta > 12f -> onSwipeDown()   // Swipe Down -> Notification Shade
                    }
                },
                orientation = Orientation.Vertical
            ),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Digital Clock and Search Bar
        Column(
            modifier = Modifier
                .padding(top = 28.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = currentTime, fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(text = currentDate, fontSize = 17.sp, color = Color.White.copy(alpha = 0.85f))

            Spacer(modifier = Modifier.height(18.dp))

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

        // Center Touch & Gesture Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDoubleTap()
                        },
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPressHome()
                        }
                    )
                }
        ) {
            if (widgetIds.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
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
            }
        }

        // 5-Column Dock
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .draggable(
                    state = rememberDraggableState { delta ->
                        if (delta < -10f) onOpenDrawer()
                    },
                    orientation = Orientation.Vertical
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                dockApps.forEach { app ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        AppIconItem(
                            app = app,
                            showLabel = false,
                            onClick = { onLaunchApp(app.packageName) }
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(Icons.Default.Apps, contentDescription = "Open Drawer", tint = Color.White)
                    }
                }
            }
        }
    }
}
