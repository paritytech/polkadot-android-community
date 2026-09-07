package io.paritytech.polkadotapp.feature_coinage_api.domain.usecase

import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageTransferDetection
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageOperationGroupId
import io.paritytech.polkadotapp.feature_transactions.api.domain.model.TransactionSignerSource
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Onboards an amount into the recycler as a batch of vouchers signed by a signer, via the
 * `load_recycler_with_external_asset_unpaid` call — fees are free for any account, even empty ones.
 */
interface OnboardingUseCase {
    /**
     * One attempt, reported only as whether it was registered. What becomes of each voucher afterwards is
     * left to the recovery pass, and nothing retries the ones that fail.
     *
     * For callers that onboard the same money again on their own terms — a faucet tap, a deposit the
     * converter re-observes. Anything that must onboard a given sum exactly once belongs on [onboardDurably].
     */
    suspend fun onboard(
        amount: BigDecimal,
        signerSource: TransactionSignerSource.Signed,
    ): Result<Unit>

    /**
     * Reports [CoinageTransferDetection.Claimed] as soon as every voucher is in a block, and keeps emitting
     * until the onboarding is over — so a fork that takes an inclusion away is reported too, as a return to
     * [CoinageTransferDetection.Claiming].
     *
     * Onboarding is not one-shot. A voucher whose transaction failed minted nothing, so the money it stood
     * for is still in [signerSource]'s account and still owed; the shortfall is submitted again as soon as
     * the account can cover it. Fresh vouchers each time — the ledger refuses an output any entry has
     * already minted, so a retry cannot re-offer the ones the failed attempt registered.
     *
     * The flow completes when every denomination has a finalized voucher, or when [retryUntil] has passed.
     * Unlike a claim, a closed window is the end of it even on a first attempt: the funds are the caller's
     * own and nothing else will spend them, so a late attempt would be a second charge against money the
     * caller has already written off rather than money nobody else would collect.
     *
     * Because it ends only when nothing further will be attempted, completion is what tells a caller the
     * onboarding is finished. No status emitted along the way means that, a partial
     * [CoinageTransferDetection.Claimed] least of all.
     *
     * [groupId] must be derived from something stable about the operation — the one-time key the funds sit
     * on, say — because a second call with the same id rejoins the vouchers already registered instead of
     * onboarding the amount again. That is what makes a retry after process death safe.
     */
    @OptIn(ExperimentalTime::class)
    fun onboardDurably(
        amount: BigDecimal,
        signerSource: TransactionSignerSource.Signed,
        groupId: CoinageOperationGroupId,
        retryUntil: Instant,
    ): Flow<CoinageTransferDetection>
}
