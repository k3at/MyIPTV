package com.myiptv.app.data

import android.util.Base64
import com.myiptv.app.config.AppConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal Xtream Codes API client.
 *
 * Loads Live channels, VOD (movies) and Series using the credentials the user
 * entered in the in-app Settings screen, and resolves episodes + short EPG
 * (now/next) on demand.
 */
class XtreamApi(
    private val client: OkHttpClient,
    private val settings: SettingsStore
) {

    private val server: String get() = settings.xtreamServer.trimEnd('/')

    /** Live + VOD + Series, flattened into one list of navigable items. */
    fun loadChannels(): List<Channel> {
        val result = ArrayList<Channel>()

        runCatching {
            val liveCategories = categoryMap(getArray("get_live_categories"))
            val liveStreams = getArray("get_live_streams")
            for (i in 0 until liveStreams.length()) {
                val o = liveStreams.optJSONObject(i) ?: continue
                val id = o.optString("stream_id", "")
                if (id.isBlank()) continue
                result.add(
                    Channel(
                        id = ChannelIds.XT_LIVE + id,
                        name = o.optString("name", "Channel $id"),
                        logo = o.optString("stream_icon", ""),
                        streamUrl = "$server/live/${settings.xtreamUsername}/${settings.xtreamPassword}/$id.${settings.xtreamLiveExt}",
                        category = liveCategories[o.optString("category_id", "")] ?: "Live",
                        type = ContentType.LIVE
                    )
                )
            }
        }

        runCatching {
            val vodCategories = categoryMap(getArray("get_vod_categories"))
            val vodStreams = getArray("get_vod_streams")
            for (i in 0 until vodStreams.length()) {
                val o = vodStreams.optJSONObject(i) ?: continue
                val id = o.optString("stream_id", "")
                if (id.isBlank()) continue
                val ext = o.optString("container_extension", "mp4")
                result.add(
                    Channel(
                        id = ChannelIds.XT_VOD + id,
                        name = o.optString("name", "Movie $id"),
                        logo = o.optString("stream_icon", ""),
                        streamUrl = "$server/movie/${settings.xtreamUsername}/${settings.xtreamPassword}/$id.$ext",
                        category = vodCategories[o.optString("category_id", "")] ?: "Movies",
                        type = ContentType.VOD
                    )
                )
            }
        }

        runCatching {
            val seriesCategories = categoryMap(getArray("get_series_categories"))
            val series = getArray("get_series")
            for (i in 0 until series.length()) {
                val o = series.optJSONObject(i) ?: continue
                val id = o.optString("series_id", "")
                if (id.isBlank()) continue
                result.add(
                    Channel(
                        id = ChannelIds.XT_SERIES + id,
                        name = o.optString("name", "Series $id"),
                        logo = o.optString("cover", ""),
                        // Episodes are resolved lazily via loadEpisodes(); a series
                        // itself is not directly playable.
                        streamUrl = "",
                        category = seriesCategories[o.optString("category_id", "")] ?: "Series",
                        type = ContentType.SERIES
                    )
                )
            }
        }

        return result
    }

    /** All episodes of a series, flattened and sorted by season then episode. */
    fun loadEpisodes(seriesId: String): List<Episode> {
        val body = rawBody("get_series_info", "series_id" to seriesId)
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val episodesObj = root.optJSONObject("episodes") ?: return emptyList()

        // Season keys arrive as strings ("1", "2", …); order them numerically.
        val seasonKeys = episodesObj.keys().asSequence()
            .toList()
            .sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }

        val out = ArrayList<Episode>()
        for (key in seasonKeys) {
            val seasonNum = key.toIntOrNull() ?: 0
            val arr = episodesObj.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id", "")
                if (id.isBlank()) continue
                val ext = o.optString("container_extension", "mp4")
                val info = o.optJSONObject("info")
                val number = o.optString("episode_num", "").toIntOrNull() ?: (i + 1)
                val season = if (seasonNum > 0) seasonNum
                else o.optString("season", "").toIntOrNull() ?: 1
                val title = o.optString("title", "")
                    .ifBlank { info?.optString("name", "").orEmpty() }
                    .ifBlank { "Episode $number" }
                val plot = info?.optString("plot", "").orEmpty()
                    .ifBlank { o.optString("plot", "") }
                out.add(
                    Episode(
                        id = "xt:ep:$id",
                        title = title,
                        season = season,
                        number = number,
                        streamUrl = "$server/series/${settings.xtreamUsername}/${settings.xtreamPassword}/$id.$ext",
                        logo = info?.optString("movie_image", "").orEmpty().ifBlank { null },
                        plot = plot.ifBlank { null }
                    )
                )
            }
        }
        return out
    }

    /**
     * Short EPG for a single live stream, used for the "now / next" overlay.
     * Xtream returns the programme title/description base64-encoded.
     */
    fun loadShortEpg(streamId: String, limit: Int): List<EpgEntry> {
        val body = rawBody(
            "get_short_epg",
            "stream_id" to streamId,
            "limit" to limit.toString()
        )
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val arr = root.optJSONArray("epg_listings") ?: return emptyList()

        val out = ArrayList<EpgEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val start = o.optString("start_timestamp", "").toLongOrNull() ?: continue
            val end = o.optString("stop_timestamp", "").toLongOrNull() ?: continue
            out.add(
                EpgEntry(
                    title = decodeB64(o.optString("title", "")),
                    description = decodeB64(o.optString("description", "")),
                    startMs = start * 1000L,
                    endMs = end * 1000L
                )
            )
        }
        return out
    }

    // ── internals ────────────────────────────────────────────────────────

    private val base64Regex = Regex("^[A-Za-z0-9+/]+={0,2}$")

    /** Decodes a base64 field, falling back to the raw value when it isn't base64. */
    private fun decodeB64(value: String): String {
        val v = value.trim()
        if (v.isEmpty() || v.length % 4 != 0 || !base64Regex.matches(v)) return value
        val decoded = runCatching {
            String(Base64.decode(v, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull() ?: return value
        // Control characters mean it wasn't really base64 encoded text.
        return if (decoded.any { it.code < 0x09 }) value else decoded
    }

    private fun categoryMap(arr: JSONArray): Map<String, String> {
        val map = HashMap<String, String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("category_id", "")
            val name = o.optString("category_name", "")
            if (id.isNotEmpty()) map[id] = if (name.isNotEmpty()) name else "Category $id"
        }
        return map
    }

    private fun getArray(action: String, vararg params: Pair<String, String>): JSONArray {
        val body = rawBody(action, *params)
        if (body.isNotBlank()) {
            runCatching { JSONArray(body) }.getOrNull()?.let { return it }
        }
        return JSONArray()
    }

    private fun rawBody(action: String, vararg params: Pair<String, String>): String {
        val url = StringBuilder("$server/player_api.php?username=${settings.xtreamUsername}")
            .append("&password=${settings.xtreamPassword}")
            .append("&action=$action")
        for (p in params) url.append('&').append(p.first).append('=').append(p.second)

        val request = Request.Builder()
            .url(url.toString())
            .header("User-Agent", AppConfig.USER_AGENT)
            .build()
        return client.newCall(request).execute().use { it.body?.string().orEmpty() }
    }
}
