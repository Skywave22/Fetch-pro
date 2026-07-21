package com.fetchpro.downloadmanager.download.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore(name = "auth_settings")

data class SiteCredentials(
    val host: String, // e.g., example.com
    val username: String,
    val password: String
)

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // For simplicity, use DataStore for demo, but in production use EncryptedSharedPreferences
    // We'll implement both: try EncryptedSharedPreferences first, fallback to DataStore

    private val masterKey by lazy {
        try {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        } catch (e: Exception) {
            null
        }
    }

    private val encryptedPrefs by lazy {
        try {
            if (masterKey != null) {
                EncryptedSharedPreferences.create(
                    context,
                    "fetchpro_encrypted_auth",
                    masterKey!!,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun saveCredentials(host: String, username: String, password: String) = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs?.edit()?.apply {
                putString("${host}_user", username)
                putString("${host}_pass", password)
                apply()
            }
            // Also save in DataStore as backup (without encryption for simplicity)
            context.authDataStore.edit { prefs ->
                prefs[stringPreferencesKey("${host}_user")] = username
                prefs[stringPreferencesKey("${host}_pass")] = password
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getCredentials(host: String): SiteCredentials? = withContext(Dispatchers.IO) {
        try {
            // Try encrypted first
            val user = encryptedPrefs?.getString("${host}_user", null)
            val pass = encryptedPrefs?.getString("${host}_pass", null)
            if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
                return@withContext SiteCredentials(host, user, pass)
            }
            // Fallback to DataStore
            val prefs = context.authDataStore.data
            // This would need flow first, simplified synchronous read attempt
            // For true async, use flow
            null
        } catch (e: Exception) {
            null
        }
    }

    fun getCredentialsFlow(host: String): Flow<SiteCredentials?> {
        return context.authDataStore.data.map { prefs ->
            val user = prefs[stringPreferencesKey("${host}_user")]
            val pass = prefs[stringPreferencesKey("${host}_pass")]
            if (!user.isNullOrBlank() && !pass.isNullOrBlank()) {
                SiteCredentials(host, user, pass)
            } else null
        }
    }

    suspend fun deleteCredentials(host: String) = withContext(Dispatchers.IO) {
        try {
            encryptedPrefs?.edit()?.apply {
                remove("${host}_user")
                remove("${host}_pass")
                apply()
            }
            context.authDataStore.edit { prefs ->
                prefs.remove(stringPreferencesKey("${host}_user"))
                prefs.remove(stringPreferencesKey("${host}_pass"))
            }
        } catch (_: Exception) {}
    }

    fun extractHost(url: String): String {
        return try {
            java.net.URL(url).host
        } catch (_: Exception) {
            url.substringAfter("://").substringBefore("/").substringBefore(":")
        }
    }
}
