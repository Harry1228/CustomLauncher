package com.harry.launcher.data.repository

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import com.harry.launcher.data.cache.IconCache
import com.harry.launcher.data.graphics.IconNormalizer
import com.harry.launcher.data.iconpack.IconPackInfo
import com.harry.launcher.data.iconpack.IconPackManager
import com.harry.launcher.data.model.AppModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(
    private val context: Context,
    private val iconPackManager: IconPackManager,
    private val iconCache: IconCache = IconCache(),
    private val iconNormalizer: IconNormalizer = IconNormalizer(context)
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    private var activeIconPackMapping: Map<String, String> = emptyMap()

    fun getSelectedIconPack(): String? {
        val pack = prefs.getString("selected_icon_pack", "") ?: ""
        return pack.ifBlank { null }
    }

    fun setSelectedIconPack(packageName: String?) {
        prefs.edit().putString("selected_icon_pack", packageName ?: "").apply()
        iconCache.clear() // Invalidate cached bitmaps when theme switches
    }

    suspend fun getAvailableIconPacks(): List<IconPackInfo> {
        return iconPackManager.getAvailableIconPacks()
    }

    suspend fun loadInstalledApps(): List<AppModel> = withContext(Dispatchers.IO) {
        val selectedPack = getSelectedIconPack()
        activeIconPackMapping = if (!selectedPack.isNullOrBlank()) {
            iconPackManager.parseAppFilter(selectedPack)
        } else {
            emptyMap()
        }

        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val activities: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)

        activities
            .filter { it.activityInfo != null && it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map {
                val pkgName = it.activityInfo.packageName
                val icon = resolveOptimizedIcon(it, pkgName, selectedPack, pm)

                AppModel(
                    label = it.loadLabel(pm).toString(),
                    packageName = pkgName,
                    icon = icon
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
        val resolveInfo = pm.queryIntentActivities(intent, 0).firstOrNull() ?: return@withContext null

        val selectedPack = getSelectedIconPack()
        val icon = resolveOptimizedIcon(resolveInfo, packageName, selectedPack, pm)

        AppModel(
            label = resolveInfo.loadLabel(pm).toString(),
            packageName = packageName,
            icon = icon
        )
    }

    private fun resolveOptimizedIcon(
        resolveInfo: ResolveInfo,
        packageName: String,
        selectedPack: String?,
        pm: PackageManager
    ): ImageBitmap {
        val cacheKey = "$packageName:${selectedPack ?: "system"}"
        val cached = iconCache.get(cacheKey)
        if (cached != null) return cached

        val customDrawableName = activeIconPackMapping[packageName]
        val resolvedDrawable = if (!selectedPack.isNullOrBlank() && !customDrawableName.isNullOrBlank()) {
            iconPackManager.loadIconFromPack(selectedPack, customDrawableName) ?: resolveInfo.loadIcon(pm)
        } else {
            resolveInfo.loadIcon(pm)
        }

        val normalizedBitmap = iconNormalizer.normalize(resolvedDrawable)
        iconCache.put(cacheKey, normalizedBitmap)
        return normalizedBitmap
    }

    fun evictFromCache(packageName: String) {
        val selectedPack = getSelectedIconPack()
        iconCache.remove("$packageName:${selectedPack ?: "system"}")
    }

    fun trimMemory(level: Int) {
        iconCache.trimMemory(level)
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

    fun getSavedWidgetIds(): List<Int> {
        val raw = prefs.getString("saved_widget_ids", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { it.toIntOrNull() }
    }

    fun saveWidgetIds(ids: List<Int>) {
        prefs.edit().putString("saved_widget_ids", ids.joinToString(",")).apply()
    }
}
