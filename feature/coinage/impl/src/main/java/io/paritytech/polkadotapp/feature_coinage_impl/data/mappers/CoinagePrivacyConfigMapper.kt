package io.paritytech.polkadotapp.feature_coinage_impl.data.mappers

import io.paritytech.polkadotapp.common.utils.Fraction.Companion.percents
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.AnonymityRequirements
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.CoinagePrivacyConfig
import io.paritytech.polkadotapp.feature_coinage_api.domain.recycling.VoucherReadiness
import io.paritytech.polkadotapp.feature_coinage_impl.data.config.CoinagePrivacyConfigRemote
import io.paritytech.polkadotapp.feature_coinage_impl.data.config.PrivacyModeRemote
import kotlin.time.Duration.Companion.minutes

internal fun CoinagePrivacyConfigRemote?.toDomain(): CoinagePrivacyConfig = CoinagePrivacyConfig(
    balanced = this?.balanced.toDomain(CoinagePrivacyConfig.BUNDLED.balanced),
    maxPrivacy = this?.maxPrivacy.toDomain(CoinagePrivacyConfig.BUNDLED.maxPrivacy),
)

private fun PrivacyModeRemote?.toDomain(bundled: VoucherReadiness): VoucherReadiness = VoucherReadiness(
    requiredRingFill = this?.ringFillPercent?.takeIf { it in 0..100 }?.percents ?: bundled.requiredRingFill,
    anonymityRequirements = bundled.anonymityRequirements?.let { bundledAnonymity ->
        AnonymityRequirements(
            anonymityTime = this?.anonymityMinutes?.takeIf { it > 0 }?.minutes ?: bundledAnonymity.anonymityTime,
            anonymityMembers = this?.anonymityMembers?.takeIf { it > 0 } ?: bundledAnonymity.anonymityMembers,
        )
    },
)
