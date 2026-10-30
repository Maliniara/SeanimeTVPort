package com.malina.seanimetvport.data.plugin

import android.content.SharedPreferences

/**
 * Key-value store bridge for the QuickJS plugin engine.
 *
 * Exposes Android [SharedPreferences] as the `${'$'}store` global in plugin JavaScript.
 * Both [get] and [set] are synchronous — this is required because the plugin calls
 * `${'$'}store.get(key)` and `${'$'}store.set(key, value)` without await.
 *
 * JSON serialization for complex values is handled by the plugin's own `storeGet` /
 * `storeSet` TypeScript wrappers (which call JSON.parse / JSON.stringify). This bridge
 * stores and retrieves raw strings only.
 *
 * Bridge failures are caught and silenced — they must never propagate as exceptions
 * into the QuickJS runtime.
 */
class JsStoreBridge(private val prefs: SharedPreferences) {

    /**
     * Returns the raw string stored under [key], or null if the key does not exist.
     */
    fun get(key: String): String? = try {
        prefs.getString(key, null)
    } catch (e: Exception) {
        null
    }

    /**
     * Stores [value] under [key] using SharedPreferences async commit.
     *
     * The plugin's TypeScript wrapper serializes complex objects to JSON strings before
     * calling this method, so [value] is always a plain string at this layer.
     */
    fun set(key: String, value: String) {
        try {
            prefs.edit().putString(key, value).apply()
        } catch (e: Exception) {
            // Bridge failures must not crash QuickJS; swallow silently.
        }
    }
}
