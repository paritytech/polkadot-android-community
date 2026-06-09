package io.paritytech.polkadotapp.common.utils

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.KeyAgreement

/**
 * P-256 ECDH shared secret derivation.
 */
fun ecdhSharedSecret(localPrivateKey: PrivateKey, remotePublicKey: PublicKey): ByteArray {
    val keyAgreement = KeyAgreement.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME)
    keyAgreement.init(localPrivateKey)
    keyAgreement.doPhase(remotePublicKey, true)
    return keyAgreement.generateSecret()
}
