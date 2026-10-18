package com.myiptv.app.data

import java.util.Locale

/**
 * Robust M3U / M3U8 parser.
 *
 * Supports per-entry attributes (tvg-logo, tvg-name, group-title) and the
 * running `#EXTGRP:` directive. The display name is everything after the first
 * comma that is *outside* quotes, so names containing commas are kept intact.
 */
object M3uParser {

    private val ATTR_REGEX = Regex("([A-Za-z0-9\\-_]+)=\"([^\"]*)\"")

    fun parse(content: String): List<Channel> {
        val channels = ArrayList<Channel>()

        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup: String? = null
        var runningGroup: String? = null

        content.lineSequence().forEach { raw ->
            val line = raw.trim().trimStart('\uFEFF')
            if (line.isEmpty()) return@forEach

            when {
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val info = line.substringAfter(':', "")
                    val (meta, name) = splitMetaAndName(info)
                    pendingName = name.ifBlank { null }
                    pendingLogo = null
                    var attrGroup: String? = null
                    ATTR_REGEX.findAll(meta).forEach { m ->
                        when (m.groupValues[1].lowercase(Locale.ROOT)) {
                            "tvg-logo" -> pendingLogo = m.groupValues[2].ifBlank { null }
                            "group-title" -> attrGroup = m.groupValues[2].ifBlank { null }
                            "tvg-name" -> if (pendingName == null) {
                                pendingName = m.groupValues[2].ifBlank { null }
                            }
                        }
                    }
                    pendingGroup = attrGroup ?: runningGroup
                }

                line.startsWith("#EXTGRP", ignoreCase = true) -> {
                    val g = line.substringAfter(':', "").trim().ifBlank { null }
                    if (g != null) {
                        runningGroup = g
                        pendingGroup = g
                    }
                }

                line.startsWith("#") -> Unit // ignore other directives

                else -> {
                    val url = line
                    val fallbackName = url.substringAfterLast('/').substringBefore('?')
                    channels.add(
                        Channel(
                            id = "m3u:${channels.size}:$url",
                            name = (pendingName ?: fallbackName).ifBlank { "Channel ${channels.size + 1}" },
                            logo = pendingLogo,
                            streamUrl = url,
                            category = pendingGroup ?: runningGroup ?: "Uncategorized",
                            type = ContentType.LIVE
                        )
                    )
                    pendingName = null
                    pendingLogo = null
                    pendingGroup = runningGroup
                }
            }
        }
        return channels
    }

    private fun splitMetaAndName(info: String): Pair<String, String> {
        var inQuotes = false
        for (i in info.indices) {
            val c = info[i]
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> return info.substring(0, i) to info.substring(i + 1).trim()
            }
        }
        return info to ""
    }
}
