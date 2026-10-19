package com.myiptv.app.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.myiptv.app.MyIptvApplication
import com.myiptv.app.data.ChannelNames
import java.util.Locale

/**
 * Base class for every screen: keeps the whole app on the language chosen in
 * Settings.
 *
 * The chosen locale is applied to this activity's own context, which is what
 * makes `values-ar` resources and right-to-left mirroring take effect from the
 * first frame. If the language changes while an activity is alive (the user
 * tapped it in Settings) the activity recreates itself, because text that was
 * already inflated from XML would otherwise keep the old language.
 *
 * `AppCompatDelegate.setApplicationLocales()` is still the thing that tells the
 * system, and Settings calls it - but on API 33+ it cannot do anything before an
 * activity exists, and on older releases it only reaches activities that are
 * currently alive, so the resources themselves are handled here.
 */
abstract class LocalizedActivity : AppCompatActivity() {

    /** The language this instance's views were built with. */
    private var appliedTag: String = ""

    override fun attachBaseContext(newBase: Context) {
        val tag = appOf(newBase)?.prefs?.languageTag.orEmpty()
        super.attachBaseContext(if (tag.isEmpty()) newBase else withLocale(newBase, tag))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        appliedTag = appOf(this)?.prefs?.languageTag.orEmpty()
        super.onCreate(savedInstanceState)
        // Provider channel names follow the language too: a Latin catalogue is
        // rendered in Arabic while the app is in Arabic (see ChannelNames).
        ChannelNames.setArabic(isArabicScreen())
    }

    /** True when this screen's views were built in Arabic. */
    @Suppress("DEPRECATION")
    private fun isArabicScreen(): Boolean {
        val config = resources.configuration
        val locale =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) config.locales[0] else config.locale
        return locale?.language == "ar"
    }

    override fun onResume() {
        super.onResume()
        if (appOf(this)?.prefs?.languageTag.orEmpty() != appliedTag) recreate()
    }

    private fun appOf(context: Context): MyIptvApplication? =
        context.applicationContext as? MyIptvApplication

    private fun withLocale(base: Context, tag: String): Context {
        val locale = Locale.forLanguageTag(tag)
        if (locale.language.isEmpty()) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
