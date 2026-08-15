@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.seanimetv.feature.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.seanimetv.components.entranceReveal
import com.example.seanimetv.components.rememberEntranceProgress
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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

