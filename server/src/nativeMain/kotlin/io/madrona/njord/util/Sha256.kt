package io.madrona.njord.util

import File
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.refTo
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.usePinned
import libssl.EVP_DigestFinal_ex
import libssl.EVP_DigestInit_ex
import libssl.EVP_DigestUpdate
import libssl.EVP_MD_CTX_free
import libssl.EVP_MD_CTX_new
import libssl.EVP_sha256
import platform.posix.fclose
import platform.posix.ferror
import platform.posix.fopen
import platform.posix.fread

/**
 * Streaming SHA-256 (lowercase hex) of a file's contents, read in 1 MiB chunks so multi-GB
 * region archives are never held in memory. Returns null if the file cannot be read or a
 * digest call fails.
 */
@OptIn(ExperimentalForeignApi::class)
fun sha256File(file: File): String? {
    val fp = fopen(file.getAbsolutePath().toString(), "rb") ?: return null
    val ctx = EVP_MD_CTX_new() ?: run {
        fclose(fp)
        return null
    }
    try {
        if (EVP_DigestInit_ex(ctx, EVP_sha256(), null) != 1) return null
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val read = fread(buffer.refTo(0), 1.toULong(), buffer.size.toULong(), fp)
            if (read == 0.toULong()) break
            if (EVP_DigestUpdate(ctx, buffer.refTo(0), read) != 1) return null
        }
        if (ferror(fp) != 0) return null
        val digest = ByteArray(32)
        val len = uintArrayOf(0u).toCValues()
        digest.usePinned { pinned ->
            if (EVP_DigestFinal_ex(ctx, pinned.addressOf(0).reinterpret(), len) != 1) return null
        }
        return digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    } finally {
        EVP_MD_CTX_free(ctx)
        fclose(fp)
    }
}
