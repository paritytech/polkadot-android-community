@file:OptIn(ExperimentalTime::class)

package io.paritytech.polkadotapp.feature_statement_store_impl.domain.slotAllocator

import io.novasama.substrate_sdk_android.encrypt.keypair.substrate.Sr25519Keypair
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.repository.ChainStateRepository
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.coerceToUnit
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.mapToSet
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.common.utils.progressStallReport.markRegion
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_account_api.domain.usecase.AccountDerivationUseCase
import io.paritytech.polkadotapp.feature_chain_resources_api.data.api.replaceStatementStoreAccount
import io.paritytech.polkadotapp.feature_chain_resources_api.data.api.resourcesCalls
import io.paritytech.polkadotapp.feature_statement_store_api.domain.slotAllocator.CollectionSlot
import io.paritytech.polkadotapp.feature_statement_store_api.domain.slotAllocator.StatementStoreSlot
import io.paritytech.polkadotapp.feature_statement_store_api.domain.slotAllocator.StatementStoreSlotAllocationError
import io.paritytech.polkadotapp.feature_statement_store_impl.data.repository.StatementStoreSlotAllocationRepository
import io.paritytech.polkadotapp.feature_statement_store_impl.data.signer.origins.StatementStoreOrigins
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicService
import io.paritytech.polkadotapp.feature_transactions.api.data.FormExtrinsic
import io.paritytech.polkadotapp.feature_transactions.api.data.flattenExecutionFailure
import io.paritytech.polkadotapp.feature_transactions.api.data.validation.ExtrinsicValidator
import io.paritytech.polkadotapp.feature_transactions.api.data.validation.InvalidTransaction
import io.paritytech.polkadotapp.feature_transactions.api.data.validation.TransactionValidity
import io.paritytech.polkadotapp.feature_transactions.api.domain.model.TransactionOrigin
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.ExperimentalTime
import io.paritytech.polkadotapp.common.R as RCommon

private const val REPLACEMENT_TOO_EARLY_VALIDITY_CODE: UByte = 228u

class StatementStoreSlotReplacer @Inject constructor(
    private val extrinsicService: ExtrinsicService,
    private val extrinsicValidator: ExtrinsicValidator,
    private val chainStateRepository: ChainStateRepository,
    private val statementStoreOrigins: StatementStoreOrigins,
    private val accountDerivationUseCase: AccountDerivationUseCase,
    private val allocationRepository: StatementStoreSlotAllocationRepository,
    private val slotLoader: StatementStoreSlotLoader,
) {
    context(diagnostics: StalenessReportCollector)
    suspend fun replaceFirstAvailable(
        context: AllocateContext,
        candidates: List<CollectionSlot<StatementStoreSlot.Taken>>,
        newAccount: AccountId,
    ): Result<CollectionSlot<StatementStoreSlot.Taken>> {
        return runCancellableCatching {
            allocationRepository.signerDerivationPathsFor(context.chain.id, candidates.mapToSet { it.slot.accountId })
        }.flatMap { signerPaths ->
            replaceFirstAvailable(context, candidates, signerPaths, newAccount, anyNotYetAllowed = false)
        }
    }

    context(diagnostics: StalenessReportCollector)
    private suspend fun replaceFirstAvailable(
        context: AllocateContext,
        candidates: List<CollectionSlot<StatementStoreSlot.Taken>>,
        signerPaths: Map<AccountId, String>,
        newAccount: AccountId,
        anyNotYetAllowed: Boolean,
    ): Result<CollectionSlot<StatementStoreSlot.Taken>> {
        val candidate = candidates.firstOrNull() ?: return Result.failure(noReplaceableSlotError(anyNotYetAllowed))
        val remaining = candidates.drop(1)

        return tryReplace(context, candidate, signerPaths[candidate.slot.accountId], newAccount).flatMap { outcome ->
            Timber.i("allocate: replacing seq=${candidate.slot.seq} in ${candidate.collection}: $outcome")
            when (outcome) {
                ReplacementOutcome.REPLACED -> Result.success(candidate)
                ReplacementOutcome.NOT_YET_ALLOWED ->
                    replaceFirstAvailable(context, remaining, signerPaths, newAccount, anyNotYetAllowed = true)
                ReplacementOutcome.HOLDER_KEY_UNAVAILABLE ->
                    replaceFirstAvailable(context, remaining, signerPaths, newAccount, anyNotYetAllowed)
            }
        }
    }

    context(diagnostics: StalenessReportCollector)
    private suspend fun tryReplace(
        context: AllocateContext,
        candidate: CollectionSlot<StatementStoreSlot.Taken>,
        signerDerivationPath: String?,
        newAccount: AccountId,
    ): Result<ReplacementOutcome> {
        if (signerDerivationPath == null) return Result.success(ReplacementOutcome.HOLDER_KEY_UNAVAILABLE)

        return holderKeypair(signerDerivationPath, candidate.slot.accountId).flatMap { keypair ->
            if (keypair == null) {
                Result.success(ReplacementOutcome.HOLDER_KEY_UNAVAILABLE)
            } else {
                replaceAsHolder(context, candidate, statementStoreOrigins.asResourcesSlotHolder(keypair), newAccount)
            }
        }
    }

    private suspend fun holderKeypair(derivationPath: String, holder: AccountId): Result<Sr25519Keypair?> {
        return accountDerivationUseCase.deriveKeypair(derivationPath).map { keypair ->
            (keypair as? Sr25519Keypair)?.takeIf { it.publicKey.toDataByteArray() == holder }
        }
    }

    context(diagnostics: StalenessReportCollector)
    private suspend fun replaceAsHolder(
        context: AllocateContext,
        candidate: CollectionSlot<StatementStoreSlot.Taken>,
        holderOrigin: TransactionOrigin,
        newAccount: AccountId,
    ): Result<ReplacementOutcome> {
        return slotLoader.slotAlias(candidate.collection, context.period, candidate.slot.seq).flatMap { alias ->
            val replaceCall: FormExtrinsic = {
                resourcesCalls.replaceStatementStoreAccount(
                    period = context.period,
                    alias = alias.value.toDataByteArray(),
                    newAccount = newAccount,
                    since = candidate.slot.since.epochSeconds.toULong(),
                )
            }

            when (val dryRun = dryRun(context.chain, holderOrigin, replaceCall)) {
                ReplacementDryRun.Valid,
                ReplacementDryRun.Inconclusive -> submit(context.chain, holderOrigin, replaceCall).map { ReplacementOutcome.REPLACED }
                ReplacementDryRun.NotYetAllowed -> Result.success(ReplacementOutcome.NOT_YET_ALLOWED)
                is ReplacementDryRun.Rejected -> Result.failure(SlotReplacementRejectedException(dryRun.reason))
            }
        }
    }

    context(diagnostics: StalenessReportCollector)
    private suspend fun dryRun(chain: Chain, holderOrigin: TransactionOrigin, replaceCall: FormExtrinsic): ReplacementDryRun =
        diagnostics.markRegion(RCommon.string.stall_reading_chain_state) {
            extrinsicService.buildExtrinsic(chain, holderOrigin, ExtrinsicService.SubmissionOptions(), replaceCall)
                .flatMap { extrinsic ->
                    runCancellableCatching { chainStateRepository.currentBlockHash(chain.id) }.flatMap { blockHash ->
                        extrinsicValidator.validate(chain.id, extrinsic.bytesWithoutLength.toDataByteArray(), blockHash)
                    }
                }
                .fold(
                    onSuccess = { validity -> validity.toReplacementDryRun() },
                    onFailure = { error ->
                        Timber.w(error, "allocate: slot replacement dry run failed; submitting without it")
                        ReplacementDryRun.Inconclusive
                    },
                )
        }

    context(diagnostics: StalenessReportCollector)
    private suspend fun submit(chain: Chain, holderOrigin: TransactionOrigin, replaceCall: FormExtrinsic): Result<Unit> =
        diagnostics.markRegion(RCommon.string.stall_submitting_transaction) {
            extrinsicService.submitExtrinsicAndAwaitExecution(chain, holderOrigin, formExtrinsic = replaceCall)
                .flattenExecutionFailure()
                .coerceToUnit()
        }
}

private enum class ReplacementOutcome {
    REPLACED,
    NOT_YET_ALLOWED,
    HOLDER_KEY_UNAVAILABLE,
}

private sealed interface ReplacementDryRun {
    data object Valid : ReplacementDryRun

    data object Inconclusive : ReplacementDryRun

    data object NotYetAllowed : ReplacementDryRun

    class Rejected(val reason: InvalidTransaction) : ReplacementDryRun
}

private fun TransactionValidity.toReplacementDryRun(): ReplacementDryRun = when (this) {
    TransactionValidity.Valid -> ReplacementDryRun.Valid
    is TransactionValidity.Unknown -> ReplacementDryRun.Inconclusive
    is TransactionValidity.Invalid -> if (reason.isReplacementNotYetAllowed()) {
        ReplacementDryRun.NotYetAllowed
    } else {
        ReplacementDryRun.Rejected(reason)
    }
}

private fun InvalidTransaction.isReplacementNotYetAllowed(): Boolean {
    return this is InvalidTransaction.Payment || this == InvalidTransaction.Custom(REPLACEMENT_TOO_EARLY_VALIDITY_CODE)
}

private fun noReplaceableSlotError(anyNotYetAllowed: Boolean): Throwable = if (anyNotYetAllowed) {
    EvictionNotYetAllowedException("No evictable slot this device can sign for may be replaced yet")
} else {
    StatementStoreSlotAllocationError.NoAllocationAvailable(IllegalStateException("No evictable slot is held by a key on this device"))
}
