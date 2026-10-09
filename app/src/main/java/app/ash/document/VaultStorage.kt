package app.ash.document

import android.content.Context
import app.ash.security.VaultCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted files in app-private storage: filesDir/vault/<random-uuid>.bin.
 * Plaintext is only ever held in memory, or in a short-lived file under noBackupFilesDir/view
 * that callers delete as soon as the viewer closes.
 */
@Singleton
class VaultStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crypto: VaultCrypto,
) {
    data class Stored(val fileName: String, val sizeBytes: Long, val sha256: String)

    class TooLarge : Exception("File exceeds vault size limit")

    private val vaultDir: File get() = File(context.filesDir, "vault").apply { mkdirs() }
    private val viewDir: File get() = File(context.noBackupFilesDir, "view").apply { mkdirs() }

    fun newFileName(): String = "${UUID.randomUUID()}.bin"

    /** Streams [input] through encryption; never buffers the whole file. */
    fun store(input: InputStream, fileName: String = newFileName(), maxBytes: Long = MAX_BYTES): Stored {
        val target = File(vaultDir, fileName)
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        try {
            DigestInputStream(input, digest).use { source ->
                target.outputStream().buffered().use { raw ->
                    crypto.encryptingStream(raw, fileName).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = source.read(buffer)
                            if (read < 0) break
                            size += read
                            if (size > maxBytes) throw TooLarge()
                            out.write(buffer, 0, read)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        return Stored(fileName, size, digest.digest().joinToString("") { "%02x".format(it) })
    }

    fun storeBytes(bytes: ByteArray, fileName: String): Stored = store(bytes.inputStream(), fileName)

    fun readBytes(fileName: String): ByteArray =
        File(vaultDir, fileName).inputStream().buffered().use { raw ->
            crypto.decryptingStream(raw, fileName).use { it.readBytes() }
        }

    fun openDecrypted(fileName: String): InputStream =
        crypto.decryptingStream(File(vaultDir, fileName).inputStream().buffered(), fileName)

    fun exists(fileName: String): Boolean = File(vaultDir, fileName).exists()

    fun decryptToTemp(fileName: String, extension: String): File {
        val temp = File(viewDir, "${UUID.randomUUID()}.$extension")
        openDecrypted(fileName).use { input -> temp.outputStream().use { input.copyTo(it) } }
        return temp
    }

    fun delete(fileName: String) {
        File(vaultDir, fileName).delete()
    }

    /** Removes leftover plaintext view files (e.g. after a crash). Called at startup. */
    fun clearViewCache() {
        viewDir.listFiles()?.forEach { it.delete() }
        File(context.cacheDir, "share").listFiles()?.forEach { it.delete() }
    }

    fun deleteAll() {
        vaultDir.deleteRecursively()
        clearViewCache()
    }

    fun totalBytes(): Long = vaultDir.listFiles()?.sumOf { it.length() } ?: 0

    companion object {
        const val MAX_BYTES = 50L * 1024 * 1024
        fun thumbName(fileName: String) = "$fileName.thumb"
    }
}
