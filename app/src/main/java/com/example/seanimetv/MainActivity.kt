package com.example.seanimetv

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.example.seanimetv.data.anilist.AccountStore
import com.example.seanimetv.data.anilist.AniListAccount
import com.example.seanimetv.data.anilist.AniListApiClient
import com.example.seanimetv.data.anilist.TokenDataStore
import com.example.seanimetv.data.anilist.repository.AniListRepositoryImpl
import com.example.seanimetv.data.extension.ExtensionRepositoryImpl
import com.example.seanimetv.data.history.WatchHistoryStore
import com.example.seanimetv.data.metadata.AniSkipClient
import com.example.seanimetv.data.metadata.AniZipClient
import com.example.seanimetv.data.player.SubtitleStylePrefs
import com.example.seanimetv.data.plugin.PluginRepositoryImpl
import com.example.seanimetv.data.settings.AnimeSettingsStore
import com.example.seanimetv.data.plugin.QuickJSEngine
import com.example.seanimetv.data.plugin.TsTranspiler
import com.example.seanimetv.feature.extensions.ExtensionsScreen
import com.example.seanimetv.feature.extensions.ExtensionsViewModel
import com.example.seanimetv.data.plugin.model.EpisodeServer
import com.example.seanimetv.data.tvchannel.TvChannelPublisher
import coil.imageLoader
import coil.request.ImageRequest
import com.example.seanimetv.components.LocalAnimeSettings
import com.example.seanimetv.components.LocalContentRevealed
import com.example.seanimetv.components.LocalIsLowRamDevice
import com.example.seanimetv.data.settings.AnimeSettings
import com.example.seanimetv.feature.account.AccountPickerScreen
import com.example.seanimetv.feature.home.HomeScreen
import com.example.seanimetv.feature.home.HomeUiState
import com.example.seanimetv.feature.home.HomeViewModel
import com.example.seanimetv.feature.login.LoginScreen
import com.example.seanimetv.feature.login.LoginViewModel
import com.example.seanimetv.feature.player.PlayerScreen
import com.example.seanimetv.feature.player.PlayerViewModel
import com.example.seanimetv.feature.series.SeriesDetailScreen
import com.example.seanimetv.feature.series.SeriesDetailViewModel
import com.example.seanimetv.feature.settings.SettingsScreen
import com.example.seanimetv.feature.settings.SettingsUiState
import com.example.seanimetv.feature.settings.SettingsViewModel
import com.example.seanimetv.ui.theme.SeanimeBackground
import com.example.seanimetv.ui.theme.SeanimeOnSurface
import com.example.seanimetv.ui.theme.SeanimeTVTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.example.seanimetv.data.net.StreamingHttpClient
import okhttp3.OkHttpClient

const val ROUTE_LOGIN = "login"
const val ROUTE_ACCOUNT_PICKER = "account_picker"
const val ROUTE_HOME = "home"
const val ROUTE_SERIES = "series/{anilistId}"
const val ROUTE_PLAYER = "player"
const val ROUTE_SETTINGS = "settings"

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Detected once — drives skipping entrance animations / offscreen effects
        // on memory-constrained boxes like the Chromecast with Google TV (HD).
        // isLowRamDevice alone is unreliable: some 1.5–2 GB TV boxes don't set
        // ro.config.low_ram, so also treat anything under 2 GB total as low-RAM.
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val isLowRamDevice = activityManager.isLowRamDevice ||
            memoryInfo.totalMem < 2L * 1024 * 1024 * 1024
        android.util.Log.i(
            "SeanimeTV",
            "lowRam gate: isLowRamDevice=${activityManager.isLowRamDevice} " +
                "totalMem=${memoryInfo.totalMem / (1024 * 1024)}MB -> effective=$isLowRamDevice"
        )

        // Multi-account store; migrates the legacy single-token store on first read.
        val authRepository = AccountStore(
            applicationContext,
            legacy = TokenDataStore(applicationContext)
        )
        val watchHistoryStore = WatchHistoryStore(applicationContext)
        val subtitleStylePrefs = SubtitleStylePrefs(applicationContext)
        val animeSettingsStore = AnimeSettingsStore(applicationContext)
        val aniListApiClient = AniListApiClient(authRepository)
        val aniListRepository = AniListRepositoryImpl(aniListApiClient)
        val tvChannelPublisher = TvChannelPublisher(applicationContext, aniListRepository)
        val pluginRepository = PluginRepositoryImpl(applicationContext)
        // Streaming/plugin traffic only — tolerates the incomplete TLS chains
        // some anime CDNs serve. Never carries the AniList auth token (that has
        // its own strict client). See StreamingHttpClient.
        val okHttpClient = StreamingHttpClient.build()
        // Shared so its per-series episode-thumbnail cache is reused across the
        // Home Continue Watching row and the series detail screen.
        val aniZipClient = AniZipClient(okHttpClient)
        // Auth/OAuth traffic (token exchange). Strict TLS in release; only
        // relaxed in DEBUG so emulators missing the GTS root CA can log in.
        val authHttpClient = OkHttpClient.Builder()
            .apply { if (BuildConfig.DEBUG) StreamingHttpClient.applyLenientTls(this) }
            .build()
        val storePrefs = getSharedPreferences("plugin_store", Context.MODE_PRIVATE)
        val extensionRepository = ExtensionRepositoryImpl(
            context = applicationContext,
            okHttpClient = okHttpClient,
            pluginRepository = pluginRepository,
            transpiler = TsTranspiler(applicationContext)
        )

        val homeViewModel: HomeViewModel by viewModels {
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return HomeViewModel(aniListRepository, watchHistoryStore, animeSettingsStore, authRepository) as T
                }
            }
        }
        setContent {
            SeanimeTVTheme {
                CompositionLocalProvider(LocalIsLowRamDevice provides isLowRamDevice) {
                Surface(modifier = Modifier.fillMaxSize(), shape = RectangleShape) {
                    val pluginReloadTrigger = remember { MutableStateFlow(0) }
                    // QuickJSEngine is remembered so it is created once and survives
                    // recompositions. Released when the activity composition is disposed.
                    val engine = remember { QuickJSEngine(applicationContext, storePrefs, okHttpClient) }
                    DisposableEffect(Unit) {
                        onDispose { engine.close() }
                    }
                    LaunchedEffect(engine, pluginReloadTrigger.collectAsState().value) {
                        runCatching {
                            // No forced default provider anymore — load only when the
                            // user has an active plugin (Multistream EN was removed).
                            pluginRepository.getActivePlugin()?.let { engine.loadPlugin(it) }
                        }
                    }

                    val navController = rememberNavController()
                    val animeSettings by animeSettingsStore.settings.collectAsState(initial = AnimeSettings())

                    // Determine start destination asynchronously to avoid a flash of the
                    // wrong screen. Null means "still loading" — the splash covers it.
                    var startDestination by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(Unit) {
                        // >1 account → let the user pick before Home; 1 → straight in.
                        val accountCount = authRepository.listAccounts().size
                        startDestination = when {
                            accountCount > 1 -> ROUTE_ACCOUNT_PICKER
                            authRepository.getToken() != null -> ROUTE_HOME
                            else -> ROUTE_LOGIN
                        }
                    }

                    // TV home-screen channel: keep its programs fresh while it
                    // is enabled, and jump straight to a series when the app
                    // was launched from one of the channel's programs.
                    LaunchedEffect(Unit) { tvChannelPublisher.refreshIfEnabled() }
                    val deepLinkSeriesId = remember {
                        intent?.data
                            ?.takeIf { it.scheme == "seanimetv" && it.host == "series" }
                            ?.lastPathSegment?.toIntOrNull()?.takeIf { it > 0 }
                    }
                    LaunchedEffect(startDestination) {
                        if (startDestination != null && deepLinkSeriesId != null) {
                            navController.navigate("series/$deepLinkSeriesId")
                        }
                    }

                    // Splash stays up for a minimum beat so the logo animation can
                    // play, then the overlay zooms/fades away revealing the app.
                    var splashMinTimeElapsed by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(1600)
                        splashMinTimeElapsed = true
                    }

                    // The splash also waits for the app to be genuinely ready:
                    // home data loaded AND the hero banner decoded into Coil's
                    // cache — otherwise the reveal exposes spinners and images
                    // still popping in. Login route needs no data. A hard cap
                    // prevents the splash from hanging on a dead network.
                    val homeUiState by homeViewModel.uiState.collectAsState()
                    var contentReady by remember { mutableStateOf(false) }
                    LaunchedEffect(startDestination, homeUiState) {
                        if (contentReady) return@LaunchedEffect
                        val dest = startDestination ?: return@LaunchedEffect
                        if (dest == ROUTE_LOGIN || dest == ROUTE_ACCOUNT_PICKER) {
                            contentReady = true
                            return@LaunchedEffect
                        }
                        when (val s = homeUiState) {
                            is HomeUiState.Success -> {
                                val hero = (s.currentList + s.planningList).firstOrNull()
                                val heroUrl = hero?.bannerImageUrl ?: hero?.coverImageUrl
                                if (heroUrl != null) {
                                    runCatching {
                                        applicationContext.imageLoader.execute(
                                            ImageRequest.Builder(applicationContext)
                                                .data(heroUrl)
                                                .build()
                                        )
                                    }
                                }
                                // One extra frame-settle beat so first-row cards
                                // render before the reveal
                                delay(250)
                                contentReady = true
                            }
                            is HomeUiState.Error -> contentReady = true
                            else -> Unit
                        }
                    }
                    LaunchedEffect(Unit) {
                        delay(8000)
                        contentReady = true
                    }

                    // Entrance choreography across screens waits until the
                    // splash starts dismissing — otherwise it plays hidden
                    // underneath and the reveal shows already-settled content.
                    val splashGone = startDestination != null && splashMinTimeElapsed && contentReady

                    Box(modifier = Modifier.fillMaxSize()) {

                    if (startDestination != null) {
                    CompositionLocalProvider(
                        LocalContentRevealed provides splashGone,
                        LocalAnimeSettings provides animeSettings
                    ) {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination!!
                    ) {
                        composable(ROUTE_LOGIN) {
                            val loginScope = rememberCoroutineScope()
                            val loginViewModel = remember {
                                LoginViewModel(
                                    authRepository = authRepository,
                                    okHttpClient = authHttpClient,
                                    onSuccess = {
                                        loginScope.launch {
                                            // Token is active now — attach the AniList identity so the
                                            // account shows up (name/avatar) in settings & the picker.
                                            runCatching {
                                                val token = authRepository.getToken()
                                                val viewer = aniListRepository.getViewer().getOrNull()
                                                if (token != null && viewer != null) {
                                                    authRepository.addOrUpdateAccount(
                                                        AniListAccount(
                                                            id = viewer.id,
                                                            name = viewer.name,
                                                            avatarUrl = viewer.avatar?.medium,
                                                            token = token
                                                        )
                                                    )
                                                }
                                            }
                                            navController.navigate(ROUTE_HOME) {
                                                popUpTo(ROUTE_LOGIN) { inclusive = true }
                                            }
                                        }
                                    }
                                )
                            }
                            LoginScreen(
                                viewModel = loginViewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        composable(ROUTE_ACCOUNT_PICKER) {
                            AccountPickerScreen(
                                accountRepository = authRepository,
                                onAccountChosen = {
                                    navController.navigate(ROUTE_HOME) {
                                        popUpTo(ROUTE_ACCOUNT_PICKER) { inclusive = true }
                                    }
                                },
                                onAddAccount = { navController.navigate(ROUTE_LOGIN) }
                            )
                        }

                        composable(ROUTE_HOME) {
                            val currentRoute by navController.currentBackStackEntryAsState()
                            LaunchedEffect(currentRoute?.destination?.route) {
                                if (currentRoute?.destination?.route == ROUTE_HOME) {
                                    homeViewModel.retry()
                                }
                            }
                            val extensionsViewModel = remember {
                                ExtensionsViewModel(
                                    extensionRepository = extensionRepository,
                                    pluginRepository = pluginRepository,
                                    onActivePluginChanged = { pluginReloadTrigger.value++ }
                                )
                            }
                            HomeScreen(
                                viewModel = homeViewModel,
                                aniListRepository = aniListRepository,
                                onCardClick = { anilistId ->
                                    navController.navigate("series/$anilistId")
                                },
                                onResumeClick = { anilistId, episode ->
                                    navController.navigate("series/$anilistId?resumeEpisode=$episode")
                                },
                                onSettingsClick = { navController.navigate(ROUTE_SETTINGS) },
                                extensionsPanel = { ExtensionsScreen(viewModel = extensionsViewModel) },
                                aniZipClient = aniZipClient
                            )
                        }

                        composable(
                            route = "series/{anilistId}?resumeEpisode={resumeEpisode}",
                            arguments = listOf(
                                navArgument("resumeEpisode") { defaultValue = "-1" }
                            )
                        ) { backStackEntry ->
                            val anilistId = backStackEntry.arguments
                                ?.getString("anilistId")
                                ?.toIntOrNull()
                                ?: return@composable
                            val resumeEpisode = backStackEntry.arguments
                                ?.getString("resumeEpisode")?.toIntOrNull() ?: -1
                            // Consume the resume request ONCE. Coming BACK from
                            // the player recomposes this entry with the same nav
                            // argument — re-triggering auto-play bounced the user
                            // straight back into the player on every Back press.
                            var resumeConsumed by rememberSaveable { mutableStateOf(false) }
                            val effectiveResume = if (resumeConsumed) -1 else resumeEpisode
                            LaunchedEffect(Unit) { resumeConsumed = true }
                            val seriesViewModel = remember(anilistId) {
                                SeriesDetailViewModel(
                                    aniListRepository = aniListRepository,
                                    engine = engine,
                                    pluginRepository = pluginRepository,
                                    aniZipClient = aniZipClient
                                )
                            }
                            SeriesDetailScreen(
                                viewModel = seriesViewModel,
                                anilistId = anilistId,
                                autoResumeEpisode = effectiveResume,
                                onBack = { navController.popBackStack() },
                                onMediaClick = { id -> navController.navigate("series/$id") },
                                onNavigateToPlayer = { req ->
                                    val encoded = Uri.encode(Json.encodeToString(req.server))
                                    val encTitle = Uri.encode(req.title)
                                    val encEpTitle = Uri.encode(req.episodeTitle)
                                    navController.navigate(
                                        "player?server=$encoded&title=$encTitle&epTitle=$encEpTitle&episode=${req.episodeNumber}&mediaId=${req.mediaId}"
                                    )
                                }
                            )
                        }

                        composable(
                            route = "player?server={server}&title={title}&epTitle={epTitle}&episode={episode}&mediaId={mediaId}",
                            arguments = listOf(
                                navArgument("server") { defaultValue = "" },
                                navArgument("title") { defaultValue = "" },
                                navArgument("epTitle") { defaultValue = "" },
                                navArgument("episode") { defaultValue = "0" },
                                navArgument("mediaId") { defaultValue = "0" }
                            )
                        ) { backStackEntry ->
                            val serverJson = Uri.decode(
                                backStackEntry.arguments?.getString("server") ?: ""
                            )
                            val episodeServer = runCatching {
                                Json.decodeFromString<EpisodeServer>(serverJson)
                            }.getOrNull() ?: run {
                                navController.popBackStack()
                                return@composable
                            }
                            val playerTitle = Uri.decode(backStackEntry.arguments?.getString("title") ?: "")
                                .ifBlank { "Now playing" }
                            val playerEpTitle = Uri.decode(backStackEntry.arguments?.getString("epTitle") ?: "")
                            val playerEpisode = backStackEntry.arguments?.getString("episode")?.toIntOrNull() ?: 1
                            val playerMediaId = backStackEntry.arguments?.getString("mediaId")?.toIntOrNull() ?: 0
                            val playerViewModel = remember {
                                PlayerViewModel(
                                    aniListRepository = aniListRepository,
                                    okHttpClient = okHttpClient,
                                    watchHistoryStore = watchHistoryStore,
                                    subtitleStylePrefs = subtitleStylePrefs,
                                    aniSkipClient = AniSkipClient(okHttpClient)
                                )
                            }
                            PlayerScreen(
                                viewModel = playerViewModel,
                                episodeServer = episodeServer,
                                mediaId = playerMediaId,
                                episodeNumber = playerEpisode,
                                title = playerTitle,
                                episodeTitle = playerEpTitle,
                                episodes = emptyList(),
                                onBack = { navController.popBackStack() },
                                onPlayNext = { nextEpisode ->
                                    // Advance to the next episode by re-entering the
                                    // series detail in resume-mode (reuses the plugin
                                    // resolution + auto-play path). Replace the finished
                                    // player so Back doesn't stack episodes.
                                    navController.navigate("series/$playerMediaId?resumeEpisode=$nextEpisode") {
                                        popUpTo("series/{anilistId}?resumeEpisode={resumeEpisode}") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(ROUTE_SETTINGS) {
                            val settingsViewModel = remember {
                                SettingsViewModel(
                                    authRepository = authRepository,
                                    aniListRepository = aniListRepository,
                                    animeSettingsStore = animeSettingsStore,
                                    accountRepository = authRepository,
                                    channelPublisher = tvChannelPublisher
                                )
                            }
                            val settingsUiState by settingsViewModel.uiState.collectAsState()
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onAddAccount = { navController.navigate(ROUTE_LOGIN) },
                                onNavigateBack = {
                                    // On logout, navigate to login and clear the entire back stack.
                                    // On normal back, pop to the previous screen.
                                    if (settingsUiState is SettingsUiState.LoggedOut) {
                                        navController.navigate(ROUTE_LOGIN) {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    } else {
                                        navController.popBackStack()
                                    }
                                }
                            )
                        }
                    }
                    }
                    }

                    // Splash overlay — drawn on top of the NavHost; exits with a
                    // gentle zoom + fade once startup work and the minimum beat
                    // are both done.
                    AnimatedVisibility(
                        visible = startDestination == null || !splashMinTimeElapsed || !contentReady,
                        exit = fadeOut(animationSpec = tween(durationMillis = 500)) +
                            scaleOut(targetScale = 1.08f, animationSpec = tween(durationMillis = 500))
                    ) {
                        SeanimeSplash()
                    }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun SeanimeSplash() {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val logoScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.72f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "splashLogoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "splashLogoAlpha"
    )
    val wordmarkAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 600, delayMillis = 350),
        label = "splashWordmarkAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SeanimeBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.seanime_logo),
                contentDescription = "Seanime",
                modifier = Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                        alpha = logoAlpha
                    }
                    .clip(RoundedCornerShape(26.dp))
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "SEANIME",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 6.sp,
                color = SeanimeOnSurface,
                modifier = Modifier.graphicsLayer { alpha = wordmarkAlpha }
            )
        }
    }
}
