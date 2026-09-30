package com.harry.launcher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
