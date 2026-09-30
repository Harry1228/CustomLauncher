package com.harry.launcher.data.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

@Immutable
data class AppModel(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap
)
