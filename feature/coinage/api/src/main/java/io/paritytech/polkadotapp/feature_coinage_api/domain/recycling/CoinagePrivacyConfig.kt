package io.paritytech.polkadotapp.feature_coinage_api.domain.recycling

import io.paritytech.polkadotapp.common.utils.Fraction.Companion.percents
import kotlin.time.Duration.Companion.minutes

data class CoinagePrivacyConfig(
    val balanced: VoucherReadiness,
    val maxPrivacy: VoucherReadiness,
) {
    companion object {
        /** What the presets use until a deployment publishes its own. */
        val BUNDLED = CoinagePrivacyConfig(
            balanced = VoucherReadiness(
                requiredRingFill = 20.percents,
                anonymityRequirements = AnonymityRequirements(anonymityTime = 10.minutes, anonymityMembers = 8),
            ),
            maxPrivacy = VoucherReadiness(
                requiredRingFill = 90.percents,
                anonymityRequirements = AnonymityRequirements(anonymityTime = 10.minutes, anonymityMembers = 16),
            ),
        )
    }
}
