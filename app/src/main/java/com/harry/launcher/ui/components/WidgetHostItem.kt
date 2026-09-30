package com.harry.launcher.ui.components

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalFoundationApi::class)
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
