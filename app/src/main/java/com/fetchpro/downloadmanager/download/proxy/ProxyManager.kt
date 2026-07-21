package com.fetchpro.downloadmanager.download.proxy

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import javax.inject.Inject
import javax.inject.Singleton

private val Context.proxyDataStore by preferencesDataStore(name = "proxy_settings")

enum class ProxyType {
    NONE,
    HTTP,
    SOCKS
}

data class ProxyConfig(
    val type: ProxyType = ProxyType.NONE,
    val host: String = "",
    val port: Int = 8080,
    val username: String? = null,
    val password: String? = null,
    val enabled: Boolean = false
) {
    val isConfigured: Boolean
        get() = enabled && type != ProxyType.NONE && host.isNotBlank() && port in 1..65535
}

@Singleton
class ProxyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val KEY_TYPE = stringPreferencesKey("proxy_type")
        private val KEY_HOST = stringPreferencesKey("proxy_host")
        private val KEY_PORT = intPreferencesKey("proxy_port")
        private val KEY_USERNAME = stringPreferencesKey("proxy_username")
        private val KEY_PASSWORD = stringPreferencesKey("proxy_password")
        private val KEY_ENABLED = booleanPreferencesKey("proxy_enabled")
    }

    val proxyFlow: Flow<ProxyConfig> = context.proxyDataStore.data.map { prefs ->
        ProxyConfig(
            type = try { ProxyType.valueOf(prefs[KEY_TYPE] ?: "NONE") } catch (_: Exception) { ProxyType.NONE },
            host = prefs[KEY_HOST] ?: "",
            port = prefs[KEY_PORT] ?: 8080,
            username = prefs[KEY_USERNAME],
            password = prefs[KEY_PASSWORD],
            enabled = prefs[KEY_ENABLED] ?: false
        )
    }

    suspend fun saveProxy(config: ProxyConfig) {
        context.proxyDataStore.edit { prefs ->
            prefs[KEY_TYPE] = config.type.name
            prefs[KEY_HOST] = config.host
            prefs[KEY_PORT] = config.port
            if (config.username != null) prefs[KEY_USERNAME] = config.username else prefs.remove(KEY_USERNAME)
            if (config.password != null) prefs[KEY_PASSWORD] = config.password else prefs.remove(KEY_PASSWORD)
            prefs[KEY_ENABLED] = config.enabled
        }
    }

    suspend fun clearProxy() {
        context.proxyDataStore.edit { it.clear() }
    }

    fun createProxy(config: ProxyConfig): Proxy? {
        if (!config.isConfigured) return null
        return when (config.type) {
            ProxyType.HTTP -> Proxy(Proxy.Type.HTTP, InetSocketAddress(config.host, config.port))
            ProxyType.SOCKS -> Proxy(Proxy.Type.SOCKS, InetSocketAddress(config.host, config.port))
            ProxyType.NONE -> null
        }
    }

    fun createAuthenticator(config: ProxyConfig): Authenticator? {
        if (config.username.isNullOrBlank() || config.password.isNullOrBlank()) return null
        return Authenticator { _, response ->
            val credential = Credentials.basic(config.username, config.password)
            response.request.newBuilder()
                .header("Proxy-Authorization", credential)
                .build()
        }
    }

    fun applyToClientBuilder(builder: OkHttpClient.Builder, config: ProxyConfig): OkHttpClient.Builder {
        val proxy = createProxy(config) ?: return builder
        builder.proxy(proxy)
        createAuthenticator(config)?.let { builder.proxyAuthenticator(it) }
        return builder
    }
}
