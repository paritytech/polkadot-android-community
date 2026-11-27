package io.paritytech.polkadotapp.feature_products_impl.domain.signTransaction

import io.paritytech.polkadotapp.feature_products_api.model.signing.SigningAccount

fun SigningAccount.requiresDeviceAuthentication(): Boolean = when (this) {
    is SigningAccount.Product -> false
    SigningAccount.IdentityAccount -> true
}
