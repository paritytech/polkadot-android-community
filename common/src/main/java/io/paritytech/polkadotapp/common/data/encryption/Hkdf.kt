package io.paritytech.polkadotapp.common.data.encryption

import io.paritytech.polkadotapp.common.domain.model.AeadKey
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters

// Empty salt/info: the X25519 shared secret has full entropy and the caller provides domain separation.
fun hkdfSha256(inputKeyMaterial: ByteArray): AeadKey {
    val hkdf = HKDFBytesGenerator(SHA256Digest())
    hkdf.init(HKDFParameters(inputKeyMaterial, byteArrayOf(), byteArrayOf()))

    val out = ByteArray(AeadKey.SIZE_BYTES)
    hkdf.generateBytes(out, 0, AeadKey.SIZE_BYTES)

    return AeadKey.fromDerivedBytes(out)
}
