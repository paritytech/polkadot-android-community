package io.paritytech.polkadotapp.feature_account_api.domain.model

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext

data class AliasAccountDerivationOverride(
    val context: BandersnatchContext,
    val derivationPath: String
)
