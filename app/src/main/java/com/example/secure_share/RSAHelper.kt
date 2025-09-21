package com.example.secure_share

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

object RSAHelper {

    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val RSA_ALGORITHM = "RSA/ECB/PKCS1Padding"
    private const val RSA_KEY_SIZE = 2048

    private fun getAliasForUser(uid: String): String {
        return "rsa_keypair_$uid"
    }

    fun generateKeyPair(uid: String): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val alias = getAliasForUser(uid)

            if (keyStore.containsAlias(alias)) return true

            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, KEYSTORE_PROVIDER
            )

            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(RSA_KEY_SIZE)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .build()

            keyPairGenerator.initialize(spec)
            keyPairGenerator.generateKeyPair()
            true
        } catch (e: Exception) {
            Log.e("RSAHelper", "KeyPair generation failed", e)
            false
        }
    }

    fun getPrivateKey(uid: String): PrivateKey? {
        return try {
            val alias = getAliasForUser(uid)
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            entry?.privateKey
        } catch (e: Exception) {
            Log.e("RSAHelper", "Private key retrieval failed", e)
            null
        }
    }

    fun getPublicKeyBase64(uid: String): String? {
        return try {
            val alias = getAliasForUser(uid)
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val entry = keyStore.getCertificate(alias)
            val publicKeyBytes = entry?.publicKey?.encoded ?: return null
            Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("RSAHelper", "Public key retrieval failed", e)
            null
        }
    }

    fun decodePublicKey(base64: String): PublicKey? {
        return try {
            val keyBytes = Base64.decode(base64, Base64.NO_WRAP)
            val spec = X509EncodedKeySpec(keyBytes)
            KeyFactory.getInstance("RSA").generatePublic(spec)
        } catch (e: Exception) {
            Log.e("RSAHelper", "Public key decoding failed", e)
            null
        }
    }

    fun encryptAESKey(secretKey: SecretKey, publicKey: PublicKey): String {
        return try {
            val cipher = Cipher.getInstance(RSA_ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val encrypted = cipher.doFinal(secretKey.encoded)
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e("RSAHelper", "AES key encryption failed", e)
            ""
        }
    }

    fun decryptAESKey(base64EncryptedKey: String, privateKey: PrivateKey): SecretKey {
        try {
            val encryptedBytes = Base64.decode(base64EncryptedKey, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(RSA_ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            val decrypted = cipher.doFinal(encryptedBytes)
            return SecretKeySpec(decrypted, 0, decrypted.size, "AES")
        } catch (e: Exception) {
            Log.e("RSAHelper", "AES key decryption failed. Input: $base64EncryptedKey", e)
            throw SecurityException("Decryption failed", e)
        }
    }

    fun regenerateKeys(uid: String): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val alias = getAliasForUser(uid)
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
            generateKeyPair(uid)
        } catch (e: Exception) {
            Log.e("RSAHelper", "Key regeneration failed", e)
            false
        }
    }

    fun getPublicKeyFingerprint(uid: String): String? {
        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            val certificate = keyStore.getCertificate(getAliasForUser(uid)) ?: return null
            val pubKeyBytes = certificate.publicKey.encoded
            val digest = MessageDigest.getInstance("SHA-256").digest(pubKeyBytes)
            digest.joinToString("") { "%02x".format(it) } // hex fingerprint
        } catch (e: Exception) {
            Log.e("RSAHelper", "Failed to get fingerprint", e)
            null
        }
    }

}
