package com.example.seanimetv.data.plugin.store

import kotlinx.serialization.Serializable

@Serializable
data class PluginStoreManifest(val plugins: List<PluginStoreEntry>)

@Serializable
data class PluginStoreEntry(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val iconUrl: String? = null,
    val scriptUrl: String,
    val tags: List<String> = emptyList()
)
