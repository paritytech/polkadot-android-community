package io.paritytech.polkadotapp.feature_members_api.data.model

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchDomainSize
import kotlinx.serialization.Serializable

@Serializable
enum class RingExponent {
    R2e9,
    R2e10,
    R2e14
}

fun RingExponent.toDomainSize(): BandersnatchDomainSize {
    return when (this) {
        RingExponent.R2e9 -> BandersnatchDomainSize.Domain11
        RingExponent.R2e10 -> BandersnatchDomainSize.Domain12
        RingExponent.R2e14 -> BandersnatchDomainSize.Domain16
    }
}
