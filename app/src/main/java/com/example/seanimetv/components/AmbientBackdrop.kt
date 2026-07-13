package com.example.seanimetv.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.transition.CrossfadeTransition
import com.example.seanimetv.ui.theme.SeanimeBackground
import kotlinx.coroutines.delay

/**
 * Full-bleed ambient backdrop shared by browsing screens (Home, My Library,
 * Discovery): banner artwork, a full-height left band for text legibility, and a
 * vertical scrim that settles at 80% opacity so the artwork stays faintly visible
 * behind content rows.
 *
 * A single [AsyncImage] does the URL crossfade at the Coil layer (the old Compose
 * [androidx.compose.animation.Crossfade] kept BOTH banners composed during the
 * transition — a full extra screen of overdraw on a weak GPU). The banner is also
 * decoded at ~½ resolution since it sits behind two scrims.
 */
@Composable
fun AmbientBackdrop(
    imageUrl: String?,
    dimmed: Boolean = false,
    modifier: Modifier = Modifier
) {
    val settle = rememberEntranceProgress(durationMillis = 1400)
    val bannerScale = 1.10f - settle * 0.06f

    // Rapid D-pad scrubbing changes imageUrl several times a second; starting
    // a load per step aborts crossfades mid-flight and the final banner then
    // pops in with no animation ("locked" backdrop). Only a URL the focus has
    // RESTED on for 300ms is actually displayed.
    var displayedUrl by remember { mutableStateOf(imageUrl) }
    LaunchedEffect(imageUrl) {
        if (displayedUrl == imageUrl) return@LaunchedEffect
        delay(300)
        displayedUrl = imageUrl
    }

    // Extra darkening while UI that needs contrast (e.g. Discovery's filter
    // chips) holds focus — some banners are too bright for small labels.
    val dimAlpha by animateFloatAsState(
        targetValue = if (dimmed) 0.55f else 0f,
        animationSpec = tween(220),
        label = "backdropDim"
    )

    Box(modifier = modifier.fillMaxSize()) {
        val url = displayedUrl
        if (url != null) {
            val context = LocalContext.current
            // Previous banner's cache key — used as the placeholder of the next
            // request so the old artwork stays on screen until the new one is
            // decoded, then crossfades. Without it every focus change flashed
            // to black before the new banner popped in.
            var previousUrl by remember { mutableStateOf<String?>(null) }
            val request = remember(url) {
                ImageRequest.Builder(context)
                    .data(url)
                    // 720p decode: noticeably sharper than the old 960×540 on a
                    // 1080p panel, still half the memory of a full-res banner.
                    .size(1280, 720)
                    .memoryCacheKey(url)
                    .placeholderMemoryCacheKey(previousUrl)
                    // Coil's default crossfade SKIPS memory-cache hits, so a
                    // revisited banner swapped instantly. This factory animates
                    // every source — the transition is always smooth.
                    .transitionFactory { target, result ->
                        CrossfadeTransition(target, result, 500, false)
                    }
                    .build()
            }
            SideEffect { previousUrl = url }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = bannerScale; scaleY = bannerScale }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0F1A)))
        }
        // Left band for text legibility — full height, so it has no bottom edge
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.72f),
                        0.55f to Color.Black.copy(alpha = 0.22f),
                        1f to Color.Transparent
                    )
                )
        )
        // Vertical fade into an 80%-opaque panel where content rows sit
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.34f to SeanimeBackground.copy(alpha = 0.8f),
                        1f to SeanimeBackground.copy(alpha = 0.8f)
                    )
                )
        )
        // Focus-driven dim, drawn topmost so it darkens artwork AND scrims.
        if (dimAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha))
            )
        }
    }
}
