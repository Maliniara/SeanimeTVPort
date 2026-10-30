package com.malina.seanimetvport.feature.series

import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

/**
 * Full-screen YouTube trailer. A single [WebView] hosts the privacy-friendly
 * `youtube-nocookie` embed; the D-pad BACK key closes the overlay and the
 * WebView is torn down on dispose so audio never keeps playing behind the panel.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TrailerOverlay(
    trailerId: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    // Hold the created WebView so the dispose hook can stop + release it.
    val player = remember { arrayOfNulls<WebView>(1) }
    DisposableEffect(trailerId) {
        onDispose {
            player[0]?.run { stopLoading(); destroy() }
            player[0] = null
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    with(settings) {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                    }
                    webChromeClient = WebChromeClient()
                    requestFocus()
                    loadUrl("https://www.youtube-nocookie.com/embed/$trailerId?autoplay=1&playsinline=1")
                    player[0] = this
                }
            }
        )
        Text(
            text = "D-pad BACK  →  closes trailer",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(24.dp)
        )
    }
}
