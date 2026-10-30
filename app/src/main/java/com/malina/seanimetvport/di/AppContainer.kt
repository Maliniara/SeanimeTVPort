package com.malina.seanimetvport.di

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import com.malina.seanimetvport.BuildConfig
import com.malina.seanimetvport.data.anilist.AccountStore
import com.malina.seanimetvport.data.anilist.AniListApiClient
import com.malina.seanimetvport.data.anilist.repository.AniListRepository
import com.malina.seanimetvport.data.anilist.repository.AniListRepositoryImpl
import com.malina.seanimetvport.data.extension.ExtensionRepositoryImpl
import com.malina.seanimetvport.data.history.WatchHistoryStore
import com.malina.seanimetvport.data.metadata.AniSkipClient
import com.malina.seanimetvport.data.metadata.AniZipClient
import com.malina.seanimetvport.data.net.StreamingHttpClient
import com.malina.seanimetvport.data.player.SubtitleStylePrefs
import com.malina.seanimetvport.data.plugin.PluginRepositoryImpl
import com.malina.seanimetvport.data.plugin.TsTranspiler
import com.malina.seanimetvport.data.settings.AnimeSettingsStore
import com.malina.seanimetvport.data.tvchannel.TvChannelPublisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * The application's dependency graph, built once from the application context and
 * held for the process lifetime by `SeanimeApp`.
 *
 * Deliberately hand-written rather than generated: this is a single-module app
 * whose graph is a dozen shared singletons, so a DI framework would add annotation
 * processing and build complexity without a matching payoff. Everything is `by
 * lazy`, so nothing is constructed until a screen actually needs it.
 *
 * Read dependencies from here instead of re-instantiating them at call sites —
 * several own caches or persistent stores that only behave correctly when shared.
 */
class AppContainer(private val context: Context) {

    // ── Networking ───────────────────────────────────────────────────────
    /**
     * Streaming / plugin traffic only. Tolerates the incomplete TLS chains some
     * anime CDNs serve, and therefore MUST NEVER carry the AniList auth token —
     * [authHttpClient] exists for that.
     */
    val streamingHttpClient: OkHttpClient by lazy { StreamingHttpClient.build() }

    /**
     * Auth / OAuth traffic (token exchange). Strict TLS in release builds; lenient
     * only in DEBUG so emulators missing the GTS root CA can still log in.
     */
    val authHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .apply { if (BuildConfig.DEBUG) StreamingHttpClient.applyLenientTls(this) }
            .build()
    }

    // ── Persistence ──────────────────────────────────────────────────────
    /** Multi-account store; migrates the legacy single-token store on first read. */
    val accountStore: AccountStore by lazy {
        AccountStore(context)
    }
    val watchHistoryStore: WatchHistoryStore by lazy { WatchHistoryStore(context) }
    val subtitleStylePrefs: SubtitleStylePrefs by lazy { SubtitleStylePrefs(context) }
    val animeSettingsStore: AnimeSettingsStore by lazy { AnimeSettingsStore(context) }
    val pluginStorePrefs: SharedPreferences by lazy {
        context.getSharedPreferences("plugin_store", Context.MODE_PRIVATE)
    }

    /** Process-lifetime scope for container-level wiring (see [aniListRepository]). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ── AniList ──────────────────────────────────────────────────────────
    val aniListRepository: AniListRepository by lazy {
        AniListRepositoryImpl(AniListApiClient(accountStore)).also { repo ->
            // The repository caches the viewer for 30 minutes under a key that is
            // NOT account-scoped. It used to be rebuilt in MainActivity.onCreate, so
            // an activity recreation happened to clear it; as a process-lifetime
            // singleton nothing would, and switching accounts kept serving the
            // previous account's identity — and therefore its library. Drop the
            // cache whenever the active account changes.
            appScope.launch {
                accountStore.activeAccountId
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { repo.clearCache() }
            }
        }
    }

    // ── Metadata ─────────────────────────────────────────────────────────
    /**
     * Shared so its per-series episode-thumbnail cache is reused across the Home
     * Continue Watching row and the series detail screen.
     */
    val aniZipClient: AniZipClient by lazy { AniZipClient(streamingHttpClient) }

    /** Crowd-sourced intro/outro skip times. Anonymous — carries no auth token. */
    val aniSkipClient: AniSkipClient by lazy { AniSkipClient(streamingHttpClient) }

    // ── Plugins / extensions ─────────────────────────────────────────────
    val pluginRepository: PluginRepositoryImpl by lazy { PluginRepositoryImpl(context) }

    val extensionRepository: ExtensionRepositoryImpl by lazy {
        ExtensionRepositoryImpl(
            context = context,
            okHttpClient = streamingHttpClient,
            pluginRepository = pluginRepository,
            transpiler = TsTranspiler(context)
        )
    }

    // ── TV integration ───────────────────────────────────────────────────
    val tvChannelPublisher: TvChannelPublisher by lazy {
        TvChannelPublisher(context, aniListRepository)
    }

    /**
     * True on memory-constrained boxes (e.g. Chromecast with Google TV HD), where
     * entrance animations and offscreen effects are skipped. `isLowRamDevice` alone
     * is unreliable — some 1.5–2 GB TV boxes never set `ro.config.low_ram` — so
     * anything under 2 GB total counts as low-RAM too.
     */
    val isLowRamDevice: Boolean by lazy {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val lowRam = am.isLowRamDevice || memoryInfo.totalMem < LOW_RAM_THRESHOLD_BYTES
        android.util.Log.i(
            "SeanimeTV",
            "lowRam gate: isLowRamDevice=${am.isLowRamDevice} " +
                "totalMem=${memoryInfo.totalMem / (1024 * 1024)}MB -> effective=$lowRam"
        )
        lowRam
    }

    private companion object {
        const val LOW_RAM_THRESHOLD_BYTES = 2L * 1024 * 1024 * 1024
    }
}
