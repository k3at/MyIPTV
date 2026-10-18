package com.myiptv.app.data

import com.myiptv.app.config.AppConfig
import com.myiptv.app.config.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Loads the playlist (either a raw M3U file or the Xtream API) from the saved settings. */
class PlaylistRepository(private val settings: SettingsStore) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(AppConfig.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(AppConfig.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build()

    private val xtream = XtreamApi(client, settings)

    @Volatile
    private var cache: List<Channel>? = null

    /**
     * Loads the configured playlist. Results are cached in memory so view
     * recreation (rotation, etc.) is instant. Pass forceRefresh = true to
     * re-download (also required after the connection settings change).
     */
    suspend fun load(forceRefresh: Boolean = false): List<Channel> {
        if (!forceRefresh) cache?.let { return it }
        val data = withContext(Dispatchers.IO) {
            when (settings.sourceType) {
                SourceType.XTREAM -> xtream.loadChannels()
                SourceType.M3U -> M3uParser.parse(fetchM3u())
            }
        }
        cache = data
        return data
    }

    /** Drops the in-memory cache (e.g. after the user edits their settings). */
    fun invalidate() {
        cache = null
    }

    /**
     * Resolves the episodes of a series. Only meaningful for Xtream sources;
     * M3U playlists have no notion of series, so an empty list is returned.
     */
    suspend fun loadEpisodes(seriesId: String): List<Episode> = withContext(Dispatchers.IO) {
        if (settings.sourceType == SourceType.XTREAM) {
            runCatching { xtream.loadEpisodes(seriesId) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }

    /**
     * Short EPG (now/next) for a single live stream. Returns an empty list for
     * M3U sources or when EPG is disabled in [AppConfig].
     */
    suspend fun loadEpg(streamId: String, limit: Int = 4): List<EpgEntry> = withContext(Dispatchers.IO) {
        if (settings.sourceType == SourceType.XTREAM && AppConfig.EPG_ENABLED) {
            runCatching { xtream.loadShortEpg(streamId, limit) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }

    private fun fetchM3u(): String {
        val url = settings.m3uUrl
        check(url.isNotBlank()) { "No playlist URL configured" }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", AppConfig.USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            return response.body?.string() ?: error("Empty playlist response")
        }
    }
}
