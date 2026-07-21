package com.fetchpro.downloadmanager.download.engine

import java.io.File
import java.security.MessageDigest

object IntegrityChecker {

    fun sha256(file: File): String = hash(file, "SHA-256")

    fun md5(file: File): String = hash(file, "MD5")

    fun sha1(file: File): String = hash(file, "SHA-1")

    private fun hash(file: File, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifyChecksum(file: File, expected: String, algorithm: String = "SHA-256"): Boolean {
        if (expected.isBlank()) return true
        return try {
            val actual = when (algorithm.uppercase()) {
                "MD5" -> md5(file)
                "SHA1", "SHA-1" -> sha1(file)
                else -> sha256(file)
            }
            actual.equals(expected, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    // Legacy method for backward compatibility
    fun verifyChecksum(file: File, expectedSha256: String): Boolean {
        return verifyChecksum(file, expectedSha256, "SHA-256")
    }

    fun calculateAll(file: File): Map<String, String> {
        return try {
            mapOf(
                "MD5" to md5(file),
                "SHA-1" to sha1(file),
                "SHA-256" to sha256(file)
            )
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
