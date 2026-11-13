package com.example.data.repository

import androidx.core.text.HtmlCompat
import com.example.data.local.SocialPostDao
import com.example.data.local.SocialPostEntity
import com.example.data.model.SocialNetwork
import com.example.data.model.SocialPost
import com.example.data.remote.BlueskyApiService
import com.example.data.remote.MastodonApiService
import com.example.data.remote.RedditApiService
import com.example.data.remote.SocialApiFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class SocialFeedRepository(
    private val dao: SocialPostDao,
    private val blueskyApi: BlueskyApiService = SocialApiFactory.blueskyApi,
    private val mastodonApi: MastodonApiService = SocialApiFactory.mastodonApi,
    private val redditApi: RedditApiService = SocialApiFactory.redditApi
) {

    fun observeNetworkFeed(network: SocialNetwork): Flow<List<SocialPost>> {
        return when (network) {
            SocialNetwork.BOOKMARKS -> dao.observeBookmarkedPosts()
            else -> dao.observePostsByNetwork(network.id)
        }.map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun refreshNetwork(network: SocialNetwork): Result<Int> = withContext(Dispatchers.IO) {
        if (network == SocialNetwork.BOOKMARKS) return@withContext Result.success(0)

        runCatching {
            val likedIds = dao.getLikedIds().toSet()
            val bookmarkedIds = dao.getBookmarkedIds().toSet()
            val nowMillis = System.currentTimeMillis()

            val freshPosts: List<SocialPost> = when (network) {
                SocialNetwork.BLUESKY -> fetchBlueskyPosts(likedIds, bookmarkedIds)
                SocialNetwork.MASTODON -> fetchMastodonPosts(likedIds, bookmarkedIds)
                SocialNetwork.REDDIT -> fetchRedditPosts(likedIds, bookmarkedIds)
                SocialNetwork.BOOKMARKS -> emptyList()
            }

            if (freshPosts.isNotEmpty()) {
                dao.clearTransientNetworkPosts(network.id)
                dao.upsertAll(freshPosts.map { SocialPostEntity.fromDomain(it, nowMillis) })
            }
            freshPosts.size
        }
    }

    suspend fun toggleLike(postId: String): Boolean = withContext(Dispatchers.IO) {
        val current = dao.getPostById(postId) ?: return@withContext false
        val newLiked = !current.isLiked
        val newCount = (current.likesCount + if (newLiked) 1 else -1).coerceAtLeast(0)
        dao.updateLike(postId, newLiked, newCount)
        newLiked
    }

    suspend fun setLiked(postId: String, liked: Boolean) = withContext(Dispatchers.IO) {
        val current = dao.getPostById(postId) ?: return@withContext
        if (current.isLiked == liked) return@withContext
        val newCount = (current.likesCount + if (liked) 1 else -1).coerceAtLeast(0)
        dao.updateLike(postId, liked, newCount)
    }

    suspend fun toggleBookmark(postId: String): Boolean = withContext(Dispatchers.IO) {
        val current = dao.getPostById(postId) ?: return@withContext false
        val newBookmarked = !current.isBookmarked
        dao.updateBookmark(postId, newBookmarked)
        newBookmarked
    }

    private suspend fun fetchBlueskyPosts(
        likedIds: Set<String>,
        bookmarkedIds: Set<String>
    ): List<SocialPost> {
        val response = blueskyApi.searchPosts(
            query = "tecnologia OR ciencia OR diseño OR android OR IA",
            sort = "top",
            limit = 25
        )
        return response.posts.mapNotNull { dto ->
            val text = dto.record.text.trim()
            if (text.isBlank()) return@mapNotNull null

            val imageUrl = dto.embed?.images?.firstOrNull()?.thumb
                ?: dto.embed?.media?.images?.firstOrNull()?.thumb

            val rkey = dto.uri.substringAfterLast("/", "")
            val webUrl = if (rkey.isNotBlank()) {
                "https://bsky.app/profile/${dto.author.handle}/post/$rkey"
            } else {
                "https://bsky.app/profile/${dto.author.handle}"
            }

            val epochSec = parseIsoToEpochSeconds(dto.record.createdAt)
            val id = "bsky_${dto.uri.hashCode()}"
            SocialPost(
                id = id,
                network = SocialNetwork.BLUESKY,
                authorName = dto.author.displayName?.takeIf { it.isNotBlank() } ?: dto.author.handle,
                authorHandle = "@${dto.author.handle}",
                avatarUrl = dto.author.avatar,
                content = text,
                imageUrl = imageUrl,
                relativeTime = formatRelativeTime(epochSec),
                likesCount = (dto.likeCount ?: 0) + if (id in likedIds) 1 else 0,
                repostsCount = dto.repostCount ?: 0,
                repliesCount = dto.replyCount ?: 0,
                externalUrl = webUrl,
                isLiked = id in likedIds,
                isBookmarked = id in bookmarkedIds,
                createdEpochSeconds = epochSec
            )
        }
    }

    private suspend fun fetchMastodonPosts(
        likedIds: Set<String>,
        bookmarkedIds: Set<String>
    ): List<SocialPost> {
        val statuses = mastodonApi.getPublicTimeline(local = true, limit = 30)
        return statuses.mapNotNull { dto ->
            val cleanText = stripHtml(dto.content)
            if (cleanText.length < 12) return@mapNotNull null

            val imageUrl = dto.mediaAttachments
                .firstOrNull { it.type == "image" }
                ?.let { it.previewUrl ?: it.url }

            val epochSec = parseIsoToEpochSeconds(dto.createdAt)
            val id = "mstdn_${dto.id}"
            val displayName = dto.account.displayName.takeIf { it.isNotBlank() } ?: dto.account.username
            val acct = if (dto.account.acct.startsWith("@")) dto.account.acct else "@${dto.account.acct}"

            SocialPost(
                id = id,
                network = SocialNetwork.MASTODON,
                authorName = displayName,
                authorHandle = acct,
                avatarUrl = dto.account.avatar,
                content = cleanText,
                imageUrl = imageUrl,
                relativeTime = formatRelativeTime(epochSec),
                likesCount = dto.favouritesCount + if (id in likedIds) 1 else 0,
                repostsCount = dto.reblogsCount,
                repliesCount = dto.repliesCount,
                externalUrl = dto.url ?: "https://mastodon.social",
                isLiked = id in likedIds,
                isBookmarked = id in bookmarkedIds,
                createdEpochSeconds = epochSec
            )
        }
    }

    private suspend fun fetchRedditPosts(
        likedIds: Set<String>,
        bookmarkedIds: Set<String>
    ): List<SocialPost> {
        val listing = redditApi.getPopularPosts(limit = 25)
        val children = listing.data?.children.orEmpty()
        return children.mapNotNull { child ->
            val dto = child.data ?: return@mapNotNull null
            if (dto.over18 || dto.title.isBlank()) return@mapNotNull null

            val bodySnippet = dto.selftext.trim().take(220)
            val combinedContent = if (bodySnippet.isNotBlank()) {
                "${dto.title}\n\n$bodySnippet"
            } else {
                dto.title
            }

            val validImage = when {
                dto.mediaUrl?.endsWith(".jpg") == true ||
                    dto.mediaUrl?.endsWith(".png") == true ||
                    dto.mediaUrl?.endsWith(".webp") == true -> dto.mediaUrl
                dto.thumbnail?.startsWith("http") == true -> dto.thumbnail
                else -> null
            }

            val epochSec = dto.createdUtc.toLong()
            val id = "reddit_${dto.id}"
            SocialPost(
                id = id,
                network = SocialNetwork.REDDIT,
                authorName = dto.subreddit.ifBlank { "r/popular" },
                authorHandle = "u/${dto.author}",
                avatarUrl = null,
                content = combinedContent,
                imageUrl = validImage,
                relativeTime = formatRelativeTime(epochSec),
                likesCount = dto.ups + if (id in likedIds) 1 else 0,
                repostsCount = 0,
                repliesCount = dto.numComments,
                externalUrl = "https://www.reddit.com${dto.permalink}",
                isLiked = id in likedIds,
                isBookmarked = id in bookmarkedIds,
                createdEpochSeconds = epochSec
            )
        }
    }

    private fun stripHtml(rawHtml: String): String {
        val decoded = HtmlCompat.fromHtml(rawHtml, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
        return decoded.replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    private fun parseIsoToEpochSeconds(iso: String?): Long {
        if (iso.isNullOrBlank()) return System.currentTimeMillis() / 1000L
        val normalized = iso.replace(Regex("\\.\\d+Z$"), "Z")
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(normalized)?.time
            }.getOrNull()
            if (parsed != null) return parsed / 1000L
        }
        return System.currentTimeMillis() / 1000L
    }

    private fun formatRelativeTime(epochSeconds: Long): String {
        if (epochSeconds <= 0L) return "ahora"
        val nowSeconds = System.currentTimeMillis() / 1000L
        val minutes = ((nowSeconds - epochSeconds) / 60L).coerceAtLeast(0L)
        return when {
            minutes < 1 -> "ahora"
            minutes < 60 -> "${minutes}m"
            minutes < 1440 -> "${minutes / 60}h"
            else -> "${minutes / 1440}d"
        }
    }
}
