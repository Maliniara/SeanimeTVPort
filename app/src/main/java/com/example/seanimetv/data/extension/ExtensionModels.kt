package com.example.seanimetv.data.extension

import kotlinx.serialization.Serializable

/**
 * One entry of a Seanime marketplace index (e.g. Bas1874/Seanime-Marketplace
 * Main.json — a JSON array of these objects).
 */
@Serializable
data class MarketplaceEntry(
    val id: String,
    val name: String,
    val version: String? = null,
    val author: String? = null,
    val description: String? = null,
    /** "plugin", "onlinestream-provider", "manga-provider", "anime-torrent-provider", "custom-source" */
    val type: String? = null,
    /** Payload language: "typescript" / "javascript" */
    val language: String? = null,
    /** Content language: "en", "it", "multi"… */
    val lang: String? = null,
    val icon: String? = null,
    val manifestURI: String? = null,
    val payloadURI: String? = null,
    val website: String? = null,
    /** VirusTotal flags, e.g. "0/61" or "Unscanned" */
    val flags: String? = null,
    val workingTag: Boolean = false,
    val brokenTag: Boolean = false,
    val deprecatedTag: Boolean = false,
    val scannedOnVersion: String? = null,
    val lastWorkingVersion: String? = null,
    val stars: Int = 0
)

/**
 * A single Seanime extension manifest (the JSON behind [MarketplaceEntry.manifestURI]).
 * The payload is either embedded ([payload]) or referenced ([payloadURI]).
 */
@Serializable
data class ExtensionManifest(
    val id: String,
    val name: String,
    val version: String? = null,
    val author: String? = null,
    val description: String? = null,
    val type: String? = null,
    val language: String? = null,
    val lang: String? = null,
    val icon: String? = null,
    val website: String? = null,
    val payload: String? = null,
    val payloadURI: String? = null,
    val userConfig: ExtensionUserConfig? = null
)

/** Extension user configuration — defaults are substituted into `{{name}}` payload placeholders. */
@Serializable
data class ExtensionUserConfig(
    val version: Int = 1,
    val requiresConfig: Boolean = false,
    val fields: List<ExtensionConfigField> = emptyList()
)

@Serializable
data class ExtensionConfigField(
    val type: String? = null,
    val name: String,
    val label: String? = null,
    val default: String? = null
)
