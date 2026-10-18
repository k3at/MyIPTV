package com.myiptv.app

import android.app.Application
import android.content.Context
import com.myiptv.app.data.Channel
import com.myiptv.app.data.PlaylistRepository
import com.myiptv.app.data.Prefs
import com.myiptv.app.data.SettingsStore

class MyIptvApplication : Application() {

    val settings: SettingsStore by lazy { SettingsStore(this) }
    val repository: PlaylistRepository by lazy { PlaylistRepository(settings) }
    val prefs: Prefs by lazy { Prefs(this) }

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
