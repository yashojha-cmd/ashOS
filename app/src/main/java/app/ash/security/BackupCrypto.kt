package app.ash.security

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Password-based encryption for exports. Keystore keys cannot leave the phone, so a backup
 * that must restore on a new device is protected by a user password instead:
 *
 *   file = "ASHBK1" | iterations (int) | salt (16 bytes) | AES-256-GCM-HKDF streaming ciphertext
 *   key  = PBKDF2-HMAC-SHA256(password, salt, iterations)
 */
object BackupCrypto {
    private val MAGIC = "ASHBK1".toByteArray()
    const val DEFAULT_ITERATIONS = 310_000
    private const val SEGMENT = 1 shl 20

    class WrongPasswordOrCorrupt(cause: Throwable? = null) : Exception("Backup could not be decrypted", cause)
    class NotABackup : Exception("Not an ashOS backup file")

    fun encryptingStream(out: OutputStream, password: CharArray, iterations: Int = DEFAULT_ITERATIONS): OutputStream {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val header = DataOutputStream(out)
        header.write(MAGIC)
        header.writeInt(iterations)
        header.write(salt)
        header.flush()
        return streaming(password, salt, iterations).newEncryptingStream(out, MAGIC)
    }

    fun decryptingStream(input: InputStream, password: CharArray): InputStream {
        val header = DataInputStream(input)
        val magic = ByteArray(MAGIC.size)
        header.readFully(magic)
        if (!magic.contentEquals(MAGIC)) throw NotABackup()
        val iterations = header.readInt()
        if (iterations !in 10_000..5_000_000) throw NotABackup()
        val salt = ByteArray(16)
        header.readFully(salt)
        return streaming(password, salt, iterations).newDecryptingStream(input, MAGIC)
    }

    private fun streaming(password: CharArray, salt: ByteArray, iterations: Int): AesGcmHkdfStreaming {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return AesGcmHkdfStreaming(key, "HmacSha256", 32, SEGMENT, 0)
    }
}
