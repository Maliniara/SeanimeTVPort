package com.malina.seanimetvport.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.malina.seanimetvport.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.malina.seanimetvport.ui.theme.SeanimeBorder
import com.malina.seanimetvport.ui.theme.SeanimeOnSurface
import com.malina.seanimetvport.ui.theme.ExpressiveShapes
import com.malina.seanimetvport.ui.theme.SeanimePrimary

enum class NavSection { HOME, SEARCH, SCHEDULE, LIBRARY, ANILIST, EXTENSIONS }
enum class LibraryTab { ALL, WATCHING, PLANNING, COMPLETED, DROPPED, PAUSED }

private val SidebarBgTop = Color(0xFF0C0E18)
private val SidebarBgBottom = Color(0xFF05060B)
private val SidebarBg = Color(0xFF080A10)
// Expressive: rounder floating sidebar card.
private val CardShape = ExpressiveShapes.large

private data class NavItem(
    val section: NavSection,
    val label: String,
    val icon: ImageVector
)

private val NAV_ITEMS = listOf(
    NavItem(NavSection.HOME, "Home", Icons.Default.Home),
    NavItem(NavSection.SEARCH, "Search", Icons.Default.Search),
    NavItem(NavSection.SCHEDULE, "Schedule", Icons.Default.DateRange),
    NavItem(NavSection.LIBRARY, "My Library", Icons.Default.VideoLibrary),
    NavItem(NavSection.ANILIST, "Discovery", Icons.Default.Explore),
    NavItem(NavSection.EXTENSIONS, "Extensions", Icons.Default.Extension)
)

private val MiniWidth = 56.dp
private val FullWidth = 220.dp
private val ItemHeight = 46.dp

@Composable
fun SideNavigation(
    selectedSection: NavSection,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSectionSelected: (NavSection) -> Unit,
    onSettingsClick: () -> Unit,
    onExitRight: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Spring animations give the floating card its bounce on open/close.
    // Width overshoot past 220dp (and slightly below 56dp) is safe: labels
    // never rewrap (softWrap=false) and the rounded clip cuts cleanly.
    val bounceSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val expandProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = bounceSpring,
        label = "sidebarExpand"
    )
    // Animate width in sync with expandProgress so the clip boundary moves smoothly.
    // Without this, the Box snaps to MiniWidth while expandProgress is still > 0,
    // leaving full-width content momentarily visible outside the collapsed bounds.
    val animatedWidth by animateDpAsState(
        targetValue = if (expanded) FullWidth else MiniWidth,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "sidebarWidth"
    )

    // Floating rounded card: detached from the screen edge with its own
    // margins, rounded corners, and a hairline border for lift.
    Box(
        modifier = modifier
            .padding(start = 12.dp, top = 16.dp, bottom = 16.dp)
            .width(animatedWidth)
            .fillMaxHeight()
            .graphicsLayer {
                clip = true
                shape = CardShape
            }
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.07f),
                shape = CardShape
            )
    ) {
        // Card background — subtle vertical gradient gives depth
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        0f to SidebarBgTop,
                        1f to SidebarBgBottom
                    )
                )
        )

        // Right-edge gradient: masks content near the clip boundary during animation,
        // eliminating hard-cut edges. Width 64dp spans ~1/3 of the icon strip.
        // Alpha is highest at mid-animation (peaks at expandProgress=0.5) so the
        // gradient is invisible when fully collapsed or fully expanded.
        val gradientAlpha = 1f - kotlin.math.abs(expandProgress * 2f - 1f)
        if (gradientAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .alpha(gradientAlpha)
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            1f to SidebarBg
                        )
                    )
            )
        }

        // NOTE: the column is deliberately clamped by the animated parent width
        // (no unbounded measurement) so nav items' focus rects stay inside the
        // collapsed 56dp rail — otherwise their 220dp rects overlap the content
        // area and D-pad LEFT from content can't find them as candidates.
        // Labels never rewrap because they use softWrap=false + maxLines=1.
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(FullWidth)
                .onFocusChanged { onExpandedChange(it.hasFocus) }
                // The expanded card overlaps the content area, so spatial focus
                // search can't find a "strictly to the right" candidate — exit
                // to the content explicitly on D-pad RIGHT.
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.DirectionRight && onExitRight != null) {
                        if (event.type == KeyEventType.KeyDown) onExitRight()
                        true
                    } else {
                        false
                    }
                }
                .focusGroup(),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(Modifier.height(18.dp))
            LogoArea(expandProgress)
            Spacer(Modifier.height(16.dp))

            NAV_ITEMS.forEach { item ->
                NavRailItem(
                    icon = item.icon,
                    label = item.label,
                    isSelected = selectedSection == item.section,
                    expandProgress = expandProgress,
                    onClick = { onSectionSelected(item.section) }
                )
            }

            // Settings pinned to the bottom of the rail
            Spacer(Modifier.weight(1f))
            SectionDivider()
            Spacer(Modifier.height(6.dp))

            NavRailItem(
                icon = Icons.Default.Settings,
                label = "Settings",
                isSelected = false,
                expandProgress = expandProgress,
                onClick = onSettingsClick
            )
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun LogoBadge() {
    Image(
        painter = painterResource(R.drawable.seanime_logo),
        contentDescription = "Seanime",
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
    )
}

@Composable
private fun LogoArea(expandProgress: Float) {
    // Single-layer layout: the badge is drawn exactly ONCE at a fixed
    // position and never fades — crossfading two copies of the same glyph
    // dips combined opacity mid-animation and reads as flicker. Only the
    // wordmark fades with the expansion.
    Box(
        modifier = Modifier
            .width(FullWidth)
            .height(28.dp)
            .clip(RectangleShape)
    ) {
        Box(
            modifier = Modifier.size(MiniWidth, 28.dp),
            contentAlignment = Alignment.Center
        ) {
            LogoBadge()
        }
        Text(
            text = "SEANIME",
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.5.sp,
            color = SeanimeOnSurface,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = MiniWidth)
                .graphicsLayer { alpha = expandProgress }
        )
    }
}

@Composable
private fun NavRailItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    expandProgress: Float,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val iconScale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1f,
        animationSpec = tween(130),
        label = "navIconScale"
    )
    // Smoothly animated highlight + content colors — no hard state jumps
    val pillColor by animateColorAsState(
        targetValue = when {
            isSelected -> SeanimePrimary.copy(alpha = 0.16f)
            isFocused -> Color.White.copy(alpha = 0.10f)
            else -> Color.Transparent
        },
        animationSpec = tween(160),
        label = "navPill"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected -> SeanimePrimary
            isFocused -> SeanimeOnSurface
            else -> SeanimeOnSurface.copy(alpha = 0.50f)
        },
        animationSpec = tween(160),
        label = "navContent"
    )

    Box(
        modifier = Modifier
            .width(FullWidth)
            .height(ItemHeight)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
    ) {
        // Selected accent bar at the left edge — visible in BOTH mini and
        // expanded states, so the active section always reads at a glance.
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .height(18.dp)
                    .background(SeanimePrimary, RoundedCornerShape(topEnd = 1.5.dp, bottomEnd = 1.5.dp))
            )
        }

        // Single-layer layout: one pill whose width follows the expansion, one
        // icon drawn ONCE at a fixed position, and a label that only fades.
        // The old two-layer crossfade drew the icon twice at ~50% alpha each
        // mid-animation, which dipped combined opacity and read as flicker.
        val pillStart = lerp(8.dp, 10.dp, expandProgress)
        val pillWidth = lerp(40.dp, FullWidth - 20.dp, expandProgress)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = pillStart)
                .size(pillWidth, 34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(pillColor)
        )
        Box(
            modifier = Modifier.size(MiniWidth, ItemHeight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                tint = contentColor
            )
        }
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 52.dp)
                .graphicsLayer { alpha = expandProgress }
        )
    }
}

@Composable
private fun SectionDivider() {
    Box(
        modifier = Modifier
            .width(FullWidth)
            .padding(horizontal = 10.dp)
            .height(1.dp)
            .background(SeanimeBorder.copy(alpha = 0.50f))
    )
}
