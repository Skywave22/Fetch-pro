package com.fetchpro.downloadmanager.di

import android.content.Context
import androidx.room.Room
import com.fetchpro.downloadmanager.data.local.db.DownloadDao
import com.fetchpro.downloadmanager.data.local.db.DownloadDatabase
import com.fetchpro.downloadmanager.data.repository.DownloadRepositoryImpl
import com.fetchpro.downloadmanager.domain.repository.DownloadRepository
import com.fetchpro.downloadmanager.download.engine.HttpClientProvider
import com.fetchpro.downloadmanager.download.engine.MultiPartDownloader
import com.fetchpro.downloadmanager.download.queue.DownloadQueueManager
import com.fetchpro.downloadmanager.download.service.DownloadNotificationManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModuleBinds {
    @Binds
    @Singleton
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AppModuleProvides {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DownloadDatabase {
        return Room.databaseBuilder(
            context,
            DownloadDatabase::class.java,
            "fetchpro_downloads.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideDownloadDao(db: DownloadDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun provideHistoryDao(db: DownloadDatabase) = db.browserHistoryDao()

    @Provides
    fun provideBookmarkDao(db: DownloadDatabase) = db.bookmarkDao()

    @Provides
    @Singleton
    fun provideHttpClientProvider(proxyManager: com.fetchpro.downloadmanager.download.proxy.ProxyManager): HttpClientProvider =
        HttpClientProvider(proxyManager)

    @Provides
    @Singleton
    fun provideOkHttpClient(httpClientProvider: HttpClientProvider): okhttp3.OkHttpClient =
        httpClientProvider.client

    @Provides
    @Singleton
    fun provideSpeedLimiterManager(): com.fetchpro.downloadmanager.download.limiter.SpeedLimiterManager =
        com.fetchpro.downloadmanager.download.limiter.SpeedLimiterManager()

    @Provides
    @Singleton
    fun provideMultiPartDownloader(
        provider: HttpClientProvider,
        speedLimiterManager: com.fetchpro.downloadmanager.download.limiter.SpeedLimiterManager,
        retryManager: com.fetchpro.downloadmanager.download.utils.RetryManager
    ): MultiPartDownloader =
        MultiPartDownloader(provider, speedLimiterManager, retryManager)

    @Provides
    @Singleton
    fun provideNotificationManager(@ApplicationContext context: Context): DownloadNotificationManager =
        DownloadNotificationManager(context)

    @Provides
    @Singleton
    fun provideQueueManager(dao: DownloadDao, settings: com.fetchpro.downloadmanager.data.local.datastore.SettingsDataStore): DownloadQueueManager = DownloadQueueManager(dao, settings)
}
