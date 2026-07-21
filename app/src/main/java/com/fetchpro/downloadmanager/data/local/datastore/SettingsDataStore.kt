package com.fetchpro.downloadmanager.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.fetchpro.downloadmanager.domain.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "fetchpro_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_MAX_CONCURRENT = intPreferencesKey("max_concurrent")
        private val KEY_MAX_PARTS = intPreferencesKey("max_parts")
        private val KEY_WIFI_ONLY = booleanPreferencesKey("wifi_only")
        private val KEY_CUSTOM_DIR = stringPreferencesKey("custom_dir")
        private val KEY_AUTO_RETRY = booleanPreferencesKey("auto_retry")
        private val KEY_KEEP_COMPLETED_DAYS = intPreferencesKey("keep_completed_days")
        private val KEY_ASK_BEFORE_DOWNLOAD = booleanPreferencesKey("ask_before_download")
        private val KEY_GLOBAL_SPEED_LIMIT = longPreferencesKey("global_speed_limit")
        private val KEY_RETRY_DELAY = longPreferencesKey("retry_delay")
        private val KEY_UNLIMITED_RETRY = booleanPreferencesKey("unlimited_retry")
        private val KEY_SMART_CLIPBOARD = booleanPreferencesKey("smart_clipboard")
        private val KEY_VIBRATE_COMPLETE = booleanPreferencesKey("vibrate_complete")
        private val KEY_SOUND_COMPLETE = booleanPreferencesKey("sound_complete")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            maxConcurrent = prefs[KEY_MAX_CONCURRENT] ?: 3,
            maxParts = prefs[KEY_MAX_PARTS] ?: 8,
            wifiOnly = prefs[KEY_WIFI_ONLY] ?: false,
            customDownloadDir = prefs[KEY_CUSTOM_DIR],
            autoRetry = prefs[KEY_AUTO_RETRY] ?: true,
            keepCompletedHistoryDays = prefs[KEY_KEEP_COMPLETED_DAYS] ?: 30,
            askBeforeDownload = prefs[KEY_ASK_BEFORE_DOWNLOAD] ?: false,
            globalSpeedLimitBps = prefs[KEY_GLOBAL_SPEED_LIMIT] ?: 0L,
            retryDelayMs = prefs[KEY_RETRY_DELAY] ?: 5000L,
            unlimitedRetry = prefs[KEY_UNLIMITED_RETRY] ?: true,
            smartClipboardEnabled = prefs[KEY_SMART_CLIPBOARD] ?: false,
            vibrationOnComplete = prefs[KEY_VIBRATE_COMPLETE] ?: true,
            soundOnComplete = prefs[KEY_SOUND_COMPLETE] ?: true
        )
    }

    suspend fun updateMaxConcurrent(count: Int) {
        context.dataStore.edit { it[KEY_MAX_CONCURRENT] = count.coerceIn(1, 30) } // increased to 30 per 1DM+
    }

    suspend fun updateMaxParts(count: Int) {
        context.dataStore.edit { it[KEY_MAX_PARTS] = count.coerceIn(1, 32) } // increased to 32 per 1DM+
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.dataStore.edit { it[KEY_WIFI_ONLY] = enabled }
    }

    suspend fun setCustomDir(path: String?) {
        context.dataStore.edit {
            if (path == null) it.remove(KEY_CUSTOM_DIR) else it[KEY_CUSTOM_DIR] = path
        }
    }

    suspend fun setAutoRetry(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_RETRY] = enabled }
    }

    suspend fun setKeepHistoryDays(days: Int) {
        context.dataStore.edit { it[KEY_KEEP_COMPLETED_DAYS] = days }
    }

    suspend fun setAskBeforeDownload(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ASK_BEFORE_DOWNLOAD] = enabled }
    }

    suspend fun setGlobalSpeedLimit(bps: Long) {
        context.dataStore.edit { it[KEY_GLOBAL_SPEED_LIMIT] = bps.coerceAtLeast(0) }
    }

    suspend fun setRetryDelay(delayMs: Long) {
        context.dataStore.edit { it[KEY_RETRY_DELAY] = delayMs.coerceIn(1000, 60000) }
    }

    suspend fun setUnlimitedRetry(enabled: Boolean) {
        context.dataStore.edit { it[KEY_UNLIMITED_RETRY] = enabled }
    }

    suspend fun setSmartClipboard(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SMART_CLIPBOARD] = enabled }
    }

    suspend fun setVibrationOnComplete(enabled: Boolean) {
        context.dataStore.edit { it[KEY_VIBRATE_COMPLETE] = enabled }
    }

    suspend fun setSoundOnComplete(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SOUND_COMPLETE] = enabled }
    }
}
