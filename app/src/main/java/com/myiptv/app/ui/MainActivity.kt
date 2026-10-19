package com.myiptv.app.ui

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.myiptv.app.MyIptvApplication
import com.myiptv.app.R
import com.myiptv.app.data.CategoryKeys
import com.myiptv.app.data.CategoryRow
import com.myiptv.app.data.Channel
import com.myiptv.app.data.ChannelNames
import com.myiptv.app.data.ChannelRow
import com.myiptv.app.data.ContentType
import com.myiptv.app.data.SortMode
import com.myiptv.app.data.labelRes
import com.myiptv.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : LocalizedActivity() {

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

    /**
     * The catalogue, split once by type. Filtering then works on one slice
     * instead of rescanning every live channel, movie and series on each
     * keystroke.
     */
    private var liveItems: List<Channel> = emptyList()
    private var vodItems: List<Channel> = emptyList()
    private var seriesItems: List<Channel> = emptyList()

    private var visibleChannels: List<Channel> = emptyList()
    private var contentType: ContentType = ContentType.LIVE
    private var selectedCategory: String = CategoryKeys.ALL
    private var query: String = ""
    private var hasError: Boolean = false

    /** Loading/filtering jobs; only the newest of each may touch the UI. */
    private var loadJob: Job? = null
    private var filterJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclers()
        setupTabs()
        setupSort()
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
        if (hasData()) {
            buildCategories()
            applyFilters()
        }
        updateResumeChip()
    }

    private fun hasData(): Boolean =
        liveItems.isNotEmpty() || vodItems.isNotEmpty() || seriesItems.isNotEmpty()

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
            typeLabel = { getString(it.labelRes) },
            onPlay = { openChannel(it) },
            onToggleFavorite = { toggleFavorite(it) }
        )
        binding.channelList.layoutManager = GridLayoutManager(this, gridSpanCount())
        binding.channelList.adapter = channelAdapter
        binding.channelList.itemAnimator = null
        binding.channelList.setHasFixedSize(true)
        binding.channelList.setItemViewCacheSize(12)
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

    // ── ordering ─────────────────────────────────────────────────────────

    private fun setupSort() {
        binding.sortChip.setOnClickListener { cycleSort() }
        updateSortUi()
    }

    /** One tap cycles: provider order → newest first → by name. */
    private fun cycleSort() {
        prefs.sortMode = when (prefs.sortMode) {
            SortMode.PROVIDER -> SortMode.RECENT
            SortMode.RECENT -> SortMode.NAME
            SortMode.NAME -> SortMode.PROVIDER
        }
        updateSortUi()
        applyFilters()
    }

    private fun updateSortUi() {
        val label = getString(sortLabelRes(prefs.sortMode))
        binding.sortChip.text = getString(R.string.sort_chip, label)
        binding.sortChip.contentDescription = getString(R.string.cd_sort) + ". " + label
    }

    private fun sortLabelRes(mode: SortMode): Int = when (mode) {
        SortMode.PROVIDER -> R.string.sort_provider
        SortMode.RECENT -> R.string.sort_recent
        SortMode.NAME -> R.string.sort_name
    }

    private fun setupSearch() {
        binding.searchInput.doAfterTextChanged { editable ->
            val next = editable?.toString()?.trim().orEmpty()
            if (next == query) return@doAfterTextChanged
            query = next
            applyFilters(debounce = true)
        }
    }

    // ── loading ──────────────────────────────────────────────────────────

    /**
     * Shows the cached catalogue straight away (it survives restarts), then
     * refreshes from the panel in the background. A manual refresh skips
     * straight to the download.
     */
    private fun loadPlaylist(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        hasError = false
        binding.errorGroup.visibility = View.GONE

        loadJob = lifecycleScope.launch {
            var showingSomething = false

            if (!forceRefresh) {
                val cached = repository.cachedOrNull()
                if (cached != null) {
                    showCatalog(cached)
                    showingSomething = true
                } else {
                    binding.progress.visibility = View.VISIBLE
                }
            } else {
                binding.progress.visibility = View.VISIBLE
            }

            try {
                val fresh = repository.load(forceRefresh = true)
                binding.progress.visibility = View.GONE
                showCatalog(fresh)
            } catch (e: Exception) {
                binding.progress.visibility = View.GONE
                // With a cached catalogue on screen a failed refresh is not worth
                // interrupting the user for; the next launch tries again.
                if (!showingSomething) {
                    hasError = true
                    binding.errorGroup.visibility = View.VISIBLE
                    binding.errorText.text = getString(R.string.error_load)
                }
            }
        }
    }

    private fun showCatalog(list: List<Channel>) {
        liveItems = list.filter { it.type == ContentType.LIVE }
        vodItems = list.filter { it.type == ContentType.VOD }
        seriesItems = list.filter { it.type == ContentType.SERIES }

        buildCategories()
        applyFilters()
        updateResumeChip()

        if (isLandscape()) {
            binding.channelList.post { binding.channelList.requestFocus() }
        }
    }

    private fun typedItems(): List<Channel> = when (contentType) {
        ContentType.LIVE -> liveItems
        ContentType.VOD -> vodItems
        ContentType.SERIES -> seriesItems
    }

    // ── filtering ────────────────────────────────────────────────────────

    private fun buildCategories() {
        val typed = typedItems()
        val favorites = prefs.favorites()

        val groups = LinkedHashMap<String, Int>()
        var favoriteCount = 0
        for (channel in typed) {
            if (favorites.contains(channel.id)) favoriteCount++
            groups[channel.category] = (groups[channel.category] ?: 0) + 1
        }

        val rows = ArrayList<CategoryRow>(groups.size + 2)
        rows.add(CategoryRow(CategoryKeys.ALL, getString(R.string.all_channels), typed.size))
        rows.add(CategoryRow(CategoryKeys.FAVORITES, getString(R.string.favorites), favoriteCount))
        groups.forEach { (name, count) -> rows.add(CategoryRow(name, name, count)) }

        if (rows.none { it.key == selectedCategory }) selectedCategory = CategoryKeys.ALL
        categoryAdapter.submit(rows)
        categoryAdapter.selectedKey = selectedCategory
    }

    /**
     * Rebuilds the visible list. The work itself happens on a background
     * dispatcher and the previous pass is cancelled, so hammering the search box
     * never queues up expensive filtering on the main thread.
     */
    private fun applyFilters(debounce: Boolean = false) {
        if (hasError) return

        val typed = typedItems()
        val category = selectedCategory
        val needle = query.lowercase()
        val sort = prefs.sortMode
        val favorites = prefs.favorites()

        filterJob?.cancel()
        filterJob = lifecycleScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)

            val result = withContext(Dispatchers.Default) {
                filterAndSort(typed, category, needle, sort, favorites)
            }
            visibleChannels = result.channels
            channelAdapter.submitList(result.rows)
            updateEmptyState(typed.isEmpty(), result.channels.isEmpty())
        }
    }

    private class Result(val channels: List<Channel>, val rows: List<ChannelRow>)

    private fun filterAndSort(
        typed: List<Channel>,
        category: String,
        needle: String,
        sort: SortMode,
        favorites: Set<String>
    ): Result {
        val matched = ArrayList<Channel>(typed.size)
        when (category) {
            CategoryKeys.ALL -> matched.addAll(typed)
            CategoryKeys.FAVORITES -> {
                for (channel in typed) if (favorites.contains(channel.id)) matched.add(channel)
            }
            else -> {
                for (channel in typed) if (channel.category == category) matched.add(channel)
            }
        }

        if (needle.isNotEmpty()) {
            // While Arabic is in use the rows show Arabic names, so an Arabic
            // search has to match those too - not only what the provider sent.
            val matchArabic = ChannelNames.isArabic && ChannelNames.isArabicText(needle)
            val iterator = matched.iterator()
            while (iterator.hasNext()) {
                val channel = iterator.next()
                if (channel.name.lowercase().contains(needle)) continue
                if (matchArabic && ChannelNames.toArabic(channel.name).contains(needle)) continue
                iterator.remove()
            }
        }

        when (sort) {
            SortMode.PROVIDER -> Unit
            SortMode.RECENT -> matched.sortWith(BY_RECENT)
            SortMode.NAME -> matched.sortWith(BY_NAME)
        }

        val rows = ArrayList<ChannelRow>(matched.size)
        for (channel in matched) rows.add(ChannelRow(channel, favorites.contains(channel.id)))
        return Result(matched, rows)
    }

    private fun updateEmptyState(typedEmpty: Boolean, listEmpty: Boolean) {
        if (listEmpty) {
            binding.emptyText.setText(
                if (typedEmpty) R.string.empty_source else R.string.empty_category
            )
            binding.emptyText.visibility = View.VISIBLE
        } else {
            binding.emptyText.visibility = View.GONE
        }
    }

    // ── actions ──────────────────────────────────────────────────────────

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
            binding.resumeChip.text = getString(R.string.resume_last, ChannelNames.display(name))
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

    private companion object {
        /** Types are stopped while the user is still typing. */
        const val SEARCH_DEBOUNCE_MS = 140L

        /** Name ties keep provider order, so the sort stays predictable. */
        val BY_NAME = Comparator<Channel> { a, b ->
            a.name.compareTo(b.name, ignoreCase = true)
        }

        /** Newest first; items the provider gave no timestamp for sink to the bottom. */
        val BY_RECENT = Comparator<Channel> { a, b ->
            val byDate = b.added.compareTo(a.added)
            if (byDate != 0) byDate else a.name.compareTo(b.name, ignoreCase = true)
        }
    }
}
