package com.myiptv.app.data

import android.content.Context

/** Tiny SharedPreferences store for favorites, the last watched item and UI prefs. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("myiptv_prefs", Context.MODE_PRIVATE)

    /**
     * In-memory mirror of the favorites set. The list screens ask for it on every
     * filter pass, and real providers expose tens of thousands of items, so
     * hitting SharedPreferences each time is not an option.
     */
    private var favoritesCache: MutableSet<String>? = null

    /**
     * Panel the per-device state below belongs to.
     *
     * Favorites and the resume point are keyed by ids the provider hands out,
     * and two panels happily reuse the same id for different channels, so the
     * keys are namespaced per profile: switching panels must not show another
     * panel's favorites.
     */
    @Volatile
    private var scope: String = ""

    /**
     * Points the per-panel data at [profileId] (normally the active profile).
     * Called on start-up and whenever the user switches panels.
     */
    @Synchronized
    fun attachProfile(profileId: String) {
        if (profileId == scope) return
        scope = profileId
        favoritesCache = null
        adoptLegacyKeys()
    }

    /** Namespaces a storage key with the current profile. */
    private fun key(base: String): String = if (scope.isEmpty()) base else "$base@$scope"

    /**
     * 1.0/1.1 kept favorites and the resume point under unscoped keys. On the
     * first run after the upgrade, hand them to the active profile - once, so a
     * panel added later still starts empty.
     */
    private fun adoptLegacyKeys() {
        if (scope.isEmpty() || sp.getBoolean(KEY_SCOPED, false)) return
        val editor = sp.edit()
        val legacyFavorites = sp.getStringSet(KEY_FAV, null)
        if (!legacyFavorites.isNullOrEmpty()) editor.putStringSet(key(KEY_FAV), legacyFavorites)
        for (base in CARRIED_KEYS) sp.getString(base, null)?.let { editor.putString(key(base), it) }
        editor.putBoolean(KEY_SCOPED, true).apply()
        favoritesCache = null
    }

    @Synchronized
    fun favorites(): Set<String> {
        favoritesCache?.let { return it }
        val loaded = (sp.getStringSet(key(KEY_FAV), emptySet()) ?: emptySet()).toMutableSet()
        favoritesCache = loaded
        return loaded
    }

    fun isFavorite(channelId: String): Boolean = favorites().contains(channelId)

    /** @return true if the channel is now a favorite, false if it was removed. */
    @Synchronized
    fun toggleFavorite(channelId: String): Boolean {
        val set = favorites().toMutableSet()
        val nowFavorite = if (set.contains(channelId)) {
            set.remove(channelId); false
        } else {
            set.add(channelId); true
        }
        favoritesCache = set
        sp.edit().putStringSet(key(KEY_FAV), set).apply()
        return nowFavorite
    }

    var lastId: String?
        get() = sp.getString(key(KEY_LAST_ID), null)
        set(v) = sp.edit().putString(key(KEY_LAST_ID), v).apply()

    var lastName: String?
        get() = sp.getString(key(KEY_LAST_NAME), null)
        set(v) = sp.edit().putString(key(KEY_LAST_NAME), v).apply()

    var lastUrl: String?
        get() = sp.getString(key(KEY_LAST_URL), null)
        set(v) = sp.edit().putString(key(KEY_LAST_URL), v).apply()

    var lastCategory: String?
        get() = sp.getString(key(KEY_LAST_CAT), null)
        set(v) = sp.edit().putString(key(KEY_LAST_CAT), v).apply()

    // ── Look & language ──────────────────────────────────────────────────
    /**
     * Locale tag the UI is forced into ("en", "ar"), or "" to follow the device.
     */
    var languageTag: String
        get() = sp.getString(KEY_LANGUAGE, "").orEmpty()
        set(v) = sp.edit().putString(KEY_LANGUAGE, v).apply()

    /** How the lists are ordered; see [SortMode]. */
    var sortMode: SortMode
        get() = runCatching {
            SortMode.valueOf(sp.getString(KEY_SORT, SortMode.PROVIDER.name).orEmpty())
        }.getOrDefault(SortMode.PROVIDER)
        set(v) = sp.edit().putString(KEY_SORT, v.name).apply()

    // ── Player preferences (remembered between sessions) ─────────────────
    /** Music stream volume index, or null when never set. */
    var playerVolume: Int?
        get() = sp.getInt(KEY_VOLUME, -1).takeIf { it >= 0 }
        set(v) = sp.edit().putInt(KEY_VOLUME, v ?: -1).apply()

    /** Window brightness 0..1, or null to follow the system. */
    var playerBrightness: Float?
        get() = sp.getFloat(KEY_BRIGHTNESS, -1f).takeIf { it >= 0f }
        set(v) = sp.edit().putFloat(KEY_BRIGHTNESS, v ?: -1f).apply()

    /** Index into the aspect-ratio list. */
    var playerAspect: Int
        get() = sp.getInt(KEY_ASPECT, 0)
        set(v) = sp.edit().putInt(KEY_ASPECT, v).apply()

    /** Index into the playback-speed list. */
    var playerSpeed: Int
        get() = sp.getInt(KEY_SPEED, DEFAULT_SPEED_INDEX)
        set(v) = sp.edit().putInt(KEY_SPEED, v).apply()

    private companion object {
        const val KEY_FAV = "favorites"
        const val KEY_LAST_ID = "last_id"
        const val KEY_LAST_NAME = "last_name"
        const val KEY_LAST_URL = "last_url"
        const val KEY_LAST_CAT = "last_category"
        const val KEY_LANGUAGE = "ui_language"
        const val KEY_SORT = "list_sort"
        const val KEY_VOLUME = "player_volume"
        const val KEY_BRIGHTNESS = "player_brightness"
        const val KEY_ASPECT = "player_aspect"
        const val KEY_SPEED = "player_speed"

        /** Set once the pre-1.1 flat keys have been handed to a profile. */
        const val KEY_SCOPED = "scoped_favorites"

        /** Flat keys from 1.0/1.1 that move with the favorites. */
        val CARRIED_KEYS = listOf(KEY_LAST_ID, KEY_LAST_NAME, KEY_LAST_URL, KEY_LAST_CAT)

        /** 1.0× in the speed list. */
        const val DEFAULT_SPEED_INDEX = 2
    }
}

/** How the movies / series / live lists are ordered. */
enum class SortMode {
    /** Exactly the order the provider returned. */
    PROVIDER,

    /** Newest first, using the provider's "added" timestamp. */
    RECENT,

    /** Alphabetical by name. */
    NAME
}
