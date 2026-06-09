package io.paritytech.polkadotapp.feature_statement_store_api.domain

import io.paritytech.polkadotapp.common.domain.model.EncodedPublicKey
import java.security.KeyPair

/**
 * Lazily-generated, persisted P-256 keypair identifying this device for multi-device
 * envelope encryption. Distinct from the identity chat keypair.
 */
interface OurDeviceKeypairProvider {
    suspend fun get(): KeyPair

    suspend fun publicKey(): EncodedPublicKey
}
