@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.seanimetv.feature.extensions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.seanimetv.components.ActionButton
import com.example.seanimetv.components.PageTitle
import com.example.seanimetv.components.entranceReveal
import com.example.seanimetv.components.rememberEntranceProgress
import com.example.seanimetv.data.extension.MarketplaceEntry
import com.example.seanimetv.data.plugin.model.PluginEntry
import com.example.seanimetv.ui.theme.SeanimeOnSurface
import com.example.seanimetv.ui.theme.SeanimeOnSurfaceMuted
import com.example.seanimetv.ui.theme.SeanimePrimary

// Chip palette matching the Seanime marketplace look

@Composable
fun ExtensionsScreen(viewModel: ExtensionsViewModel, modifier: Modifier = Modifier) {
    val tab by viewModel.tab.collectAsState()
    val installed by viewModel.installed.collectAsState()
    val marketplace by viewModel.marketplace.collectAsState()
    val repoUrl by viewModel.repoUrl.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()
    val installingIds by viewModel.installingIds.collectAsState()
    val installErrors by viewModel.installErrors.collectAsState()
    val updateCheckMessage by viewModel.updateCheckMessage.collectAsState()
    val repoDialogVisible by viewModel.repoDialogVisible.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()

    LaunchedEffect(Unit) { viewModel.start() }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(top = 20.dp, end = 32.dp)) {

            // ── Centered pill toggle: Installed | Marketplace ────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(4.dp)
                    .entranceReveal(rememberEntranceProgress())
            ) {
                TabPill(
                    label = "Installed",
                    selected = tab == ExtensionsTab.INSTALLED,
                    onClick = { viewModel.selectTab(ExtensionsTab.INSTALLED) }
                )
                TabPill(
                    label = "Marketplace",
                    selected = tab == ExtensionsTab.MARKETPLACE,
                    onClick = { viewModel.selectTab(ExtensionsTab.MARKETPLACE) }
                )
            }

            Spacer(Modifier.height(16.dp))

            when (tab) {
                ExtensionsTab.INSTALLED -> InstalledPanel(
                    installed = installed,
                    updateCheckMessage = updateCheckMessage,
                    sortMode = sortMode,
                    onSort = { viewModel.setSortMode(it) },
                    onCheckUpdates = { viewModel.checkForUpdates() },
                    onAddExtensions = { viewModel.showRepoDialog() },
                    onSetEnabled = { id, enabled -> viewModel.setEnabled(id, enabled) },
                    onUninstall = { viewModel.uninstall(it) },
                    onUpdate = { viewModel.update(it) }
                )

                ExtensionsTab.MARKETPLACE -> MarketplacePanel(
                    state = marketplace,
                    repoUrl = repoUrl,
                    typeFilter = typeFilter,
                    installedIds = installed.map { it.entry.id }.toSet(),
                    installingIds = installingIds,
                    installErrors = installErrors,
                    onTypeFilter = { viewModel.setTypeFilter(it) },
                    onRefresh = { viewModel.refreshMarketplace() },
                    onChangeRepository = { viewModel.showRepoDialog() },
                    onInstall = { viewModel.install(it) }
                )
            }
        }

        if (repoDialogVisible) {
            RepositoryDialog(
                currentUrl = repoUrl,
                onSave = { viewModel.saveRepoUrl(it) },
                onClose = { viewModel.hideRepoDialog() }
            )
        }
    }
}

