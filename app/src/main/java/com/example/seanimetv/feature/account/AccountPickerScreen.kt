package com.example.seanimetv.feature.account

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.seanimetv.data.anilist.AccountRepository
import com.example.seanimetv.data.anilist.AniListAccount
import com.example.seanimetv.ui.theme.ExpressiveMotion
import com.example.seanimetv.ui.theme.SeanimeBackground
import com.example.seanimetv.ui.theme.SeanimeOnSurface
import com.example.seanimetv.ui.theme.SeanimeOnSurfaceMuted
import com.example.seanimetv.ui.theme.SeanimePrimary
import com.example.seanimetv.ui.theme.SeanimeSurface
import kotlinx.coroutines.launch

/**
 * Startup account chooser, shown before Home when more than one account is
 * signed in. Netflix-style tiles; picking one activates it and proceeds. Also
 * offers an "Add account" tile that launches the AniList login.
 */
@Composable
fun AccountPickerScreen(
    accountRepository: AccountRepository,
    onAccountChosen: () -> Unit,
    onAddAccount: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accounts by accountRepository.accounts.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }

    Box(
        modifier = modifier.fillMaxSize().background(SeanimeBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(36.dp)
        ) {
            Text(
                text = "Who's watching?",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = SeanimeOnSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                accounts.forEachIndexed { index, account ->
                    AccountTile(
                        account = account,
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                        onClick = {
                            scope.launch {
                                accountRepository.setActiveAccount(account.id)
                                onAccountChosen()
                            }
                        }
                    )
                }
                AddAccountTile(onClick = onAddAccount)
            }
        }
    }

    LaunchedEffect(accounts) {
        if (accounts.isNotEmpty()) runCatching { firstFocus.requestFocus() }
    }
}

@Composable
private fun AccountTile(
    account: AniListAccount,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "accountTileScale"
    )
    Column(
        modifier = modifier
            .width(150.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(SeanimeSurface)
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) SeanimePrimary else Color.White.copy(alpha = 0.12f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            when {
                account.isGuest -> Text("👤", fontSize = 48.sp)
                account.avatarUrl != null -> AsyncImage(
                    model = account.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.size(112.dp).clip(CircleShape)
                )
                else -> Text(
                    account.name.take(1).uppercase(),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = SeanimeOnSurface
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = account.name,
            fontSize = 15.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            color = if (focused) SeanimeOnSurface else SeanimeOnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (account.isGuest) "Local list" else "AniList",
            fontSize = 11.sp,
            color = SeanimeOnSurfaceMuted.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun AddAccountTile(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.08f else 1f,
        animationSpec = ExpressiveMotion.spatialFast(),
        label = "addTileScale"
    )
    Column(
        modifier = Modifier
            .width(150.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.04f))
                .border(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) SeanimePrimary else Color.White.copy(alpha = 0.12f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("＋", fontSize = 44.sp, color = if (focused) SeanimePrimary else SeanimeOnSurfaceMuted)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Add account",
            fontSize = 15.sp,
            color = if (focused) SeanimeOnSurface else SeanimeOnSurfaceMuted
        )
    }
}
