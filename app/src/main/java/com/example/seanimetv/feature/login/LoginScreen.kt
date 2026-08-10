package com.example.seanimetv.feature.login

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.seanimetv.R
import com.example.seanimetv.components.entranceReveal
import com.example.seanimetv.components.rememberEntranceProgress
import com.example.seanimetv.ui.theme.AniListBlue
import com.example.seanimetv.ui.theme.SeanimeBorder
import com.example.seanimetv.ui.theme.SeanimeBackground
import com.example.seanimetv.ui.theme.SeanimeOnSurface
import com.example.seanimetv.ui.theme.SeanimeOnSurfaceMuted
import com.example.seanimetv.ui.theme.SeanimePrimary
import com.example.seanimetv.ui.theme.SeanimeSurface
import com.example.seanimetv.ui.theme.SeanimeSurfaceVariant
import com.example.seanimetv.ui.theme.SeanimeTVTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(SeanimeBackground)
            .padding(horizontal = 56.dp, vertical = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Left panel: branding ──────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(0.38f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Seanime",
                fontSize = 54.sp,
                fontWeight = FontWeight.ExtraBold,
                color = SeanimeOnSurface,
                modifier = Modifier.entranceReveal(rememberEntranceProgress())
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Your anime client for Android TV",
                fontSize = 17.sp,
                color = SeanimeOnSurfaceMuted,
                modifier = Modifier.entranceReveal(rememberEntranceProgress(delayMillis = 120))
            )

            Spacer(Modifier.height(40.dp))

            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(listOf(AniListBlue, AniListBlue.copy(alpha = 0f)))
                    )
            )

            Spacer(Modifier.height(32.dp))

            Text(text = "Sign in with", fontSize = 13.sp, color = SeanimeOnSurfaceMuted)
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.size(10.dp).background(AniListBlue, CircleShape))
                Text(
                    text = "AniList",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AniListBlue
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Browse your anime list, track watch progress and discover new titles.",
                fontSize = 14.sp,
                color = SeanimeOnSurfaceMuted,
                lineHeight = 22.sp
            )
        }

        // ── Right panel ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(0.62f)
                .fillMaxHeight()
                .border(1.dp, SeanimeBorder, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(SeanimeSurface),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {

                is LoginUiState.Idle -> {
                    var webViewRef by remember { mutableStateOf<WebView?>(null) }
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SeanimeSurfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(onClick = { webViewRef?.goBack() }) {
                                Text(text = "← Back", fontSize = 13.sp)
                            }
                            Button(onClick = { webViewRef?.reload() }) {
                                Text(text = "↺ Refresh", fontSize = 13.sp)
                            }
                            Text(
                                text = "D-pad moves the pointer · OK clicks",
                                fontSize = 11.sp,
                                color = SeanimeOnSurfaceMuted,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            var cursor by remember { mutableStateOf(Offset.Zero) }
                            val density = LocalDensity.current
                            val moveStep = with(density) { 36.dp.toPx() }
                            val scrollStepPx = with(density) { 140.dp.toPx() }.roundToInt()
                            val cursorRadiusPx = with(density) { 11.dp.toPx() }

                            AndroidView(
                                factory = { context ->
                                    CursorWebView(context).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.setSupportZoom(false)
                                        settings.builtInZoomControls = false
                                        settings.loadWithOverviewMode = false
                                        isFocusableInTouchMode = true
                                        webViewClient = object : WebViewClient() {
                                            override fun onPageStarted(
                                                view: WebView,
                                                url: String,
                                                favicon: android.graphics.Bitmap?
                                            ) {
                                                val uri = android.net.Uri.parse(url)
                                                if (uri.host == "anilist.co" &&
                                                    uri.path == "/api/v2/oauth/pin"
                                                ) {
                                                    val code = uri.getQueryParameter("code")
                                                    if (code != null) viewModel.onCodeReceived(code)
                                                    else viewModel.onError("Missing authorization code")
                                                }
                                            }

                                            override fun shouldOverrideUrlLoading(
                                                view: WebView,
                                                request: WebResourceRequest
                                            ): Boolean {
                                                val host = request.url.host.orEmpty()
                                                val path = request.url.path.orEmpty()
                                                if (host == "anilist.co" && path == "/api/v2/oauth/pin") {
                                                    val code = request.url.getQueryParameter("code")
                                                    if (code != null) viewModel.onCodeReceived(code)
                                                    else viewModel.onError("Missing authorization code")
                                                    return true
                                                }
                                                if (!host.endsWith("anilist.co")) return true
                                                return false
                                            }
                                        }
                                        // D-pad drives a virtual pointer, handled ON the WebView (not
                                        // a Compose overlay) so the WebView keeps Android focus:
                                        // IME still works after tapping a field, and Cloudflare /
                                        // Turnstile — which keyboard focus can't reach — is clickable.
                                        keyHandler = { keyCode, event ->
                                            val w = width.toFloat()
                                            val h = height.toFloat()
                                            if (cursor == Offset.Zero && w > 0f && h > 0f) {
                                                cursor = Offset(w / 2f, h / 2f)
                                            }
                                            val down = event.action == KeyEvent.ACTION_DOWN
                                            when (keyCode) {
                                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                    if (down) cursor = Offset((cursor.x - moveStep).coerceAtLeast(0f), cursor.y)
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                    if (down) cursor = Offset((cursor.x + moveStep).coerceAtMost(w), cursor.y)
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_UP -> {
                                                    // At the very top with nothing to scroll → let the
                                                    // event bubble so focus can reach the toolbar.
                                                    val release = cursor.y <= 0f && !canScrollVertically(-1)
                                                    if (!release && down) {
                                                        if (cursor.y > 0f) cursor = Offset(cursor.x, (cursor.y - moveStep).coerceAtLeast(0f))
                                                        else scrollBy(0, -scrollStepPx)
                                                    }
                                                    !release
                                                }
                                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                    if (down) {
                                                        if (cursor.y < h) cursor = Offset(cursor.x, (cursor.y + moveStep).coerceAtMost(h))
                                                        else if (canScrollVertically(1)) scrollBy(0, scrollStepPx)
                                                    }
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_CENTER,
                                                KeyEvent.KEYCODE_ENTER,
                                                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                                                    if (down) tapWebView(this, cursor.x, cursor.y)
                                                    true
                                                }
                                                else -> false
                                            }
                                        }
                                        loadUrl(viewModel.buildOAuthUrl())
                                    }.also { webViewRef = it }
                                },
                                modifier = Modifier.matchParentSize()
                            )

                            // The pointer itself (visual only — not focusable).
                            Box(
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            (cursor.x - cursorRadiusPx).roundToInt(),
                                            (cursor.y - cursorRadiusPx).roundToInt()
                                        )
                                    }
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(SeanimePrimary.copy(alpha = 0.45f))
                                    .border(2.dp, Color.White, CircleShape)
                            )

                            // Keep the WebView focused so its key listener drives the pointer.
                            LaunchedEffect(webViewRef) { webViewRef?.requestFocus() }
                        }
                    }
                }

                is LoginUiState.ExchangingToken -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = AniListBlue)
                        Text(
                            text = "Signing in…",
                            color = SeanimeOnSurfaceMuted,
                            fontSize = 15.sp
                        )
                    }
                }

                is LoginUiState.Error -> {
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(text = "⚠", fontSize = 40.sp, color = Color(0xFFFF6B6B))
                        Text(
                            text = state.message,
                            color = SeanimeOnSurfaceMuted,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { viewModel.retry() },
                            modifier = Modifier.focusRequester(focusRequester)
                        ) {
                            Text(text = stringResource(R.string.login_retry))
                        }
                    }
                }
            }
        }
    }
}

/**
 * WebView that lets [keyHandler] intercept keys BEFORE the web renderer. A plain
 * `View.OnKeyListener` is bypassed by WebView (its `dispatchKeyEvent` delegates
 * straight to the renderer), so the D-pad pointer must hook here or the page just
 * scrolls and the pointer never moves.
 */
private class CursorWebView(context: Context) : WebView(context) {
    var keyHandler: ((keyCode: Int, event: KeyEvent) -> Boolean)? = null
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (keyHandler?.invoke(event.keyCode, event) == true) return true
        return super.dispatchKeyEvent(event)
    }
}

/** Dispatches a synthetic tap (down+up) to the WebView at the pointer position. */
private fun tapWebView(webView: WebView, x: Float, y: Float) {
    val t = SystemClock.uptimeMillis()
    val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)
    webView.dispatchTouchEvent(down)
    down.recycle()
    val up = MotionEvent.obtain(t, t + 60, MotionEvent.ACTION_UP, x, y, 0)
    webView.dispatchTouchEvent(up)
    up.recycle()
}

// ── Previews ─────────────────────────────────────────────────────────────────

private class PreviewFakeAuthRepository : com.example.seanimetv.data.anilist.repository.AuthRepository {
    override suspend fun getToken(): String? = null
    override suspend fun saveToken(token: String) {}
    override suspend fun clearToken() {}
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, widthDp = 1280, heightDp = 720)
@Composable
@OptIn(ExperimentalTvMaterial3Api::class)
fun LoginScreenPreview() {
    SeanimeTVTheme {
        LoginScreen(viewModel = LoginViewModel(PreviewFakeAuthRepository()))
    }
}
