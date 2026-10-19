package com.myiptv.app.data

import android.util.Base64
import com.myiptv.app.config.AppConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.SimpleDateFormat
import java.util.Locale

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

    /**
     * Live + VOD + Series, flattened into one list of navigable items.
     *
     * The three catalogues - and the category lists they need - are fetched
     * concurrently: a panel with a lot of content answers these slowly, and
     * serialising six round trips is what makes the first screen feel slow.
     */
    suspend fun loadChannels(): List<Channel> = coroutineScope {
        val live = async { loadLive() }
        val vod = async { loadVod() }
        val series = async { loadSeries() }
        val out = ArrayList<Channel>(4096)
        out.addAll(live.await())
        out.addAll(vod.await())
        out.addAll(series.await())
        out
    }

    private suspend fun loadLive(): List<Channel> = coroutineScope {
        val categories = async { categoryMap(getArray("get_live_categories")) }
        val streams = async { getArray("get_live_streams") }
        val cats = categories.await()
        val arr = streams.await()
        val out = ArrayList<Channel>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("stream_id", "")
            if (id.isBlank()) continue
            out.add(
                Channel(
                    id = ChannelIds.XT_LIVE + id,
                    name = o.optString("name", "Channel $id"),
                    logo = o.optString("stream_icon", ""),
                    streamUrl = "$server/live/${settings.xtreamUsername}/${settings.xtreamPassword}/$id.${settings.xtreamLiveExt}",
                    category = cats[o.optString("category_id", "")] ?: "Live",
                    type = ContentType.LIVE,
                    added = addedMillis(o.optString("added", ""))
                )
            )
        }
        out
    }

    private suspend fun loadVod(): List<Channel> = coroutineScope {
        val categories = async { categoryMap(getArray("get_vod_categories")) }
        val streams = async { getArray("get_vod_streams") }
        val cats = categories.await()
        val arr = streams.await()
        val out = ArrayList<Channel>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("stream_id", "")
            if (id.isBlank()) continue
            val ext = o.optString("container_extension", "mp4")
            out.add(
                Channel(
                    id = ChannelIds.XT_VOD + id,
                    name = o.optString("name", "Movie $id"),
                    logo = o.optString("stream_icon", ""),
                    streamUrl = "$server/movie/${settings.xtreamUsername}/${settings.xtreamPassword}/$id.$ext",
                    category = cats[o.optString("category_id", "")] ?: "Movies",
                    type = ContentType.VOD,
                    added = addedMillis(o.optString("added", ""))
                )
            )
        }
        out
    }

    private suspend fun loadSeries(): List<Channel> = coroutineScope {
        val categories = async { categoryMap(getArray("get_series_categories")) }
        val series = async { getArray("get_series") }
        val cats = categories.await()
        val arr = series.await()
        val out = ArrayList<Channel>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("series_id", "")
            if (id.isBlank()) continue
            out.add(
                Channel(
                    id = ChannelIds.XT_SERIES + id,
                    name = o.optString("name", "Series $id"),
                    logo = o.optString("cover", ""),
                    // Episodes are resolved lazily via loadEpisodes(); a series
                    // itself is not directly playable.
                    streamUrl = "",
                    category = cats[o.optString("category_id", "")] ?: "Series",
                    type = ContentType.SERIES,
                    // Panels disagree about the field: newer ones send "added",
                    // older ones only "last_modified", and some only the release
                    // date. Fall through all of them.
                    added = addedMillis(
                        o.optString("added", ""),
                        o.optString("last_modified", ""),
                        o.optString("releaseDate", "")
                    )
                )
            )
        }
        out
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

    /**
     * Timestamps from IPTV panels are "usually" unix seconds, but some send
     * milliseconds, "-1", "0" or a MySQL datetime. Take the first value that
     * parses to something sane and return epoch millis (0 when nothing works).
     */
    private fun addedMillis(vararg values: String): Long {
        for (raw in values) {
            val v = raw.trim()
            if (v.isEmpty() || v == "-1" || v == "0") continue
            v.toLongOrNull()?.let { n ->
                if (n <= 0) return@let
                return if (n < 100_000_000_000L) n * 1000L else n
            }
            parseDate(v)?.let { return it }
        }
        return 0L
    }

    private fun parseDate(value: String): Long? {
        for (pattern in DATE_PATTERNS) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = true }.parse(value)
            }.getOrNull()
            if (parsed != null) return parsed.time
        }
        return null
    }

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
        val map = HashMap<String, String>(arr.length() * 2)
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
        return client.newCall(request).execute().use { response ->
            val body = response.body ?: return ""
            decodeText(body.bytes(), body.contentType()?.charset()?.name())
        }
    }

    /**
     * Reads a response body as text without mangling non-Latin names.
     *
     * Panels are careless about `Content-Type`: a lot of them answer
     * `text/html` (or `iso-8859-1`, which is what a missing charset means) with
     * plain UTF-8 inside, and some Arabic panels really do send Windows-1256
     * bytes. So: trust UTF-8 when the headers don't say otherwise *and* the bytes
     * are valid UTF-8, otherwise honour the declared charset, otherwise fall back
     * to the Arabic code page and finally Latin-1.
     */
    private fun decodeText(bytes: ByteArray, declaredName: String?): String {
        val name = declaredName?.trim()?.lowercase(Locale.ROOT)
        if (name == null || name in UNSPECIFIED_CHARSETS) {
            strictUtf8(bytes)?.let { return it }
        }
        val declared = name?.let { n -> runCatching { Charset.forName(n) }.getOrNull() }
        if (declared != null) return String(bytes, declared)
        strictUtf8(bytes)?.let { return it }
        return String(bytes, legacyArabic)
    }

    /** Returns the text only when [bytes] is *valid* UTF-8, else null. */
    private fun strictUtf8(bytes: ByteArray): String? = runCatching {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrElse { if (it is CharacterCodingException) null else null }

    private companion object {
        /**
         * Charsets that say nothing useful: a missing charset, or the two values
         * servers use when they could not be bothered. All of them may still be
         * carrying UTF-8.
         */
        val UNSPECIFIED_CHARSETS = setOf(
            "utf-8", "utf8", "us-ascii", "ascii",
            "iso-8859-1", "iso8859-1", "latin1", "windows-1252", "cp1252"
        )

        val legacyArabic: Charset =
            runCatching { Charset.forName("windows-1256") }.getOrDefault(Charsets.ISO_8859_1)

        val DATE_PATTERNS = arrayOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd"
        )
    }
}
