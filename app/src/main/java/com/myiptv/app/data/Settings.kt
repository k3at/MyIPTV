package com.myiptv.app.data

import android.content.Context
import com.myiptv.app.config.AppConfig
import com.myiptv.app.config.SourceType

/**
 * Persists the connection settings the user enters in the in-app Settings screen.
 *
 * Stored in a private SharedPreferences file. Note: values are kept in plain text
 * (typical for sideloaded IPTV clients); do not rely on this for high-value secrets.
 */
class SettingsStore(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("myiptv_settings", Context.MODE_PRIVATE)

    /** Active source. Defaults to Xtream so a fresh install shows the right fields. */
    var sourceType: SourceType
        get() = runCatching {
            SourceType.valueOf(sp.getString(KEY_SOURCE, SourceType.XTREAM.name).orEmpty())
        }.getOrDefault(SourceType.XTREAM)
        set(v) = sp.edit().putString(KEY_SOURCE, v.name).apply()

    /** Full playlist URL for M3U mode. */
    var m3uUrl: String
        get() = sp.getString(KEY_M3U_URL, "").orEmpty()
        set(v) = sp.edit().putString(KEY_M3U_URL, v.trim()).apply()

    /** Xtream server base, without /player_api.php (e.g. http://host:8080). */
    var xtreamServer: String
        get() = sp.getString(KEY_XT_SERVER, "").orEmpty()
        set(v) = sp.edit().putString(KEY_XT_SERVER, v.trim().trimEnd('/')).apply()

    var xtreamUsername: String
        get() = sp.getString(KEY_XT_USER, "").orEmpty()
        set(v) = sp.edit().putString(KEY_XT_USER, v.trim()).apply()

    var xtreamPassword: String
        get() = sp.getString(KEY_XT_PASS, "").orEmpty()
        set(v) = sp.edit().putString(KEY_XT_PASS, v.trim()).apply()

    /** Live container extension for Xtream: "m3u8" (HLS) or "ts". */
    var xtreamLiveExt: String
        get() = sp.getString(KEY_XT_EXT, AppConfig.DEFAULT_XTREAM_LIVE_EXT)
            ?.ifBlank { AppConfig.DEFAULT_XTREAM_LIVE_EXT }
            ?: AppConfig.DEFAULT_XTREAM_LIVE_EXT
        set(v) = sp.edit().putString(KEY_XT_EXT, v).apply()

    /** True when the active source has everything it needs to load a playlist. */
    val isConfigured: Boolean
        get() = when (sourceType) {
            SourceType.M3U -> m3uUrl.isNotBlank()
            SourceType.XTREAM ->
                xtreamServer.isNotBlank() && xtreamUsername.isNotBlank() && xtreamPassword.isNotBlank()
        }

    private companion object {
        const val KEY_SOURCE = "source_type"
        const val KEY_M3U_URL = "m3u_url"
        const val KEY_XT_SERVER = "xtream_server"
        const val KEY_XT_USER = "xtream_username"
        const val KEY_XT_PASS = "xtream_password"
        const val KEY_XT_EXT = "xtream_live_ext"
    }
}
