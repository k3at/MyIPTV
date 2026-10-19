package com.myiptv.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.myiptv.app.MyIptvApplication
import com.myiptv.app.R
import com.myiptv.app.data.CategoryRow
import com.myiptv.app.data.Channel
import com.myiptv.app.data.ChannelIds
import com.myiptv.app.data.ChannelNames
import com.myiptv.app.data.ContentType
import com.myiptv.app.data.Episode
import com.myiptv.app.databinding.ActivitySeriesBinding
import kotlinx.coroutines.launch

/**
 * Series detail screen: pick a season, then an episode. Tapping an episode plays
 * it in [PlayerActivity] with the rest of that season as the zap list.
 */
class SeriesActivity : LocalizedActivity() {

    private lateinit var binding: ActivitySeriesBinding
    private lateinit var seasonAdapter: CategoryAdapter
    private lateinit var episodeAdapter: EpisodeAdapter

    private val repository get() = MyIptvApplication.from(this).repository
    private val prefs get() = MyIptvApplication.from(this).prefs

    private var seriesId: String = ""
    private var seriesName: String = ""
    private var seriesLogo: String? = null

    private var episodes: List<Episode> = emptyList()
    private var selectedSeason: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySeriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        seriesId = intent.getStringExtra(EXTRA_ID).orEmpty()
        seriesName = intent.getStringExtra(EXTRA_NAME).orEmpty()
        seriesLogo = intent.getStringExtra(EXTRA_LOGO)
        if (seriesId.isBlank()) {
            finish()
            return
        }

        binding.seriesTitle.text = ChannelNames.display(seriesName)
        Glide.with(this)
            .load(seriesLogo)
            .placeholder(R.drawable.placeholder_channel)
            .error(R.drawable.placeholder_channel)
            .into(binding.seriesLogo)

        setupRecyclers()
        binding.backBtn.setOnClickListener { finish() }
        binding.retryBtn.setOnClickListener { loadEpisodes() }
        loadEpisodes()
    }

    private fun setupRecyclers() {
        seasonAdapter = CategoryAdapter { row ->
            selectedSeason = row.key.toIntOrNull()
            seasonAdapter.selectedKey = row.key
            applySeason()
        }
        binding.seasonList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.seasonList.adapter = seasonAdapter
        binding.seasonList.itemAnimator = null

        episodeAdapter = EpisodeAdapter { episode -> play(episode) }
        binding.episodeList.layoutManager = LinearLayoutManager(this)
        binding.episodeList.adapter = episodeAdapter
        binding.episodeList.itemAnimator = null
    }

    private fun loadEpisodes() {
        binding.progress.visibility = View.VISIBLE
        binding.errorGroup.visibility = View.GONE
        binding.emptyText.visibility = View.GONE
        binding.episodeList.visibility = View.INVISIBLE

        lifecycleScope.launch {
            episodes = repository.loadEpisodes(seriesId)
            binding.progress.visibility = View.GONE
            binding.episodeList.visibility = View.VISIBLE

            if (episodes.isEmpty()) {
                binding.seriesSubtitle.text = getString(R.string.tab_series)
                binding.emptyText.visibility = View.VISIBLE
                seasonAdapter.submit(emptyList())
                episodeAdapter.submit(emptyList())
                return@launch
            }

            binding.seriesSubtitle.text = resources.getQuantityString(
                R.plurals.episode_count, episodes.size, episodes.size
            )
            buildSeasons()
        }
    }

    private fun buildSeasons() {
        val bySeason = LinkedHashMap<Int, Int>()
        episodes.forEach { bySeason[it.season] = (bySeason[it.season] ?: 0) + 1 }

        val rows = bySeason.map { (season, count) ->
            CategoryRow(
                key = season.toString(),
                title = getString(R.string.season_n, season),
                count = count
            )
        }
        if (selectedSeason == null || bySeason.keys.none { it == selectedSeason }) {
            selectedSeason = bySeason.keys.firstOrNull()
        }
        seasonAdapter.selectedKey = selectedSeason?.toString()
        seasonAdapter.submit(rows)
        applySeason()
    }

    private fun applySeason() {
        val season = selectedSeason
        val list = if (season == null) episodes else episodes.filter { it.season == season }
        episodeAdapter.submit(list)
    }

    private fun play(episode: Episode) {
        val season = selectedSeason ?: episode.season
        val seasonEpisodes = episodes.filter { it.season == season }.map { toChannel(it) }
        if (seasonEpisodes.isEmpty()) return
        val index = seasonEpisodes.indexOfFirst { it.id == episode.id }.coerceAtLeast(0)
        prefs.lastId = episode.id
        prefs.lastName = episode.title
        prefs.lastUrl = episode.streamUrl
        prefs.lastCategory = seriesName
        startActivity(PlayerActivity.intent(this, seasonEpisodes, index))
    }

    private fun toChannel(episode: Episode): Channel = Channel(
        id = episode.id,
        name = "S${episode.season}E${episode.number} · ${episode.title}",
        logo = episode.logo ?: seriesLogo,
        streamUrl = episode.streamUrl,
        category = seriesName,
        type = ContentType.VOD
    )

    companion object {
        private const val EXTRA_ID = "series_id"
        private const val EXTRA_NAME = "series_name"
        private const val EXTRA_LOGO = "series_logo"

        fun intent(context: Context, seriesChannel: Channel): Intent = Intent(context, SeriesActivity::class.java)
            .putExtra(EXTRA_ID, ChannelIds.suffix(seriesChannel.id, ChannelIds.XT_SERIES))
            .putExtra(EXTRA_NAME, seriesChannel.name)
            .putExtra(EXTRA_LOGO, seriesChannel.logo)
    }
}
