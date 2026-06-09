package io.paritytech.polkadotapp.feature_coinage_api.domain.usecase

import io.paritytech.polkadotapp.feature_transactions.api.domain.model.TransactionSignerSource
import java.math.BigDecimal

/**
 * Onboards [amount] into the recycler as a batch of vouchers signed by [signerSource], via the
 * `load_recycler_with_external_asset_unpaid` call — fees are free for any account, even empty ones.
 */
interface OnboardingUseCase {
    suspend fun onboard(
        amount: BigDecimal,
        signerSource: TransactionSignerSource.Signed,
    ): Result<Unit>
}
