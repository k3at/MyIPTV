package com.myiptv.app.data

import android.content.Context

/** Tiny SharedPreferences store for favorites and the last watched channel. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("myiptv_prefs", Context.MODE_PRIVATE)

    fun favorites(): MutableSet<String> =
        (sp.getStringSet(KEY_FAV, emptySet()) ?: emptySet()).toMutableSet()

    fun isFavorite(channelId: String): Boolean = favorites().contains(channelId)

    /** @return true if the channel is now a favorite, false if it was removed. */
    fun toggleFavorite(channelId: String): Boolean {
        val set = favorites()
        val nowFavorite = if (set.contains(channelId)) {
            set.remove(channelId); false
        } else {
            set.add(channelId); true
        }
        sp.edit().putStringSet(KEY_FAV, set).apply()
        return nowFavorite
    }

    var lastId: String?
        get() = sp.getString(KEY_LAST_ID, null)
        set(v) = sp.edit().putString(KEY_LAST_ID, v).apply()

    var lastName: String?
        get() = sp.getString(KEY_LAST_NAME, null)
        set(v) = sp.edit().putString(KEY_LAST_NAME, v).apply()

    var lastUrl: String?
        get() = sp.getString(KEY_LAST_URL, null)
        set(v) = sp.edit().putString(KEY_LAST_URL, v).apply()

    var lastCategory: String?
        get() = sp.getString(KEY_LAST_CAT, null)
        set(v) = sp.edit().putString(KEY_LAST_CAT, v).apply()

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
        const val KEY_VOLUME = "player_volume"
        const val KEY_BRIGHTNESS = "player_brightness"
        const val KEY_ASPECT = "player_aspect"
        const val KEY_SPEED = "player_speed"

        /** 1.0× in the speed list. */
        const val DEFAULT_SPEED_INDEX = 2
    }
}
