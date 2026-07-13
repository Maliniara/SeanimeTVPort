package com.example.seanimetv.data.plugin

import android.content.Context
import com.dokar.quickjs.QuickJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * TypeScript → JavaScript transpiler running Sucrase inside an ephemeral
 * QuickJS instance.
 *
 * Seanime's Go server transpiles TypeScript extension payloads before
 * execution; this mirrors that behaviour on-device. The Sucrase bundle
 * (~560KB IIFE exposing `__sucraseTransform(code)`) is evaluated once per
 * [transpile] call in a fresh QuickJS context which is closed immediately
 * after — installs are rare, so the cost is paid only when installing or
 * updating an extension. The transpiled output is cached to disk by the
 * caller ([com.example.seanimetv.data.extension.ExtensionRepositoryImpl]).
 */
class TsTranspiler(private val context: Context) {

    private val json = Json

    suspend fun transpile(typescriptCode: String): Result<String> = runCatching {
        withContext(Dispatchers.Default) {
            val bundle = context.assets.open(SUCRASE_ASSET).bufferedReader().use { it.readText() }
            val qjs = QuickJs.create(Dispatchers.Default)
            try {
                qjs.evaluate<Any?>(bundle)
                qjs.evaluate<String>(
                    "__sucraseTransform(${json.encodeToString(typescriptCode)})"
                )
            } finally {
                qjs.close()
            }
        }
    }

    private companion object {
        const val SUCRASE_ASSET = "sucrase.bundle.js"
    }
}
