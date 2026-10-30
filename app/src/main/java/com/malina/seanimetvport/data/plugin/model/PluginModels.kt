package com.malina.seanimetvport.data.plugin.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

// EpisodeServer, VideoSource, and Subtitle are marked @Serializable for JSON-encoded
// NavArgs transport via Navigation Compose 2.8+ type-safe routes. @Parcelize was
// investigated but the kotlin-parcelize compiler plugin does not generate Parcelable
// implementations in the current KGP 2.2.10 + AGP 9.2.1 setup. Navigation Compose 2.8+
// supports serializable types for custom NavArgs natively, making @Serializable sufficient.

@Serializable
data class EpisodeServer(
    val server: String,
    val headers: Map<String, String>,
    val videoSources: List<VideoSource>,
    val provider: String? = null
)

@Serializable
data class VideoSource(
    val url: String,
    val type: String,
    val quality: String,
    val subtitles: List<Subtitle>,
    val label: String? = null
)

@Serializable
data class Subtitle(
    val url: String,
    val id: String = "",
    val language: String = "",
    val isDefault: Boolean = false
)

data class Episode(
    val id: String,
    val number: Int,
    val title: String? = null,
    val thumbnail: String? = null,
    val url: String? = null,
    val provider: String? = null,
    /** Short synopsis for this episode (from the metadata provider). */
    val description: String? = null
)

data class SearchResult(
    val id: String,
    val title: String,
    val url: String? = null,
    val subOrDub: String? = null
)

@Serializable
data class PluginEntry(
    val id: String,
    val name: String,
    val assetPath: String? = null,
    val remoteUrl: String? = null,
    val version: String? = null,
    val description: String? = null,
    /** Local file with the (transpiled) JS payload — used by marketplace installs. */
    val filePath: String? = null,
    val author: String? = null,
    /** Source language of the original payload: "typescript" or "javascript". */
    val language: String? = null,
    /** Content language: "en", "it", "multi"… */
    val lang: String? = null,
    val iconUrl: String? = null,
    /** Seanime extension type: "onlinestream-provider", "plugin", … */
    val typeStr: String? = null,
    val website: String? = null,
    /** True when the payload carries the @seanime-tv-compatible marker. */
    val tvReady: Boolean = false,
    /** Whether the plugin is active. NOT serialized — derived at load time from the
     *  separately-stored set of disabled ids, so a disabled plugin keeps its entry. */
    @Transient val enabled: Boolean = true
)
