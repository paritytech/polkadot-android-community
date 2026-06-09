package io.paritytech.polkadotapp.common.data.encryption

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface MessageEncryption {
    companion object;

    fun encrypt(plainMessage: ByteArray): ByteArray
    fun decrypt(encrypted: ByteArray): ByteArray
}

fun MessageEncryption.Companion.aes(aesKey: ByteArray): MessageEncryption {
    return AesGcmMessageEncryption(aesKey)
}

private class AesGcmMessageEncryption(aesKey: ByteArray) : MessageEncryption {
    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }

    private val keySpec = SecretKeySpec(aesKey, "AES")

    override fun encrypt(plainMessage: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).apply { SecureRandom().nextBytes(this) }
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val cipherText = cipher.doFinal(plainMessage)

        return iv + cipherText
    }

    override fun decrypt(encrypted: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = encrypted.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val cipherText = encrypted.copyOfRange(GCM_IV_LENGTH_BYTES, encrypted.size)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)

        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        return cipher.doFinal(cipherText)
    }
}
