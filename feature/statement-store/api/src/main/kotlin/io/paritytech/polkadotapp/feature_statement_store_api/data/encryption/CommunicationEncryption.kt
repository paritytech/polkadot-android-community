package io.paritytech.polkadotapp.feature_statement_store_api.data.encryption

import io.paritytech.polkadotapp.common.domain.model.EncodedPublicKey
import io.paritytech.polkadotapp.feature_account_api.domain.model.SharedSecretDerivationDomain
import java.security.KeyPair

interface CommunicationEncryption {
    interface Factory {
        suspend fun create(
            domain: SharedSecretDerivationDomain,
            peerPublicKey: EncodedPublicKey,
        ): CommunicationEncryption

        suspend fun createOneTimeUse(
            peerPublicKey: EncodedPublicKey,
        ): CommunicationEncryption

        /**
         * Builds a [CommunicationEncryption] with the provided [localKeypair] as the local
         * side. Used when the local side is something other than a wallet-seed-derived
         * keypair — e.g. inter-own-device sync uses the device's own P-256 keypair from
         * [io.paritytech.polkadotapp.feature_statement_store_api.domain.OurDeviceKeypairProvider].
         */
        suspend fun createEncryption(
            localKeypair: KeyPair,
            peerPublicKey: EncodedPublicKey,
        ): CommunicationEncryption

        suspend fun createWithDeviceKeypair(
            peerPublicKey: EncodedPublicKey,
        ): CommunicationEncryption
    }

    val sharedSecret: ByteArray

    val localPublicKey: EncodedPublicKey

    fun encrypt(message: ByteArray): ByteArray

    fun decrypt(encryptedMessage: ByteArray): ByteArray
}
