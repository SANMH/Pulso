package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AutoScrollSpeed
import com.example.data.model.WatchShapeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.watchDataStore: DataStore<Preferences> by preferencesDataStore(name = "pulso_watch_prefs")

data class WatchPreferences(
    val autoScrollSpeed: AutoScrollSpeed = AutoScrollSpeed.OFF,
    val tapToScrollEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val largeTextEnabled: Boolean = false,
    val watchShapeMode: WatchShapeMode = WatchShapeMode.ROUND,
    val hasSeenGestureHint: Boolean = false
)

class WatchPreferencesRepository(private val context: Context) {
    private object Keys {
        val AUTO_SCROLL_SPEED = stringPreferencesKey("auto_scroll_speed")
        val TAP_TO_SCROLL = booleanPreferencesKey("tap_to_scroll")
        val HAPTICS = booleanPreferencesKey("haptics")
        val LARGE_TEXT = booleanPreferencesKey("large_text")
        val WATCH_SHAPE = stringPreferencesKey("watch_shape")
        val SEEN_HINT = booleanPreferencesKey("seen_gesture_hint")
    }

    val preferencesFlow: Flow<WatchPreferences> = context.watchDataStore.data.map { prefs ->
        WatchPreferences(
            autoScrollSpeed = prefs[Keys.AUTO_SCROLL_SPEED]
                ?.let { runCatching { AutoScrollSpeed.valueOf(it) }.getOrNull() }
                ?: AutoScrollSpeed.OFF,
            tapToScrollEnabled = prefs[Keys.TAP_TO_SCROLL] ?: true,
            hapticsEnabled = prefs[Keys.HAPTICS] ?: true,
            largeTextEnabled = prefs[Keys.LARGE_TEXT] ?: false,
            watchShapeMode = prefs[Keys.WATCH_SHAPE]
                ?.let { runCatching { WatchShapeMode.valueOf(it) }.getOrNull() }
                ?: WatchShapeMode.ROUND,
            hasSeenGestureHint = prefs[Keys.SEEN_HINT] ?: false
        )
    }

    suspend fun setAutoScrollSpeed(speed: AutoScrollSpeed) {
        context.watchDataStore.edit { it[Keys.AUTO_SCROLL_SPEED] = speed.name }
    }

    suspend fun setTapToScroll(enabled: Boolean) {
        context.watchDataStore.edit { it[Keys.TAP_TO_SCROLL] = enabled }
    }

    suspend fun setHaptics(enabled: Boolean) {
        context.watchDataStore.edit { it[Keys.HAPTICS] = enabled }
    }

    suspend fun setLargeText(enabled: Boolean) {
        context.watchDataStore.edit { it[Keys.LARGE_TEXT] = enabled }
    }

    suspend fun setWatchShapeMode(mode: WatchShapeMode) {
        context.watchDataStore.edit { it[Keys.WATCH_SHAPE] = mode.name }
    }

    suspend fun markGestureHintSeen() {
        context.watchDataStore.edit { it[Keys.SEEN_HINT] = true }
    }
}
