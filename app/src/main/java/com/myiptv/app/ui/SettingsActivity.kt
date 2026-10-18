package com.myiptv.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import com.myiptv.app.MyIptvApplication
import com.myiptv.app.R
import com.myiptv.app.config.SourceType
import com.myiptv.app.databinding.ActivitySettingsBinding

/**
 * In-app connection settings. The user picks a source type and enters either
 * Xtream credentials (server + username + password) or a full M3U playlist URL.
 *
 * On first run this screen is opened automatically and cannot be dismissed
 * without saving; afterwards it is reachable from the gear button on the home
 * screen.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val settings get() = MyIptvApplication.from(this).settings
    private val repository get() = MyIptvApplication.from(this).repository

    private var sourceType: SourceType = SourceType.XTREAM
    private var liveExt: String = "m3u8"
    private var firstRun: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firstRun = intent.getBooleanExtra(EXTRA_FIRST_RUN, false)
        binding.backBtn.visibility = if (firstRun) View.GONE else View.VISIBLE

        prefill()
        setupControls()

        binding.backBtn.setOnClickListener { finish() }
        binding.saveBtn.setOnClickListener { save() }

        onBackPressedDispatcher.addCallback(this) {
            if (firstRun) finishAffinity() else finish()
        }

        if (firstRun) binding.serverInput.requestFocus()
    }

    private fun prefill() {
        sourceType = settings.sourceType
        liveExt = settings.xtreamLiveExt

        binding.serverInput.setText(settings.xtreamServer)
        binding.usernameInput.setText(settings.xtreamUsername)
        binding.passwordInput.setText(settings.xtreamPassword)
        binding.m3uInput.setText(settings.m3uUrl)

        applySource()
        applyLiveExt()
    }

    private fun setupControls() {
        binding.tabXtream.setOnClickListener { setSource(SourceType.XTREAM) }
        binding.tabM3u.setOnClickListener { setSource(SourceType.M3U) }
        binding.chipHls.setOnClickListener { setLiveExt("m3u8") }
        binding.chipTs.setOnClickListener { setLiveExt("ts") }
    }

    private fun setSource(type: SourceType) {
        sourceType = type
        binding.errorText.visibility = View.GONE
        applySource()
    }

    private fun applySource() {
        val xtream = sourceType == SourceType.XTREAM
        binding.tabXtream.isSelected = xtream
        binding.tabM3u.isSelected = !xtream
        binding.xtreamGroup.visibility = if (xtream) View.VISIBLE else View.GONE
        binding.m3uGroup.visibility = if (xtream) View.GONE else View.VISIBLE
    }

    private fun setLiveExt(ext: String) {
        liveExt = ext
        applyLiveExt()
    }

    private fun applyLiveExt() {
        binding.chipHls.isSelected = liveExt == "m3u8"
        binding.chipTs.isSelected = liveExt != "m3u8"
    }

    private fun save() {
        val server = normalizeUrl(binding.serverInput.text.toString())
        val user = binding.usernameInput.text.toString().trim()
        val pass = binding.passwordInput.text.toString().trim()
        val m3u = normalizeUrl(binding.m3uInput.text.toString())

        val errorRes = when (sourceType) {
            SourceType.XTREAM -> when {
                server.isBlank() || user.isBlank() || pass.isBlank() -> R.string.settings_error_fields
                !isHttp(server) -> R.string.settings_error_url
                else -> 0
            }
            SourceType.M3U -> when {
                m3u.isBlank() -> R.string.settings_error_fields
                !isHttp(m3u) -> R.string.settings_error_m3u
                else -> 0
            }
        }
        if (errorRes != 0) {
            binding.errorText.setText(errorRes)
            binding.errorText.visibility = View.VISIBLE
            return
        }

        settings.sourceType = sourceType
        settings.xtreamServer = server
        settings.xtreamUsername = user
        settings.xtreamPassword = pass
        settings.xtreamLiveExt = liveExt
        settings.m3uUrl = m3u

        repository.invalidate()
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    /** Prepends http:// when the user typed a bare host, and trims whitespace. */
    private fun normalizeUrl(raw: String): String {
        var value = raw.trim()
        if (value.isEmpty()) return value
        if (!value.contains("://")) value = "http://$value"
        return value
    }

    private fun isHttp(value: String): Boolean =
        value.startsWith("http://") || value.startsWith("https://")

    companion object {
        private const val EXTRA_FIRST_RUN = "first_run"

        fun intent(context: Context, firstRun: Boolean): Intent =
            Intent(context, SettingsActivity::class.java).putExtra(EXTRA_FIRST_RUN, firstRun)
    }
}
