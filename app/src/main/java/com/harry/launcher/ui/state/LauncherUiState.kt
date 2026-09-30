package com.harry.launcher.ui.state

import com.harry.launcher.data.iconpack.IconPackInfo
import com.harry.launcher.data.model.AppModel

data class LauncherUiState(
    val allApps: List<AppModel> = emptyList(),
    val filteredApps: List<AppModel> = emptyList(),
    val dockApps: List<AppModel> = emptyList(),
    val widgetIds: List<Int> = emptyList(),
    val availableIconPacks: List<IconPackInfo> = emptyList(),
    val selectedIconPack: String? = null,
    val searchQuery: String = "",
    val isDrawerOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val showHomeScreenMenu: Boolean = false,
    val selectedAppForMenu: AppModel? = null,
    val gridColumns: Int = 4,
    val showAppLabels: Boolean = true,
    val isLoading: Boolean = true
)
