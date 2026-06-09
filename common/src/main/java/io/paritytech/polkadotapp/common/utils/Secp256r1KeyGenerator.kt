package io.paritytech.polkadotapp.common.utils

import io.paritytech.polkadotapp.common.domain.model.EncodedPrivateKey
import io.paritytech.polkadotapp.common.domain.model.EncodedPublicKey
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.interfaces.ECPrivateKey
import org.bouncycastle.jce.interfaces.ECPublicKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECPrivateKeySpec
import org.bouncycastle.jce.spec.ECPublicKeySpec
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Secp256r1KeyGenerator @Inject constructor() {
    private val secp256r1Spec by lazy {
        requireBouncyCastle()
        ECNamedCurveTable.getParameterSpec("secp256r1")
    }

    private val ecKeyFactory by lazy {
        requireBouncyCastle()
        KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
    }

    fun generateRandomKeypair(): KeyPair {
        requireBouncyCastle()
        val keyPairGenerator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        return keyPairGenerator.generateKeyPair()
    }

    fun createPrivateKey(privateKeyRaw: ByteArray): PrivateKey {
        val d = BigInteger(1, privateKeyRaw)

        val keySpec = ECPrivateKeySpec(d, secp256r1Spec)
        return ecKeyFactory.generatePrivate(keySpec)
    }

    fun derivePublicKey(publicKeyRaw: ByteArray): PublicKey {
        val point = secp256r1Spec.curve.decodePoint(publicKeyRaw)
        val pubSpec = ECPublicKeySpec(point, secp256r1Spec)

        return ecKeyFactory.generatePublic(pubSpec)
    }

    fun createKeyPair(privateKeyRaw: ByteArray): KeyPair {
        val d = BigInteger(1, privateKeyRaw)

        val privateKeySpec = ECPrivateKeySpec(d, secp256r1Spec)
        val privateKey = ecKeyFactory.generatePrivate(privateKeySpec)

        val q = secp256r1Spec.g.multiply(d).normalize()
        val publicKeySpec = ECPublicKeySpec(q, secp256r1Spec)
        val publicKey = ecKeyFactory.generatePublic(publicKeySpec)

        return KeyPair(publicKey, privateKey)
    }

    fun encode(publicKey: PublicKey): EncodedPublicKey {
        val ecPublicKey = publicKey as ECPublicKey
        val value = ecPublicKey.q.getEncoded(false)
        return EncodedPublicKey(value)
    }

    fun encodePrivate(privateKey: PrivateKey): EncodedPrivateKey {
        val ecPrivateKey = privateKey as ECPrivateKey
        return ecPrivateKey.d.toFixedLengthUnsignedByteArray(PRIVATE_KEY_BYTES)
            .toDataByteArray()
    }

    private companion object {
        const val PRIVATE_KEY_BYTES = 32
    }
}

/**
 * Encodes a non-negative [BigInteger] as a fixed-length unsigned big-endian byte array.
 *
 * [BigInteger.toByteArray] uses signed two's complement, so for unsigned values it can either
 * prepend a 0x00 sign byte (when MSB ≥ 0x80) or omit leading zero bytes — both producing wrong
 * lengths for crypto contexts that expect a fixed encoding.
 */
private fun BigInteger.toFixedLengthUnsignedByteArray(length: Int): ByteArray {
    require(signum() >= 0) { "Cannot encode a negative value as unsigned" }

    val raw = toByteArray()
    return when {
        raw.size == length -> raw
        raw.size == length + 1 && raw[0] == 0.toByte() -> raw.copyOfRange(1, raw.size)
        raw.size < length -> ByteArray(length - raw.size) + raw
        else -> error("Value does not fit into $length bytes: ${raw.size} bytes (incl. sign byte)")
    }
}
