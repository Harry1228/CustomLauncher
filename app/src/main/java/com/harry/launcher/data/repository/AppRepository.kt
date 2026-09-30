package com.harry.launcher.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.harry.launcher.data.model.AppModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
                val drawable = it.loadIcon(pm)
                AppModel(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = drawableToHardwareBitmap(drawable)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun drawableToHardwareBitmap(drawable: Drawable): ImageBitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap.asImageBitmap()
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
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
