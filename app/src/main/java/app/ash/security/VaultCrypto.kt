package app.ash.security

import android.content.Context
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streaming authenticated encryption (AES-256-GCM-HKDF, 1 MB segments) for vault files.
 * The Tink keyset is itself encrypted by a Keystore key, so files are useless off-device.
 */
@Singleton
class VaultCrypto @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val streamingAead: StreamingAead by lazy {
        StreamingAeadConfig.register()
        val handle = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, PREFS_NAME)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM_HKDF_1MB"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
        handle.getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)
    }

    /** Associated data binds each ciphertext to its file name, so files cannot be swapped. */
    fun encryptingStream(target: OutputStream, fileName: String): OutputStream =
        streamingAead.newEncryptingStream(target, fileName.toByteArray())

    fun decryptingStream(source: InputStream, fileName: String): InputStream =
        streamingAead.newDecryptingStream(source, fileName.toByteArray())

    fun destroyKeys() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.deleteSharedPreferences(PREFS_NAME)
        runCatching {
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("ash_vault_master")
        }
    }

    private companion object {
        const val KEYSET_NAME = "ash_vault_keyset"
        const val PREFS_NAME = "ash_vault_keys"
        const val MASTER_KEY_URI = "android-keystore://ash_vault_master"
    }
}
