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
private val ChipGreen = Color(0xFF52B788)
private val ChipTeal = Color(0xFF4CC9F0)
private val ChipRed = Color(0xFFE5484D)
private val ChipBlue = Color(0xFF6AA6FF)
private val CardBg = Color(0xFF101322)

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

// ---------------------------------------------------------------------------
// Installed tab
// ---------------------------------------------------------------------------

@Composable
private fun InstalledPanel(
    installed: List<InstalledExtension>,
    updateCheckMessage: String?,
    sortMode: SortMode,
    onSort: (SortMode) -> Unit,
    onCheckUpdates: () -> Unit,
    onAddExtensions: () -> Unit,
    onSetEnabled: (String, Boolean) -> Unit,
    onUninstall: (String) -> Unit,
    onUpdate: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                PageTitle("Extensions")
                Text(
                    text = "Manage your plugins and content providers.",
                    fontSize = 12.sp,
                    color = SeanimeOnSurfaceMuted,
                    modifier = Modifier.padding(start = 13.dp, top = 4.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(label = "⟳  Check for updates", onClick = onCheckUpdates)
                ActionButton(label = "⤓  Add extensions", onClick = onAddExtensions, primary = true)
            }
        }

        if (updateCheckMessage != null) {
            Text(
                text = updateCheckMessage,
                fontSize = 12.sp,
                color = SeanimePrimary,
                modifier = Modifier.padding(start = 13.dp, top = 8.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        // Sort selector
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Sort by",
                fontSize = 12.sp,
                color = SeanimeOnSurfaceMuted,
                modifier = Modifier.padding(start = 13.dp, end = 4.dp)
            )
            SortMode.entries.forEach { mode ->
                FilterPill(label = mode.label, selected = sortMode == mode, onClick = { onSort(mode) })
            }
        }

        Spacer(Modifier.height(14.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(installed, key = { it.entry.id }) { ext ->
                InstalledCard(
                    ext = ext,
                    onSetEnabled = { enabled -> onSetEnabled(ext.entry.id, enabled) },
                    onUninstall = { onUninstall(ext.entry.id) },
                    onUpdate = { onUpdate(ext.entry.id) }
                )
            }
        }
    }
}

@Composable
private fun InstalledCard(
    ext: InstalledExtension,
    onSetEnabled: (Boolean) -> Unit,
    onUninstall: () -> Unit,
    onUpdate: () -> Unit
) {
    val entry = ext.entry
    val isBundled = entry.assetPath != null

    ExtensionCardFrame {
        ExtensionCardHeader(
            iconUrl = entry.iconUrl,
            name = entry.name,
            subtitle = entry.id
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = entry.description ?: "",
            fontSize = 11.sp,
            color = SeanimeOnSurfaceMuted,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(10.dp))
        ChipsRow {
            entry.version?.let { Chip(it, ChipGreen, filled = true) }
            entry.author?.let { Chip(it, ChipGreen) }
            entry.lang?.let { Chip(it, ChipRed) }
            if (isBundled) Chip("local", ChipRed)
            if (!entry.enabled) Chip("Disabled", ChipRed, filled = true)
            if (entry.tvReady) Chip("📺 TV Ready", SeanimePrimary, filled = true)
            entry.language?.let {
                Chip(it.replaceFirstChar { c -> c.uppercase() }, ChipBlue)
            }
            ext.updateVersion?.let { Chip("Update → $it", ChipTeal, filled = true) }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (ext.updateVersion != null) {
                    SmallCardButton("Update", onClick = onUpdate, accent = true)
                }
                if (!isBundled) {
                    SmallCardButton("Uninstall", onClick = onUninstall, danger = true)
                }
            }
            // Enable / disable the plugin without removing it (M3 switch).
            Switch(
                checked = entry.enabled,
                onCheckedChange = onSetEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SeanimePrimary,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
                )
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Marketplace tab
// ---------------------------------------------------------------------------

@Composable
private fun MarketplacePanel(
    state: MarketplaceState,
    repoUrl: String,
    typeFilter: TypeFilter,
    installedIds: Set<String>,
    installingIds: Set<String>,
    installErrors: Map<String, String>,
    onTypeFilter: (TypeFilter) -> Unit,
    onRefresh: () -> Unit,
    onChangeRepository: () -> Unit,
    onInstall: (MarketplaceEntry) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                PageTitle("Marketplace")
                Text(
                    text = "Browse and install extensions from the repository.",
                    fontSize = 12.sp,
                    color = SeanimeOnSurfaceMuted,
                    modifier = Modifier.padding(start = 13.dp, top = 4.dp)
                )
                Text(
                    text = "Source: $repoUrl",
                    fontSize = 10.sp,
                    color = SeanimeOnSurfaceMuted.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 13.dp, top = 2.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(label = "⟳  Refresh", onClick = onRefresh)
                ActionButton(label = "⚙  Change repository", onClick = onChangeRepository)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Type filter chips row
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TypeFilter.entries.toList(), key = { it.name }) { filter ->
                FilterPill(
                    label = filter.label,
                    selected = typeFilter == filter,
                    onClick = { onTypeFilter(filter) }
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        when (state) {
            is MarketplaceState.Loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SeanimePrimary)
            }

            is MarketplaceState.Error -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.message, color = SeanimeOnSurfaceMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    ActionButton("Retry", onClick = onRefresh)
                }
            }

            is MarketplaceState.Loaded -> {
                val filtered = if (typeFilter.typeValue == null) {
                    state.entries
                } else {
                    state.entries.filter { it.type == typeFilter.typeValue }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.id }) { entry ->
                        MarketplaceCard(
                            entry = entry,
                            isInstalled = entry.id in installedIds,
                            isInstalling = entry.id in installingIds,
                            error = installErrors[entry.id],
                            onInstall = { onInstall(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketplaceCard(
    entry: MarketplaceEntry,
    isInstalled: Boolean,
    isInstalling: Boolean,
    error: String?,
    onInstall: () -> Unit
) {
    // Installing is only supported for types the TV engine can run
    val installable = entry.type == "onlinestream-provider"

    ExtensionCardFrame {
        ExtensionCardHeader(
            iconUrl = entry.icon,
            name = entry.name,
            subtitle = entry.id
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = entry.description ?: "",
            fontSize = 11.sp,
            color = SeanimeOnSurfaceMuted,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(10.dp))
        ChipsRow {
            entry.version?.let { Chip(it, ChipGreen, filled = true) }
            entry.author?.let { Chip(it, ChipGreen) }
            entry.lang?.let { Chip(it, ChipRed) }
            entry.language?.let {
                Chip(it.replaceFirstChar { c -> c.uppercase() }, ChipBlue)
            }
            if (entry.workingTag) Chip("Working", ChipGreen)
            if (entry.brokenTag) Chip("Broken", ChipRed, filled = true)
            if (entry.deprecatedTag) Chip("Deprecated", ChipRed)
            entry.flags?.let { Chip(if (it == "Unscanned") "VT Unscanned" else "VT $it", ChipBlue) }
            entry.scannedOnVersion?.let { Chip("Scanned $it", ChipBlue) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Status and scan data are hints. Review plugins yourself before installing.",
            fontSize = 9.sp,
            color = SeanimeOnSurfaceMuted.copy(alpha = 0.7f),
            lineHeight = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when {
                isInstalled -> Chip("Installed", SeanimePrimary, filled = true)
                isInstalling -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = SeanimePrimary
                    )
                    Text("Installing…", fontSize = 11.sp, color = SeanimeOnSurfaceMuted)
                }
                installable -> SmallCardButton("⤓ Install", onClick = onInstall, accent = true)
                else -> Text(
                    text = "Not supported on TV",
                    fontSize = 10.sp,
                    color = SeanimeOnSurfaceMuted
                )
            }
            if (error != null) {
                Text(
                    text = error,
                    fontSize = 10.sp,
                    color = ChipRed,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Repository dialog
// ---------------------------------------------------------------------------

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun RepositoryDialog(
    currentUrl: String,
    onSave: (String) -> Unit,
    onClose: () -> Unit
) {
    BackHandler { onClose() }
    var url by remember { mutableStateOf(currentUrl) }
    val fieldFocuser = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        try { fieldFocuser.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.80f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(720.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CardBg)
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .focusProperties { exit = { FocusRequester.Cancel } }
                .focusGroup()
                .padding(24.dp)
        ) {
            Text(
                text = "Extension repository",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = SeanimeOnSurface
            )
            Text(
                text = "Marketplace index URL (JSON array of extensions, e.g. a raw GitHub link)",
                fontSize = 11.sp,
                color = SeanimeOnSurfaceMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(14.dp))
            BasicTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                textStyle = TextStyle(color = SeanimeOnSurface, fontSize = 13.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(SeanimePrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldFocuser)
                    .border(1.5.dp, SeanimePrimary, RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            )
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton("Cancel", onClick = onClose)
                    ActionButton("Save", onClick = { onSave(url) }, primary = true)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared card building blocks
// ---------------------------------------------------------------------------

@Composable
private fun ExtensionCardFrame(content: @Composable () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg)
            .border(
                width = 1.5.dp,
                color = if (isFocused) SeanimePrimary else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
            )
            .focusable(interactionSource = interactionSource)
            .padding(14.dp)
    ) {
        content()
    }
}

@Composable
private fun ExtensionCardHeader(iconUrl: String?, name: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            if (!iconUrl.isNullOrBlank()) {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = name.take(1).uppercase(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = SeanimeOnSurface
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = SeanimeOnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = SeanimeOnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Wrapping chips row. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChipsRow(content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

@Composable
private fun Chip(text: String, color: Color, filled: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (filled) color.copy(alpha = 0.22f) else Color.Transparent)
            .border(1.dp, color.copy(alpha = if (filled) 0.6f else 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1
        )
    }
}

@Composable
private fun SmallCardButton(
    label: String,
    onClick: () -> Unit,
    accent: Boolean = false,
    danger: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val baseColor = when {
        danger -> ChipRed
        accent -> SeanimePrimary
        else -> Color.White.copy(alpha = 0.7f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isFocused && danger -> ChipRed.copy(alpha = 0.25f)
                    isFocused -> SeanimePrimary.copy(alpha = 0.25f)
                    accent -> SeanimePrimary.copy(alpha = 0.15f)
                    else -> Color.White.copy(alpha = 0.06f)
                }
            )
            .border(
                width = 1.dp,
                color = if (isFocused) baseColor else baseColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isFocused) SeanimeOnSurface else baseColor
        )
    }
}

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                when {
                    selected -> Color.White.copy(alpha = 0.14f)
                    isFocused -> Color.White.copy(alpha = 0.08f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = 1.5.dp,
                color = if (isFocused) SeanimePrimary else Color.Transparent,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) SeanimeOnSurface else SeanimeOnSurfaceMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    selected -> SeanimePrimary.copy(alpha = 0.25f)
                    isFocused -> Color.White.copy(alpha = 0.10f)
                    else -> Color.White.copy(alpha = 0.06f)
                }
            )
            .border(
                width = 1.5.dp,
                color = if (isFocused) SeanimePrimary else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = when {
                selected -> SeanimePrimary
                isFocused -> SeanimeOnSurface
                else -> SeanimeOnSurfaceMuted
            },
            fontWeight = if (selected || isFocused) FontWeight.Medium else FontWeight.Normal
        )
    }
}
