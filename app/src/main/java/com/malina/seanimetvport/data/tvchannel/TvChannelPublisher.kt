package com.malina.seanimetvport.data.tvchannel

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.malina.seanimetvport.R
import com.malina.seanimetvport.data.anilist.model.MediaListStatus
import com.malina.seanimetvport.data.anilist.repository.AniListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "TvChannel"
private const val PREFS = "tv_channel"
private const val KEY_ENABLED = "enabled"
private const val KEY_CHANNEL_ID = "channel_id"

/**
 * Publishes a "Seanime — Discovery" preview channel on the Android TV home
 * screen: the viewer's Continue Watching entries followed by trending
 * recommendations. Each program deep-links into the series screen via
 * seanimetv://series/{anilistId}. Toggled from Settings; refreshed on app
 * start while enabled.
 */
class TvChannelPublisher(
    private val context: Context,
    private val aniListRepository: AniListRepository
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    suspend fun enable() {
        prefs.edit().putBoolean(KEY_ENABLED, true).apply()
        refresh()
    }

    suspend fun refreshIfEnabled() {
        if (isEnabled()) refresh()
    }

    fun disable() {
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        // Also clear our Watch Next entries from the system row.
        runCatching { clearOurWatchNext() }
        val channelId = prefs.getLong(KEY_CHANNEL_ID, -1L)
        if (channelId > 0) {
            runCatching {
                context.contentResolver.delete(
                    TvContractCompat.buildChannelUri(channelId), null, null
                )
            }.onFailure { Log.w(TAG, "channel delete failed: ${it.message}") }
            prefs.edit().remove(KEY_CHANNEL_ID).apply()
        }
    }

    private suspend fun refresh() = withContext(Dispatchers.IO) {
        publishWatchNext()
        runCatching {
            val channelId = ensureChannel() ?: return@runCatching
            val items = buildItems()
            // Replace-all sync — simplest correct strategy for ≤20 programs.
            context.contentResolver.delete(
                TvContractCompat.buildPreviewProgramsUriForChannel(channelId), null, null
            )
            items.forEach { item ->
                val program = PreviewProgram.Builder()
                    .setChannelId(channelId)
                    .setType(TvContractCompat.PreviewPrograms.TYPE_TV_SERIES)
                    .setTitle(item.title)
                    .setDescription(item.subtitle)
                    .setPosterArtUri(Uri.parse(item.coverUrl))
                    .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
                    .setIntentUri(Uri.parse("seanimetv://series/${item.seriesId}"))
                    .build()
                context.contentResolver.insert(
                    TvContractCompat.PreviewPrograms.CONTENT_URI, program.toContentValues()
                )
            }
        }.onFailure { Log.w(TAG, "refresh failed: ${it.message}") }
        Unit
    }

    /** Deletes only the Watch Next rows this app published (seanimetv:// intent). */
    private fun clearOurWatchNext() {
        runCatching {
            context.contentResolver.query(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                arrayOf(
                    TvContractCompat.WatchNextPrograms._ID,
                    TvContractCompat.WatchNextPrograms.COLUMN_INTENT_URI
                ),
                null, null, null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(TvContractCompat.WatchNextPrograms._ID)
                val intentCol = cursor.getColumnIndex(TvContractCompat.WatchNextPrograms.COLUMN_INTENT_URI)
                while (cursor.moveToNext()) {
                    val intent = if (intentCol >= 0) cursor.getString(intentCol) else null
                    if (intent?.startsWith("seanimetv://") == true) {
                        val rowId = cursor.getLong(idCol)
                        context.contentResolver.delete(
                            TvContractCompat.buildWatchNextProgramUri(rowId), null, null
                        )
                    }
                }
            }
        }.onFailure { Log.w(TAG, "watch-next clear failed: ${it.message}") }
    }

    /**
     * Publishes the viewer's Continue Watching series into Google TV's SYSTEM
     * "Continue watching" row (Watch Next). Only series whose next episode has
     * actually aired are included — an unaired next episode isn't watchable.
     * This is separate from the preview channel and needs no channel id.
     */
    private suspend fun publishWatchNext() {
        runCatching {
            val user = aniListRepository.getViewer().getOrNull() ?: return
            val byStatus = aniListRepository.getMediaListCollection(user.id).getOrNull().orEmpty()
            val current = byStatus[MediaListStatus.CURRENT].orEmpty()

            // Replace-all: clear OUR previously published Watch Next entries.
            // The provider rejects a WHERE clause here, so query then delete
            // each of our rows by its own URI (ours = seanimetv:// intent).
            clearOurWatchNext()

            current.forEach { entry ->
                val media = entry.media
                val nextEp = entry.progress + 1
                val total = media.episodes
                // Skip finished series and series whose next episode is unaired.
                val nextAired = media.nextAiringEpisode?.let { nextEp < it.episode } ?: true
                val hasNext = total == null || nextEp <= total
                if (!nextAired || !hasNext) return@forEach
                val cover = media.coverImage?.extraLarge ?: media.coverImage?.large
                    ?: return@forEach

                val program = androidx.tvprovider.media.tv.WatchNextProgram.Builder()
                    .setType(TvContractCompat.WatchNextPrograms.TYPE_TV_SERIES)
                    .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                    .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                    .setTitle(media.title?.english ?: media.title?.romaji ?: "Unknown")
                    .setEpisodeNumber(nextEp)
                    .setPosterArtUri(Uri.parse(cover))
                    .setPosterArtAspectRatio(TvContractCompat.WatchNextPrograms.ASPECT_RATIO_2_3)
                    .setIntentUri(Uri.parse("seanimetv://series/${media.id}"))
                    .setInternalProviderId(media.id.toString())
                    .build()
                context.contentResolver.insert(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI, program.toContentValues()
                )
            }
        }.onFailure { Log.w(TAG, "watch-next publish failed: ${it.message}") }
    }

    /** Creates the channel on first use; asks the launcher to show it. */
    private fun ensureChannel(): Long? {
        val existing = prefs.getLong(KEY_CHANNEL_ID, -1L)
        if (existing > 0) return existing
        val channel = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName("Seanime — Discovery")
            .setAppLinkIntentUri(Uri.parse("seanimetv://series/0"))
            .build()
        val uri = context.contentResolver.insert(
            TvContractCompat.Channels.CONTENT_URI, channel.toContentValues()
        ) ?: return null
        val channelId = ContentUris.parseId(uri)
        runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.tv_banner)
                ?.let { ChannelLogoUtils.storeChannelLogo(context, channelId, it) }
        }
        // System consent dialog asking the user to show the channel.
        TvContractCompat.requestChannelBrowsable(context, channelId)
        prefs.edit().putLong(KEY_CHANNEL_ID, channelId).apply()
        return channelId
    }

    private data class ChannelItem(
        val seriesId: Int,
        val title: String,
        val subtitle: String,
        val coverUrl: String
    )

    private suspend fun buildItems(): List<ChannelItem> {
        val user = aniListRepository.getViewer().getOrNull() ?: return emptyList()
        val byStatus = aniListRepository.getMediaListCollection(user.id).getOrNull().orEmpty()
        val current = byStatus[MediaListStatus.CURRENT].orEmpty()
        val continueWatching = current.take(10).mapNotNull { entry ->
            val cover = entry.media.coverImage?.extraLarge
                ?: entry.media.coverImage?.large
                ?: return@mapNotNull null
            ChannelItem(
                seriesId = entry.media.id,
                title = entry.media.title?.english ?: entry.media.title?.romaji ?: "Unknown",
                subtitle = "Continue watching — Ep ${entry.progress + 1}",
                coverUrl = cover
            )
        }
        val userMediaIds = current.map { it.media.id }.toSet()
        val recommended = aniListRepository.browseMedia(sort = listOf("TRENDING_DESC"))
            .getOrNull().orEmpty()
            .filter { it.id !in userMediaIds }
            .take(10)
            .mapNotNull { media ->
                val cover = media.coverImage?.extraLarge
                    ?: media.coverImage?.large
                    ?: return@mapNotNull null
                ChannelItem(
                    seriesId = media.id,
                    title = media.title?.english ?: media.title?.romaji ?: "Unknown",
                    subtitle = "Recommended for you",
                    coverUrl = cover
                )
            }
        return continueWatching + recommended
    }
}
