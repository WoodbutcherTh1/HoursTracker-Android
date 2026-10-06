package com.hourstracker.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Keeps the worker's ID number encrypted with an Android Keystore key (AES-GCM, see [IdCipher]). The key
 * never leaves the Keystore (tests pass their own [cipher]); the ciphertext lives in a private preferences file excluded from every backup.
 */
class SecureIdStore(context: Context, private val cipher: IdCipher? = null) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val keystoreCipher by lazy { IdCipher(::keystoreKey) }
    private val activeCipher: IdCipher get() = cipher ?: keystoreCipher

    fun hasValue(): Boolean = prefs.contains(KEY_VALUE)

    fun read(): String? = prefs.getString(KEY_VALUE, null)?.let { activeCipher.decrypt(it) }

    fun write(value: String) {
        if (value.isEmpty()) {
            prefs.edit { remove(KEY_VALUE) }
        } else {
            prefs.edit { putString(KEY_VALUE, activeCipher.encrypt(value)) }
        }
    }

    private fun keystoreKey(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val FILE = "secure_id"
        const val KEY_VALUE = "id_number"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "hourstracker_id_number"
    }
}
