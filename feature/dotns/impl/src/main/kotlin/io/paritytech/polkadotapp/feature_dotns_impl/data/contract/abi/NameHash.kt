package io.paritytech.polkadotapp.feature_dotns_impl.data.contract.abi

import org.bouncycastle.jcajce.provider.digest.Keccak

object NameHash {
    fun nameHash(name: String): ByteArray {
        if (name.isEmpty()) return ByteArray(32)

        var node = ByteArray(32)

        val labels = name.split(".")
        for (label in labels.reversed()) {
            val labelHash = keccak256(label.toByteArray(Charsets.UTF_8))
            node = keccak256(node + labelHash)
        }

        return node
    }

    private fun keccak256(input: ByteArray): ByteArray {
        return Keccak.Digest256().digest(input)
    }
}
