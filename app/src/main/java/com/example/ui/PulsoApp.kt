package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.AutoScrollSpeed
import com.example.data.model.SocialNetwork
import com.example.data.model.SocialPost
import com.example.data.model.WatchShapeMode
import com.example.ui.components.GestureGuideOverlay
import com.example.ui.components.InteractiveDigitalCrown
import com.example.ui.components.WatchClockText
import com.example.ui.components.WatchPostCard
import com.example.ui.components.WatchQuickMenuOverlay
import com.example.ui.components.WatchScrollArc
import com.example.ui.components.WatchSettingsOverlay
import com.example.ui.components.ZoomedImageOverlay
import com.example.ui.theme.CoralHeart
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.StudioDeep
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WatchBlack
import com.example.ui.theme.WatchBorder
import com.example.ui.theme.WatchSurface
import com.example.ui.theme.WatchSurfaceElevated
import com.example.ui.viewmodel.NetworkFeedState
import com.example.ui.viewmodel.PulsoUiState
import com.example.ui.viewmodel.PulsoViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun PulsoApp(viewModel: PulsoViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val networks = SocialNetwork.entries
    val horizontalPagerState = rememberPagerState(
        initialPage = networks.indexOf(uiState.activeNetwork).coerceAtLeast(0),
        pageCount = { networks.size }
    )

    // Keep track of vertical pager states per network so crown, remote buttons, and auto-scroll act cleanly
    val verticalPagers = networks.associateWith { network ->
        val posts = uiState.feeds[network]?.posts.orEmpty()
        rememberPagerState(pageCount = { posts.size.coerceAtLeast(1) })
    }

    // Sync horizontal swipe -> ViewModel
    LaunchedEffect(horizontalPagerState) {
        snapshotFlow { horizontalPagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                val targetNetwork = networks[page]
                if (targetNetwork != uiState.activeNetwork) {
                    viewModel.selectNetwork(targetNetwork, fromSwipe = true)
                    if (uiState.preferences.hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }
            }
    }

    // Sync ViewModel network change -> HorizontalPager
    LaunchedEffect(uiState.activeNetwork) {
        val targetIndex = networks.indexOf(uiState.activeNetwork)
        if (targetIndex >= 0 && horizontalPagerState.currentPage != targetIndex) {
            horizontalPagerState.animateScrollToPage(targetIndex)
        }
    }

    val activeVerticalPager = verticalPagers[uiState.activeNetwork]!!
    val activePosts = uiState.activeFeed.posts
    val activePost: SocialPost? = activePosts.getOrNull(activeVerticalPager.currentPage)

    fun performStepScroll(delta: Int, telemetryRes: Int) {
        if (activePosts.isEmpty()) return
        val nextIndex = (activeVerticalPager.currentPage + delta).coerceIn(0, activePosts.lastIndex)
        if (nextIndex != activeVerticalPager.currentPage) {
            scope.launch {
                activeVerticalPager.animateScrollToPage(nextIndex)
            }
            if (uiState.preferences.hapticsEnabled) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
        viewModel.recordTelemetry(telemetryRes)
    }

    // Auto-scroll loop with smooth progress indicator
    var autoScrollProgress by remember { mutableFloatStateOf(0f) }
    val autoScrollSpeed = uiState.preferences.autoScrollSpeed
    val anyOverlayOpen = uiState.showQuickMenu ||
        uiState.showSettings ||
        uiState.showGestureGuide ||
        !uiState.preferences.hasSeenGestureHint ||
        uiState.zoomedImageUrl != null

    LaunchedEffect(autoScrollSpeed, uiState.activeNetwork, activePosts.size, anyOverlayOpen) {
        autoScrollProgress = 0f
        if (autoScrollSpeed == AutoScrollSpeed.OFF || activePosts.size <= 1 || anyOverlayOpen) {
            return@LaunchedEffect
        }
        val stepMs = 50L
        val totalMs = autoScrollSpeed.delayMillis
        while (true) {
            var elapsed = 0L
            while (elapsed < totalMs) {
                delay(stepMs)
                elapsed += stepMs
                autoScrollProgress = (elapsed.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
            }
            autoScrollProgress = 0f
            val next = if (activeVerticalPager.currentPage < activePosts.lastIndex) {
                activeVerticalPager.currentPage + 1
            } else {
                0
            }
            activeVerticalPager.animateScrollToPage(next)
            if (uiState.preferences.hapticsEnabled) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    // Handle system Back when any watch overlay is open
    BackHandler(enabled = anyOverlayOpen) {
        when {
            uiState.zoomedImageUrl != null -> viewModel.openImageZoom(null)
            uiState.showSettings -> viewModel.openSettings(false)
            uiState.showGestureGuide || !uiState.preferences.hasSeenGestureHint -> viewModel.dismissGestureHint()
            uiState.showQuickMenu -> viewModel.openQuickMenu(false)
        }
    }

    fun openExternalUrl(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(WatchBlack)
    ) {
        val isNativeWatchScreen = maxWidth < 320.dp && maxHeight < 340.dp

        if (isNativeWatchScreen) {
            // Native Wear OS / Small Watch full-bleed mode
            val isRoundWatch = context.resources.configuration.isScreenRound
            WatchDialViewport(
                uiState = uiState,
                networks = networks,
                horizontalPagerState = horizontalPagerState,
                verticalPagers = verticalPagers,
                isRound = isRoundWatch,
                autoScrollProgress = autoScrollProgress,
                onPrevPost = { performStepScroll(-1, R.string.telemetry_prev) },
                onNextPost = { performStepScroll(1, R.string.telemetry_next) },
                onDoubleTapLike = { post ->
                    val liked = viewModel.onDoubleTapLike(post)
                    if (uiState.preferences.hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    liked
                },
                onToggleLike = { post ->
                    viewModel.toggleLike(post)
                    if (uiState.preferences.hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
                onToggleBookmark = { post ->
                    viewModel.toggleBookmark(post)
                    if (uiState.preferences.hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                },
                onLongPressMenu = {
                    if (uiState.preferences.hapticsEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    viewModel.openQuickMenu(true)
                },
                onImageTap = { url -> viewModel.openImageZoom(url) },
                onOpenExternalLink = { openExternalUrl(it) },
                onSelectNetwork = { viewModel.selectNetwork(it) },
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Phone / Tablet Adaptive Studio Preview + Interactive Smartwatch Frame
            val shapeMode = uiState.preferences.watchShapeMode
            val accent = uiState.activeNetwork.accentColor

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.14f),
                                StudioElevated,
                                StudioDeep
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Bar: Brand + Watch Shape Switcher + Settings/Help
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 560.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(CyanPulse.copy(alpha = 0.15f))
                                    .border(1.dp, CyanPulse.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Watch,
                                    contentDescription = null,
                                    tint = CyanPulse,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.app_name).uppercase(),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        letterSpacing = 1.5.sp
                                    ),
                                    color = TextPrimary
                                )
                                Text(
                                    text = stringResource(uiState.activeNetwork.labelRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.openGestureGuide(true) },
                                modifier = Modifier.testTag("top_guide_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Gesture,
                                    contentDescription = stringResource(R.string.cd_open_guide),
                                    tint = TextSecondary
                                )
                            }
                            IconButton(
                                onClick = { viewModel.openSettings(true) },
                                modifier = Modifier.testTag("top_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = stringResource(R.string.cd_open_settings),
                                    tint = TextSecondary
                                )
                            }
                        }
                    }

                    // Shape Switcher + Quick Network Selector Pills
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Watch Chassis Mode Selector
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WatchShapeMode.entries.forEach { mode ->
                                val selected = shapeMode == mode
                                Surface(
                                    onClick = { viewModel.setWatchShapeMode(mode) },
                                    shape = RoundedCornerShape(50),
                                    color = if (selected) CyanPulse else WatchSurfaceElevated,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (selected) CyanPulse else WatchBorder
                                    ),
                                    modifier = Modifier.testTag("shape_${mode.name.lowercase()}")
                                ) {
                                    Text(
                                        text = stringResource(mode.labelRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) WatchBlack else TextSecondary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        // Network Channels Strip
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            networks.forEach { net ->
                                val selected = uiState.activeNetwork == net
                                Surface(
                                    onClick = { viewModel.selectNetwork(net) },
                                    shape = RoundedCornerShape(50),
                                    color = if (selected) net.accentColor.copy(alpha = 0.2f) else Color.Transparent,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (selected) net.accentColor else WatchBorder
                                    ),
                                    modifier = Modifier.testTag("channel_${net.id}")
                                ) {
                                    Text(
                                        text = stringResource(net.labelRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (selected) net.accentColor else TextSecondary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Centerpiece: Watch Hardware Chassis OR Expanded Small-Screen Reader
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (shapeMode == WatchShapeMode.FULL) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 460.dp)
                                    .padding(vertical = 8.dp)
                                    .clip(RoundedCornerShape(28.dp))
                                    .border(1.5.dp, WatchBorder, RoundedCornerShape(28.dp))
                            ) {
                                WatchDialViewport(
                                    uiState = uiState,
                                    networks = networks,
                                    horizontalPagerState = horizontalPagerState,
                                    verticalPagers = verticalPagers,
                                    isRound = false,
                                    autoScrollProgress = autoScrollProgress,
                                    onPrevPost = { performStepScroll(-1, R.string.telemetry_prev) },
                                    onNextPost = { performStepScroll(1, R.string.telemetry_next) },
                                    onDoubleTapLike = { post ->
                                        val liked = viewModel.onDoubleTapLike(post)
                                        if (uiState.preferences.hapticsEnabled) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        liked
                                    },
                                    onToggleLike = { post ->
                                        viewModel.toggleLike(post)
                                        if (uiState.preferences.hapticsEnabled) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                    onToggleBookmark = { post ->
                                        viewModel.toggleBookmark(post)
                                        if (uiState.preferences.hapticsEnabled) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                    onLongPressMenu = {
                                        if (uiState.preferences.hapticsEnabled) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        viewModel.openQuickMenu(true)
                                    },
                                    onImageTap = { url -> viewModel.openImageZoom(url) },
                                    onOpenExternalLink = { openExternalUrl(it) },
                                    onSelectNetwork = { viewModel.selectNetwork(it) },
                                    viewModel = viewModel,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            val isRound = shapeMode == WatchShapeMode.ROUND
                            val dialSize = 278.dp
                            val bezelShape = if (isRound) CircleShape else RoundedCornerShape(46.dp)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                // Metallic Watch Bezel + AMOLED Dial
                                Box(
                                    modifier = Modifier
                                        .size(dialSize + 18.dp)
                                        .shadow(24.dp, bezelShape, ambientColor = accent)
                                        .clip(bezelShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    Color(0xFF2B3544),
                                                    Color(0xFF141923),
                                                    Color(0xFF283140)
                                                )
                                            )
                                        )
                                        .border(2.dp, Color(0xFF3E4B5E), bezelShape)
                                        .padding(9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(dialSize)
                                            .clip(bezelShape)
                                            .background(WatchBlack)
                                    ) {
                                        WatchDialViewport(
                                            uiState = uiState,
                                            networks = networks,
                                            horizontalPagerState = horizontalPagerState,
                                            verticalPagers = verticalPagers,
                                            isRound = isRound,
                                            autoScrollProgress = autoScrollProgress,
                                            onPrevPost = { performStepScroll(-1, R.string.telemetry_prev) },
                                            onNextPost = { performStepScroll(1, R.string.telemetry_next) },
                                            onDoubleTapLike = { post ->
                                                val liked = viewModel.onDoubleTapLike(post)
                                                if (uiState.preferences.hapticsEnabled) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                                liked
                                            },
                                            onToggleLike = { post ->
                                                viewModel.toggleLike(post)
                                                if (uiState.preferences.hapticsEnabled) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            },
                                            onToggleBookmark = { post ->
                                                viewModel.toggleBookmark(post)
                                                if (uiState.preferences.hapticsEnabled) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            },
                                            onLongPressMenu = {
                                                if (uiState.preferences.hapticsEnabled) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                                viewModel.openQuickMenu(true)
                                            },
                                            onImageTap = { url -> viewModel.openImageZoom(url) },
                                            onOpenExternalLink = { openExternalUrl(it) },
                                            onSelectNetwork = { viewModel.selectNetwork(it) },
                                            viewModel = viewModel,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                // Interactive Draggable Digital Crown & Side Action Button
                                InteractiveDigitalCrown(
                                    accentColor = accent,
                                    onCrownScrollStep = { step ->
                                        performStepScroll(step, R.string.telemetry_crown)
                                    },
                                    onSideButtonClick = {
                                        viewModel.cycleAutoScrollSpeed()
                                        if (uiState.preferences.hapticsEnabled) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    // Bottom Live Gesture Telemetry & One-Thumb Quick Controls
                    Column(
                        modifier = Modifier.widthIn(max = 460.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Live gesture telemetry feedback badge
                        val telemetryText = if (uiState.telemetry.formatArg != null) {
                            stringResource(uiState.telemetry.labelRes, uiState.telemetry.formatArg!!)
                        } else {
                            stringResource(uiState.telemetry.labelRes)
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = WatchSurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, WatchBorder)
                        ) {
                            AnimatedContent(
                                targetState = telemetryText,
                                label = "telemetryAnim"
                            ) { label ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanPulse,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // Ergonomic remote thumb controls (great for accessibility & desktop testing)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RemoteControlButton(
                                icon = Icons.Filled.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.cd_prev_post),
                                tint = TextPrimary,
                                testTag = "remote_prev",
                                onClick = { performStepScroll(-1, R.string.telemetry_prev) }
                            )
                            RemoteControlButton(
                                icon = if (autoScrollSpeed == AutoScrollSpeed.OFF) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                contentDescription = stringResource(R.string.cd_toggle_autoscroll),
                                tint = if (autoScrollSpeed != AutoScrollSpeed.OFF) CoralHeart else CyanPulse,
                                testTag = "remote_autoscroll",
                                onClick = { viewModel.cycleAutoScrollSpeed() }
                            )
                            RemoteControlButton(
                                icon = Icons.Filled.Favorite,
                                contentDescription = stringResource(R.string.cd_like_button),
                                tint = if (activePost?.isLiked == true) CoralHeart else TextSecondary,
                                testTag = "remote_like",
                                onClick = { activePost?.let { viewModel.toggleLike(it) } }
                            )
                            RemoteControlButton(
                                icon = Icons.Filled.Bookmark,
                                contentDescription = stringResource(R.string.cd_bookmark_button),
                                tint = if (activePost?.isBookmarked == true) accent else TextSecondary,
                                testTag = "remote_bookmark",
                                onClick = { activePost?.let { viewModel.toggleBookmark(it) } }
                            )
                            RemoteControlButton(
                                icon = Icons.Filled.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.cd_next_post),
                                tint = TextPrimary,
                                testTag = "remote_next",
                                onClick = { performStepScroll(1, R.string.telemetry_next) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = WatchSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, WatchBorder),
        modifier = Modifier
            .size(48.dp)
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun WatchDialViewport(
    uiState: PulsoUiState,
    networks: List<SocialNetwork>,
    horizontalPagerState: androidx.compose.foundation.pager.PagerState,
    verticalPagers: Map<SocialNetwork, androidx.compose.foundation.pager.PagerState>,
    isRound: Boolean,
    autoScrollProgress: Float,
    onPrevPost: () -> Unit,
    onNextPost: () -> Unit,
    onDoubleTapLike: (SocialPost) -> Boolean,
    onToggleLike: (SocialPost) -> Unit,
    onToggleBookmark: (SocialPost) -> Unit,
    onLongPressMenu: () -> Unit,
    onImageTap: (String) -> Unit,
    onOpenExternalLink: (String) -> Unit,
    onSelectNetwork: (SocialNetwork) -> Unit,
    viewModel: PulsoViewModel,
    modifier: Modifier = Modifier
) {
    val activeFeed = uiState.activeFeed
    val activeVerticalPager = verticalPagers[uiState.activeNetwork]!!
    val activePost = activeFeed.posts.getOrNull(activeVerticalPager.currentPage)

    Box(
        modifier = modifier
            .background(WatchBlack)
    ) {
        // Horizontal Pager across social networks
        HorizontalPager(
            state = horizontalPagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val network = networks[pageIndex]
            val feedState = uiState.feeds[network] ?: NetworkFeedState()
            val vPagerState = verticalPagers[network]!!

            when {
                feedState.posts.isEmpty() && feedState.isLoading -> {
                    WatchLoadingView(network = network)
                }
                feedState.posts.isEmpty() -> {
                    WatchEmptyView(
                        network = network,
                        errorMessage = feedState.errorMessage,
                        onRetry = { viewModel.refreshNetwork(network) },
                        onLongPressMenu = onLongPressMenu
                    )
                }
                else -> {
                    VerticalPager(
                        state = vPagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("vertical_feed_${network.id}")
                    ) { postIndex ->
                        val post = feedState.posts[postIndex]
                        WatchPostCard(
                            post = post,
                            isRound = isRound,
                            largeText = uiState.preferences.largeTextEnabled,
                            tapToScrollEnabled = uiState.preferences.tapToScrollEnabled,
                            onPrevPost = onPrevPost,
                            onNextPost = onNextPost,
                            onDoubleTapLike = { onDoubleTapLike(post) },
                            onToggleLike = { onToggleLike(post) },
                            onToggleBookmark = { onToggleBookmark(post) },
                            onLongPressMenu = onLongPressMenu,
                            onImageTap = onImageTap
                        )
                    }
                }
            }
        }

        // Top Watch Header: Time + Active Network Pill + Index Counter
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (isRound) 10.dp else 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WatchClockText()
                val nextNet = networks[(networks.indexOf(uiState.activeNetwork) + 1) % networks.size]
                Text(
                    text = stringResource(uiState.activeNetwork.labelRes),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = uiState.activeNetwork.accentColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(uiState.activeNetwork.accentColor.copy(alpha = 0.16f))
                        .clickable { onSelectNetwork(nextNet) }
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
                if (activeFeed.posts.isNotEmpty()) {
                    Text(
                        text = "${activeVerticalPager.currentPage + 1}/${activeFeed.posts.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }

        // Perimeter Curved Scroll Arc + Auto-Scroll Ring
        WatchScrollArc(
            currentIndex = activeVerticalPager.currentPage,
            totalCount = activeFeed.posts.size,
            accentColor = uiState.activeNetwork.accentColor,
            autoScrollProgress = autoScrollProgress,
            isAutoScrollActive = uiState.preferences.autoScrollSpeed != AutoScrollSpeed.OFF,
            isRound = isRound
        )

        // Overlays inside the watch dial
        AnimatedVisibility(
            visible = uiState.showQuickMenu,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            WatchQuickMenuOverlay(
                currentPost = activePost,
                autoScrollSpeed = uiState.preferences.autoScrollSpeed,
                onToggleLike = { activePost?.let { viewModel.toggleLike(it) } },
                onToggleBookmark = { activePost?.let { viewModel.toggleBookmark(it) } },
                onCycleAutoScroll = { viewModel.cycleAutoScrollSpeed() },
                onOpenExternalLink = onOpenExternalLink,
                onRefreshFeed = { viewModel.refreshNetwork(uiState.activeNetwork) },
                onOpenSettings = { viewModel.openSettings(true) },
                onDismiss = { viewModel.openQuickMenu(false) }
            )
        }

        AnimatedVisibility(
            visible = uiState.showSettings,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            WatchSettingsOverlay(
                preferences = uiState.preferences,
                onSetAutoScroll = { viewModel.setAutoScrollSpeed(it) },
                onSetTapToScroll = { viewModel.setTapToScroll(it) },
                onSetHaptics = { viewModel.setHaptics(it) },
                onSetLargeText = { viewModel.setLargeText(it) },
                onOpenGuide = { viewModel.openGestureGuide(true) },
                onClose = { viewModel.openSettings(false) }
            )
        }

        AnimatedVisibility(
            visible = uiState.showGestureGuide || !uiState.preferences.hasSeenGestureHint,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            GestureGuideOverlay(
                onDismiss = { viewModel.dismissGestureHint() }
            )
        }

        val zoomedUrl = uiState.zoomedImageUrl
        AnimatedVisibility(
            visible = zoomedUrl != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (zoomedUrl != null) {
                ZoomedImageOverlay(
                    imageUrl = zoomedUrl,
                    onDismiss = { viewModel.openImageZoom(null) }
                )
            }
        }
    }
}

@Composable
private fun WatchLoadingView(network: SocialNetwork) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(
                color = network.accentColor,
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = stringResource(R.string.loading_feed),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun WatchEmptyView(
    network: SocialNetwork,
    errorMessage: String?,
    onRetry: () -> Unit,
    onLongPressMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onLongPressMenu)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(
                    if (network == SocialNetwork.BOOKMARKS) R.string.empty_bookmarks_title
                    else R.string.empty_feed_title
                ),
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = errorMessage ?: stringResource(
                    if (network == SocialNetwork.BOOKMARKS) R.string.empty_bookmarks_subtitle
                    else R.string.empty_feed_subtitle
                ),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 3
            )
            if (network != SocialNetwork.BOOKMARKS) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = network.accentColor,
                        contentColor = WatchBlack
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.retry_button),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
