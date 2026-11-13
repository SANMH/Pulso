package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.SocialNetwork
import com.example.data.model.SocialPost
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "social_posts")
data class SocialPostEntity(
    @PrimaryKey val id: String,
    val networkId: String,
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
    val isLiked: Boolean,
    val isBookmarked: Boolean,
    val createdEpochSeconds: Long,
    val fetchedAtMillis: Long
) {
    fun toDomain(): SocialPost = SocialPost(
        id = id,
        network = SocialNetwork.fromId(networkId),
        authorName = authorName,
        authorHandle = authorHandle,
        avatarUrl = avatarUrl,
        content = content,
        imageUrl = imageUrl,
        relativeTime = relativeTime,
        likesCount = likesCount,
        repostsCount = repostsCount,
        repliesCount = repliesCount,
        externalUrl = externalUrl,
        isLiked = isLiked,
        isBookmarked = isBookmarked,
        createdEpochSeconds = createdEpochSeconds
    )

    companion object {
        fun fromDomain(post: SocialPost, fetchedAtMillis: Long): SocialPostEntity = SocialPostEntity(
            id = post.id,
            networkId = post.network.id,
            authorName = post.authorName,
            authorHandle = post.authorHandle,
            avatarUrl = post.avatarUrl,
            content = post.content,
            imageUrl = post.imageUrl,
            relativeTime = post.relativeTime,
            likesCount = post.likesCount,
            repostsCount = post.repostsCount,
            repliesCount = post.repliesCount,
            externalUrl = post.externalUrl,
            isLiked = post.isLiked,
            isBookmarked = post.isBookmarked,
            createdEpochSeconds = post.createdEpochSeconds,
            fetchedAtMillis = fetchedAtMillis
        )
    }
}

@Dao
interface SocialPostDao {
    @Query("SELECT * FROM social_posts WHERE networkId = :networkId ORDER BY fetchedAtMillis DESC, createdEpochSeconds DESC")
    fun observePostsByNetwork(networkId: String): Flow<List<SocialPostEntity>>

    @Query("SELECT * FROM social_posts WHERE isBookmarked = 1 ORDER BY fetchedAtMillis DESC")
    fun observeBookmarkedPosts(): Flow<List<SocialPostEntity>>

    @Query("SELECT * FROM social_posts WHERE id = :id LIMIT 1")
    suspend fun getPostById(id: String): SocialPostEntity?

    @Query("SELECT id FROM social_posts WHERE isLiked = 1")
    suspend fun getLikedIds(): List<String>

    @Query("SELECT id FROM social_posts WHERE isBookmarked = 1")
    suspend fun getBookmarkedIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(posts: List<SocialPostEntity>)

    @Query("UPDATE social_posts SET isLiked = :liked, likesCount = :likesCount WHERE id = :id")
    suspend fun updateLike(id: String, liked: Boolean, likesCount: Int)

    @Query("UPDATE social_posts SET isBookmarked = :bookmarked WHERE id = :id")
    suspend fun updateBookmark(id: String, bookmarked: Boolean)

    @Query("DELETE FROM social_posts WHERE networkId = :networkId AND isBookmarked = 0 AND isLiked = 0")
    suspend fun clearTransientNetworkPosts(networkId: String)
}

@Database(entities = [SocialPostEntity::class], version = 1, exportSchema = false)
abstract class PulsoDatabase : RoomDatabase() {
    abstract fun socialPostDao(): SocialPostDao

    companion object {
        @Volatile
        private var INSTANCE: PulsoDatabase? = null

        fun getInstance(context: Context): PulsoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PulsoDatabase::class.java,
                    "pulso_watch_cache.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
