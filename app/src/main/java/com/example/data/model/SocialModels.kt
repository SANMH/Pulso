package com.example.data.model

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.example.R
import com.example.ui.theme.BlueskyTint
import com.example.ui.theme.BookmarksTint
import com.example.ui.theme.MastodonTint
import com.example.ui.theme.RedditTint
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class SocialNetwork(
    val id: String,
    @StringRes val labelRes: Int,
    val accentColor: Color
) {
    BLUESKY("bluesky", R.string.network_bluesky, BlueskyTint),
    MASTODON("mastodon", R.string.network_mastodon, MastodonTint),
    REDDIT("reddit", R.string.network_reddit, RedditTint),
    BOOKMARKS("bookmarks", R.string.network_bookmarks, BookmarksTint);

    companion object {
        fun fromId(id: String): SocialNetwork =
            entries.firstOrNull { it.id == id } ?: BLUESKY
    }
}

enum class AutoScrollSpeed(val delayMillis: Long, @StringRes val labelRes: Int) {
    OFF(0L, R.string.speed_off),
    SLOW(7000L, R.string.speed_slow),
    MEDIUM(4500L, R.string.speed_medium),
    FAST(2500L, R.string.speed_fast)
}

enum class WatchShapeMode(@StringRes val labelRes: Int) {
    ROUND(R.string.mode_round),
    SQUARE(R.string.mode_square),
    FULL(R.string.mode_full)
}

data class SocialPost(
    val id: String,
    val network: SocialNetwork,
    val authorName: String,
    val authorHandle: String,
    val avatarUrl: String?,
    val content: String,
    val imageUrl: String?,
    val relativeTime: String,
    val likesCount: Int,
    val repostsCount: Int,
    val repliesCount: Int,
    val externalUrl: String,
    val isLiked: Boolean = false,
    val isBookmarked: Boolean = false,
    val createdEpochSeconds: Long = 0L
)

// --- Bluesky Public API DTOs ---

@JsonClass(generateAdapter = true)
data class BlueskySearchResponse(
    val posts: List<BlueskyPostDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BlueskyPostDto(
    val uri: String,
    val author: BlueskyAuthorDto,
    val record: BlueskyRecordDto,
    val embed: BlueskyEmbedDto? = null,
    val replyCount: Int? = 0,
    val repostCount: Int? = 0,
    val likeCount: Int? = 0
)

@JsonClass(generateAdapter = true)
data class BlueskyAuthorDto(
    val handle: String,
    val displayName: String? = null,
    val avatar: String? = null
)

@JsonClass(generateAdapter = true)
data class BlueskyRecordDto(
    val text: String = "",
    val createdAt: String? = null
)

@JsonClass(generateAdapter = true)
data class BlueskyEmbedDto(
    val images: List<BlueskyImageDto>? = null,
    val media: BlueskyEmbedMediaDto? = null
)

@JsonClass(generateAdapter = true)
data class BlueskyEmbedMediaDto(
    val images: List<BlueskyImageDto>? = null
)

@JsonClass(generateAdapter = true)
data class BlueskyImageDto(
    val thumb: String? = null,
    val fullsize: String? = null
)

// --- Mastodon Public API DTOs ---

@JsonClass(generateAdapter = true)
data class MastodonStatusDto(
    val id: String,
    @Json(name = "created_at") val createdAt: String? = null,
    val content: String = "",
    val url: String? = null,
    val language: String? = null,
    @Json(name = "replies_count") val repliesCount: Int = 0,
    @Json(name = "reblogs_count") val reblogsCount: Int = 0,
    @Json(name = "favourites_count") val favouritesCount: Int = 0,
    val account: MastodonAccountDto,
    @Json(name = "media_attachments") val mediaAttachments: List<MastodonMediaDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class MastodonAccountDto(
    val username: String = "",
    val acct: String = "",
    @Json(name = "display_name") val displayName: String = "",
    val avatar: String? = null
)

@JsonClass(generateAdapter = true)
data class MastodonMediaDto(
    val type: String = "",
    @Json(name = "preview_url") val previewUrl: String? = null,
    val url: String? = null
)

// --- Reddit Public JSON DTOs ---

@JsonClass(generateAdapter = true)
data class RedditListingResponse(
    val data: RedditListingDataDto? = null
)

@JsonClass(generateAdapter = true)
data class RedditListingDataDto(
    val children: List<RedditChildDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class RedditChildDto(
    val data: RedditPostDto? = null
)

@JsonClass(generateAdapter = true)
data class RedditPostDto(
    val id: String = "",
    val title: String = "",
    val selftext: String = "",
    val author: String = "",
    @Json(name = "subreddit_name_prefixed") val subreddit: String = "",
    @Json(name = "created_utc") val createdUtc: Double = 0.0,
    val ups: Int = 0,
    @Json(name = "num_comments") val numComments: Int = 0,
    val permalink: String = "",
    val thumbnail: String? = null,
    @Json(name = "url_overridden_by_dest") val mediaUrl: String? = null,
    @Json(name = "over_18") val over18: Boolean = false
)
