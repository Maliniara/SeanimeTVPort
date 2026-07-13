package com.example.seanimetv.feature.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.seanimetv.data.extension.ExtensionRepository
import com.example.seanimetv.data.extension.MarketplaceEntry
import com.example.seanimetv.data.plugin.PluginRepository
import com.example.seanimetv.data.plugin.model.PluginEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ExtensionsTab { INSTALLED, MARKETPLACE }

/** Sort order for the installed-extensions list. */
enum class SortMode(val label: String) {
    NAME("Name"),
    TYPE("Type"),
    STATUS("Enabled first")
}

/** Marketplace extension type filter, mirroring Seanime's tabs. */
enum class TypeFilter(val label: String, val typeValue: String?) {
    ALL("All Types", null),
    PLUGINS("Plugins", "plugin"),
    TORRENTS("Anime Torrents", "anime-torrent-provider"),
    MANGA("Manga", "manga-provider"),
    STREAMING("Online Streaming", "onlinestream-provider"),
    CUSTOM("Custom Sources", "custom-source")
}

sealed class MarketplaceState {
    object Loading : MarketplaceState()
    data class Loaded(val entries: List<MarketplaceEntry>) : MarketplaceState()
    data class Error(val message: String) : MarketplaceState()
}

data class InstalledExtension(
    val entry: PluginEntry,
    val isActive: Boolean,
    val updateVersion: String? = null
)

class ExtensionsViewModel(
    private val extensionRepository: ExtensionRepository,
    private val pluginRepository: PluginRepository,
    private val onActivePluginChanged: () -> Unit
) : ViewModel() {

    private val _tab = MutableStateFlow(ExtensionsTab.INSTALLED)
    val tab: StateFlow<ExtensionsTab> = _tab.asStateFlow()

    private val _installed = MutableStateFlow<List<InstalledExtension>>(emptyList())
    val installed: StateFlow<List<InstalledExtension>> = _installed.asStateFlow()

    private val _marketplace = MutableStateFlow<MarketplaceState>(MarketplaceState.Loading)
    val marketplace: StateFlow<MarketplaceState> = _marketplace.asStateFlow()

    private val _repoUrl = MutableStateFlow("")
    val repoUrl: StateFlow<String> = _repoUrl.asStateFlow()

    private val _typeFilter = MutableStateFlow(TypeFilter.ALL)
    val typeFilter: StateFlow<TypeFilter> = _typeFilter.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.NAME)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    private val _installingIds = MutableStateFlow<Set<String>>(emptySet())
    val installingIds: StateFlow<Set<String>> = _installingIds.asStateFlow()

    private val _installErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val installErrors: StateFlow<Map<String, String>> = _installErrors.asStateFlow()

    private val _updateCheckMessage = MutableStateFlow<String?>(null)
    val updateCheckMessage: StateFlow<String?> = _updateCheckMessage.asStateFlow()

    private val _repoDialogVisible = MutableStateFlow(false)
    val repoDialogVisible: StateFlow<Boolean> = _repoDialogVisible.asStateFlow()

    private var updateVersions: Map<String, String> = emptyMap()
    private var marketplaceLoadedOnce = false

    fun start() {
        viewModelScope.launch { _repoUrl.value = extensionRepository.getRepoUrl() }
        refreshInstalled()
    }

    fun selectTab(tab: ExtensionsTab) {
        _tab.value = tab
        if (tab == ExtensionsTab.MARKETPLACE && !marketplaceLoadedOnce) {
            refreshMarketplace()
        }
    }

    fun setTypeFilter(filter: TypeFilter) {
        _typeFilter.value = filter
    }

    fun showRepoDialog() { _repoDialogVisible.value = true }
    fun hideRepoDialog() { _repoDialogVisible.value = false }

    fun saveRepoUrl(url: String) {
        viewModelScope.launch {
            extensionRepository.setRepoUrl(url)
            _repoUrl.value = extensionRepository.getRepoUrl()
            _repoDialogVisible.value = false
            _tab.value = ExtensionsTab.MARKETPLACE
            refreshMarketplace()
        }
    }

    fun refreshMarketplace() {
        viewModelScope.launch {
            _marketplace.value = MarketplaceState.Loading
            extensionRepository.fetchMarketplace().fold(
                onSuccess = { entries ->
                    marketplaceLoadedOnce = true
                    _marketplace.value = MarketplaceState.Loaded(entries)
                },
                onFailure = { e ->
                    _marketplace.value =
                        MarketplaceState.Error(e.message ?: "Failed to load marketplace")
                }
            )
        }
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
        refreshInstalled()
    }

    fun refreshInstalled() {
        viewModelScope.launch {
            val active = pluginRepository.getActivePlugin()
            val list = pluginRepository.getInstalledPlugins().map { entry ->
                InstalledExtension(
                    entry = entry,
                    isActive = entry.id == active?.id,
                    updateVersion = updateVersions[entry.id]
                )
            }
            _installed.value = when (_sortMode.value) {
                SortMode.NAME -> list.sortedBy { it.entry.name.lowercase() }
                SortMode.TYPE -> list.sortedWith(
                    compareBy({ it.entry.typeStr ?: "" }, { it.entry.name.lowercase() })
                )
                SortMode.STATUS -> list.sortedWith(
                    compareByDescending<InstalledExtension> { it.entry.enabled }
                        .thenBy { it.entry.name.lowercase() }
                )
            }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            pluginRepository.setPluginEnabled(id, enabled)
            refreshInstalled()
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            _updateCheckMessage.value = "Checking for updates…"
            extensionRepository.checkUpdates().fold(
                onSuccess = { updates ->
                    updateVersions = updates
                    _updateCheckMessage.value = if (updates.isEmpty()) {
                        "All extensions are up to date"
                    } else {
                        "${updates.size} update(s) available"
                    }
                    refreshInstalled()
                },
                onFailure = { e ->
                    _updateCheckMessage.value = "Update check failed: ${e.message}"
                }
            )
        }
    }

    fun install(entry: MarketplaceEntry) {
        if (entry.id in _installingIds.value) return
        viewModelScope.launch {
            _installingIds.value = _installingIds.value + entry.id
            _installErrors.value = _installErrors.value - entry.id
            extensionRepository.install(entry).fold(
                onSuccess = {
                    updateVersions = updateVersions - entry.id
                    refreshInstalled()
                },
                onFailure = { e ->
                    _installErrors.value =
                        _installErrors.value + (entry.id to (e.message ?: "Install failed"))
                }
            )
            _installingIds.value = _installingIds.value - entry.id
        }
    }

    /** Re-installs an already-installed extension at the marketplace version. */
    fun update(installedId: String) {
        viewModelScope.launch {
            val marketplaceEntries =
                (_marketplace.value as? MarketplaceState.Loaded)?.entries
                    ?: extensionRepository.fetchMarketplace().getOrNull()
                    ?: return@launch
            val entry = marketplaceEntries.find { it.id == installedId } ?: return@launch
            install(entry)
        }
    }

    fun uninstall(id: String) {
        viewModelScope.launch {
            pluginRepository.removePlugin(id)
            refreshInstalled()
        }
    }

}
