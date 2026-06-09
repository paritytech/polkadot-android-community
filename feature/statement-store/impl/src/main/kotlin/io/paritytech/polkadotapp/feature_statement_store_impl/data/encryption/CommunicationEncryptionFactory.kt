package io.paritytech.polkadotapp.feature_statement_store_impl.data.encryption

import io.paritytech.polkadotapp.common.domain.model.EncodedPublicKey
import io.paritytech.polkadotapp.common.utils.Secp256r1KeyGenerator
import io.paritytech.polkadotapp.feature_account_api.domain.model.SharedSecretDerivationDomain
import io.paritytech.polkadotapp.feature_account_api.domain.usecase.SharedSecretDerivationUseCase
import io.paritytech.polkadotapp.feature_statement_store_api.data.encryption.CommunicationEncryption
import io.paritytech.polkadotapp.feature_statement_store_api.domain.OurDeviceKeypairProvider
import java.security.KeyPair
import javax.inject.Inject

class CommunicationEncryptionFactory @Inject constructor(
    private val sharedSecretDerivationUseCase: SharedSecretDerivationUseCase,
    private val keyGenerator: Secp256r1KeyGenerator,
    private val ourDeviceKeypairProvider: OurDeviceKeypairProvider,
) : CommunicationEncryption.Factory {
    override suspend fun createWithDeviceKeypair(peerPublicKey: EncodedPublicKey): CommunicationEncryption {
        return createEncryption(
            localKeypair = ourDeviceKeypairProvider.get(),
            peerPublicKey = peerPublicKey,
        )
    }

    override suspend fun create(
        domain: SharedSecretDerivationDomain,
        peerPublicKey: EncodedPublicKey,
    ): CommunicationEncryption {
        return createEncryption(
            localKeypair = sharedSecretDerivationUseCase.deriveForDomain(domain),
            peerPublicKey = peerPublicKey
        )
    }

    override suspend fun createOneTimeUse(peerPublicKey: EncodedPublicKey): CommunicationEncryption {
        return createEncryption(
            localKeypair = sharedSecretDerivationUseCase.generateOneTimeUse(),
            peerPublicKey = peerPublicKey
        )
    }

    override suspend fun createEncryption(
        localKeypair: KeyPair,
        peerPublicKey: EncodedPublicKey,
    ): CommunicationEncryption {
        return RealCommunicationEncryption(
            localPublicKey = keyGenerator.encode(localKeypair.public),
            localKeypair = localKeypair,
            remotePublicKey = keyGenerator.derivePublicKey(peerPublicKey.value)
        )
    }
}
