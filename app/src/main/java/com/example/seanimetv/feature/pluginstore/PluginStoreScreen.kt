package com.example.seanimetv.feature.pluginstore

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.seanimetv.R
import com.example.seanimetv.components.PageTitle
import com.example.seanimetv.components.entranceReveal
import com.example.seanimetv.components.rememberEntranceProgress
import com.example.seanimetv.data.plugin.store.PluginStoreEntry
import com.example.seanimetv.ui.theme.SeanimeBorder
import com.example.seanimetv.ui.theme.SeanimeOnSurface
import com.example.seanimetv.ui.theme.SeanimePrimary
import com.example.seanimetv.ui.theme.SeanimeSurfaceVariant
import com.example.seanimetv.ui.theme.SeanimeTVTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PluginStoreScreen(
    viewModel: PluginStoreViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadCatalogue() }

    Column(modifier = modifier.fillMaxSize()) {
        // Top strip — same 48dp height / 48dp start inset pattern as SettingsScreen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(start = 48.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.entranceReveal(rememberEntranceProgress())
            ) {
                Button(onClick = onNavigateBack) {
                    Text(text = stringResource(R.string.settings_back))
                }
                Spacer(modifier = Modifier.width(16.dp))
                PageTitle(text = stringResource(R.string.plugin_store_title))
            }
        }

        // Content area — branches on current UI state
        when (val currentState = uiState) {
            is PluginStoreUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is PluginStoreUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(currentState.message)
                        Button(onClick = { viewModel.loadCatalogue() }) {
                            Text(stringResource(R.string.plugin_store_retry))
                        }
                    }
                }
            }

            is PluginStoreUiState.Loaded -> {
                if (currentState.catalogue.plugins.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.plugin_store_empty))
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 48.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        currentState.catalogue.plugins.forEach { entry ->
                            PluginCard(
                                entry = entry,
                                isInstalled = entry.id in currentState.installedIds,
                                onInstall = { viewModel.installPlugin(entry) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PluginCard(
    entry: PluginStoreEntry,
    isInstalled: Boolean,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "pluginCardScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.hasFocus }
            .drawWithContent {
                val s = scale
                scale(scale = s, pivot = center) {
                    this@drawWithContent.drawContent()
                }
            }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) SeanimePrimary else SeanimeBorder,
                shape = RoundedCornerShape(8.dp)
            )
            .background(SeanimeSurfaceVariant, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon slot — 56×56dp
            if (entry.iconUrl != null) {
                AsyncImage(
                    model = entry.iconUrl,
                    contentDescription = entry.name,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                )
            }

            // Name, author/version, description, tags
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = SeanimeOnSurface
                )
                Text(
                    text = "${entry.author} · v${entry.version}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SeanimeOnSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = SeanimeOnSurface.copy(alpha = 0.7f)
                )
                if (entry.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        entry.tags.take(3).forEach { tag ->
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .background(
                                        SeanimePrimary.copy(alpha = 0.15f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                color = SeanimePrimary
                            )
                        }
                    }
                }
            }

            // Install / Installed button
            if (!isInstalled) {
                Button(onClick = onInstall) {
                    Text(stringResource(R.string.plugin_store_install))
                }
            } else {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.alpha(0.5f)
                ) {
                    Text(stringResource(R.string.plugin_store_installed))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFF0C0E14)
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PluginStoreScreenLoadingPreview() {
    SeanimeTVTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C0E14)
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PluginCardUninstalledPreview() {
    val sampleEntry = PluginStoreEntry(
        id = "sample",
        name = "KickAssAnime",
        version = "1.0.0",
        description = "Streams from KAA",
        author = "kaa-dev",
        scriptUrl = "https://example.com/kaa.js"
    )
    SeanimeTVTheme {
        PluginCard(entry = sampleEntry, isInstalled = false, onInstall = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0C0E14)
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PluginCardInstalledPreview() {
    val sampleEntry = PluginStoreEntry(
        id = "sample",
        name = "KickAssAnime",
        version = "1.0.0",
        description = "Streams from KAA",
        author = "kaa-dev",
        scriptUrl = "https://example.com/kaa.js"
    )
    SeanimeTVTheme {
        PluginCard(entry = sampleEntry, isInstalled = true, onInstall = {})
    }
}
