package com.malina.seanimetvport.data.anilist

import com.malina.seanimetvport.BuildConfig

internal object AniListConfig {
    val CLIENT_ID: String get() = BuildConfig.ANILIST_CLIENT_ID
    val CLIENT_SECRET: String get() = BuildConfig.ANILIST_CLIENT_SECRET
}
