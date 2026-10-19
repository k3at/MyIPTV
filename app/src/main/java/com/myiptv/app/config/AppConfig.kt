package com.myiptv.app.config

/**
 * Non-user-facing app constants.
 *
 * Connection details (host, username, password) are deliberately NOT here. The
 * user enters them in the in-app Settings screen and they are persisted by
 * [com.myiptv.app.data.SettingsStore].
 */
object AppConfig {

    /** Shown in the UI (also set in res/values/strings.xml as app_name). */
    const val APP_NAME = "MyIPTV"

    // ── Networking ───────────────────────────────────────────────────────
    const val USER_AGENT: String = "MyIPTV/1.0 (Android)"
    const val CONNECT_TIMEOUT_MS: Long = 15_000
    const val READ_TIMEOUT_MS: Long = 20_000

    /**
     * The player cache needs a handful of player_api.php calls at once; the
     * OkHttp default (5 per host) would serialise them.
     */
    const val MAX_REQUESTS_PER_HOST: Int = 8

    /** Default live container for Xtream; the user can change it in Settings. */
    const val DEFAULT_XTREAM_LIVE_EXT: String = "m3u8"

    // ── Playlist disk cache ──────────────────────────────────────────────
    /** File name (in the app's private files dir) holding the parsed playlist. */
    const val CACHE_FILE: String = "playlist-cache.tsv"

    /**
     * Bump when the cache line format changes so stale files are ignored instead
     * of misparsed.
     */
    const val CACHE_FORMAT: Int = 1

    // ── EPG (now / next) ─────────────────────────────────────────────────
    // Uses the get_short_epg API.
    const val EPG_ENABLED: Boolean = true
}
