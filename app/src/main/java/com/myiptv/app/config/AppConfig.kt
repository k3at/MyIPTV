package com.myiptv.app.config

/** Which backend the app pulls its playlist from. Chosen in the in-app Settings screen. */
enum class SourceType { M3U, XTREAM }

/**
 * Non-user-facing app constants.
 *
 * Connection details (source type, host/URL, username, password) are deliberately
 * NOT here. The user enters them in the in-app Settings screen and they are
 * persisted by [com.myiptv.app.data.SettingsStore].
 */
object AppConfig {

    /** Shown in the UI (also set in res/values/strings.xml as app_name). */
    const val APP_NAME = "MyIPTV"

    // ── Networking ───────────────────────────────────────────────────────
    const val USER_AGENT: String = "MyIPTV/1.0 (Android)"
    const val CONNECT_TIMEOUT_MS: Long = 15_000
    const val READ_TIMEOUT_MS: Long = 20_000

    /** Default live container for Xtream; the user can change it in Settings. */
    const val DEFAULT_XTREAM_LIVE_EXT: String = "m3u8"

    // ── EPG (now / next) ─────────────────────────────────────────────────
    // Only available for Xtream sources (uses the get_short_epg API).
    const val EPG_ENABLED: Boolean = true
}
