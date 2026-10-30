@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.malina.seanimetvport.feature.extensions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malina.seanimetvport.components.ActionButton
import com.malina.seanimetvport.components.PageTitle
import com.malina.seanimetvport.data.extension.MarketplaceEntry
import com.malina.seanimetvport.ui.theme.SeanimeOnSurface
import com.malina.seanimetvport.ui.theme.SeanimeOnSurfaceMuted
import com.malina.seanimetvport.ui.theme.SeanimePrimary
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


// ---------------------------------------------------------------------------
// Installed tab
// ---------------------------------------------------------------------------

@Composable
internal fun InstalledPanel(
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
internal fun MarketplacePanel(
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
internal fun RepositoryDialog(
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

