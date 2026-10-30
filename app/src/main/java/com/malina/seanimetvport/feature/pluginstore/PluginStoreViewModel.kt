package com.malina.seanimetvport.feature.pluginstore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.malina.seanimetvport.data.plugin.PluginRepository
import com.malina.seanimetvport.data.plugin.store.PluginStoreEntry
import com.malina.seanimetvport.data.plugin.store.PluginStoreManifest
import com.malina.seanimetvport.data.plugin.store.PluginStoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PluginStoreUiState {
    object Loading : PluginStoreUiState()
    data class Loaded(
        val catalogue: PluginStoreManifest,
        val installedIds: Set<String>
    ) : PluginStoreUiState()
    data class Error(val message: String) : PluginStoreUiState()
}

class PluginStoreViewModel(
    private val storeRepository: PluginStoreRepository,
    private val pluginRepository: PluginRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PluginStoreUiState>(PluginStoreUiState.Loading)
    val uiState: StateFlow<PluginStoreUiState> = _uiState.asStateFlow()

    fun loadCatalogue() {
        viewModelScope.launch {
            _uiState.value = PluginStoreUiState.Loading
            val catalogueResult = storeRepository.fetchCatalogue()
            val installedIds = runCatching {
                pluginRepository.getInstalledPlugins().map { it.id }.toSet()
            }.getOrElse { emptySet() }
            catalogueResult.fold(
                onSuccess = { manifest ->
                    _uiState.value = PluginStoreUiState.Loaded(
                        catalogue = manifest,
                        installedIds = installedIds
                    )
                },
                onFailure = { e ->
                    val msg = when {
                        e is kotlinx.serialization.SerializationException ||
                        e.message?.startsWith("Unexpected JSON") == true ->
                            "Plugin store returned an invalid response. The store URL may not be configured yet."
                        e.message?.contains("Unable to resolve host") == true ||
                        e.message?.contains("failed to connect") == true ->
                            "Could not reach the plugin store. Check your network connection."
                        else -> e.message ?: "Failed to load plugins"
                    }
                    _uiState.value = PluginStoreUiState.Error(msg)
                }
            )
        }
    }

    fun installPlugin(entry: PluginStoreEntry) {
        if (_uiState.value !is PluginStoreUiState.Loaded) return
        viewModelScope.launch {
            val result = pluginRepository.installPlugin(entry)
            if (result.isSuccess) {
                val snap = _uiState.value as? PluginStoreUiState.Loaded ?: return@launch
                _uiState.value = snap.copy(installedIds = snap.installedIds + entry.id)
            }
        }
    }
}
