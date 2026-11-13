package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.SocialPost
import com.example.ui.theme.CoralHeart
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.MintBookmark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WatchBlack
import com.example.ui.theme.WatchBorder
import com.example.ui.theme.WatchSurface
import com.example.ui.theme.WatchSurfaceElevated
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WatchClockText(modifier: Modifier = Modifier) {
    var timeText by remember { mutableStateOf(currentWatchTime()) }

    LaunchedEffect(Unit) {
        while (true) {
            timeText = currentWatchTime()
            delay(15_000L)
        }
    }

    Text(
        text = timeText,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = TextPrimary
        ),
        modifier = modifier
    )
}

private fun currentWatchTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

@Composable
fun WatchScrollArc(
    currentIndex: Int,
    totalCount: Int,
    accentColor: Color,
    autoScrollProgress: Float,
    isAutoScrollActive: Boolean,
    isRound: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedFraction by animateFloatAsState(
        targetValue = if (totalCount <= 1) 1f else (currentIndex + 1).toFloat() / totalCount.toFloat(),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "scrollArcFraction"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val strokePx = 3.5.dp.toPx()
        val inset = strokePx + 3.dp.toPx()

        if (isRound) {
            // Auto-scroll countdown ring along top-left/perimeter when active
            if (isAutoScrollActive) {
                drawArc(
                    color = CoralHeart.copy(alpha = 0.22f),
                    startAngle = 125f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
                drawArc(
                    color = CoralHeart,
                    startAngle = 125f,
                    sweepAngle = 110f * autoScrollProgress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }

            // Right-edge scroll progress arc (-55 deg to +55 deg)
            if (totalCount > 0) {
                drawArc(
                    color = WatchBorder.copy(alpha = 0.6f),
                    startAngle = -48f,
                    sweepAngle = 96f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
                val thumbSweep = (96f / totalCount.coerceAtLeast(1)).coerceAtLeast(12f)
                val maxTravel = 96f - thumbSweep
                val startDeg = -48f + (maxTravel * if (totalCount <= 1) 0f else currentIndex.toFloat() / (totalCount - 1).toFloat())
                drawArc(
                    color = accentColor,
                    startAngle = startDeg,
                    sweepAngle = thumbSweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        } else {
            // Square / Rectangular slim edge scrollbar
            if (totalCount > 0) {
                val trackX = size.width - 7.dp.toPx()
                val topY = size.height * 0.22f
                val trackH = size.height * 0.56f
                drawLine(
                    color = WatchBorder.copy(alpha = 0.65f),
                    start = Offset(trackX, topY),
                    end = Offset(trackX, topY + trackH),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
                val fillH = (trackH * animatedFraction).coerceAtLeast(10.dp.toPx())
                drawLine(
                    color = accentColor,
                    start = Offset(trackX, topY),
                    end = Offset(trackX, topY + fillH),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }
            if (isAutoScrollActive) {
                val barY = size.height - 5.dp.toPx()
                drawLine(
                    color = CoralHeart,
                    start = Offset(size.width * 0.2f, barY),
                    end = Offset(size.width * (0.2f + 0.6f * autoScrollProgress.coerceIn(0f, 1f)), barY),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
fun InteractiveDigitalCrown(
    accentColor: Color,
    onCrownScrollStep: (Int) -> Unit,
    onSideButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var accumulatedDrag by remember { mutableFloatStateOf(0f) }
    var notchOffset by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Draggable rotating digital crown
        Box(
            modifier = Modifier
                .size(width = 22.dp, height = 56.dp)
                .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF3A4454),
                            Color(0xFF1F2733),
                            Color(0xFF3A4454)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = accentColor.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)
                )
                .testTag("digital_crown")
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = { accumulatedDrag = 0f },
                        onDragCancel = { accumulatedDrag = 0f }
                    ) { change, dragAmount ->
                        change.consume()
                        accumulatedDrag += dragAmount
                        notchOffset = (notchOffset + dragAmount * 0.4f).mod(12f)
                        val threshold = 42f
                        if (accumulatedDrag >= threshold) {
                            accumulatedDrag -= threshold
                            onCrownScrollStep(1)
                        } else if (accumulatedDrag <= -threshold) {
                            accumulatedDrag += threshold
                            onCrownScrollStep(-1)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val ridgeCount = 5
                val spacing = size.height / (ridgeCount + 1)
                for (i in 1..ridgeCount) {
                    val y = (i * spacing + notchOffset) % size.height
                    drawLine(
                        color = accentColor.copy(alpha = 0.7f),
                        start = Offset(4.dp.toPx(), y),
                        end = Offset(size.width - 3.dp.toPx(), y),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // Secondary Side Action Button (toggles Auto-Scroll)
        Surface(
            onClick = onSideButtonClick,
            shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
            color = WatchSurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, WatchBorder),
            modifier = Modifier
                .size(width = 16.dp, height = 34.dp)
                .testTag("side_action_button")
        ) {}
    }
}

@Composable
fun WatchPostCard(
    post: SocialPost,
    isRound: Boolean,
    largeText: Boolean,
    tapToScrollEnabled: Boolean,
    onPrevPost: () -> Unit,
    onNextPost: () -> Unit,
    onDoubleTapLike: () -> Boolean,
    onToggleLike: () -> Unit,
    onToggleBookmark: () -> Unit,
    onLongPressMenu: () -> Unit,
    onImageTap: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showHeartBurst by remember(post.id) { mutableStateOf(false) }
    val heartScale = remember { Animatable(0.4f) }

    LaunchedEffect(showHeartBurst) {
        if (showHeartBurst) {
            heartScale.snapTo(0.4f)
            heartScale.animateTo(
                targetValue = 1.25f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            delay(320L)
            showHeartBurst = false
        }
    }

    val horizontalInset = if (isRound) 24.dp else 14.dp
    val topInset = if (isRound) 38.dp else 32.dp
    val bottomInset = if (isRound) 22.dp else 14.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(post.id, tapToScrollEnabled) {
                detectTapGestures(
                    onDoubleTap = {
                        val liked = onDoubleTapLike()
                        if (liked) {
                            showHeartBurst = true
                        }
                    },
                    onLongPress = { onLongPressMenu() },
                    onTap = { offset ->
                        if (tapToScrollEnabled) {
                            val relY = offset.y / size.height.toFloat()
                            when {
                                relY < 0.24f -> onPrevPost()
                                relY > 0.72f -> onNextPost()
                            }
                        }
                    }
                )
            }
            .padding(
                PaddingValues(
                    start = horizontalInset,
                    end = horizontalInset,
                    top = topInset,
                    bottom = bottomInset
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Author Row (compact & legible)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!post.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = post.avatarUrl,
                        contentDescription = stringResource(R.string.cd_avatar, post.authorName),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(WatchSurfaceElevated)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(post.network.accentColor.copy(alpha = 0.22f))
                            .border(1.dp, post.network.accentColor.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = post.authorName.take(1).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = post.network.accentColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.authorName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = if (largeText) 13.sp else 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${post.authorHandle} · ${post.relativeTime}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Main Post Content (scrollable inside post if long, crisp typography)
            val bodyScroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .verticalScroll(bodyScroll),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = post.content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = if (largeText) 16.sp else 14.sp,
                        lineHeight = if (largeText) 22.sp else 19.sp
                    ),
                    color = TextPrimary,
                    maxLines = if (post.imageUrl != null) 4 else 6,
                    overflow = TextOverflow.Ellipsis
                )

                if (!post.imageUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    AsyncImage(
                        model = post.imageUrl,
                        contentDescription = stringResource(R.string.cd_post_image),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(WatchSurface)
                            .border(1.dp, WatchBorder, RoundedCornerShape(10.dp))
                            .clickable { onImageTap(post.imageUrl) }
                    )
                }
            }

            // Bottom Minimalist Action & Tap-Scroll Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Like action pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onToggleLike)
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                        .testTag("post_like_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (post.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(R.string.cd_like_button),
                        tint = if (post.isLiked) CoralHeart else TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = compactCount(post.likesCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (post.isLiked) CoralHeart else TextSecondary
                    )
                }

                // Micro replies/reposts info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (post.repostsCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Repeat,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = compactCount(post.repostsCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = compactCount(post.repliesCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }

                // Bookmark pill
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onToggleBookmark)
                        .padding(6.dp)
                        .testTag("post_bookmark_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (post.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = stringResource(R.string.cd_bookmark_button),
                        tint = if (post.isBookmarked) MintBookmark else TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // Subtle Tap-to-Scroll edge chevrons when enabled
        if (tapToScrollEnabled) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = stringResource(R.string.cd_prev_post),
                tint = TextMuted.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(14.dp)
                    .clickable(onClick = onPrevPost)
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.cd_next_post),
                tint = TextMuted.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(14.dp)
                    .clickable(onClick = onNextPost)
            )
        }

        // Double-Tap Coral Heart Burst
        AnimatedVisibility(
            visible = showHeartBurst,
            enter = fadeIn(tween(100)) + scaleIn(initialScale = 0.5f),
            exit = fadeOut(tween(250)) + scaleOut(targetScale = 1.4f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(WatchBlack.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = CoralHeart,
                    modifier = Modifier.size((42 * heartScale.value).dp)
                )
            }
        }
    }
}

private fun compactCount(count: Int): String = when {
    count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000f)
    count >= 1_000 -> String.format(Locale.US, "%.1fk", count / 1_000f)
    else -> count.toString()
}
