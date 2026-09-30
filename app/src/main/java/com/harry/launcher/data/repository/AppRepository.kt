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
import android.net.Uri
import android.provider.MediaStore
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
                    icon = drawableToOptimizedBitmap(drawable)
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    suspend fun loadSingleApp(packageName: String): AppModel? = withContext(Dispatchers.IO) {
        if (packageName == context.packageName) return@withContext null
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            `package` = packageName
        }
        val resolveInfo = pm.queryIntentActivities(intent, 0).firstOrNull()

        resolveInfo?.let {
            val drawable = it.loadIcon(pm)
            AppModel(
                label = it.loadLabel(pm).toString(),
                packageName = it.activityInfo.packageName,
                icon = drawableToOptimizedBitmap(drawable)
            )
        }
    }

    fun resolveDefaultDockPackages(): List<String> {
        val pm = context.packageManager
        val dockList = mutableListOf<String>()

        fun addFromIntent(intent: Intent) {
            val resolve = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            val pkg = resolve?.activityInfo?.packageName
            if (pkg != null && pkg != "android" && !dockList.contains(pkg)) {
                dockList.add(pkg)
            }
        }

        addFromIntent(Intent(Intent.ACTION_DIAL))
        addFromIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")))
        addFromIntent(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))
        addFromIntent(Intent(MediaStore.ACTION_IMAGE_CAPTURE))

        return dockList
    }

    private fun drawableToOptimizedBitmap(drawable: Drawable): ImageBitmap {
        val targetSize = 144
        val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, targetSize, targetSize)
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
