package com.fetchpro.downloadmanager.domain.usecase

import com.fetchpro.downloadmanager.data.local.db.DownloadDao
import com.fetchpro.downloadmanager.data.local.db.DownloadStateEntity
import com.fetchpro.downloadmanager.domain.repository.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PauseAllDownloadsUseCase @Inject constructor(
    private val dao: DownloadDao,
    private val repository: DownloadRepository
) {
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        val active = dao.getDownloadsByStates(listOf(DownloadStateEntity.DOWNLOADING, DownloadStateEntity.QUEUED))
        active.forEach { repository.pauseDownload(it.id) }
    }
}

class ResumeAllDownloadsUseCase @Inject constructor(
    private val dao: DownloadDao,
    private val repository: DownloadRepository
) {
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        val paused = dao.getDownloadsByStates(listOf(DownloadStateEntity.PAUSED, DownloadStateEntity.FAILED))
        paused.forEach { repository.resumeDownload(it.id) }
    }
}

class CancelAllDownloadsUseCase @Inject constructor(
    private val dao: DownloadDao,
    private val repository: DownloadRepository
) {
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        val all = dao.getDownloadsByStates(listOf(DownloadStateEntity.DOWNLOADING, DownloadStateEntity.QUEUED, DownloadStateEntity.PAUSED))
        all.forEach { repository.cancelDownload(it.id) }
    }
}

class DeleteCompletedDownloadsUseCase @Inject constructor(
    private val dao: DownloadDao,
    private val repository: DownloadRepository
) {
    suspend operator fun invoke(deleteFiles: Boolean = false) = withContext(Dispatchers.IO) {
        val completed = dao.getDownloadsByStates(listOf(DownloadStateEntity.COMPLETED))
        completed.forEach {
            if (!deleteFiles) {
                dao.deleteDownload(it.id) // keep file, delete DB entry handled in repo? repo deletes file, so use dao directly if keep file
            } else {
                repository.delete(it.id)
            }
        }
    }
}

class DeleteAllDownloadsUseCase @Inject constructor(
    private val dao: DownloadDao,
    private val repository: DownloadRepository
) {
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        val all = dao.getDownloadsByStates(DownloadStateEntity.values().toList())
        all.forEach { repository.delete(it.id) }
    }
}
