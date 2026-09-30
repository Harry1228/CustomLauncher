package com.harry.launcher.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.harry.launcher.data.model.AppModel
import com.harry.launcher.data.repository.AppRepository
import com.harry.launcher.ui.state.LauncherUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LauncherViewModel(private val repository: AppRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    init {
        loadApps()
        loadWidgets()
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val apps = repository.loadInstalledApps()

            val dock = apps.filter {
                val pkg = it.packageName.lowercase()
                pkg.contains("dialer") || pkg.contains("chrome") ||
                pkg.contains("messaging") || pkg.contains("camera") ||
                pkg.contains("whatsapp")
            }.take(4)

            _uiState.update {
                it.copy(
                    allApps = apps,
                    filteredApps = apps,
                    dockApps = dock,
                    isLoading = false
                )
            }
        }
    }

    private fun loadWidgets() {
        val saved = repository.getSavedWidgetIds()
        _uiState.update { it.copy(widgetIds = saved) }
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
            val filtered = if (query.isBlank()) {
                state.allApps
            } else {
                state.allApps.filter { it.label.contains(query, ignoreCase = true) }
            }
            state.copy(searchQuery = query, filteredApps = filtered)
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

class LauncherViewModelFactory(private val repository: AppRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LauncherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LauncherViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
