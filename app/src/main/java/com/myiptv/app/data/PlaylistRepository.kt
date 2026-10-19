package com.myiptv.app.data

import android.content.Context
import com.myiptv.app.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Loads the Xtream catalogue and keeps it cached.
 *
 * Two levels of cache keep the app feeling instant:
 *  1. the in-memory list (activity recreation, tab switches, returning from the
 *     player) - free;
 *  2. a line-based file in the app's private storage, so a cold start shows the
 *     last catalogue straight away and only the (quiet, background) refresh
 *     waits on the network.
 *
 * The panel stays the source of truth: nothing is ever served from a cache that
 * belongs to a different account.
 */
class PlaylistRepository(context: Context, private val settings: SettingsStore) {

    private val appContext = context.applicationContext

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(AppConfig.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(AppConfig.READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        // The catalogue is fetched as several parallel calls; the default of 5
        // per host would serialise the last one.
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = AppConfig.MAX_REQUESTS_PER_HOST })
        .build()

    private val xtream = XtreamApi(client, settings)

    private val cacheFile: File get() = File(appContext.filesDir, AppConfig.CACHE_FILE)

    @Volatile
    private var cache: List<Channel>? = null

    /**
     * Returns the cached catalogue without touching the network, or null when
     * nothing usable is cached for the current account.
     */
    suspend fun cachedOrNull(): List<Channel>? {
        cache?.let { return it }
        return withContext(Dispatchers.IO) { readCache()?.also { cache = it } }
    }

    /**
     * Downloads the catalogue and replaces both caches. Callers normally try
     * [cachedOrNull] first so this only runs on a cold start or a refresh.
     */
    suspend fun load(forceRefresh: Boolean = false): List<Channel> {
        if (!forceRefresh) cache?.let { return it }
        val data = withContext(Dispatchers.IO) { xtream.loadChannels().also { writeCache(it) } }
        cache = data
        return data
    }

    /** Drops every cache (e.g. after the user edits their connection settings). */
    fun invalidate() {
        cache = null
        runCatching { cacheFile.delete() }
    }

    /**
     * Resolves the episodes of a series. Xtream-only, like the rest of the app;
     * failures surface as "no episodes" rather than a crash.
     */
    suspend fun loadEpisodes(seriesId: String): List<Episode> = withContext(Dispatchers.IO) {
        runCatching { xtream.loadEpisodes(seriesId) }.getOrDefault(emptyList())
    }

    /** Short EPG (now/next) for a single live stream, or empty when disabled. */
    suspend fun loadEpg(streamId: String, limit: Int = 4): List<EpgEntry> = withContext(Dispatchers.IO) {
        if (!AppConfig.EPG_ENABLED) return@withContext emptyList()
        runCatching { xtream.loadShortEpg(streamId, limit) }.getOrDefault(emptyList())
    }

    // ── disk cache ───────────────────────────────────────────────────────
    //
    // One tab-separated row per item:
    //   TYPE <tab> id <tab> addedMillis <tab> name <tab> logo <tab> category <tab> url
    // prefixed by a header line carrying the format version and the account it
    // belongs to. Tabs/newlines inside a field are impossible in practice for
    // this data, and are scrubbed on the way in anyway.

    private fun readCache(): List<Channel>? = runCatching {
        val file = cacheFile
        if (!file.exists()) return null
        file.bufferedReader().use { reader ->
            val header = reader.readLine()
            val expected = "v${AppConfig.CACHE_FORMAT}\t${settings.accountKey}"
            if (header != expected) return null

            val out = ArrayList<Channel>(8192)
            while (true) {
                val line = reader.readLine() ?: break
                parseRow(line)?.let(out::add)
            }
            out.ifEmpty { null }
        }
    }.getOrNull()

    private fun parseRow(line: String): Channel? {
        if (line.isBlank()) return null
        val f = line.split('\t')
        if (f.size != CACHE_COLUMNS) return null
        val type = when (f[0]) {
            "LIVE" -> ContentType.LIVE
            "VOD" -> ContentType.VOD
            "SERIES" -> ContentType.SERIES
            else -> return null
        }
        if (f[1].isBlank()) return null
        return Channel(
            id = f[1],
            name = f[3],
            logo = f[4].ifBlank { null },
            streamUrl = f[6],
            category = f[5],
            type = type,
            added = f[2].toLongOrNull() ?: 0L
        )
    }

    private fun writeCache(data: List<Channel>) {
        if (data.isEmpty() || data.size > MAX_CACHED_ITEMS) return
        runCatching {
            val tmp = File(appContext.filesDir, AppConfig.CACHE_FILE + ".tmp")
            tmp.bufferedWriter().use { w ->
                w.append("v").append(AppConfig.CACHE_FORMAT.toString())
                    .append('\t').append(settings.accountKey).append('\n')
                for (c in data) {
                    w.append(c.type.name).append('\t')
                        .append(scrub(c.id)).append('\t')
                        .append(c.added.toString()).append('\t')
                        .append(scrub(c.name)).append('\t')
                        .append(scrub(c.logo.orEmpty())).append('\t')
                        .append(scrub(c.category)).append('\t')
                        .append(scrub(c.streamUrl)).append('\n')
                }
            }
            // Swap in the new file whole, so an interrupted write can never be
            // read back as a truncated catalogue.
            if (!tmp.renameTo(cacheFile)) {
                cacheFile.delete()
                tmp.renameTo(cacheFile)
            }
        }.onFailure { runCatching { File(appContext.filesDir, AppConfig.CACHE_FILE + ".tmp").delete() } }
    }

    private fun scrub(value: String): String =
        if (value.none { it == '\t' || it == '\n' || it == '\r' }) value
        else value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    private companion object {
        const val CACHE_COLUMNS = 7

        /** Beyond this the file stops being worth the disk space and the write time. */
        const val MAX_CACHED_ITEMS = 200_000
    }
}
