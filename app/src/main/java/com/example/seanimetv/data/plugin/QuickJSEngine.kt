package com.example.seanimetv.data.plugin

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.example.seanimetv.data.anilist.model.AniListMedia
import com.example.seanimetv.data.plugin.model.Episode
import com.example.seanimetv.data.plugin.model.EpisodeServer
import com.example.seanimetv.data.plugin.model.PluginEntry
import com.example.seanimetv.data.plugin.model.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient

/**
 * QuickJS-powered JavaScript plugin engine.
 *
 * Loads a JS plugin (from assets or remote URL) into a fresh [QuickJs] instance and
 * exposes the plugin's Provider API — `search`, `findEpisodes`, `findEpisodeServer` —
 * as Kotlin suspend functions.
 *
 * ### Bridge injection
 *
 * Native Kotlin capabilities are exposed to JS in two layers:
 *
 * **Layer 1 — primitive Kotlin-side functions (registered with quickjs-kt)**
 * - `__nativeFetch(paramsJson)` — async; wraps [JsHttpBridge]
 * - `__domLoadDoc(html)` — sync; parses HTML via [JsDomBridge], returns docId
 * - `__domSelect(paramsJson)` — sync; querySelector on a parsed doc, returns elId
 * - `__elFind/First/Attr/Text/Length/Get(...)` — sync; DOM element operations
 * - `console.log/warn/error(...)` — sync; forwards to `android.util.Log`
 * - `$store.get/set(...)` — sync; forwards to [JsStoreBridge]
 *
 * **Layer 2 — JS bootstrap shim (evaluated via `evaluate`)**
 * Defines `fetch(url, options?)` and `LoadDoc(html)` globals that wrap Layer 1
 * functions with idiomatic jQuery-like and Fetch-API-like interfaces that match
 * what the multistream-en plugin expects.
 *
 * ### QuickJS integration tests
 * All integration tests requiring an actual QuickJS native runtime are marked
 * `@Ignore` in [QuickJSEngineCompileTest]. Run them as instrumentation tests on
 * a device or emulator.
 */
@Suppress("FunctionName")
class QuickJSEngine(
    private val context: Context,
    private val storePrefs: SharedPreferences,
    private val okHttpClient: OkHttpClient
) : PluginEngine {

    private val json = Json { ignoreUnknownKeys = true }
    private val httpBridge = JsHttpBridge(okHttpClient)
    private val storeBridge = JsStoreBridge(storePrefs)
    private val domBridge = JsDomBridge()

    private var quickJs: QuickJs? = null
    private var availableServers: List<String> = emptyList()

    // Async result relay: async IIFEs hand their JSON result (or error) back to
    // Kotlin via __resolveResult / __rejectResult instead of returning a Promise
    // from evaluate(). quickjs-kt's public evaluate() does not unwrap a Promise
    // that settles synchronously within the same microtask, so top-level
    // `return await …` yields the raw Promise object. The relay sidesteps that:
    // the IIFE runs to completion while evaluate() drains pending jobs, and the
    // last resolve/reject wins.
    private var asyncResult: String? = null
    private var asyncError: String? = null

    // ---- Element registry for the DOM bridge ----
    // All DOM operations are synchronous and run on the QuickJS thread, so no
    // concurrent access can occur. Cleared on each loadPlugin / close call.
    private val docStore = mutableMapOf<String, JsDoc>()
    private val elemStore = mutableMapOf<String, JsElements>()
    private var idCounter = 0

    private fun nextId(prefix: String): String = "${prefix}_${idCounter++}"

    // ---- Public API ----

    /**
     * Loads a plugin from [entry]'s asset path or remote URL into a fresh QuickJS instance.
     * Any previously loaded plugin is closed first. After this call, [search],
     * [findEpisodes], and [findEpisodeServer] become usable.
     */
    override suspend fun loadPlugin(entry: PluginEntry) {
        quickJs?.close()
        clearRegistry()

        val qjs = QuickJs.create(Dispatchers.Default)
        quickJs = qjs

        registerKotlinBridges(qjs)
        qjs.evaluate<Any?>(JS_BOOTSTRAP_SHIM)

        val pluginCode = withContext(Dispatchers.IO) { resolvePluginCode(entry) }
        // Top-level `class Provider` is block-scoped to this script and invisible
        // to later evaluate() calls, so export it to globalThis in the SAME
        // evaluation where it is still in lexical scope.
        runCatching { qjs.evaluate<Any?>(pluginCode + PROVIDER_EXPORT_SUFFIX) }
            .onFailure { Log.e(TAG, "Plugin payload evaluate failed for '${entry.id}': ${it.message}", it) }

        availableServers = discoverServers(qjs)
    }

    /**
     * No-op guard. There is no bundled plugin to auto-load anymore — a provider
     * is loaded explicitly via [loadPlugin]. When none is active, the callers
     * below fail gracefully with "No plugin loaded".
     */
    private fun ensurePluginLoaded() {
        // Intentionally empty.
    }

    /**
     * Searches for anime matching [query] with optional AniList [media] context and [dub] flag.
     * Auto-loads the built-in plugin on first call if needed.
     */
    override suspend fun search(
        query: String,
        media: AniListMedia?,
        dub: Boolean
    ): Result<List<SearchResult>> =
        runCatching {
            ensurePluginLoaded()
            val qjs = quickJs ?: error("No plugin loaded")
            val resultJson = runProviderCall(
                qjs,
                "new globalThis.__ProviderClass().search(${encodeSearchArgs(query, media, dub)})"
            )
            json.parseToJsonElement(resultJson).jsonArray.map { el ->
                val obj = el.jsonObject
                SearchResult(
                    id = obj["id"]!!.jsonPrimitive.content,
                    title = obj["title"]!!.jsonPrimitive.content,
                    url = obj["url"]?.jsonPrimitive?.contentOrNull,
                    subOrDub = obj["subOrDub"]?.jsonPrimitive?.contentOrNull
                )
            }
        }.onFailure { Log.e(TAG, "search() failed: ${it.message}", it) }

    /**
     * Finds available episodes for [searchId] (an ID returned by [search]).
     */
    override suspend fun findEpisodes(searchId: String): Result<List<Episode>> = runCatching {
        ensurePluginLoaded()
        val qjs = quickJs ?: error("No plugin loaded")
        val resultJson = runProviderCall(
            qjs,
            "new globalThis.__ProviderClass().findEpisodes(${json.encodeToString(searchId)})"
        )
        json.parseToJsonElement(resultJson).jsonArray.map { el ->
            val obj = el.jsonObject
            Episode(
                id = obj["id"]!!.jsonPrimitive.content,
                number = obj["number"]!!.jsonPrimitive.int,
                title = obj["title"]?.jsonPrimitive?.contentOrNull,
                thumbnail = obj["thumbnail"]?.jsonPrimitive?.contentOrNull,
                url = obj["url"]?.jsonPrimitive?.contentOrNull,
                provider = obj["provider"]?.jsonPrimitive?.contentOrNull
            )
        }
    }.onFailure { Log.e(TAG, "findEpisodes() failed: ${it.message}", it) }

    /**
     * Resolves streaming sources for [episode] on [server].
     */
    override suspend fun findEpisodeServer(episode: Episode, server: String): Result<EpisodeServer> =
        runCatching {
            ensurePluginLoaded()
            val qjs = quickJs ?: error("No plugin loaded")
            val episodeJson = buildJsonObject {
                put("id", episode.id)
                put("number", episode.number)
                put("title", episode.title ?: "")
                put("thumbnail", episode.thumbnail ?: "")
                put("url", episode.url ?: "")
                put("provider", episode.provider ?: "")
            }.toString()
            val resultJson = runProviderCall(
                qjs,
                "new globalThis.__ProviderClass().findEpisodeServer($episodeJson, ${json.encodeToString(server)})"
            )
            json.decodeFromString<EpisodeServer>(resultJson)
        }.onFailure { Log.e(TAG, "findEpisodeServer() failed: ${it.message}", it) }

    /**
     * Returns the episode server names reported by the loaded plugin's `getSettings()`.
     * Empty list if no plugin is loaded or the plugin does not declare servers.
     */
    override fun getAvailableServers(): List<String> = availableServers

    /** Closes the QuickJS instance and releases native resources. */
    fun close() {
        quickJs?.close()
        quickJs = null
        clearRegistry()
    }

    // ---- Private helpers ----

    private fun clearRegistry() {
        docStore.clear()
        elemStore.clear()
        idCounter = 0
    }

    private fun resolvePluginCode(entry: PluginEntry): String = when {
        entry.filePath != null ->
            java.io.File(entry.filePath).readText()

        entry.assetPath != null ->
            context.assets.open(entry.assetPath).bufferedReader().use { it.readText() }

        entry.remoteUrl != null -> {
            val resp = httpBridge.fetch(entry.remoteUrl, null)
            if (!resp.ok) {
                error("Failed to load plugin from ${entry.remoteUrl}: HTTP ${resp.status}")
            }
            resp.body
        }

        else -> error("PluginEntry '${entry.id}' has no filePath, assetPath, or remoteUrl")
    }

    private suspend fun discoverServers(qjs: QuickJs): List<String> {
        return try {
            val settingsJson = qjs.evaluate<String>(
                """
                (function() {
                    try {
                        var p = new globalThis.__ProviderClass();
                        var s = p.getSettings();
                        return JSON.stringify(s);
                    } catch (e) {
                        return JSON.stringify({episodeServers:[], supportsDub:false});
                    }
                })()
                """.trimIndent()
            )
            json.parseToJsonElement(settingsJson)
                .jsonObject["episodeServers"]
                ?.jsonArray
                ?.map { it.jsonPrimitive.content }
                ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Could not discover plugin servers: ${e.message}")
            emptyList()
        }
    }

    // ---- Bridge registration ----

    /**
     * Registers all Kotlin-side bridge functions on [qjs].
     * Called synchronously before the JS bootstrap shim is evaluated.
     */
    private fun registerKotlinBridges(qjs: QuickJs) {
        registerConsoleBridge(qjs)
        registerStoreBridge(qjs)
        registerFetchBridge(qjs)
        registerDomBridges(qjs)
        registerResultRelay(qjs)
    }

    /** __resolveResult(json) / __rejectResult(msg) — see [asyncResult] docs. */
    private fun registerResultRelay(qjs: QuickJs) {
        qjs.function<String, Unit>("__resolveResult") { json -> asyncResult = json }
        qjs.function<String, Unit>("__rejectResult") { msg -> asyncError = msg }
    }

    /**
     * Runs a provider method inside an async IIFE that relays its JSON result
     * back through [asyncResult] instead of returning a Promise. [body] is the
     * JS expression evaluated as `await (body)`, e.g.
     * `new Provider().search(<args>)`.
     */
    private suspend fun runProviderCall(qjs: QuickJs, body: String): String {
        asyncResult = null
        asyncError = null
        qjs.evaluate<Any?>(
            """
            (async function() {
                try {
                    var __r = await ($body);
                    __resolveResult(JSON.stringify(__r));
                } catch (e) {
                    var __m = (e && e.message) ? e.message : String(e);
                    if (e && e.stack) __m += " | " + e.stack;
                    __rejectResult(__m);
                }
            })();
            """.trimIndent()
        )
        asyncError?.let { error("Plugin error: $it") }
        return asyncResult ?: error("Plugin returned no result")
    }

    private fun registerConsoleBridge(qjs: QuickJs) {
        qjs.define("console") {
            function("log") { args: Array<Any?> ->
                Log.d(TAG_JS, args.joinToString(" ") { it?.toString() ?: "null" })
            }
            function("warn") { args: Array<Any?> ->
                Log.w(TAG_JS, args.joinToString(" ") { it?.toString() ?: "null" })
            }
            function("error") { args: Array<Any?> ->
                Log.e(TAG_JS, args.joinToString(" ") { it?.toString() ?: "null" })
            }
        }
    }

    private fun registerStoreBridge(qjs: QuickJs) {
        // $store global: get(key) / set(key, value) — both synchronous.
        // Inside define { }, function receives variadic args as Array<Any?>.
        qjs.define("\$store") {
            function("get") { args: Array<Any?> ->
                val key = args.getOrNull(0)?.toString() ?: return@function null
                storeBridge.get(key)
            }
            function("set") { args: Array<Any?> ->
                val key = args.getOrNull(0)?.toString() ?: return@function null
                val value = args.getOrNull(1)?.toString() ?: return@function null
                storeBridge.set(key, value)
                null
            }
        }
    }

    /**
     * Registers `__nativeFetch(paramsJson)` as an async function.
     *
     * [paramsJson] is a JSON object `{url: string, headers: object|null}`.
     * Returns a JSON string `{ok: boolean, status: number, body: string}`.
     *
     * The JS bootstrap shim wraps this as `globalThis.fetch(url, options?)`.
     */
    private fun registerFetchBridge(qjs: QuickJs) {
        qjs.asyncFunction<String, String>("__nativeFetch") { paramsJson ->
            val params = json.parseToJsonElement(paramsJson).jsonObject
            // The fallback must be on the same line as return@asyncFunction to avoid Kotlin
            // treating the bare `return@asyncFunction` as returning Unit before the string.
            val url = params["url"]?.jsonPrimitive?.content
                ?: return@asyncFunction """{"ok":false,"status":0,"body":""}"""
            val headersObj = params["headers"]
            val headersJson: String? = if (headersObj != null && headersObj.toString() != "null") {
                headersObj.toString()
            } else {
                null
            }
            val method = params["method"]?.jsonPrimitive?.contentOrNull ?: "GET"
            val body = params["body"]?.jsonPrimitive?.contentOrNull
            withContext(Dispatchers.IO) {
                val resp = httpBridge.fetch(url, headersJson, method, body)
                buildFetchResponseJson(resp)
            }
        }
    }

    private fun buildFetchResponseJson(resp: JsResponse): String =
        """{"ok":${resp.ok},"status":${resp.status},"body":${json.encodeToString(resp.body)}}"""

    /**
     * Registers DOM bridge functions with JSON-encoded single-string arguments.
     *
     * All functions are synchronous (Jsoup is CPU-only). They use a Kotlin-side
     * element registry (docStore / elemStore) keyed by opaque string IDs.
     * The JS bootstrap shim wraps these as `globalThis.LoadDoc(html)`.
     */
    private fun registerDomBridges(qjs: QuickJs) {
        // __domLoadDoc(html: String) -> docId: String
        qjs.function<String, String>("__domLoadDoc") { html ->
            val id = nextId("doc")
            docStore[id] = domBridge.parse(html)
            id
        }

        // __domSelect(paramsJson: String) -> elId: String
        // paramsJson: {docId, selector}
        qjs.function<String, String>("__domSelect") { paramsJson ->
            val params = json.parseToJsonElement(paramsJson).jsonObject
            val docId = params["docId"]!!.jsonPrimitive.content
            val selector = params["selector"]!!.jsonPrimitive.content
            val doc = docStore[docId] ?: return@function ""
            val id = nextId("el")
            elemStore[id] = doc.select(selector)
            id
        }

        // __elFind(paramsJson: String) -> elId: String
        // paramsJson: {elId, selector}
        qjs.function<String, String>("__elFind") { paramsJson ->
            val params = json.parseToJsonElement(paramsJson).jsonObject
            val elId = params["elId"]!!.jsonPrimitive.content
            val selector = params["selector"]!!.jsonPrimitive.content
            val el = elemStore[elId] ?: return@function ""
            val id = nextId("el")
            elemStore[id] = el.find(selector)
            id
        }

        // __elFirst(elId: String) -> elId: String
        qjs.function<String, String>("__elFirst") { elId ->
            val el = elemStore[elId] ?: return@function ""
            val id = nextId("el")
            elemStore[id] = el.first()
            id
        }

        // __elAttr(paramsJson: String) -> value: String
        // paramsJson: {elId, name}
        qjs.function<String, String>("__elAttr") { paramsJson ->
            val params = json.parseToJsonElement(paramsJson).jsonObject
            val elId = params["elId"]!!.jsonPrimitive.content
            val name = params["name"]!!.jsonPrimitive.content
            elemStore[elId]?.attr(name) ?: ""
        }

        // __elText(elId: String) -> text: String
        qjs.function<String, String>("__elText") { elId ->
            elemStore[elId]?.text() ?: ""
        }

        // __elLength(elId: String) -> length: Int
        qjs.function<String, Int>("__elLength") { elId ->
            elemStore[elId]?.length() ?: 0
        }

        // __elGet(paramsJson: String) -> elId: String
        // Returns the element registry ID of the i-th element.
        // paramsJson: {elId, idx}
        qjs.function<String, String>("__elGet") { paramsJson ->
            val params = json.parseToJsonElement(paramsJson).jsonObject
            val elId = params["elId"]!!.jsonPrimitive.content
            val idx = params["idx"]!!.jsonPrimitive.int
            val el = elemStore[elId] ?: return@function ""
            var resultId = ""
            el.each { i, single ->
                if (i == idx) {
                    val id = nextId("el")
                    elemStore[id] = single
                    resultId = id
                }
            }
            resultId
        }

        // __elFilter(paramsJson: String) -> elId: String  {elId, selector}
        qjs.function<String, String>("__elFilter") { paramsJson ->
            val params = json.parseToJsonElement(paramsJson).jsonObject
            val elId = params["elId"]!!.jsonPrimitive.content
            val selector = params["selector"]!!.jsonPrimitive.content
            val el = elemStore[elId] ?: return@function ""
            val id = nextId("el")
            elemStore[id] = el.filter(selector)
            id
        }

        // __elLast(elId) -> elId
        qjs.function<String, String>("__elLast") { elId ->
            val el = elemStore[elId] ?: return@function ""
            val id = nextId("el")
            elemStore[id] = el.last()
            id
        }

        // __elEq(paramsJson) -> elId  {elId, idx}
        qjs.function<String, String>("__elEq") { paramsJson ->
            val params = json.parseToJsonElement(paramsJson).jsonObject
            val elId = params["elId"]!!.jsonPrimitive.content
            val idx = params["idx"]!!.jsonPrimitive.int
            val el = elemStore[elId] ?: return@function ""
            val id = nextId("el")
            elemStore[id] = el.eq(idx)
            id
        }

        // __elChildren(elId) -> elId
        qjs.function<String, String>("__elChildren") { elId ->
            val el = elemStore[elId] ?: return@function ""
            val id = nextId("el")
            elemStore[id] = el.children()
            id
        }

        // __elParent(elId) -> elId
        qjs.function<String, String>("__elParent") { elId ->
            val el = elemStore[elId] ?: return@function ""
            val id = nextId("el")
            elemStore[id] = el.parent()
            id
        }

        // __elHtml(elId) -> html
        qjs.function<String, String>("__elHtml") { elId ->
            elemStore[elId]?.html() ?: ""
        }
    }

    // ---- JS Bootstrap Shim ----

    companion object {
        private const val TAG = "QuickJSEngine"
        private const val TAG_JS = "Plugin/JS"

        /**
         * Appended after every plugin payload so its top-level `class Provider`
         * (block-scoped to the script) is promoted to a global the separate
         * provider-call evaluations can reach.
         */
        private const val PROVIDER_EXPORT_SUFFIX =
            "\n;try { globalThis.__ProviderClass = Provider; } catch (e) { globalThis.__ProviderLoadError = (e && e.message) ? e.message : String(e); }\n"

        /**
         * Encodes the search arguments into the full Seanime SearchOptions JSON shape
         * expected by the JS plugin's `Provider.search()` method.
         *
         * Exposed as `internal` so it can be directly tested without instantiating
         * [QuickJSEngine] (which requires Android native dependencies).
         */
        internal fun encodeSearchArgs(query: String, media: AniListMedia?, dub: Boolean): String =
            buildJsonObject {
                putJsonObject("media") {
                    put("id", media?.id ?: 0)
                    put("idMal", 0)
                    put("status", media?.status)
                    put("format", media?.format)
                    put("englishTitle", media?.title?.english)
                    put("romajiTitle", media?.title?.romaji)
                    put("episodeCount", media?.episodes)
                    putJsonArray("synonyms") {}
                    put("isAdult", false)
                    putJsonObject("startDate") {
                        put("year", media?.startDate?.year)
                        put("month", media?.startDate?.month)
                        put("day", 0)
                    }
                }
                put("query", query)
                put("dub", dub)
                put("year", media?.startDate?.year)
            }.toString()

        /**
         * JavaScript code evaluated once after Kotlin bridges are registered but
         * before the plugin code is loaded.
         *
         * Defines:
         * - `globalThis.fetch(url, options?)` — Fetch API compatible wrapper around
         *   `__nativeFetch`, returns `{ok, status, text(), json()}`.
         * - `globalThis.LoadDoc(html)` — returns a `$(selector)` function whose
         *   result supports jQuery-like `.each()`, `.find()`, `.first()`, `.attr()`,
         *   `.text()`, `.length()`.
         *
         * All DOM methods (`__domLoadDoc`, `__domSelect`, `__elFind`, etc.) call
         * back into Kotlin synchronously. The fetch wrapper is async, matching the
         * plugin's `await fetch(...)` call sites.
         */
        private val JS_BOOTSTRAP_SHIM = """
(function() {
    "use strict";

    // store.get auto-parses JSON; set auto-stringifies non-strings. The native
    // bridge object's methods are read-only, so REPLACE the whole store object
    // (not its properties) with a wrapper keeping references to native get/set.
    // Wrapped in try/catch so a locked-down global cannot break the shim.
    try {
        var __ns = globalThis.${'$'}store;
        if (__ns && typeof __ns.get === "function") {
            var __rawGet = function(k) { return __ns.get(k); };
            var __rawSet = function(k, v) { return __ns.set(k, v); };
            globalThis.${'$'}store = {
                get: function(key) {
                    var raw = __rawGet(key);
                    if (raw == null || raw === "") return undefined;
                    try { return JSON.parse(raw); } catch (e) { return raw; }
                },
                set: function(key, value) {
                    var toStore = (typeof value === "string") ? value : JSON.stringify(value);
                    return __rawSet(key, toStore);
                }
            };
        }
    } catch (e) { /* store stays as the native bridge */ }

    // fetch(url, options?) -> Promise<{ok, status, text(), json()}>
    // Supports method + body (string) like the Seanime server runtime.
    globalThis.fetch = async function(url, options) {
        var params = JSON.stringify({
            url: url,
            headers: (options && options.headers) ? options.headers : null,
            method: (options && options.method) ? options.method : "GET",
            body: (options && options.body != null) ? String(options.body) : null
        });
        var respJson = await __nativeFetch(params);
        var resp = JSON.parse(respJson);
        return {
            ok: resp.ok,
            status: resp.status,
            text: function() { return resp.body; },
            json: function() { return JSON.parse(resp.body); }
        };
    };

    // LoadDoc(html) -> $(selector)
    // The returned function acts as a jQuery-like root selector.
    globalThis.LoadDoc = function(html) {
        var docId = __domLoadDoc(html);

        function wrapEl(elId) {
            if (!elId) {
                // Return an empty-safe wrapper so callers can chain without null checks.
                return {
                    find: function(sel) { return wrapEl(""); },
                    each: function(cb) { return this; },
                    map: function(cb) { return []; },
                    first: function() { return wrapEl(""); },
                    last: function() { return wrapEl(""); },
                    eq: function(i) { return wrapEl(""); },
                    filter: function(sel) { return wrapEl(""); },
                    children: function() { return wrapEl(""); },
                    parent: function() { return wrapEl(""); },
                    attr: function(name) { return ""; },
                    text: function() { return ""; },
                    html: function() { return ""; },
                    length: function() { return 0; }
                };
            }
            return {
                find: function(sel) {
                    return wrapEl(__elFind(JSON.stringify({elId: elId, selector: sel})));
                },
                each: function(cb) {
                    var len = __elLength(elId);
                    for (var i = 0; i < len; i++) {
                        var childId = __elGet(JSON.stringify({elId: elId, idx: i}));
                        cb(i, wrapEl(childId));
                    }
                    return this;
                },
                map: function(cb) {
                    var out = [];
                    var len = __elLength(elId);
                    for (var i = 0; i < len; i++) {
                        var childId = __elGet(JSON.stringify({elId: elId, idx: i}));
                        out.push(cb(i, wrapEl(childId)));
                    }
                    return out;
                },
                first: function() { return wrapEl(__elFirst(elId)); },
                last: function() { return wrapEl(__elLast(elId)); },
                eq: function(i) { return wrapEl(__elEq(JSON.stringify({elId: elId, idx: i}))); },
                filter: function(sel) {
                    return wrapEl(__elFilter(JSON.stringify({elId: elId, selector: sel})));
                },
                children: function() { return wrapEl(__elChildren(elId)); },
                parent: function() { return wrapEl(__elParent(elId)); },
                attr: function(name) {
                    return __elAttr(JSON.stringify({elId: elId, name: name}));
                },
                text: function() { return __elText(elId); },
                html: function() { return __elHtml(elId); },
                length: function() { return __elLength(elId); }
            };
        }

        return function(selector) {
            var elId = __domSelect(JSON.stringify({docId: docId, selector: selector}));
            return wrapEl(elId);
        };
    };
})();
        """.trimIndent()
    }
}
