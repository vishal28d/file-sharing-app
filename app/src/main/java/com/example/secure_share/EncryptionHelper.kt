package com.example.secure_share

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.*
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionHelper {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "secure_share_aes_key"
    private const val GCM_TAG_LENGTH = 128
    const val IV_LENGTH: Int = 12
    private const val AES_KEY_SIZE = 256

    /**
     * Generates a random AES key for single-file encryption
     */
    fun generateAESKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(AES_KEY_SIZE)
        return keyGen.generateKey()
    }

    /**
     * Generates and stores a secure AES key in Android KeyStore
     */
    fun generateAndStoreKey(context: Context): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }

        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_SIZE)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Retrieves the secret key from Android KeyStore
     */
    fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
                load(null)
            }

            (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Encrypts input stream using AES-GCM
     * @return Combined IV + ciphertext
     */
    @Throws(IOException::class, IllegalStateException::class)
    fun encryptStream(input: InputStream, secretKey: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)

        val output = ByteArrayOutputStream()
        output.write(iv) // Prepend IV

        CipherOutputStream(output, cipher).use { cos ->
            input.use { it.copyTo(cos) }
        }

        return output.toByteArray()
    }

    /**
     * Decrypts combined IV + ciphertext
     */
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    fun decryptData(combinedData: ByteArray, secretKey: SecretKey): ByteArray {
        require(combinedData.size > IV_LENGTH) { "Invalid data length" }

        val iv = combinedData.copyOfRange(0, IV_LENGTH)
        val ciphertext = combinedData.copyOfRange(IV_LENGTH, combinedData.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)

        return cipher.doFinal(ciphertext)
    }

    /**
     * Encrypts plaintext byte array
     */
    @Throws(IllegalStateException::class)
    fun encryptData(plaintext: ByteArray, secretKey: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)

        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext
    }
}
