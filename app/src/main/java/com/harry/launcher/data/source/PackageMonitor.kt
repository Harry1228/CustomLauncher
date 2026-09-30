package com.harry.launcher.data.source

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

sealed interface PackageEvent {
    data class Added(val packageName: String) : PackageEvent
    data class Removed(val packageName: String) : PackageEvent
    data class Updated(val packageName: String) : PackageEvent
}

class PackageMonitor(private val context: Context) {

    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    fun observePackageEvents(): Flow<PackageEvent> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageAdded(packageName: String, user: UserHandle) {
                trySend(PackageEvent.Added(packageName))
            }

            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                trySend(PackageEvent.Removed(packageName))
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {
                trySend(PackageEvent.Updated(packageName))
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean
            ) {
                packageNames.forEach { trySend(PackageEvent.Added(it)) }
            }

            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean
            ) {
                packageNames.forEach { trySend(PackageEvent.Removed(it)) }
            }
        }

        val handler = Handler(Looper.getMainLooper())
        launcherApps.registerCallback(callback, handler)

        awaitClose {
            launcherApps.unregisterCallback(callback)
        }
    }
}
