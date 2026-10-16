package io.paritytech.polkadotapp.feature_coinage_api.domain.usecase

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.feature_coinage_api.domain.transaction.model.CoinageOperationGroupId
import kotlinx.coroutines.flow.Flow

/**
 * Takes back coins whose keys we handed to a peer and that the peer has not claimed yet, each into a fresh coin
 * of ours.
 *
 * The peer still holds the keys, so this races their claim: whichever transaction lands first wins, and a coin
 * the peer takes meanwhile stays theirs.
 */
interface ReclaimHandedOffCoinsUseCase {
    /**
     * Schedules a reclaim of every coin among [coins] that is still on chain and not already being reclaimed,
     * and returns once it is recorded — building and submitting are the engine's. Progress shows up in
     * [CoinagePaymentStatusUseCase] as [CoinagePaymentStatus.Reclaiming] and [CoinagePaymentStatus.Reclaimed].
     *
     * [groupId] must be derived from something stable about the payment: [subscribeFailedReclaims] reads the
     * attempts back from it.
     */
    suspend fun reclaim(coins: List<AccountId>, groupId: CoinageOperationGroupId): Result<Unit>

    /** Coins whose reclaim under [groupId] failed for good, with no later attempt still working on them. */
    fun subscribeFailedReclaims(groupId: CoinageOperationGroupId): Flow<Set<AccountId>>
}
