package com.example.seanimetv.data.net

import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

/**
 * Builds the OkHttpClient used for **streaming/plugin traffic only** — video
 * CDNs and extension sources reached through the QuickJS plugin engine and the
 * ExoPlayer data source.
 *
 * Many anime CDNs (e.g. kaa.lt) serve incomplete TLS certificate chains: they
 * send only the leaf certificate and omit the intermediate CA. Browsers and
 * Seanime's Go backend recover by fetching the missing intermediate (AIA), but
 * Android's default TrustManager does not, so the handshake fails with
 * "Chain validation failed" and playback never starts. To mirror the backend's
 * leniency this client accepts these connections.
 *
 * SECURITY BOUNDARY: this relaxed client is deliberately isolated. It NEVER
 * carries the AniList OAuth token — [com.example.seanimetv.data.anilist.AniListApiClient]
 * and the login flow build their own strict [OkHttpClient] with full validation.
 * The only data crossing this client is anonymous video/metadata traffic to
 * public streaming sites, where the MITM risk is immaterial.
 */
object StreamingHttpClient {

    fun build(): OkHttpClient = applyLenientTls(OkHttpClient.Builder()).build()

    /**
     * Installs a trust-all TLS setup on [builder] and returns it. Extracted so a
     * DEBUG-only build of the strict AniList client can opt in on emulators whose
     * AOSP system image lacks the Google Trust Services root CA (see
     * [com.example.seanimetv.data.anilist.AniListApiClient]). Release builds MUST
     * NOT call this on any client that carries the auth token.
     */
    fun applyLenientTls(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), java.security.SecureRandom())
        }
        return builder
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
    }
}
