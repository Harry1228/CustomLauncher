package com.harry.launcher.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harry.launcher.data.model.AppModel
import com.harry.launcher.data.repository.AppRepository
import com.harry.launcher.data.source.PackageEvent
import com.harry.launcher.data.source.PackageMonitor
import com.harry.launcher.ui.state.LauncherUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LauncherViewModel(
    private val repository: AppRepository,
    private val packageMonitor: PackageMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
        observePackageChanges()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    selectedIconPack = repository.getSelectedIconPack(),
                    availableIconPacks = repository.getAvailableIconPacks(),
                    widgetIds = repository.getSavedWidgetIds()
                )
            }
            refreshAppsList()
        }
    }

    private suspend fun refreshAppsList() {
        val apps = repository.loadInstalledApps()
        val preferredDockPkgs = repository.resolveDefaultDockPackages()

        val dock = mutableListOf<AppModel>()
        for (pkg in preferredDockPkgs) {
            apps.find { it.packageName == pkg }?.let { dock.add(it) }
        }
        if (dock.size < 4) {
            for (app in apps) {
                if (!dock.contains(app)) dock.add(app)
                if (dock.size == 4) break
            }
        }

        _uiState.update { state ->
            state.copy(
                allApps = apps,
                filteredApps = applyFilter(apps, state.searchQuery),
                dockApps = dock.take(4),
                isLoading = false
            )
        }
    }

    fun applyIconPack(packageName: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, selectedIconPack = packageName) }
            repository.setSelectedIconPack(packageName)
            refreshAppsList()
        }
    }

    private fun observePackageChanges() {
        viewModelScope.launch {
            packageMonitor.observePackageEvents().collect { event ->
                when (event) {
                    is PackageEvent.Added -> handlePackageAdded(event.packageName)
                    is PackageEvent.Removed -> handlePackageRemoved(event.packageName)
                    is PackageEvent.Updated -> handlePackageUpdated(event.packageName)
                }
            }
        }
    }

    private suspend fun handlePackageAdded(packageName: String) {
        val newApp = repository.loadSingleApp(packageName) ?: return
        _uiState.update { state ->
            val updatedAll = (state.allApps.filterNot { it.packageName == packageName } + newApp)
                .sortedBy { it.label.lowercase() }
            state.copy(
                allApps = updatedAll,
                filteredApps = applyFilter(updatedAll, state.searchQuery)
            )
        }
    }

    private fun handlePackageRemoved(packageName: String) {
        repository.evictFromCache(packageName)
        _uiState.update { state ->
            val updatedAll = state.allApps.filterNot { it.packageName == packageName }
            state.copy(
                allApps = updatedAll,
                filteredApps = applyFilter(updatedAll, state.searchQuery),
                dockApps = state.dockApps.filterNot { it.packageName == packageName }
            )
        }
    }

    private suspend fun handlePackageUpdated(packageName: String) {
        repository.evictFromCache(packageName)
        val updatedApp = repository.loadSingleApp(packageName) ?: return
        _uiState.update { state ->
            val updatedAll = state.allApps.map { if (it.packageName == packageName) updatedApp else it }
                .sortedBy { it.label.lowercase() }
            state.copy(
                allApps = updatedAll,
                filteredApps = applyFilter(updatedAll, state.searchQuery),
                dockApps = state.dockApps.map { if (it.packageName == packageName) updatedApp else it }
            )
        }
    }

    fun onTrimMemory(level: Int) {
        repository.trimMemory(level)
    }

    private fun applyFilter(apps: List<AppModel>, query: String): List<AppModel> {
        return if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    fun addWidget(id: Int) {
        val updated = _uiState.value.widgetIds + id
        _uiState.update { it.copy(widgetIds = updated) }
        repository.saveWidgetIds(updated)
    }

    fun removeWidget(id: Int) {
        val updated = _uiState.value.widgetIds.filter { it != id }
        _uiState.update { it.copy(widgetIds = updated) }
        repository.saveWidgetIds(updated)
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { state ->
            state.copy(searchQuery = query, filteredApps = applyFilter(state.allApps, query))
        }
    }

    fun setDrawerOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isDrawerOpen = isOpen, searchQuery = if (!isOpen) "" else it.searchQuery) }
    }

    fun setSettingsOpen(isOpen: Boolean) {
        _uiState.update { it.copy(isSettingsOpen = isOpen) }
    }

    fun setHomeScreenMenu(isOpen: Boolean) {
        _uiState.update { it.copy(showHomeScreenMenu = isOpen) }
    }

    fun setSelectedAppForMenu(app: AppModel?) {
        _uiState.update { it.copy(selectedAppForMenu = app) }
    }

    fun setGridColumns(columns: Int) {
        _uiState.update { it.copy(gridColumns = columns) }
    }

    fun toggleAppLabels(show: Boolean) {
        _uiState.update { it.copy(showAppLabels = show) }
    }
}

class LauncherViewModelFactory(
    private val repository: AppRepository,
    private val packageMonitor: PackageMonitor
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LauncherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LauncherViewModel(repository, packageMonitor) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
