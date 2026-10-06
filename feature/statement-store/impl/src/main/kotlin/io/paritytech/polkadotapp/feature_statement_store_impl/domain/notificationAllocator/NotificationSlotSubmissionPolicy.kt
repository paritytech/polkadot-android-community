package io.paritytech.polkadotapp.feature_statement_store_impl.domain.notificationAllocator

import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.flatten
import io.paritytech.polkadotapp.common.utils.flattenResult
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_chain_resources_api.data.api.resourcesCalls
import io.paritytech.polkadotapp.feature_chain_resources_api.data.api.setNotificationStatementAccountForSequence
import io.paritytech.polkadotapp.feature_chain_resources_api.data.model.NotificationReference
import io.paritytech.polkadotapp.feature_people_api.domain.PeopleCheckMemberInRingUseCase
import io.paritytech.polkadotapp.feature_people_api.domain.useCase.ActivePeopleCollectionUseCase
import io.paritytech.polkadotapp.feature_statement_store_impl.data.signer.origins.StatementStoreOrigins
import io.paritytech.polkadotapp.feature_statement_store_impl.domain.slotAllocator.AllocateContext
import io.paritytech.polkadotapp.feature_statement_store_impl.domain.slotAllocator.AllocateContextResolver
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicService
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.AsyncDurableSubmissionPolicy
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableFailureKind
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxEntry
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DurableTxId
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.ScheduledDurableTx
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.SubmissionPreparation
import timber.log.Timber
import javax.inject.Inject

/**
 * Builds notification slot claims. Every build picks the first free seq of the current period, so a claim that
 * collided on its seq, outlived its period or expired lands on a different slot when built again.
 */
class NotificationSlotSubmissionPolicy @Inject constructor(
    private val knownChains: KnownChains,
    private val contextResolver: AllocateContextResolver,
    private val seqPicker: NotificationSeqPicker,
    private val reservations: NotificationSeqReservations,
    private val allocationLock: NotificationSlotAllocationLock,
    private val statementStoreOrigins: StatementStoreOrigins,
    private val extrinsicService: ExtrinsicService,
    private val memberInRingUseCase: PeopleCheckMemberInRingUseCase,
    private val activePeopleCollectionUseCase: ActivePeopleCollectionUseCase,
) : AsyncDurableSubmissionPolicy {
    override val chainId: ChainId get() = knownChains.people

    // Exception to transactions.md rule 11: a rebuild re-picks period and seq, so no failure kind repeats on the
    // same effects. The only way out is GiveUp from prepareSubmission when no slot is free.
    override suspend fun canRetry(entry: DurableTxEntry, params: DataByteArray, failure: DurableFailureKind): Boolean {
        Timber.i("Notification slot claim ${entry.id.value} failed with $failure; rebuilding on a fresh seq")
        return true
    }

    override suspend fun prepareSubmission(
        transactions: List<ScheduledDurableTx>,
    ): Result<Map<DurableTxId, SubmissionPreparation>> = runCancellableCatching {
        awaitRingInclusion()
            .flatMap { contextResolver.resolve() }
            .flatMap { context -> prepareEach(context, transactions) }
    }.flatten()

    private suspend fun awaitRingInclusion(): Result<Unit> {
        return memberInRingUseCase.awaitIncluded(activePeopleCollectionUseCase.getActivePeopleCollection())
    }

    private suspend fun prepareEach(
        context: AllocateContext,
        transactions: List<ScheduledDurableTx>,
    ): Result<Map<DurableTxId, SubmissionPreparation>> {
        return transactions
            .map { tx -> prepare(context, tx).map { preparation -> tx.id to preparation } }
            .flattenResult()
            .map { it.toMap() }
    }

    private suspend fun prepare(context: AllocateContext, tx: ScheduledDurableTx): Result<SubmissionPreparation> {
        reservations.release(tx.id)

        return reserveFreeSlot(context, tx.id).flatMap { slot ->
            if (slot == null) {
                Timber.w("No free notification slot in period ${context.period}; giving up claim ${tx.id.value}")
                return@flatMap Result.success(SubmissionPreparation.GiveUp)
            }
            build(context, slot, target = tx.policy.params)
        }
    }

    private suspend fun reserveFreeSlot(context: AllocateContext, claim: DurableTxId): Result<NotificationSlot?> {
        return allocationLock.withLock {
            seqPicker.freeSlots(context).map { free ->
                free.firstOrNull()?.also { slot -> reservations.reserve(claim, slot) }
            }
        }
    }

    private suspend fun build(context: AllocateContext, slot: NotificationSlot, target: AccountId): Result<SubmissionPreparation> {
        Timber.i("Building notification slot claim: period=${slot.period} seq=${slot.seq} collection=${slot.collection}")
        val reference = NotificationReference(slot.period, slot.seq)

        return statementStoreOrigins.asResourcesNotificationSlot(slot.period, slot.seq, slot.collection).flatMap { origin ->
            extrinsicService.buildExtrinsic(context.chain, origin, ExtrinsicService.SubmissionOptions()) {
                resourcesCalls.setNotificationStatementAccountForSequence(reference, target)
            }
        }.map(SubmissionPreparation::Ready)
    }
}
