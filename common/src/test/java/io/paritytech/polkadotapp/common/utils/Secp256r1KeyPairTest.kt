package io.paritytech.polkadotapp.common.utils

import io.novasama.substrate_sdk_android.hash.Hasher.blake2b256
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Test
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.KeyAgreement

class Secp256r1KeyPairTest {
    private val keyGenerator = Secp256r1KeyGenerator()

    @Test
    fun `should generate same shared secrets`() {
        requireBouncyCastle()

        val alicePrivateKey = "i am alice".toByteArray().blake2b256()
        val bobPrivateKey = "i am bob".toByteArray().blake2b256()

        val aliceKeyPair = keyGenerator.createKeyPair(alicePrivateKey)
        val bobKeyPair = keyGenerator.createKeyPair(bobPrivateKey)

        val aliceSharedSecret = generateSharedSecret(aliceKeyPair.private, bobKeyPair.public)
        val bobSharedSecret = generateSharedSecret(bobKeyPair.private, aliceKeyPair.public)

        assert(aliceSharedSecret.contentEquals(bobSharedSecret))
    }

    private fun generateSharedSecret(privateKeyA: PrivateKey, publicKeyB: PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME)
        keyAgreement.init(privateKeyA)
        keyAgreement.doPhase(publicKeyB, true)

        return keyAgreement.generateSecret()
    }
}
