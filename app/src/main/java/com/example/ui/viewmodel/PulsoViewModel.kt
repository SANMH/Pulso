package com.example.ui.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.local.PulsoDatabase
import com.example.data.local.WatchPreferences
import com.example.data.local.WatchPreferencesRepository
import com.example.data.model.AutoScrollSpeed
import com.example.data.model.SocialNetwork
import com.example.data.model.SocialPost
import com.example.data.model.WatchShapeMode
import com.example.data.repository.SocialFeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GestureTelemetry(
    @StringRes val labelRes: Int = R.string.telemetry_ready,
    val formatArg: String? = null,
    val timestampMillis: Long = 0L
)

data class NetworkFeedState(
    val posts: List<SocialPost> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val hasLoadedOnce: Boolean = false
)

data class PulsoUiState(
    val activeNetwork: SocialNetwork = SocialNetwork.BLUESKY,
    val feeds: Map<SocialNetwork, NetworkFeedState> = SocialNetwork.entries.associateWith { NetworkFeedState() },
    val preferences: WatchPreferences = WatchPreferences(),
    val telemetry: GestureTelemetry = GestureTelemetry(),
    val showQuickMenu: Boolean = false,
    val showSettings: Boolean = false,
    val showGestureGuide: Boolean = false,
    val zoomedImageUrl: String? = null
) {
    val activeFeed: NetworkFeedState
        get() = feeds[activeNetwork] ?: NetworkFeedState()
}

class PulsoViewModel(application: Application) : AndroidViewModel(application) {
    private val database = PulsoDatabase.getInstance(application)
    private val repository = SocialFeedRepository(database.socialPostDao())
    private val prefsRepository = WatchPreferencesRepository(application)

    private val activeNetworkFlow = MutableStateFlow(SocialNetwork.BLUESKY)
    private val loadingNetworksFlow = MutableStateFlow<Set<SocialNetwork>>(emptySet())
    private val errorNetworksFlow = MutableStateFlow<Map<SocialNetwork, String?>>(emptyMap())
    private val loadedNetworksFlow = MutableStateFlow<Set<SocialNetwork>>(emptySet())
    private val telemetryFlow = MutableStateFlow(GestureTelemetry())
    private val modalStateFlow = MutableStateFlow(ModalState())

    private data class ModalState(
        val showQuickMenu: Boolean = false,
        val showSettings: Boolean = false,
        val showGestureGuide: Boolean = false,
        val zoomedImageUrl: String? = null
    )

    private val allFeedsFlow = combine(
        repository.observeNetworkFeed(SocialNetwork.BLUESKY),
        repository.observeNetworkFeed(SocialNetwork.MASTODON),
        repository.observeNetworkFeed(SocialNetwork.REDDIT),
        repository.observeNetworkFeed(SocialNetwork.BOOKMARKS)
    ) { bsky, mstdn, reddit, bookmarks ->
        mapOf(
            SocialNetwork.BLUESKY to bsky,
            SocialNetwork.MASTODON to mstdn,
            SocialNetwork.REDDIT to reddit,
            SocialNetwork.BOOKMARKS to bookmarks
        )
    }

    val uiState: StateFlow<PulsoUiState> = combine(
        activeNetworkFlow,
        allFeedsFlow,
        loadingNetworksFlow,
        errorNetworksFlow,
        loadedNetworksFlow,
        prefsRepository.preferencesFlow,
        telemetryFlow,
        modalStateFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val active = values[0] as SocialNetwork
        @Suppress("UNCHECKED_CAST")
        val postsMap = values[1] as Map<SocialNetwork, List<SocialPost>>
        @Suppress("UNCHECKED_CAST")
        val loadingSet = values[2] as Set<SocialNetwork>
        @Suppress("UNCHECKED_CAST")
        val errorsMap = values[3] as Map<SocialNetwork, String?>
        @Suppress("UNCHECKED_CAST")
        val loadedSet = values[4] as Set<SocialNetwork>
        val prefs = values[5] as WatchPreferences
        val telemetry = values[6] as GestureTelemetry
        val modal = values[7] as ModalState

        val feedsMap = SocialNetwork.entries.associateWith { network ->
            NetworkFeedState(
                posts = postsMap[network].orEmpty(),
                isLoading = network in loadingSet,
                errorMessage = errorsMap[network],
                hasLoadedOnce = network in loadedSet
            )
        }

        PulsoUiState(
            activeNetwork = active,
            feeds = feedsMap,
            preferences = prefs,
            telemetry = telemetry,
            showQuickMenu = modal.showQuickMenu,
            showSettings = modal.showSettings,
            showGestureGuide = modal.showGestureGuide,
            zoomedImageUrl = modal.zoomedImageUrl
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PulsoUiState()
    )

    init {
        refreshNetwork(SocialNetwork.BLUESKY)
        refreshNetwork(SocialNetwork.MASTODON)
    }

    fun selectNetwork(network: SocialNetwork, fromSwipe: Boolean = false) {
        if (activeNetworkFlow.value == network) return
        activeNetworkFlow.value = network
        if (fromSwipe) {
            recordTelemetry(
                R.string.telemetry_channel,
                getApplication<Application>().getString(network.labelRes)
            )
        }
        val currentFeed = uiState.value.feeds[network]
        if (network != SocialNetwork.BOOKMARKS && currentFeed?.hasLoadedOnce != true) {
            refreshNetwork(network)
        }
    }

    fun refreshNetwork(network: SocialNetwork = activeNetworkFlow.value) {
        if (network == SocialNetwork.BOOKMARKS) return
        if (network in loadingNetworksFlow.value) return

        viewModelScope.launch {
            loadingNetworksFlow.update { it + network }
            errorNetworksFlow.update { it - network }

            val result = repository.refreshNetwork(network)
            result.onFailure { throwable ->
                errorNetworksFlow.update {
                    it + (network to (throwable.localizedMessage ?: "Error de conexión"))
                }
            }

            loadedNetworksFlow.update { it + network }
            loadingNetworksFlow.update { it - network }
        }
    }

    fun onDoubleTapLike(post: SocialPost): Boolean {
        val targetLiked = !post.isLiked
        viewModelScope.launch {
            repository.setLiked(post.id, targetLiked)
        }
        recordTelemetry(if (targetLiked) R.string.telemetry_like else R.string.telemetry_unlike)
        return targetLiked
    }

    fun toggleLike(post: SocialPost) {
        viewModelScope.launch {
            val liked = repository.toggleLike(post.id)
            recordTelemetry(if (liked) R.string.telemetry_like else R.string.telemetry_unlike)
        }
    }

    fun toggleBookmark(post: SocialPost) {
        viewModelScope.launch {
            val bookmarked = repository.toggleBookmark(post.id)
            if (bookmarked) {
                recordTelemetry(R.string.telemetry_bookmark)
            }
        }
    }

    fun cycleAutoScrollSpeed() {
        val current = uiState.value.preferences.autoScrollSpeed
        val values = AutoScrollSpeed.entries
        val next = values[(current.ordinal + 1) % values.size]
        setAutoScrollSpeed(next)
    }

    fun setAutoScrollSpeed(speed: AutoScrollSpeed) {
        viewModelScope.launch {
            prefsRepository.setAutoScrollSpeed(speed)
            if (speed != AutoScrollSpeed.OFF) {
                recordTelemetry(R.string.telemetry_autoscroll)
            }
        }
    }

    fun setTapToScroll(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setTapToScroll(enabled) }
    }

    fun setHaptics(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setHaptics(enabled) }
    }

    fun setLargeText(enabled: Boolean) {
        viewModelScope.launch { prefsRepository.setLargeText(enabled) }
    }

    fun setWatchShapeMode(mode: WatchShapeMode) {
        viewModelScope.launch { prefsRepository.setWatchShapeMode(mode) }
    }

    fun dismissGestureHint() {
        viewModelScope.launch { prefsRepository.markGestureHintSeen() }
        modalStateFlow.update { it.copy(showGestureGuide = false) }
    }

    fun openQuickMenu(open: Boolean) {
        modalStateFlow.update { it.copy(showQuickMenu = open) }
    }

    fun openSettings(open: Boolean) {
        modalStateFlow.update { it.copy(showSettings = open, showQuickMenu = false) }
    }

    fun openGestureGuide(open: Boolean) {
        modalStateFlow.update { it.copy(showGestureGuide = open, showQuickMenu = false) }
    }

    fun openImageZoom(url: String?) {
        modalStateFlow.update { it.copy(zoomedImageUrl = url) }
    }

    fun recordTelemetry(@StringRes labelRes: Int, formatArg: String? = null) {
        telemetryFlow.value = GestureTelemetry(
            labelRes = labelRes,
            formatArg = formatArg,
            timestampMillis = System.currentTimeMillis()
        )
    }
}
