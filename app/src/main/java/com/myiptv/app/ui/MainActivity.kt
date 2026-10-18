package com.myiptv.app.ui

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.myiptv.app.MyIptvApplication
import com.myiptv.app.R
import com.myiptv.app.data.CategoryKeys
import com.myiptv.app.data.CategoryRow
import com.myiptv.app.data.Channel
import com.myiptv.app.data.ContentType
import com.myiptv.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var channelAdapter: ChannelAdapter

    private val repository get() = MyIptvApplication.from(this).repository
    private val prefs get() = MyIptvApplication.from(this).prefs
    private val settings get() = MyIptvApplication.from(this).settings

    /** Result handler for the in-app Settings screen. */
    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                updateResumeChip()
                loadPlaylist(forceRefresh = true)
            } else if (!settings.isConfigured) {
                // Backed out of first-run setup without saving: nothing to show.
                finishAffinity()
            }
        }

    private var allChannels: List<Channel> = emptyList()
    private var visibleChannels: List<Channel> = emptyList()
    private var contentType: ContentType = ContentType.LIVE
    private var selectedCategory: String = CategoryKeys.ALL
    private var query: String = ""
    private var hasError: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclers()
        setupTabs()
        setupSearch()

        binding.refreshBtn.setOnClickListener { loadPlaylist(forceRefresh = true) }
        binding.retryBtn.setOnClickListener { loadPlaylist(forceRefresh = true) }
        binding.settingsBtn.setOnClickListener { openSettings() }

        updateResumeChip()

        // First launch (or after the user cleared their details): ask for the
        // connection info before trying to load anything.
        if (settings.isConfigured) {
            loadPlaylist()
        } else {
            openSettings()
        }
    }

    private fun openSettings() {
        settingsLauncher.launch(SettingsActivity.intent(this, firstRun = !settings.isConfigured))
    }

    override fun onResume() {
        super.onResume()
        if (allChannels.isNotEmpty()) {
            buildCategories()
            applyFilters()
        }
        updateResumeChip()
    }

    private fun isLandscape(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun setupRecyclers() {
        categoryAdapter = CategoryAdapter { row ->
            selectedCategory = row.key
            categoryAdapter.selectedKey = row.key
            applyFilters()
        }
        binding.categoryList.layoutManager =
            if (isLandscape()) LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
            else LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.categoryList.adapter = categoryAdapter
        binding.categoryList.itemAnimator = null

        channelAdapter = ChannelAdapter(
            onPlay = { openChannel(it) },
            onToggleFavorite = { toggleFavorite(it) }
        )
        binding.channelList.layoutManager = GridLayoutManager(this, gridSpanCount())
        binding.channelList.adapter = channelAdapter
        binding.channelList.itemAnimator = null
    }

    private fun gridSpanCount(): Int {
        val cfg = resources.configuration
        val available =
            if (cfg.orientation == Configuration.ORIENTATION_LANDSCAPE) cfg.screenWidthDp - 240
            else cfg.screenWidthDp
        return maxOf(2, available / 210)
    }

    private fun setupTabs() {
        binding.tabLive.setOnClickListener { selectType(ContentType.LIVE) }
        binding.tabMovies.setOnClickListener { selectType(ContentType.VOD) }
        binding.tabSeries.setOnClickListener { selectType(ContentType.SERIES) }
        updateTabUi()
    }

    private fun selectType(type: ContentType) {
        if (contentType == type) return
        contentType = type
        selectedCategory = CategoryKeys.ALL
        updateTabUi()
        buildCategories()
        applyFilters()
    }

    private fun updateTabUi() {
        binding.tabLive.isSelected = contentType == ContentType.LIVE
        binding.tabMovies.isSelected = contentType == ContentType.VOD
        binding.tabSeries.isSelected = contentType == ContentType.SERIES
    }

    private fun setupSearch() {
        binding.searchInput.doAfterTextChanged { editable ->
            query = editable?.toString()?.trim().orEmpty()
            applyFilters()
        }
    }

    private fun loadPlaylist(forceRefresh: Boolean = false) {
        hasError = false
        binding.progress.visibility = View.VISIBLE
        binding.errorGroup.visibility = View.GONE
        binding.emptyText.visibility = View.GONE

        lifecycleScope.launch {
            try {
                allChannels = repository.load(forceRefresh)
                binding.progress.visibility = View.GONE
                buildCategories()
                applyFilters()
                updateResumeChip()
                if (isLandscape()) {
                    binding.channelList.post { binding.channelList.requestFocus() }
                }
            } catch (e: Exception) {
                hasError = true
                binding.progress.visibility = View.GONE
                binding.errorGroup.visibility = View.VISIBLE
                binding.errorText.text = getString(R.string.error_load)
            }
        }
    }

    private fun buildCategories() {
        val typed = allChannels.filter { it.type == contentType }
        val rows = ArrayList<CategoryRow>()
        rows.add(CategoryRow(CategoryKeys.ALL, getString(R.string.all_channels), typed.size))
        rows.add(
            CategoryRow(
                CategoryKeys.FAVORITES,
                getString(R.string.favorites),
                typed.count { prefs.isFavorite(it.id) }
            )
        )
        val groups = LinkedHashMap<String, Int>()
        typed.forEach { ch -> groups[ch.category] = (groups[ch.category] ?: 0) + 1 }
        groups.forEach { (name, count) -> rows.add(CategoryRow(name, name, count)) }

        if (rows.none { it.key == selectedCategory }) selectedCategory = CategoryKeys.ALL
        categoryAdapter.selectedKey = selectedCategory
        categoryAdapter.submit(rows)
    }

    private fun applyFilters() {
        if (hasError) return
        val typed = allChannels.filter { it.type == contentType }
        var list = when (selectedCategory) {
            CategoryKeys.ALL -> typed
            CategoryKeys.FAVORITES -> typed.filter { prefs.isFavorite(it.id) }
            else -> typed.filter { it.category == selectedCategory }
        }
        if (query.isNotBlank()) {
            val q = query.lowercase()
            list = list.filter { it.name.lowercase().contains(q) }
        }

        visibleChannels = list
        channelAdapter.setFavorites(prefs.favorites())
        channelAdapter.submit(list)

        if (list.isEmpty()) {
            val typedEmpty = typed.isEmpty()
            binding.emptyText.setText(
                if (typedEmpty) R.string.empty_source else R.string.empty_category
            )
            binding.emptyText.visibility = View.VISIBLE
        } else {
            binding.emptyText.visibility = View.GONE
        }
    }

    private fun openChannel(channel: Channel) {
        // Series are not playable themselves — open the season/episode screen.
        if (channel.type == ContentType.SERIES) {
            startActivity(SeriesActivity.intent(this, channel))
            return
        }
        val index = visibleChannels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        rememberLast(channel)
        startActivity(PlayerActivity.intent(this, visibleChannels, index))
    }

    private fun rememberLast(channel: Channel) {
        prefs.lastId = channel.id
        prefs.lastName = channel.name
        prefs.lastUrl = channel.streamUrl
        prefs.lastCategory = channel.category
    }

    private fun toggleFavorite(channel: Channel) {
        val added = prefs.toggleFavorite(channel.id)
        Toast.makeText(
            this,
            getString(if (added) R.string.fav_added else R.string.fav_removed),
            Toast.LENGTH_SHORT
        ).show()
        buildCategories()
        applyFilters()
    }

    private fun updateResumeChip() {
        val name = prefs.lastName
        val url = prefs.lastUrl
        if (name != null && url != null) {
            binding.resumeChip.text = getString(R.string.resume_last, name)
            binding.resumeChip.visibility = View.VISIBLE
            binding.resumeChip.setOnClickListener {
                val channel = Channel(
                    id = "resume",
                    name = name,
                    logo = null,
                    streamUrl = url,
                    category = prefs.lastCategory ?: "",
                    type = ContentType.LIVE
                )
                startActivity(PlayerActivity.intent(this, arrayListOf(channel), 0))
            }
        } else {
            binding.resumeChip.visibility = View.GONE
        }
    }
}
