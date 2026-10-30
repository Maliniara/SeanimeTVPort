package com.malina.seanimetvport.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.malina.seanimetvport.R
import com.malina.seanimetvport.components.PageTitle
import com.malina.seanimetvport.components.SectionHeader
import com.malina.seanimetvport.components.entranceReveal
import com.malina.seanimetvport.components.rememberEntranceProgress
import com.malina.seanimetvport.data.anilist.AniListAccount
import com.malina.seanimetvport.data.settings.AnimeSettings
import com.malina.seanimetvport.ui.theme.SeanimeBorder
import com.malina.seanimetvport.ui.theme.SeanimeOnSurface
import com.malina.seanimetvport.ui.theme.SeanimeOnSurfaceMuted
import com.malina.seanimetvport.ui.theme.SeanimePrimary
import com.malina.seanimetvport.ui.theme.SeanimeSurface
import com.malina.seanimetvport.ui.theme.SeanimeTVTheme
import androidx.compose.runtime.getValue

// ---------------------------------------------------------------------------
// SettingsScreen — AniList account, Anime presentation prefs (Denshi), TV channel
// ---------------------------------------------------------------------------

/**
 * Settings screen. Plugin management lives entirely in the Extensions screen now;
 * this screen shows the AniList account, the Denshi-style "Anime" preference
 * toggles, and the Android TV home-screen channel switch.
 *
 * Does not apply [SeanimeTVTheme] — the caller (MainActivity / NavHost) wraps
 * the content tree in the theme.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState is SettingsUiState.LoggedOut) {
        if (uiState is SettingsUiState.LoggedOut) {
            onNavigateBack()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 27.dp, bottom = 27.dp)
    ) {
        // Top strip — same pattern as HomeScreen logo strip
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
                PageTitle(text = stringResource(R.string.settings_title))
            }
        }

        val channelEnabled by viewModel.channelEnabled.collectAsState()
        val animeSettings by viewModel.animeSettings.collectAsState()
        val accounts by viewModel.accounts.collectAsState()
        val activeAccountId by viewModel.activeAccountId.collectAsState()

        when (val state = uiState) {
            is SettingsUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = SeanimePrimary) }

            is SettingsUiState.Loaded -> LoadedContent(
                accounts = accounts,
                activeAccountId = activeAccountId,
                onSwitchAccount = viewModel::switchAccount,
                onRemoveAccount = viewModel::removeAccount,
                onAddAccount = onAddAccount,
                onAddGuest = viewModel::addGuest,
                channelEnabled = channelEnabled,
                onToggleChannel = viewModel::setChannelEnabled,
                anime = animeSettings,
                onHideSpoilers = viewModel::setHideSpoilers,
                onHideAudienceScore = viewModel::setHideAudienceScore,
                onEnableAdultContent = viewModel::setEnableAdultContent,
                onBlurAdultContent = viewModel::setBlurAdultContent,
                onDisableCardTrailers = viewModel::setDisableCardTrailers
            )

            is SettingsUiState.Error -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = state.message, color = Color(0xFFE5484D), fontSize = 14.sp)
            }

            is SettingsUiState.LoggedOut -> {
                // Navigation handled by the LaunchedEffect above
            }
        }
    }
}

// ---------------------------------------------------------------------------
// LoadedContent
// ---------------------------------------------------------------------------

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LoadedContent(
    accounts: List<AniListAccount>,
    activeAccountId: Int?,
    onSwitchAccount: (Int) -> Unit,
    onRemoveAccount: (Int) -> Unit,
    onAddAccount: () -> Unit,
    onAddGuest: () -> Unit,
    channelEnabled: Boolean,
    onToggleChannel: (Boolean) -> Unit,
    anime: AnimeSettings,
    onHideSpoilers: (Boolean) -> Unit,
    onHideAudienceScore: (Boolean) -> Unit,
    onEnableAdultContent: (Boolean) -> Unit,
    onBlurAdultContent: (Boolean) -> Unit,
    onDisableCardTrailers: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Account ────────────────────────────────────────────────────────
        SectionHeader(stringResource(R.string.settings_account_section))
        AccountsSection(
            accounts = accounts,
            activeAccountId = activeAccountId,
            onSwitch = onSwitchAccount,
            onRemove = onRemoveAccount,
            onAddAccount = onAddAccount,
            onAddGuest = onAddGuest
        )

        // ── Anime (Denshi presentation prefs) ──────────────────────────────
        SectionHeader("Anime")
        SettingsCard {
            SettingToggleRow(
                title = "Hide anime spoilers",
                subtitle = "Use spoiler-safe episode art and text across continue watching, entry episode lists, and missing episodes.",
                checked = anime.hideSpoilers,
                onToggle = onHideSpoilers
            )
            RowDivider()
            SettingToggleRow(
                title = "Hide audience score",
                subtitle = "If enabled, the audience score will be hidden until you decide to view it.",
                checked = anime.hideAudienceScore,
                onToggle = onHideAudienceScore
            )
            RowDivider()
            SettingToggleRow(
                title = "Enable adult content",
                subtitle = "If disabled, adult content will be hidden from search results and your library.",
                checked = anime.enableAdultContent,
                onToggle = onEnableAdultContent
            )
            SettingToggleRow(
                title = "Blur adult content",
                subtitle = null,
                checked = anime.blurAdultContent,
                onToggle = onBlurAdultContent,
                indent = true,
                enabled = anime.enableAdultContent
            )
            RowDivider()
            SettingToggleRow(
                title = "Disable anime card trailers",
                subtitle = null,
                checked = anime.disableCardTrailers,
                onToggle = onDisableCardTrailers
            )
        }

        // ── TV home screen ─────────────────────────────────────────────────
        SectionHeader("TV home screen")
        SettingsCard {
            SettingToggleRow(
                title = "Discovery channel on home screen",
                subtitle = "Continue Watching + recommendations as a launcher row",
                checked = channelEnabled,
                onToggle = onToggleChannel
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------

@Composable
private fun AccountsSection(
    accounts: List<AniListAccount>,
    activeAccountId: Int?,
    onSwitch: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAddAccount: () -> Unit,
    onAddGuest: () -> Unit
) {
    SettingsCard {
        accounts.forEachIndexed { index, account ->
            if (index > 0) RowDivider()
            AccountRow(
                account = account,
                isActive = account.id == activeAccountId,
                onSwitch = { onSwitch(account.id) },
                onRemove = { onRemove(account.id) }
            )
        }
        if (accounts.isNotEmpty()) RowDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AccountActionButton(label = "＋  Add account", onClick = onAddAccount)
            AccountActionButton(label = "👤  Add guest", onClick = onAddGuest)
        }
    }
}

@Composable
private fun AccountRow(
    account: AniListAccount,
    isActive: Boolean,
    onSwitch: () -> Unit,
    onRemove: () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Tapping the identity area switches to this account (no-op if active).
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (focused) SeanimePrimary.copy(alpha = 0.14f) else Color.Transparent)
                .clickable(interactionSource = src, indication = null, enabled = !isActive, onClick = onSwitch)
                .focusable(interactionSource = src)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    account.isGuest -> Text("👤", fontSize = 18.sp)
                    account.avatarUrl != null -> AsyncImage(
                        model = account.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.size(38.dp).clip(CircleShape)
                    )
                    else -> Text(
                        account.name.take(1).uppercase(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = SeanimeOnSurface
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(account.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SeanimeOnSurface)
                Text(
                    if (account.isGuest) "Local list · no AniList" else "AniList account",
                    fontSize = 11.sp,
                    color = SeanimeOnSurfaceMuted
                )
            }
            if (isActive) {
                Text("● Active", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = SeanimePrimary)
            }
        }
        AccountActionButton(label = "Remove", danger = true, onClick = onRemove)
    }
}

@Composable
private fun AccountActionButton(label: String, onClick: () -> Unit, danger: Boolean = false) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val accent = if (danger) Color(0xFFE5484D) else SeanimePrimary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (focused) accent else accent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .focusable(interactionSource = src)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (focused) SeanimeOnSurface else accent)
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SeanimeSurface.copy(alpha = 0.55f))
            .border(1.dp, SeanimeBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) { content() }
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.06f))
    )
}

/**
 * Focusable settings row with a title, optional subtitle, and a read-only M3
 * switch as the state indicator (the row itself handles the toggle — the switch
 * is never a separate focus target, matching the TV D-pad pattern).
 */
@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    indent: Boolean = false,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val contentAlpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) SeanimePrimary.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = { onToggle(!checked) }
            )
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .padding(start = if (indent) 34.dp else 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = SeanimeOnSurface.copy(alpha = contentAlpha)
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = SeanimeOnSurfaceMuted.copy(alpha = contentAlpha),
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SeanimePrimary,
                uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
            )
        )
    }
}
