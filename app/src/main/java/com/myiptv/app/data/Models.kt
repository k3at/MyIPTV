package com.myiptv.app.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Distinguishes live channels, on-demand movies and series. */
enum class ContentType { LIVE, VOD, SERIES }

/** Short human label used in list rows ("Live · Sports"). */
val ContentType.label: String
    get() = when (this) {
        ContentType.LIVE -> "Live"
        ContentType.VOD -> "Movie"
        ContentType.SERIES -> "Series"
    }

@Parcelize
data class Channel(
    val id: String,
    val name: String,
    val logo: String?,
    val streamUrl: String,
    val category: String,
    val type: ContentType
) : Parcelable

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
