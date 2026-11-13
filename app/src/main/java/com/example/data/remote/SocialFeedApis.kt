package com.example.data.remote

import com.example.data.model.BlueskySearchResponse
import com.example.data.model.MastodonStatusDto
import com.example.data.model.RedditListingResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface BlueskyApiService {
    @GET("xrpc/app.bsky.feed.searchPosts")
    suspend fun searchPosts(
        @Query("q") query: String = "tecnologia OR ciencia OR diseño OR app",
        @Query("sort") sort: String = "top",
        @Query("limit") limit: Int = 25
    ): BlueskySearchResponse
}

interface MastodonApiService {
    @GET("api/v1/timelines/public")
    suspend fun getPublicTimeline(
        @Query("local") local: Boolean = true,
        @Query("limit") limit: Int = 30
    ): List<MastodonStatusDto>
}

interface RedditApiService {
    @GET("r/popular.json")
    suspend fun getPopularPosts(
        @Query("limit") limit: Int = 25,
        @Query("raw_json") rawJson: Int = 1
    ): RedditListingResponse
}

object SocialApiFactory {
    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        val userAgentInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "android:com.aistudio.pulsowatch:v1.0 (WearSocialScroller)")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val blueskyApi: BlueskyApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://public.api.bsky.app/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(BlueskyApiService::class.java)
    }

    val mastodonApi: MastodonApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://mastodon.social/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MastodonApiService::class.java)
    }

    val redditApi: RedditApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.reddit.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RedditApiService::class.java)
    }
}
