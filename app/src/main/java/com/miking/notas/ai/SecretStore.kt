package com.miking.notas.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Guarda la API key cifrada en las preferencias privadas de la app.
 *
 * La clave AES vive en el AndroidKeyStore, así que no sale del dispositivo.
 * Solo el texto cifrado se guarda en SharedPreferences. Si el almacén de
 * claves se invalida (por ejemplo tras restaurar el teléfono), el valor se
 * borra y la app vuelve a pedir la key.
 */
internal class SecretStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("notas_secrets", Context.MODE_PRIVATE)

    fun put(key: String, value: String) {
        if (value.isEmpty()) {
            remove(key)
            return
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())

        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted

        prefs.edit {
            putString(key, Base64.encodeToString(payload, Base64.NO_WRAP))
        }
    }

    fun get(key: String): String? {
        val encoded = prefs.getString(key, null) ?: return null

        return try {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            val iv = payload.copyOfRange(0, IV_LENGTH)
            val data = payload.copyOfRange(IV_LENGTH, payload.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))

            String(cipher.doFinal(data), Charsets.UTF_8)
        } catch (error: AEADBadTagException) {
            // La clave del almacén ya no es la misma: el texto cifrado no vale.
            remove(key)
            null
        } catch (error: Exception) {
            // Un fallo puntual del almacén no debe costarle la key al usuario.
            null
        }
    }

    fun remove(key: String) {
        prefs.edit { remove(key) }
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_ALIAS = "notas_ai_api_key"
        const val IV_LENGTH = 12
    }
}
