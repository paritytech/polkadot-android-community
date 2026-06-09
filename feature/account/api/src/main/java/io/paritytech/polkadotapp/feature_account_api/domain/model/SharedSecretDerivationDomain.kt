package io.paritytech.polkadotapp.feature_account_api.domain.model

@JvmInline
value class SharedSecretDerivationDomain(val derivationPath: String) {
    companion object {
        val CHAT = SharedSecretDerivationDomain("//wallet//chat")
        val CANDIDATE = SharedSecretDerivationDomain("//candidate//popCandidate")
    }
}
