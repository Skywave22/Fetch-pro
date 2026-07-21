package com.fetchpro.downloadmanager.download.engine

import java.io.File
import java.io.RandomAccessFile

object FileAllocator {
    fun allocate(file: File, totalBytes: Long) {
        if (totalBytes <= 0) return
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        RandomAccessFile(file, "rw").use { raf ->
            if (raf.length() != totalBytes) {
                raf.setLength(totalBytes)
            }
        }
    }

    fun writeAt(file: File, offset: Long, data: ByteArray, length: Int) {
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(offset)
            raf.write(data, 0, length)
        }
    }
}
