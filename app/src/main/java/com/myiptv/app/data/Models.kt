package com.myiptv.app.data

import android.os.Parcelable
import androidx.annotation.StringRes
import com.myiptv.app.R
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject

/** Distinguishes live channels, on-demand movies and series. */
enum class ContentType { LIVE, VOD, SERIES }

/** Short human label used in list rows ("Live · Sports"), localised. */
@get:StringRes
val ContentType.labelRes: Int
    get() = when (this) {
        ContentType.LIVE -> R.string.type_live
        ContentType.VOD -> R.string.type_movie
        ContentType.SERIES -> R.string.type_series
    }

@Parcelize
data class Channel(
    val id: String,
    val name: String,
    val logo: String?,
    val streamUrl: String,
    val category: String,
    val type: ContentType,
    /**
     * When the provider added this item, in epoch milliseconds, or 0 when it
     * doesn't say. Used by the "recently added" sort.
     */
    val added: Long = 0L
) : Parcelable

/**
 * One row of the browser list: the channel plus the per-device state the
 * provider knows nothing about.
 *
 * Keeping the favorite flag in the row is what lets the list update through a
 * single diff pass, so toggling a favorite doesn't invalidate every row.
 */
data class ChannelRow(
    val channel: Channel,
    val favorite: Boolean
)

/** A row in the category rail / chip row. */
data class CategoryRow(
    val key: String,
    val title: String,
    val count: Int
)

/**
 * One episode of a series. Series themselves are represented as [Channel]s with
 * [ContentType.SERIES]; episodes are resolved on demand and played back through
 * the normal [Channel] pipeline.
 */
data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val streamUrl: String,
    val logo: String?,
    val plot: String?
)

/** A single EPG programme slot (already decoded from Xtream's base64 fields). */
data class EpgEntry(
    val title: String,
    val description: String,
    val startMs: Long,
    val endMs: Long
)

/** Reserved keys for the two synthetic categories. */
object CategoryKeys {
    const val ALL = "__all__"
    const val FAVORITES = "__favorites__"
}

/** Prefixes used to build stable ids and to read ids back (for API calls). */
object ChannelIds {
    const val XT_LIVE = "xt:live:"
    const val XT_VOD = "xt:vod:"
    const val XT_SERIES = "xt:series:"

    /** Returns the trailing id when [id] starts with [prefix], otherwise null. */
    fun suffix(id: String, prefix: String): String? =
        if (id.startsWith(prefix)) id.removePrefix(prefix).takeIf { it.isNotBlank() } else null
}

/**
 * One saved panel: a host together with the username and password that go with
 * it.
 *
 * Households often keep more than one subscription - a main line plus a backup,
 * a second provider for a different country - so the app stores a list of these
 * and connects with whichever one is active; see `SettingsStore`.
 */
data class Profile(
    val id: String,
    val server: String = "",
    val username: String = "",
    val password: String = ""
) {
    /** True once the profile has everything an Xtream call needs. */
    val isComplete: Boolean
        get() = server.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

/**
 * Serialises profiles for storage and for the settings screen's saved state.
 * Hand-written rather than a JSON library, for the same reason the playlist
 * cache is: it has to survive being read back by a different app version.
 */
fun List<Profile>.toProfileJson(): String {
    val array = JSONArray()
    for (profile in this) {
        array.put(
            JSONObject()
                .put("id", profile.id)
                .put("server", profile.server)
                .put("username", profile.username)
                .put("password", profile.password)
        )
    }
    return array.toString()
}

/** Counterpart of [toProfileJson]; returns an empty list rather than throwing. */
fun profilesFromJson(raw: String?): List<Profile> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val entry = array.optJSONObject(index) ?: return@mapNotNull null
            val id = entry.optString("id").ifBlank { return@mapNotNull null }
            Profile(
                id = id,
                server = entry.optString("server"),
                username = entry.optString("username"),
                password = entry.optString("password")
            )
        }
    }.getOrDefault(emptyList())
}
