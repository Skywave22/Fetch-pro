package com.fetchpro.downloadmanager.download.engine

import com.fetchpro.downloadmanager.download.proxy.ProxyManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HttpClientProvider @Inject constructor(
    private val proxyManager: ProxyManager
) {

    val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)

        // Apply proxy if configured
        try {
            val proxyConfig = runBlocking { proxyManager.proxyFlow.first() }
            proxyManager.applyToClientBuilder(builder, proxyConfig)
        } catch (_: Exception) {}

        builder.build()
    }

    fun newClientForDownload(): OkHttpClient {
        val builder = client.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS) // indefinite for large downloads

        // Re-apply proxy for download client as well (in case changed)
        try {
            val proxyConfig = runBlocking { proxyManager.proxyFlow.first() }
            proxyManager.applyToClientBuilder(builder, proxyConfig)
        } catch (_: Exception) {}

        return builder.build()
    }

    /**
     * Create client with custom headers for password protected sites
     */
    fun newClientWithAuth(username: String, password: String): OkHttpClient {
        return client.newBuilder()
            .authenticator { _, response ->
                val credential = okhttp3.Credentials.basic(username, password)
                response.request.newBuilder()
                    .header("Authorization", credential)
                    .build()
            }
            .build()
    }
}
