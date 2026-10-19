package com.myiptv.app.data

import android.content.Context
import com.myiptv.app.config.AppConfig
import java.util.UUID

/**
 * Stores the Xtream Codes panels ("profiles") the user has added, and remembers
 * which one the app is connected to.
 *
 * The rest of the app only ever asks for the active panel, so switching between
 * subscriptions is a matter of changing which id is active.
 *
 * Stored in a private SharedPreferences file. Note: values are kept in plain text
 * (typical for sideloaded IPTV clients); do not rely on this for high-value secrets.
 */
class SettingsStore(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("myiptv_settings", Context.MODE_PRIVATE)

    private val lock = Any()

    /** Parsed once and kept in step with storage by [replaceAll]. */
    private var cache: List<Profile>? = null

    /** Every saved panel, in the order the user added them. */
    val profiles: List<Profile>
        get() = synchronized(lock) { loaded() }

    /** Id of the panel in use, or "" when nothing is configured yet. */
    val activeProfileId: String
        get() = synchronized(lock) { resolveActive(loaded()) }

    /** The panel the app connects with, or null when nothing is configured. */
    val activeProfile: Profile?
        get() = synchronized(lock) {
            val list = loaded()
            list.firstOrNull { it.id == resolveActive(list) }
        }

    /**
     * Replaces the whole list and picks the active panel. This is the only
     * writer, which keeps the in-memory copy in step - and lets the settings
     * screen save an addition, an edit and a removal in one go.
     */
    fun replaceAll(profiles: List<Profile>, activeId: String? = null) {
        synchronized(lock) {
            val cleaned = profiles.map { it.copy(server = cleanUrl(it.server)) }
            val active = activeId?.takeIf { id -> cleaned.any { it.id == id } }
                ?: resolveActive(cleaned)
            sp.edit()
                .putString(KEY_PROFILES, cleaned.toProfileJson())
                .putString(KEY_ACTIVE, active)
                .apply()
            cache = cleaned
        }
    }

    /** A brand new, empty panel. Nothing is stored until [replaceAll] runs. */
    fun newProfile(): Profile = Profile(id = UUID.randomUUID().toString())

    // ── the active panel, in the shape the rest of the app expects ────────
    /** Xtream server base, without /player_api.php (e.g. http://host:8080). */
    val xtreamServer: String get() = activeProfile?.server.orEmpty()

    val xtreamUsername: String get() = activeProfile?.username.orEmpty()

    val xtreamPassword: String get() = activeProfile?.password.orEmpty()

    /** Live container extension: "m3u8" (HLS) or "ts". Shared by all panels. */
    var xtreamLiveExt: String
        get() = sp.getString(KEY_XT_EXT, AppConfig.DEFAULT_XTREAM_LIVE_EXT)
            ?.ifBlank { AppConfig.DEFAULT_XTREAM_LIVE_EXT }
            ?: AppConfig.DEFAULT_XTREAM_LIVE_EXT
        set(v) = sp.edit().putString(KEY_XT_EXT, v).apply()

    /** True when the active panel has everything it needs to load a playlist. */
    val isConfigured: Boolean get() = activeProfile?.isComplete == true

    /**
     * Identifies the account the cached playlist belongs to. The disk cache is
     * ignored when this changes, so switching panels never shows the wrong
     * catalogue.
     */
    val accountKey: String
        get() = "$xtreamServer|$xtreamUsername|$xtreamLiveExt"

    // ── storage ───────────────────────────────────────────────────────────
    private fun loaded(): List<Profile> {
        cache?.let { return it }
        val stored = sp.getString(KEY_PROFILES, null)
        val list = if (stored == null) migrateLegacy() else profilesFromJson(stored)
        cache = list
        return list
    }

    private fun resolveActive(list: List<Profile>): String {
        val stored = sp.getString(KEY_ACTIVE, null)
        return if (stored != null && list.any { it.id == stored }) {
            stored
        } else {
            list.firstOrNull()?.id.orEmpty()
        }
    }

    /**
     * 1.0/1.1 stored a single connection under flat keys; turn it into the first
     * profile so upgrading keeps the user connected.
     */
    private fun migrateLegacy(): List<Profile> {
        val server = sp.getString(KEY_LEGACY_SERVER, "").orEmpty()
        if (server.isBlank()) return emptyList()
        val migrated = listOf(
            Profile(
                id = LEGACY_PROFILE_ID,
                server = server,
                username = sp.getString(KEY_LEGACY_USER, "").orEmpty(),
                password = sp.getString(KEY_LEGACY_PASS, "").orEmpty()
            )
        )
        sp.edit()
            .putString(KEY_PROFILES, migrated.toProfileJson())
            .putString(KEY_ACTIVE, LEGACY_PROFILE_ID)
            .apply()
        return migrated
    }

    private fun cleanUrl(raw: String): String = raw.trim().trimEnd('/')

    private companion object {
        const val KEY_PROFILES = "profiles_json"
        const val KEY_ACTIVE = "active_profile"
        const val KEY_XT_EXT = "xtream_live_ext"

        /** Flat keys written by 1.0/1.1, read once by [migrateLegacy]. */
        const val KEY_LEGACY_SERVER = "xtream_server"
        const val KEY_LEGACY_USER = "xtream_username"
        const val KEY_LEGACY_PASS = "xtream_password"

        const val LEGACY_PROFILE_ID = "default"
    }
}
