package com.myiptv.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.myiptv.app.MyIptvApplication
import com.myiptv.app.R
import com.myiptv.app.data.Profile
import com.myiptv.app.data.profilesFromJson
import com.myiptv.app.data.toProfileJson
import com.myiptv.app.databinding.ActivitySettingsBinding

/**
 * In-app connection settings: one or more Xtream Codes panels - each with its
 * own host, username and password - plus the display language.
 *
 * One panel is active at a time. The chips across the top pick which panel the
 * fields below are editing; saving connects the app to that one.
 *
 * On first run this screen is opened automatically and cannot be dismissed
 * without saving; afterwards it is reachable from the gear button on the home
 * screen.
 */
class SettingsActivity : LocalizedActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val app get() = MyIptvApplication.from(this)
    private val settings get() = app.settings
    private val prefs get() = app.prefs
    private val repository get() = app.repository

    private var liveExt: String = "m3u8"
    private var firstRun: Boolean = false

    /**
     * The panels as edited on this screen. Storage is only written when the user
     * saves (and, for a removal, right away) so backing out changes nothing.
     */
    private val profiles = mutableListOf<Profile>()
    private var selectedId: String = ""

    /** Chip per profile id, so selecting one does not rebuild the whole row. */
    private val chips = LinkedHashMap<String, TextView>()

    /** Guards the watcher while the fields are being filled in from code. */
    private var fillingFields = false

    /**
     * Mirrors what is typed into the profile it belongs to, which is what makes
     * switching chips - or the language, which recreates the activity - safe.
     */
    private val fieldWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (fillingFields) return
            val current = selectedProfile ?: return
            val index = profiles.indexOfFirst { it.id == current.id }
            if (index < 0) return
            profiles[index] = current.copy(
                server = binding.serverInput.text.toString(),
                username = binding.usernameInput.text.toString(),
                password = binding.passwordInput.text.toString()
            )
            relabel()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firstRun = intent.getBooleanExtra(EXTRA_FIRST_RUN, false)
        binding.backBtn.visibility = if (firstRun) View.GONE else View.VISIBLE

        loadProfiles(savedInstanceState)
        prefill()
        setupControls()
        buildChips()

        binding.backBtn.setOnClickListener { finish() }
        binding.saveBtn.setOnClickListener { save() }
        binding.profileAddBtn.setOnClickListener { addProfile() }
        binding.profileDelBtn.setOnClickListener { confirmRemove() }

        onBackPressedDispatcher.addCallback(this) {
            if (firstRun) finishAffinity() else finish()
        }

        if (firstRun) binding.serverInput.requestFocus()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Keeps a panel that was just added, and edits that were not saved yet,
        // across the recreate a language switch triggers.
        outState.putString(STATE_PROFILES, profiles.toProfileJson())
        outState.putString(STATE_SELECTED, selectedId)
    }

    // ── profiles ─────────────────────────────────────────────────────────
    private fun loadProfiles(state: Bundle?) {
        val restored = profilesFromJson(state?.getString(STATE_PROFILES))
        profiles += if (restored.isNotEmpty()) restored else settings.profiles
        if (profiles.isEmpty()) profiles += settings.newProfile()

        val wanted = state?.getString(STATE_SELECTED)
        selectedId = if (wanted != null && profiles.any { it.id == wanted }) {
            wanted
        } else {
            profiles.firstOrNull { it.id == settings.activeProfileId }?.id ?: profiles.first().id
        }
    }

    private val selectedProfile: Profile?
        get() = profiles.firstOrNull { it.id == selectedId }

    private fun prefill() {
        liveExt = settings.xtreamLiveExt
        applyLiveExt()
        applyLanguage()
        fillFields()
    }

    private fun fillFields() {
        val profile = selectedProfile
        fillingFields = true
        binding.serverInput.setText(profile?.server.orEmpty())
        binding.usernameInput.setText(profile?.username.orEmpty())
        binding.passwordInput.setText(profile?.password.orEmpty())
        fillingFields = false
    }

    private fun buildChips() {
        binding.profileRow.removeAllViews()
        chips.clear()

        val captions = captions()
        val gap = dp(6)
        profiles.forEachIndexed { index, profile ->
            val chip = TextView(this, null, 0, R.style.SettingsChip).apply {
                id = R.id.profileChip
                text = captions[profile.id].orEmpty()
                isSelected = profile.id == selectedId
                setOnClickListener { selectProfile(profile.id) }
            }
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) params.marginStart = gap
            binding.profileRow.addView(chip, params)
            chips[profile.id] = chip
        }

        binding.profileDelBtn.visibility = if (profiles.size > 1) View.VISIBLE else View.GONE
    }

    /** Refreshes the captions only, e.g. while a username is being typed. */
    private fun relabel() {
        val captions = captions()
        for ((id, chip) in chips) {
            val caption = captions[id] ?: continue
            if (chip.text != caption) chip.text = caption
        }
    }

    /**
     * Caption per panel: its username, covering the host only when two panels
     * would otherwise look the same, and a placeholder for one not filled in yet.
     */
    private fun captions(): Map<String, String> {
        val users = profiles.map { it.username.trim().lowercase() }
        val unnamed = getString(R.string.settings_profile_new)
        val blanks = users.count { it.isEmpty() }
        var seen = 0

        return profiles.associate { profile ->
            val user = profile.username.trim()
            val caption = when {
                user.isEmpty() -> if (blanks > 1) "$unnamed ${++seen}" else unnamed
                users.count { it == user.lowercase() } == 1 -> user
                else -> "$user@${host(profile.server)}".trimEnd('@')
            }
            profile.id to caption
        }
    }

    /** "http://host:8080/" -> "host". */
    private fun host(server: String): String =
        server.substringAfter("://").substringBefore('/').substringBefore(':')

    private fun selectProfile(id: String) {
        if (id == selectedId) return
        selectedId = id
        for ((profileId, chip) in chips) chip.isSelected = profileId == id
        fillFields()
    }

    private fun addProfile() {
        val fresh = settings.newProfile()
        profiles += fresh
        selectedId = fresh.id
        buildChips()
        fillFields()
        // The user is about to type the new panel in.
        binding.serverInput.requestFocus()
    }

    private fun confirmRemove() {
        val victim = selectedProfile ?: return
        if (profiles.size <= 1) return
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_profile_remove_title)
            .setMessage(
                getString(R.string.settings_profile_remove_msg, captions()[victim.id].orEmpty())
            )
            .setNegativeButton(R.string.dlg_cancel, null)
            .setPositiveButton(R.string.dlg_remove) { _, _ -> removeProfile(victim) }
            .show()
    }

    private fun removeProfile(victim: Profile) {
        val index = profiles.indexOfFirst { it.id == victim.id }
        if (index < 0) return
        profiles.removeAt(index)
        val next = profiles[(index - 1).coerceAtLeast(0)]

        // Removing a panel is destructive and can only be confirmed, so store it
        // now instead of waiting for "save" - which backing out would skip.
        val activeId = if (settings.activeProfileId == victim.id) next.id else null
        settings.replaceAll(profiles, activeId)
        if (activeId != null) {
            prefs.attachProfile(activeId)
            repository.invalidate()
            setResult(RESULT_OK)
        }

        selectedId = next.id
        buildChips()
        fillFields()
        toast(R.string.settings_profile_removed)

        if (binding.profileDelBtn.visibility == View.VISIBLE) {
            binding.profileDelBtn.requestFocus()
        } else {
            chips[next.id]?.requestFocus()
        }
    }

    // ── live format and language ─────────────────────────────────────────
    private fun setupControls() {
        binding.chipHls.setOnClickListener { setLiveExt("m3u8") }
        binding.chipTs.setOnClickListener { setLiveExt("ts") }

        binding.chipSystem.setOnClickListener { setLanguage("") }
        binding.chipEnglish.setOnClickListener { setLanguage("en") }
        binding.chipArabic.setOnClickListener { setLanguage("ar") }

        binding.serverInput.addTextChangedListener(fieldWatcher)
        binding.usernameInput.addTextChangedListener(fieldWatcher)
        binding.passwordInput.addTextChangedListener(fieldWatcher)
    }

    private fun setLiveExt(ext: String) {
        liveExt = ext
        applyLiveExt()
    }

    private fun applyLiveExt() {
        binding.chipHls.isSelected = liveExt == "m3u8"
        binding.chipTs.isSelected = liveExt != "m3u8"
    }

    /**
     * Applies the language immediately: the activity is recreated and, because
     * every field has an id, Android restores whatever the user had typed.
     */
    private fun setLanguage(tag: String) {
        if (prefs.languageTag == tag) return
        prefs.languageTag = tag
        val locales = if (tag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    private fun applyLanguage() {
        val tag = prefs.languageTag
        binding.chipSystem.isSelected = tag.isBlank()
        binding.chipEnglish.isSelected = tag == "en"
        binding.chipArabic.isSelected = tag == "ar"
    }

    // ── saving ───────────────────────────────────────────────────────────
    private fun save() {
        val server = normalizeUrl(binding.serverInput.text.toString())
        val user = binding.usernameInput.text.toString().trim()
        val pass = binding.passwordInput.text.toString().trim()

        val errorRes = when {
            server.isBlank() || user.isBlank() || pass.isBlank() -> R.string.settings_error_fields
            !isHttp(server) -> R.string.settings_error_url
            else -> 0
        }
        if (errorRes != 0) {
            binding.errorText.setText(errorRes)
            binding.errorText.visibility = View.VISIBLE
            return
        }

        val current = selectedProfile ?: settings.newProfile().also { profiles += it }
        val saved = current.copy(server = server, username = user, password = pass)
        val list = profiles.map { if (it.id == saved.id) saved else it }

        settings.xtreamLiveExt = liveExt
        settings.replaceAll(list, saved.id)
        // Favorites and the resume point belong to the panel they came from.
        prefs.attachProfile(saved.id)
        repository.invalidate()

        toast(R.string.settings_saved)
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

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_FIRST_RUN = "first_run"
        private const val STATE_PROFILES = "profiles"
        private const val STATE_SELECTED = "selected_profile"

        fun intent(context: Context, firstRun: Boolean): Intent =
            Intent(context, SettingsActivity::class.java).putExtra(EXTRA_FIRST_RUN, firstRun)
    }
}
