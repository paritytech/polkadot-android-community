package io.paritytech.polkadotapp.feature_statement_store_impl.data.encryption

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters

internal const val AES_KEY_SIZE_BYTES = 32

// Empty salt/info: P-256 ECDH output has full entropy and the caller provides domain separation.
internal fun hkdfSha256(ikm: ByteArray, outputBytes: Int = AES_KEY_SIZE_BYTES): ByteArray {
    val hkdf = HKDFBytesGenerator(SHA256Digest())
    hkdf.init(HKDFParameters(ikm, byteArrayOf(), byteArrayOf()))
    val out = ByteArray(outputBytes)
    hkdf.generateBytes(out, 0, outputBytes)
    return out
}
