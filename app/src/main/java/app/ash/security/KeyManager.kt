package app.ash.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import app.ash.logging.AshLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps secrets with a non-exportable AES-256-GCM key in the Android Keystore.
 * StrongBox (Titan M2 on Pixel 7) is used when available, otherwise the TEE.
 */
@Singleton
class KeyManager @Inject constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    @Synchronized
    fun masterKey(): SecretKey {
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return try {
            generate(strongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        } catch (e: StrongBoxUnavailableException) {
            AshLogger.i(TAG, "StrongBox unavailable, using TEE-backed key")
            generate(strongBox = false)
        }
    }

    fun isStrongBoxBacked(): Boolean = runCatching {
        val key = masterKey()
        val factory = javax.crypto.SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE)
        val info = factory.getKeySpec(key, android.security.keystore.KeyInfo::class.java) as android.security.keystore.KeyInfo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
        } else {
            @Suppress("DEPRECATION")
            info.isInsideSecureHardware
        }
    }.getOrDefault(false)

    fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain)
        return byteArrayOf(iv.size.toByte()) + iv + ct
    }

    fun unwrap(blob: ByteArray): ByteArray {
        val ivLen = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val ct = blob.copyOfRange(1 + ivLen, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }

    fun deleteMasterKey() {
        runCatching { keyStore.deleteEntry(ALIAS) }
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply { if (strongBox) setIsStrongBoxBacked(true) }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(spec)
            generateKey()
        }
    }

    companion object {
        private const val TAG = "KeyManager"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "ash_master_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

/**
 * The SQLCipher passphrase: 32 random bytes, stored only in wrapped form under noBackupFilesDir.
 */
@Singleton
class DatabaseKeyProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: KeyManager,
) {
    private fun keyFile(workspace: app.ash.workspace.Workspace) = File(context.noBackupFilesDir, "keys/${workspace.keyFile}")

    /** Each workspace has its own random passphrase, so the two databases are independently encrypted. */
    @Synchronized
    fun passphrase(workspace: app.ash.workspace.Workspace): ByteArray {
        val file = keyFile(workspace)
        if (file.exists()) {
            try {
                return keyManager.unwrap(file.readBytes())
            } catch (e: Exception) {
                // The Keystore key is gone (e.g. device credential reset). The old database is unreadable.
                AshLogger.e(TAG, "Database key for ${workspace.id} could not be unwrapped; starting a fresh encrypted database", e)
                context.deleteDatabase(workspace.dbName)
            }
        }
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        file.parentFile?.mkdirs()
        file.writeBytes(keyManager.wrap(fresh))
        return fresh
    }

    fun destroy(workspace: app.ash.workspace.Workspace) {
        keyFile(workspace).delete()
    }

    private companion object {
        const val TAG = "DbKey"
    }
}
