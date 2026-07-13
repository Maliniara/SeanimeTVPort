package com.example.seanimetv.data.anilist

import com.example.seanimetv.BuildConfig

internal object AniListConfig {
    const val CLIENT_ID = "44551"
    val CLIENT_SECRET: String get() = BuildConfig.ANILIST_CLIENT_SECRET
}
