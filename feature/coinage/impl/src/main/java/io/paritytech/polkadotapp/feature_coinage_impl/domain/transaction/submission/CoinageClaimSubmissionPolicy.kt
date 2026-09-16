package io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.submission

import io.novasama.substrate_sdk_android.encrypt.keypair.Keypair
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.common.data.time.TimeProvider
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.flatten
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.deriveKeypair
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.CoinageAssetLedger
import io.paritytech.polkadotapp.feature_coinage_impl.data.transaction.EntryAssets
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogE
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogI
import io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies.builders.ClaimExtrinsicBuilder
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.AsyncDurableSubmissionPolicy
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableFailureKind
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxEntry
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.ScheduledDurableTx
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.SubmissionPreparation
import javax.inject.Inject
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Builds a claim again into the coin its first attempt recorded. Minting into the same coin is what lets a
 * payment we already made out of that coin keep waiting on it, instead of watching a coin that will never exist.
 */
@OptIn(ExperimentalTime::class)
class CoinageClaimSubmissionPolicy @Inject constructor(
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val assetLedger: CoinageAssetLedger,
    private val coinRepository: CoinRepository,
    private val claimExtrinsicBuilder: ClaimExtrinsicBuilder,
    private val timeProvider: TimeProvider,
) : AsyncDurableSubmissionPolicy {
    override val chainId: ChainId get() = chainAssetProvider.chainId()

    /**
     * The failure decides: see [retryableFailure]. Whether the peer's coin is still there depends on the chain,
     * which only [prepareSubmission] may read.
     */
    override suspend fun canRetry(entry: DurableTxEntry, params: DataByteArray, failure: DurableFailureKind): Boolean {
        val claim = CoinageSubmissionParams.decodeClaim(params).getOrNull() ?: return false

        return retryableFailure(failure, timeProvider.now(), claim.retryUntil)
    }

    override suspend fun prepareSubmission(
        transactions: List<ScheduledDurableTx>,
    ): Result<Map<DurableTxId, SubmissionPreparation>> = runCancellableCatching {
        val assets = assetLedger.assetsOf(transactions.map { it.id }).getOrElse { return@runCancellableCatching Result.failure(it) }
        val (claims, unbuildable) = resolveClaims(transactions, assets)
        val gaveUp = unbuildable.associateWith { SubmissionPreparation.GiveUp }

        if (claims.isEmpty()) return@runCancellableCatching Result.success(gaveUp)

        val look = awaitInputs(
            presence = coinRepository.subscribeCoinPresence(chainId, claims.map { it.source }),
            wanted = claims.mapTo(mutableSetOf()) { it.source },
            earliestDeadline = claims.minOfOrNull { it.retryUntil } ?: timeProvider.now(),
            timeProvider = timeProvider,
        )

        val chain = chainAssetProvider.chain()

        val decided = claims.mapNotNull { claim ->
            when {
                claim.source in look.present -> {
                    val extrinsic = claimExtrinsicBuilder.build(chain, claim.keypair, claim.destination)
                        .getOrElse { return@runCancellableCatching Result.failure(it) }
                    claim.id to SubmissionPreparation.Ready(extrinsic)
                }

                look.abandoned(claim.source, claim.retryUntil) -> {
                    coinageLogI("claim-rebuild-abandoned entry=${claim.id.value} until=${claim.retryUntil}")
                    claim.id to SubmissionPreparation.GiveUp
                }

                else -> null
            }
        }

        Result.success(decided.toMap() + gaveUp)
    }.flatten()

    private fun resolveClaims(
        transactions: List<ScheduledDurableTx>,
        assets: Map<DurableTxId, EntryAssets>,
    ): Pair<List<Claim>, List<DurableTxId>> {
        val claims = mutableListOf<Claim>()
        val unbuildable = mutableListOf<DurableTxId>()

        transactions.forEach { tx ->
            val destination = assets[tx.id]?.outputs?.singleOrNull()?.publicKey
            val params = CoinageSubmissionParams.decodeClaim(tx.policy.params).getOrNull()

            if (destination == null || params == null) {
                // Nothing recorded can ever make this buildable, so waiting on it would only hold its lock.
                coinageLogE("claim-rebuild-impossible entry=${tx.id.value} reason=ledger-or-params-unreadable")
                unbuildable += tx.id
            } else {
                val keypair = params.receivedKey.deriveKeypair()
                claims += Claim(tx.id, keypair, keypair.publicKey.toDataByteArray(), destination, params.retryUntil)
            }
        }

        return claims to unbuildable
    }

    private class Claim(
        val id: DurableTxId,
        val keypair: Keypair,
        val source: AccountId,
        val destination: AccountId,
        val retryUntil: Instant,
    )
}
