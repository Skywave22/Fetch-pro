package com.fetchpro.downloadmanager.domain.usecase

import com.fetchpro.downloadmanager.domain.model.DownloadItem
import com.fetchpro.downloadmanager.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDownloadsUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    operator fun invoke(): Flow<List<DownloadItem>> = repository.observeAll()
}

class ObserveDownloadByIdUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    operator fun invoke(id: String): Flow<DownloadItem?> = repository.observeById(id)
}

class CreateDownloadUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(
        url: String,
        fileName: String? = null,
        destinationDir: String? = null,
        headers: Map<String, String> = emptyMap(),
        numParts: Int = DownloadItem.DEFAULT_NUM_PARTS
    ): DownloadItem = repository.createDownload(url, fileName, destinationDir, headers, numParts)
}

class PauseDownloadUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(id: String) = repository.pauseDownload(id)
}

class ResumeDownloadUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(id: String) = repository.resumeDownload(id)
}

class CancelDownloadUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(id: String) = repository.cancelDownload(id)
}

class DeleteDownloadUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(id: String) = repository.delete(id)
}

class RetryDownloadUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(id: String) = repository.retryDownload(id)
}

class QueueDownloadUseCase @Inject constructor(
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(id: String) = repository.queueDownload(id)
}
