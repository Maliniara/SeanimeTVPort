package com.example.seanimetv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/**
 * Application entry point, primarily so Coil uses a memory-conscious [ImageLoader]
 * instead of its defaults. This matters most on low-end Android TV boxes such as
 * the Chromecast with Google TV (HD) — ~1.5 GB RAM and a single-core Mali-G31:
 *
 *  - **RGB_565** for opaque images (banners, covers, thumbnails have no alpha):
 *    halves bitmap memory with negligible banding on the dark UI.
 *  - A **bounded** memory cache (Coil's default is 25 % of a small heap, which
 *    thrashes as poster rows scroll) plus a modest disk cache so re-visits don't
 *    re-download.
 *  - A short crossfade — long ones double-draw during the transition.
 *
 * Coil already trims this cache on `onTrimMemory`, so no manual handling needed.
 */
class SeanimeApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .allowRgb565(true)
            .crossfade(150)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.18)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(80L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
}
