package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwipeVertical
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.WatchPreferences
import com.example.data.model.AutoScrollSpeed
import com.example.data.model.SocialPost
import com.example.ui.theme.CoralHeart
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.MintBookmark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WatchBlack
import com.example.ui.theme.WatchBorder
import com.example.ui.theme.WatchSurface
import com.example.ui.theme.WatchSurfaceElevated

@Composable
fun WatchQuickMenuOverlay(
    currentPost: SocialPost?,
    autoScrollSpeed: AutoScrollSpeed,
    onToggleLike: () -> Unit,
    onToggleBookmark: () -> Unit,
    onCycleAutoScroll: () -> Unit,
    onOpenExternalLink: (String) -> Unit,
    onRefreshFeed: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WatchBlack.copy(alpha = 0.94f))
            .padding(horizontal = 24.dp, vertical = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.quick_menu_title),
                style = MaterialTheme.typography.titleSmall,
                color = CyanPulse,
                fontWeight = FontWeight.Bold
            )

            QuickMenuPill(
                icon = Icons.Filled.PlayArrow,
                label = "${stringResource(R.string.setting_auto_scroll)}: ${stringResource(autoScrollSpeed.labelRes)}",
                accent = if (autoScrollSpeed != AutoScrollSpeed.OFF) CoralHeart else CyanPulse,
                testTag = "quick_autoscroll",
                onClick = onCycleAutoScroll
            )

            if (currentPost != null) {
                QuickMenuPill(
                    icon = if (currentPost.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    label = stringResource(if (currentPost.isLiked) R.string.action_liked else R.string.action_like),
                    accent = CoralHeart,
                    testTag = "quick_like",
                    onClick = {
                        onToggleLike()
                        onDismiss()
                    }
                )
                QuickMenuPill(
                    icon = if (currentPost.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    label = stringResource(if (currentPost.isBookmarked) R.string.action_bookmarked else R.string.action_bookmark),
                    accent = MintBookmark,
                    testTag = "quick_bookmark",
                    onClick = {
                        onToggleBookmark()
                        onDismiss()
                    }
                )
                QuickMenuPill(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    label = stringResource(R.string.action_open_link),
                    accent = TextPrimary,
                    testTag = "quick_open_link",
                    onClick = {
                        onOpenExternalLink(currentPost.externalUrl)
                        onDismiss()
                    }
                )
            }

            QuickMenuPill(
                icon = Icons.Filled.Refresh,
                label = stringResource(R.string.action_refresh),
                accent = CyanPulse,
                testTag = "quick_refresh",
                onClick = {
                    onRefreshFeed()
                    onDismiss()
                }
            )

            QuickMenuPill(
                icon = Icons.Filled.Settings,
                label = stringResource(R.string.action_settings),
                accent = TextSecondary,
                testTag = "quick_settings",
                onClick = onOpenSettings
            )

            Spacer(modifier = Modifier.height(2.dp))

            Surface(
                onClick = onDismiss,
                shape = CircleShape,
                color = WatchSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, WatchBorder),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("quick_close")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickMenuPill(
    icon: ImageVector,
    label: String,
    accent: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = WatchSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun WatchSettingsOverlay(
    preferences: WatchPreferences,
    onSetAutoScroll: (AutoScrollSpeed) -> Unit,
    onSetTapToScroll: (Boolean) -> Unit,
    onSetHaptics: (Boolean) -> Unit,
    onSetLargeText: (Boolean) -> Unit,
    onOpenGuide: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WatchBlack.copy(alpha = 0.96f))
            .padding(horizontal = 22.dp, vertical = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleSmall,
                color = CyanPulse,
                fontWeight = FontWeight.Bold
            )

            // Auto-scroll speed chips
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = WatchSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, WatchBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = stringResource(R.string.setting_auto_scroll),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AutoScrollSpeed.entries.forEach { speed ->
                            val selected = preferences.autoScrollSpeed == speed
                            val shortName = when (speed) {
                                AutoScrollSpeed.OFF -> "Off"
                                AutoScrollSpeed.SLOW -> "7s"
                                AutoScrollSpeed.MEDIUM -> "4.5s"
                                AutoScrollSpeed.FAST -> "2.5s"
                            }
                            Surface(
                                onClick = { onSetAutoScroll(speed) },
                                shape = RoundedCornerShape(50),
                                color = if (selected) CoralHeart else WatchSurfaceElevated,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .testTag("speed_${speed.name.lowercase()}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = shortName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) WatchBlack else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            WatchToggleRow(
                label = stringResource(R.string.setting_tap_to_scroll),
                checked = preferences.tapToScrollEnabled,
                testTag = "toggle_tap_to_scroll",
                onCheckedChange = onSetTapToScroll
            )

            WatchToggleRow(
                label = stringResource(R.string.setting_haptics),
                checked = preferences.hapticsEnabled,
                testTag = "toggle_haptics",
                onCheckedChange = onSetHaptics
            )

            WatchToggleRow(
                label = stringResource(R.string.setting_large_text),
                checked = preferences.largeTextEnabled,
                testTag = "toggle_large_text",
                onCheckedChange = onSetLargeText
            )

            QuickMenuPill(
                icon = Icons.Filled.Gesture,
                label = stringResource(R.string.setting_gesture_guide),
                accent = CyanPulse,
                testTag = "open_gesture_guide",
                onClick = onOpenGuide
            )

            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPulse,
                    contentColor = WatchBlack
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("settings_close")
            ) {
                Text(
                    text = stringResource(R.string.action_close),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun WatchToggleRow(
    label: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(16.dp),
        color = WatchSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, WatchBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = WatchBlack,
                    checkedTrackColor = CyanPulse
                )
            )
        }
    }
}

@Composable
fun GestureGuideOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WatchBlack.copy(alpha = 0.96f))
            .padding(horizontal = 24.dp, vertical = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.gesture_hint_title),
                style = MaterialTheme.typography.titleSmall,
                color = CyanPulse,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            GestureRowItem(
                icon = Icons.Filled.SwipeVertical,
                title = stringResource(R.string.gesture_swipe_vertical),
                desc = stringResource(R.string.gesture_swipe_vertical_desc),
                accent = CyanPulse
            )
            GestureRowItem(
                icon = Icons.Filled.Favorite,
                title = stringResource(R.string.gesture_double_tap),
                desc = stringResource(R.string.gesture_double_tap_desc),
                accent = CoralHeart
            )
            GestureRowItem(
                icon = Icons.Filled.Gesture,
                title = stringResource(R.string.gesture_swipe_horizontal),
                desc = stringResource(R.string.gesture_swipe_horizontal_desc),
                accent = MintBookmark
            )
            GestureRowItem(
                icon = Icons.Filled.TouchApp,
                title = stringResource(R.string.gesture_long_press),
                desc = stringResource(R.string.gesture_long_press_desc),
                accent = CyanPulse
            )

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPulse,
                    contentColor = WatchBlack
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("dismiss_gesture_guide")
            ) {
                Text(
                    text = stringResource(R.string.gesture_understood),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GestureRowItem(
    icon: ImageVector,
    title: String,
    desc: String,
    accent: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WatchSurface)
            .border(1.dp, WatchBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun ZoomedImageOverlay(
    imageUrl: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WatchBlack.copy(alpha = 0.96f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = stringResource(R.string.cd_post_image),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(WatchSurfaceElevated)
                .border(1.dp, WatchBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = TextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
