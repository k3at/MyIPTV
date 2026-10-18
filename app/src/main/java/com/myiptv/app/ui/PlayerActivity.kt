package com.myiptv.app.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.myiptv.app.MyIptvApplication
import com.myiptv.app.R
import com.myiptv.app.config.AppConfig
import com.myiptv.app.data.Channel
import com.myiptv.app.data.ChannelIds
import com.myiptv.app.data.ContentType
import com.myiptv.app.data.EpgEntry
import com.myiptv.app.data.label
import com.myiptv.app.databinding.ActivityPlayerBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Fullscreen player for Live TV, Movies and Series.
 *
 * The video is rendered by a plain [androidx.media3.ui.PlayerView] (stock
 * controller disabled) and every control lives in our own overlay so we can
 * offer play/pause, seeking, channel zapping, volume, brightness, aspect ratio
 * and playback speed — on both touch screens and TV remotes.
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var player: ExoPlayer
    private lateinit var drawerAdapter: PlayerChannelAdapter
    private lateinit var audioManager: AudioManager

    private val prefs get() = MyIptvApplication.from(this).prefs
    private val repository get() = MyIptvApplication.from(this).repository

    private var channels: ArrayList<Channel> = arrayListOf()
    private var index: Int = 0
    private var epgJob: Job? = null
    private var wasPlaying = false

    /** Aspect-ratio presets cycled by the aspect button. */
    private val aspectModes = intArrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    )

    /** Playback-speed presets cycled by the speed button. */
    private val speedValues = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    private val speedLabels = arrayOf("0.5", "0.75", "1.0", "1.25", "1.5", "2.0")

    private var aspectIndex = 0
    private var speedIndex = DEFAULT_SPEED_INDEX

    /** Which slider the D-pad up/down keys drive: volume (default) or brightness. */
    private var adjustBrightness = false

    /** Volume remembered before muting, so unmuting restores it. */
    private var preMuteVolume = -1

    private val handler = Handler(Looper.getMainLooper())
    // Controls stay on screen while playback is paused.
    private val hideOsdRunnable = Runnable { if (player.isPlaying) showOsd(false) }
    private val hideIndicatorRunnable = Runnable { binding.indicator.visibility = View.GONE }

    private val clockTick = object : Runnable {
        override fun run() {
            binding.clockText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            handler.postDelayed(this, 30_000)
        }
    }

    private val progressTick = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // The queue travels in memory, not in the Intent: a full playlist is far
        // too large for a Binder transaction (see MyIptvApplication.playQueue).
        channels = ArrayList(MyIptvApplication.from(this).playQueue)
        if (channels.isEmpty()) {
            // Process was recreated (e.g. after being killed in the background) and
            // the in-memory queue is gone — fall back to the last played channel.
            lastPlayedChannel()?.let { channels.add(it) }
        }
        if (channels.isEmpty()) {
            finish()
            return
        }
        index = intent.getIntExtra(EXTRA_INDEX, 0).coerceIn(0, channels.size - 1)

        audioManager = getSystemService(AudioManager::class.java)

        buildPlayer()
        setupDrawer()
        setupControls()
        applyStoredPreferences()
        playCurrent()
        showOsd(true)
    }

    // ── Player ──────────────────────────────────────────────────────────

    private fun buildPlayer() {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(AppConfig.USER_AGENT)
            .setConnectTimeoutMs(AppConfig.CONNECT_TIMEOUT_MS.toInt())
            .setReadTimeoutMs(AppConfig.READ_TIMEOUT_MS.toInt())
            .setAllowCrossProtocolRedirects(true)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .build()

        binding.playerView.player = player
        // Keep D-pad input at the Activity level; tapping the video toggles the OSD.
        binding.playerView.isFocusable = false
        binding.playerView.setOnClickListener { toggleOsd() }

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> hideError()
                    Player.STATE_ENDED -> if (current()?.type == ContentType.LIVE) next()
                }
                updatePlayIcons()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayIcons()
                // Re-arm the auto-hide so resuming doesn't leave the OSD on screen.
                if (isPlaying && binding.osd.visibility == View.VISIBLE) {
                    handler.removeCallbacks(hideOsdRunnable)
                    handler.postDelayed(hideOsdRunnable, OSD_TIMEOUT_MS)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                showError(error.message ?: getString(R.string.playback_error))
                showOsd(true)
            }
        })
    }

    private fun setupDrawer() {
        drawerAdapter = PlayerChannelAdapter(channels) { channel ->
            val i = channels.indexOfFirst { it.id == channel.id }
            if (i >= 0) {
                index = i
                playCurrent()
            }
            toggleDrawer(false)
        }
        binding.playerChannelList.layoutManager = LinearLayoutManager(this)
        binding.playerChannelList.adapter = drawerAdapter
    }

    private fun setupControls() {
        // Transport
        binding.playBtn.setOnClickListener { togglePlayPause() }
        binding.centerPlayBtn.setOnClickListener { togglePlayPause() }
        binding.rewBtn.setOnClickListener { seekBy(-SEEK_STEP_MS) }
        binding.fwdBtn.setOnClickListener { seekBy(SEEK_STEP_MS) }
        binding.centerRewBtn.setOnClickListener { seekBy(-SEEK_STEP_MS) }
        binding.centerFwdBtn.setOnClickListener { seekBy(SEEK_STEP_MS) }
        binding.prevBtn.setOnClickListener { prev() }
        binding.nextBtn.setOnClickListener { next() }

        // Menu / likes / retry
        binding.openListBtn.setOnClickListener { toggleDrawer() }
        binding.favBtn.setOnClickListener { toggleFavorite() }
        binding.playerRetryBtn.setOnClickListener { hideError(); playCurrent() }

        // Video options
        binding.aspectBtn.setOnClickListener { cycleAspect() }
        binding.speedBtn.setOnClickListener { cycleSpeed() }
        binding.muteBtn.setOnClickListener { toggleMute() }
        binding.volumeIcon.setOnClickListener { setAdjustTarget(false) }
        binding.brightnessIcon.setOnClickListener { setAdjustTarget(true) }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    player.seekTo(progress.toLong())
                    handler.removeCallbacks(hideOsdRunnable)
                }
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {
                handler.removeCallbacks(hideOsdRunnable)
            }

            override fun onStopTrackingTouch(bar: SeekBar?) {
                showOsd(true)
            }
        })

        binding.volumeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) setVolume(progress)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = showOsd(true)
        })

        binding.brightnessBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) setBrightness(progress / 100f)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = showOsd(true)
        })

        setAdjustTarget(false, announce = false)
    }

    /** Restores volume, brightness, aspect ratio and speed from the last session. */
    private fun applyStoredPreferences() {
        val max = maxVolume()
        binding.volumeBar.max = max
        val volume = (prefs.playerVolume ?: currentVolume()).coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        binding.volumeBar.progress = volume
        refreshVolumeLabels()

        binding.brightnessBar.max = 100
        setBrightnessInternal(prefs.playerBrightness ?: systemBrightness(), persist = false)

        aspectIndex = prefs.playerAspect.coerceIn(0, aspectModes.size - 1)
        applyAspect()

        speedIndex = prefs.playerSpeed.coerceIn(0, speedValues.size - 1)
        applySpeed()
    }

    private fun current(): Channel? = channels.getOrNull(index)

    /** Last channel remembered by [Prefs]; only used when the play queue is gone. */
    private fun lastPlayedChannel(): Channel? {
        val url = prefs.lastUrl ?: return null
        return Channel(
            id = prefs.lastId ?: "resume",
            name = prefs.lastName ?: url,
            logo = null,
            streamUrl = url,
            category = prefs.lastCategory ?: "",
            type = ContentType.LIVE
        )
    }

    private fun isLive(): Boolean = current()?.type == ContentType.LIVE

    /** Live streams have no reliable seekable window, so seek UI is hidden. */
    private fun supportsSeek(): Boolean = !isLive()

    private fun playCurrent() {
        val channel = current() ?: return
        hideError()
        prefs.lastId = channel.id
        prefs.lastName = channel.name
        prefs.lastUrl = channel.streamUrl
        prefs.lastCategory = channel.category

        updateOverlay(channel)
        drawerAdapter.setCurrent(channel.id)
        loadEpg(channel)
        updateControlsForType()
        applySpeed()

        player.setMediaItem(mediaItemFor(channel.streamUrl))
        player.prepare()
        player.playWhenReady = true
    }

    private fun updateControlsForType() {
        val seekable = supportsSeek()
        val seekVisibility = if (seekable) View.VISIBLE else View.GONE
        val liveVisibility = if (seekable) View.GONE else View.VISIBLE

        binding.seekBar.visibility = seekVisibility
        binding.positionText.visibility = seekVisibility
        binding.durationText.visibility = seekVisibility
        binding.rewBtn.visibility = seekVisibility
        binding.fwdBtn.visibility = seekVisibility
        binding.centerRewBtn.visibility = seekVisibility
        binding.centerFwdBtn.visibility = seekVisibility
        binding.speedBtn.visibility = seekVisibility
        binding.liveChip.visibility = liveVisibility
    }

    /**
     * Fetches the short EPG (now/next) for the current live channel and shows it
     * in the OSD. No-op for movies/series or non-Xtream sources.
     */
    private fun loadEpg(channel: Channel) {
        epgJob?.cancel()
        binding.playerEpgText.visibility = View.GONE
        binding.playerEpgText.text = null

        if (channel.type != ContentType.LIVE) return
        val streamId = ChannelIds.suffix(channel.id, ChannelIds.XT_LIVE) ?: return

        epgJob = lifecycleScope.launch {
            val entries = repository.loadEpg(streamId)
            // The user may have zapped away while we were fetching.
            if (entries.isEmpty() || current()?.id != channel.id) return@launch
            binding.playerEpgText.text = formatEpg(entries)
            binding.playerEpgText.visibility = View.VISIBLE
        }
    }

    private fun formatEpg(entries: List<EpgEntry>): CharSequence {
        val now = System.currentTimeMillis()
        val current = entries.firstOrNull { now >= it.startMs && now < it.endMs } ?: entries.first()
        val next = entries.firstOrNull { it.startMs > current.startMs }
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())

        val sb = StringBuilder()
        sb.append(
            getString(
                R.string.epg_now, current.title,
                fmt.format(Date(current.startMs)), fmt.format(Date(current.endMs))
            )
        )
        if (next != null) {
            sb.append('\n')
            sb.append(getString(R.string.epg_next, next.title, fmt.format(Date(next.startMs))))
        }
        return sb
    }

    private fun mediaItemFor(url: String): MediaItem {
        val builder = MediaItem.Builder().setUri(url)
        val lower = url.lowercase(Locale.ROOT)
        if (lower.contains(".m3u8") || lower.contains("output=m3u8")) {
            builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        return builder.build()
    }

    private fun updateOverlay(channel: Channel) {
        binding.playerChannelName.text = channel.name
        binding.playerChannelCat.text = if (channel.category.isNotBlank()) {
            "${channel.type.label} · ${channel.category}"
        } else {
            channel.type.label
        }
        binding.favBtn.setImageResource(
            if (prefs.isFavorite(channel.id)) R.drawable.ic_star else R.drawable.ic_star_border
        )
        Glide.with(this)
            .load(channel.logo)
            .placeholder(R.drawable.placeholder_channel)
            .error(R.drawable.placeholder_channel)
            .into(binding.playerChannelLogo)
    }

    // ── Transport ───────────────────────────────────────────────────────

    private fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
        showOsd(true)
        updatePlayIcons()
    }

    private fun seekBy(deltaMs: Long) {
        if (!supportsSeek()) {
            if (deltaMs > 0) next() else prev()
            return
        }
        val duration = player.duration
        var target = player.currentPosition + deltaMs
        target = target.coerceAtLeast(0)
        if (duration > 0) target = target.coerceAtMost(duration)
        player.seekTo(target)
        showOsd(true)
        updateProgress()
    }

    private fun updatePlayIcons() {
        val icon = if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        binding.playBtn.setImageResource(icon)
        binding.centerPlayBtn.setImageResource(icon)
    }

    private fun updateProgress() {
        if (binding.osd.visibility != View.VISIBLE) return
        val position = player.currentPosition
        val duration = player.duration

        if (supportsSeek() && duration > 0) {
            binding.seekBar.max = duration.toInt()
            binding.seekBar.progress = position.toInt().coerceIn(0, duration.toInt())
            binding.durationText.text = formatTime(duration)
        }
        binding.positionText.text = formatTime(position)
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    private fun next() {
        if (channels.size < 2) return
        index = (index + 1) % channels.size
        playCurrent()
        showOsd(true)
    }

    private fun prev() {
        if (channels.size < 2) return
        index = (index - 1 + channels.size) % channels.size
        playCurrent()
        showOsd(true)
    }

    // ── Volume ──────────────────────────────────────────────────────────

    private fun maxVolume(): Int = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    private fun currentVolume(): Int = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    private fun setVolume(value: Int) {
        val max = maxVolume()
        val volume = value.coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        binding.volumeBar.progress = volume
        refreshVolumeLabels()
        prefs.playerVolume = volume
        showIndicator(
            if (volume == 0) R.drawable.ic_volume_off else R.drawable.ic_volume_up,
            if (volume == 0) getString(R.string.muted) else volumePercent(volume)
        )
        showOsd(true)
    }

    private fun adjustVolume(direction: Int) {
        val next = when {
            direction > 0 && currentVolume() == 0 -> (maxVolume() / 4).coerceAtLeast(1)
            else -> currentVolume() + direction
        }
        setVolume(next)
    }

    private fun toggleMute() {
        if (currentVolume() > 0) {
            preMuteVolume = currentVolume()
            setVolume(0)
        } else {
            setVolume(if (preMuteVolume > 0) preMuteVolume else (maxVolume() / 2).coerceAtLeast(1))
        }
    }

    private fun volumePercent(value: Int): String {
        val max = maxVolume().coerceAtLeast(1)
        return getString(R.string.volume_format, value * 100 / max)
    }

    private fun refreshVolumeLabels() {
        val volume = currentVolume()
        val muted = volume == 0
        binding.volumeValue.text = if (muted) getString(R.string.muted) else volumePercent(volume)
        binding.muteBtn.setImageResource(
            if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_up
        )
        binding.volumeIcon.setImageResource(
            if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume_up
        )
    }

    // ── Brightness ──────────────────────────────────────────────────────

    private fun systemBrightness(): Float = try {
        val value = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        (value / 255f).coerceIn(MIN_BRIGHTNESS, 1f)
    } catch (_: Exception) {
        0.6f
    }

    private fun setBrightness(value: Float) {
        setBrightnessInternal(value, persist = true)
        showIndicator(R.drawable.ic_brightness, binding.brightnessValue.text.toString())
        showOsd(true)
    }

    private fun setBrightnessInternal(value: Float, persist: Boolean) {
        val level = value.coerceIn(MIN_BRIGHTNESS, 1f)
        val attrs = window.attributes
        attrs.screenBrightness = level
        window.attributes = attrs

        val percent = (level * 100).toInt()
        binding.brightnessBar.progress = percent
        binding.brightnessValue.text = "$percent%"
        if (persist) prefs.playerBrightness = level
    }

    private fun adjustBrightness(delta: Float) {
        setBrightness(binding.brightnessBar.progress / 100f + delta)
    }

    /** Chooses which slider the D-pad up/down keys control. */
    private fun setAdjustTarget(brightness: Boolean, announce: Boolean = true) {
        adjustBrightness = brightness
        val active = ContextCompat.getColor(this, R.color.brand_accent)
        val idle = ContextCompat.getColor(this, R.color.text_secondary)
        binding.volumeIcon.imageTintList =
            ColorStateList.valueOf(if (brightness) idle else active)
        binding.brightnessIcon.imageTintList =
            ColorStateList.valueOf(if (brightness) active else idle)

        if (!announce) return
        showIndicator(
            if (brightness) R.drawable.ic_brightness else R.drawable.ic_volume_up,
            if (brightness) binding.brightnessValue.text.toString()
            else getString(R.string.player_volume_label)
        )
    }

    // ── Video options ───────────────────────────────────────────────────

    private fun cycleAspect() {
        aspectIndex = (aspectIndex + 1) % aspectModes.size
        applyAspect()
        prefs.playerAspect = aspectIndex
        showOsd(true)
    }

    private fun applyAspect() {
        val mode = aspectModes[aspectIndex]
        binding.playerView.setResizeMode(mode)
        binding.aspectBtn.text = getString(
            when (mode) {
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> R.string.aspect_fill
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> R.string.aspect_zoom
                else -> R.string.aspect_fit
            }
        )
    }

    private fun cycleSpeed() {
        speedIndex = (speedIndex + 1) % speedValues.size
        applySpeed()
        prefs.playerSpeed = speedIndex
        showOsd(true)
    }

    private fun applySpeed() {
        // Speed changes only make sense for on-demand content.
        player.setPlaybackSpeed(if (supportsSeek()) speedValues[speedIndex] else 1f)
        binding.speedBtn.text = getString(R.string.speed_format, speedLabels[speedIndex])
    }

    // ── Actions ─────────────────────────────────────────────────────────

    private fun toggleFavorite() {
        val channel = current() ?: return
        val added = prefs.toggleFavorite(channel.id)
        binding.favBtn.setImageResource(
            if (added) R.drawable.ic_star else R.drawable.ic_star_border
        )
        Toast.makeText(
            this,
            getString(if (added) R.string.fav_added else R.string.fav_removed),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun toggleDrawer(forceOpen: Boolean? = null) {
        val open = forceOpen ?: (binding.channelDrawer.visibility != View.VISIBLE)
        binding.channelDrawer.visibility = if (open) View.VISIBLE else View.GONE
        if (open) {
            showOsd(false)
            binding.playerChannelList.requestFocus()
        } else {
            binding.playerChannelList.clearFocus()
        }
    }

    // ── OSD ─────────────────────────────────────────────────────────────

    private fun showOsd(visible: Boolean) {
        binding.osd.visibility = if (visible) View.VISIBLE else View.GONE
        binding.overlayTop.visibility = if (visible) View.VISIBLE else View.GONE
        binding.centerPanel.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            handler.removeCallbacks(hideOsdRunnable)
            handler.postDelayed(hideOsdRunnable, OSD_TIMEOUT_MS)
            updateProgress()
        } else {
            handler.removeCallbacks(hideOsdRunnable)
        }
    }

    private fun toggleOsd() {
        showOsd(binding.osd.visibility != View.VISIBLE)
    }

    private fun showIndicator(iconRes: Int, text: String) {
        binding.indicatorIcon.setImageResource(iconRes)
        binding.indicatorText.text = text
        binding.indicator.visibility = View.VISIBLE
        handler.removeCallbacks(hideIndicatorRunnable)
        handler.postDelayed(hideIndicatorRunnable, INDICATOR_TIMEOUT_MS)
    }

    private fun showError(message: String) {
        binding.playerErrorGroup.visibility = View.VISIBLE
        binding.playerProgress.visibility = View.GONE
        binding.playerErrorText.visibility = View.VISIBLE
        binding.playerRetryBtn.visibility = View.VISIBLE
        binding.playerErrorText.text = message
    }

    private fun hideError() {
        binding.playerErrorGroup.visibility = View.GONE
        binding.playerErrorText.visibility = View.GONE
        binding.playerRetryBtn.visibility = View.GONE
    }

    // ── Remote / keyboard ───────────────────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // While the channel drawer is open it owns the D-pad.
        if (binding.channelDrawer.visibility == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
                toggleDrawer(false)
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> { adjustVolume(1); return true }
            KeyEvent.KEYCODE_VOLUME_DOWN -> { adjustVolume(-1); return true }

            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT -> { next(); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { prev(); return true }

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { togglePlayPause(); return true }

            KeyEvent.KEYCODE_MENU -> { toggleDrawer(); return true }

            KeyEvent.KEYCODE_DPAD_UP -> {
                if (adjustBrightness) adjustBrightness(BRIGHTNESS_STEP) else adjustVolume(1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (adjustBrightness) adjustBrightness(-BRIGHTNESS_STEP) else adjustVolume(-1)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> { seekBy(-SEEK_STEP_MS); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { seekBy(SEEK_STEP_MS); return true }

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_SPACE -> {
                if (binding.osd.visibility == View.VISIBLE) togglePlayPause() else showOsd(true)
                return true
            }

            KeyEvent.KEYCODE_BACK -> {
                if (binding.osd.visibility == View.VISIBLE) {
                    showOsd(false)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ── Lifecycle ───────────────────────────────────────────────────────

    override fun onStart() {
        super.onStart()
        handler.post(clockTick)
        handler.post(progressTick)
        if (wasPlaying && player.playbackState != Player.STATE_ENDED) player.play()
    }

    override fun onStop() {
        super.onStop()
        wasPlaying = player.isPlaying
        handler.removeCallbacks(clockTick)
        handler.removeCallbacks(progressTick)
        handler.removeCallbacks(hideOsdRunnable)
        handler.removeCallbacks(hideIndicatorRunnable)
        player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        epgJob?.cancel()
        binding.playerView.player = null
        player.release()
    }

    companion object {
        private const val EXTRA_INDEX = "extra_index"

        private const val SEEK_STEP_MS = 10_000L
        private const val BRIGHTNESS_STEP = 0.05f
        private const val MIN_BRIGHTNESS = 0.05f
        private const val OSD_TIMEOUT_MS = 5_000L
        private const val INDICATOR_TIMEOUT_MS = 1_200L
        private const val DEFAULT_SPEED_INDEX = 2 // 1.0×

        /**
         * Hands [channels] to the player through [MyIptvApplication.playQueue] and
         * ships only the start [index] in the Intent. Putting the list in an Intent
         * extra would crash `startActivity` for any real-sized playlist.
         */
        fun intent(context: Context, channels: List<Channel>, index: Int): Intent {
            MyIptvApplication.from(context).playQueue = channels
            return Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_INDEX, index)
        }
    }
}
