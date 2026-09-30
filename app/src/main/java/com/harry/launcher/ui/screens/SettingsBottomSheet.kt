@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.harry.launcher.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harry.launcher.data.iconpack.IconPackInfo

@Composable
fun SettingsSheetContent(
    gridColumns: Int,
    showAppLabels: Boolean,
    availableIconPacks: List<IconPackInfo>,
    selectedIconPack: String?,
    onGridColumnsChange: (Int) -> Unit,
    onToggleAppLabels: (Boolean) -> Unit,
    onSelectIconPack: (String?) -> Unit,
    onSetDefaultLauncher: () -> Unit,
    onOpenWallpaper: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
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

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))

        Text("Icon Theme", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectIconPack(null) }
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (selectedIconPack == null) 2.dp else 1.dp,
                                color = if (selectedIconPack == null) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "System", fontSize = 11.sp, color = Color.White, textAlign = TextAlign.Center)
                }
            }

            items(availableIconPacks) { pack ->
                val isSelected = selectedIconPack == pack.packageName
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectIconPack(pack.packageName) }
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(bitmap = pack.icon, contentDescription = pack.label, modifier = Modifier.size(46.dp))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = pack.label,
                        fontSize = 11.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(16.dp))

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
