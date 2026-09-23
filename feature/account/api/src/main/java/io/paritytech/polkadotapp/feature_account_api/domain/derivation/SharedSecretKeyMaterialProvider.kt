package io.paritytech.polkadotapp.feature_account_api.domain.derivation

import io.paritytech.polkadotapp.feature_account_api.domain.model.SharedSecretDerivationDomain

/**
 * Supplies the 32-byte X25519 private key material of one [SharedSecretDerivationDomain].
 *
 * Contributed by the module that owns the scheme. A domain without a provider keeps the RFC-0022 `ecdh`
 * tree, so `account` never has to know which scheme a feature derives its key from.
 */
interface SharedSecretKeyMaterialProvider {
    val domain: SharedSecretDerivationDomain

    suspend fun deriveKeyMaterial(): Result<ByteArray>
}
