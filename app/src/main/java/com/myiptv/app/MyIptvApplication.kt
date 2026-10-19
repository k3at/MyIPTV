package com.myiptv.app

import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.myiptv.app.data.Channel
import com.myiptv.app.data.PlaylistRepository
import com.myiptv.app.data.Prefs
import com.myiptv.app.data.SettingsStore

class MyIptvApplication : Application() {

    val settings: SettingsStore by lazy { SettingsStore(this) }
    val repository: PlaylistRepository by lazy { PlaylistRepository(this, settings) }
    val prefs: Prefs by lazy { Prefs(this) }

    override fun onCreate() {
        super.onCreate()
        applyStoredLanguage()
        // Favorites and the resume point belong to the panel in use, not to the
        // device, so point the store at whichever profile is active.
        prefs.attachProfile(settings.activeProfileId)
    }

    /**
     * Re-applies the language chosen in Settings.
     *
     * An empty locale list means "follow the device", which is the default. The
     * screens themselves apply the locale to their own context (see
     * `LocalizedActivity`); this only makes the rest of the system agree: on
     * Android 13+ the framework owns the per-app locale, and on older releases
     * AppCompat stores it for the activities it manages.
     */
    private fun applyStoredLanguage() {
        val tag = prefs.languageTag

        if (tag.isBlank()) {
            // "System default": the device decides. On Android 13+ the framework
            // owns the per-app locale, and one set there (system settings, a
            // restore from backup) is left alone instead of being cleared -
            // clearing it here would also make the process start in one language
            // and switch a moment later.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
            }
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = LocaleList.forLanguageTags(tag)
            val manager = getSystemService(LocaleManager::class.java)
            if (manager != null && manager.applicationLocales != locales) {
                // Rejects tags not listed in res/xml/locales_config.xml.
                runCatching { manager.applicationLocales = locales }
            }
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }

    /**
     * The channels the player zaps through (prev/next).
     *
     * Kept in memory on purpose: real providers expose tens of thousands of
     * channels, and shipping that list through an Intent extra blows the ~1 MB
     * Binder transaction limit, making `startActivity` die with
     * `TransactionTooLargeException` the instant a channel is picked.
     */
    var playQueue: List<Channel> = emptyList()

    companion object {
        fun from(context: Context): MyIptvApplication =
            context.applicationContext as MyIptvApplication
    }
}
