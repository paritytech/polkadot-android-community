package io.paritytech.polkadotapp.feature_products_impl.domain.topUpRequest

import io.novasama.substrate_sdk_android.extensions.toHexString
import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.chains.util.amountFromPlanks
import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.common.utils.blake2b256
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinPrivateKey
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageTransferDetection
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageOperationGroupId
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.ClaimReceivedCoinsUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.OnboardingUseCase
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

private const val COINAGE_LOG_TAG = "CoinageTransfer"

/** Outcome of a successful top-up claim. */
sealed interface TopUpClaimResult {
    /** The claimed funds matched the amount the product stated. */
    data object Exact : TopUpClaimResult

    /** A Coins top-up whose detected on-chain total [credited] differs from the stated amount. */
    data class Partial(val credited: Balance) : TopUpClaimResult
}

/** How long a product is kept waiting for its coins, and how long claiming may retry within that. */
private val CLAIM_TIMEOUT = 60.seconds

interface ExecuteTopUpUseCase {
    /**
     * Performs the top-up: onboards via the signer, or moves the coins into the user's coin set.
     * For a [TopUpSource.Coins] top-up, reports [TopUpClaimResult.Partial] when the
     * detected on-chain total differs from [amount]; otherwise [TopUpClaimResult.Exact].
     */
    suspend fun claim(source: TopUpSource, amount: Balance): Result<TopUpClaimResult>
}

@OptIn(ExperimentalTime::class)
class RealExecuteTopUpUseCase @Inject constructor(
    private val claimReceivedCoinsUseCase: ClaimReceivedCoinsUseCase,
    private val onboardingUseCase: OnboardingUseCase,
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val timeProvider: TimeProvider,
) : ExecuteTopUpUseCase {
    override suspend fun claim(source: TopUpSource, amount: Balance): Result<TopUpClaimResult> =
        when (source) {
            is TopUpSource.Onboard -> {
                val decimalAmount = chainAssetProvider.asset().amountFromPlanks(amount)

                Timber.tag(COINAGE_LOG_TAG).i("Top-up starting source=onboard amount=$amount")

                onboardingUseCase.onboard(decimalAmount, source.signerSource)
                    .onSuccess { Timber.tag(COINAGE_LOG_TAG).i("Top-up succeeded source=onboard amount=$amount") }
                    .onFailure { Timber.tag(COINAGE_LOG_TAG).e(it, "Top-up failed source=onboard amount=$amount") }
                    .map { TopUpClaimResult.Exact }
            }

            is TopUpSource.Coins -> claimCoins(source.coinKeys, amount)
        }

    private suspend fun claimCoins(coinKeys: List<CoinPrivateKey>, expectedAmount: Balance): Result<TopUpClaimResult> {
        val groupId = claimGroupOf(coinKeys)

        Timber.tag(COINAGE_LOG_TAG)
            .i("Top-up starting source=coins group=${groupId.value} coins=${coinKeys.size} expected=$expectedAmount")

        return runCatching {
            var latest: CoinageTransferDetection = CoinageTransferDetection.Detecting

            // Claiming keeps going for as long as a coin is visible on chain, which is right for money left
            // in a chat and wrong here: a product is blocked on this call. So the wait is bounded on this
            // side, and claiming is given that same span to retry within.
            val settled = withTimeoutOrNull(CLAIM_TIMEOUT) {
                claimReceivedCoinsUseCase.claim(coinKeys, groupId, timeProvider.now() + CLAIM_TIMEOUT)
                    .onEach { latest = it }
                    // The first arrival, not the last word: the coins are ours once a claim is in a block,
                    // and waiting for finality would hold the top-up open for tens of seconds after the
                    // money landed.
                    .first { it.isSettled() }
            }

            when (val outcome = settled ?: latest) {
                is CoinageTransferDetection.Claimed -> outcome.amount.asClaimResult(expectedAmount, groupId)

                // Whatever landed is the user's, whether claiming finished or we merely stopped waiting on
                // it. Calling a top-up that moved most of the money an outright failure would be worse.
                is CoinageTransferDetection.ClaimedPartially -> outcome.claimed.asClaimResult(expectedAmount, groupId)
                is CoinageTransferDetection.ClaimingRest -> outcome.claimed.asClaimResult(expectedAmount, groupId)

                else -> error("Failed to move coins into the user's coin set: $outcome")
            }
        }.onFailure {
            Timber.tag(COINAGE_LOG_TAG).e(it, "Top-up failed source=coins group=${groupId.value}")
        }
    }
}

/**
 * Content-addressed, because a top-up carries no identity of its own that survives a retry. The same set of
 * keys always names the same group, so a second attempt rejoins the claims the first one registered.
 */
private fun CoinageTransferDetection.isSettled(): Boolean =
    this is CoinageTransferDetection.Claimed ||
        this is CoinageTransferDetection.ClaimedPartially ||
        this is CoinageTransferDetection.NotClaimed

private fun Balance.asClaimResult(expectedAmount: Balance, groupId: CoinageOperationGroupId): TopUpClaimResult {
    return if (this < expectedAmount) {
        Timber.tag(COINAGE_LOG_TAG)
            .w("Top-up credited less than expected group=${groupId.value} credited=$this expected=$expectedAmount")

        TopUpClaimResult.Partial(this)
    } else {
        Timber.tag(COINAGE_LOG_TAG).i("Top-up succeeded source=coins group=${groupId.value} credited=$this")

        TopUpClaimResult.Exact
    }
}

private fun claimGroupOf(coinKeys: List<CoinPrivateKey>): CoinageOperationGroupId {
    val keys = coinKeys.map { it.value.toHexString() }.sorted().joinToString(separator = "")

    return CoinageOperationGroupId("top-up:${keys.encodeToByteArray().blake2b256().toHexString()}")
}
