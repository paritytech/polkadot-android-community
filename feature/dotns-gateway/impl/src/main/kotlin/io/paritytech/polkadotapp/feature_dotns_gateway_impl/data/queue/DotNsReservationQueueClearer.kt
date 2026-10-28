package io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.queue

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.Chain
import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.chains.network.binding.WeightV2
import io.paritytech.polkadotapp.chains.network.binding.intoBalance
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.Fraction
import io.paritytech.polkadotapp.common.utils.Fraction.Companion.percents
import io.paritytech.polkadotapp.common.utils.coerceToUnit
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.common.utils.runCancellableCatching
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_balances_api.data.type.TokenBalanceTypeRegistry
import io.paritytech.polkadotapp.feature_dotns_gateway_api.domain.model.DotNsNameReservedByOtherError
import io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.config.DotNsGatewayConfig
import io.paritytech.polkadotapp.feature_dotns_gateway_impl.data.contract.PopContractCoder
import io.paritytech.polkadotapp.feature_people_api.domain.PeopleCheckMemberInRingUseCase
import io.paritytech.polkadotapp.feature_people_api.domain.useCase.ActivePeopleCollectionUseCase
import io.paritytech.polkadotapp.feature_pgas_api.domain.OnExistingAllocationStrategy
import io.paritytech.polkadotapp.feature_pgas_api.domain.PgasChainAssetProvider
import io.paritytech.polkadotapp.feature_pgas_api.domain.PgasClaimer
import io.paritytech.polkadotapp.feature_revive_api.EvmAccountId
import io.paritytech.polkadotapp.feature_revive_api.ReviveContractApi
import io.paritytech.polkadotapp.feature_revive_api.ReviveStorageDepositUnfunded
import io.paritytech.polkadotapp.feature_revive_api.calls.call
import io.paritytech.polkadotapp.feature_revive_api.calls.revive
import io.paritytech.polkadotapp.feature_revive_api.toEvmAccountId
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicService
import io.paritytech.polkadotapp.feature_transactions.api.data.FormExtrinsic
import io.paritytech.polkadotapp.feature_transactions.api.data.flattenExecutionFailure
import io.paritytech.polkadotapp.feature_transactions.api.domain.model.TransactionOrigin
import io.paritytech.polkadotapp.feature_transactions.api.domain.model.asSignedOrigin
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

class DotNsClaimantNotWalletError : Exception("Only the wallet account can clear its own reservation queue")

class DotNsQueueClearPgasShortError(required: Balance, available: Balance) :
    Exception("Clearing the reservation queue needs $required PGAS, the wallet account holds $available")

class DotNsQueueClearPgasClaimTimeoutError : Exception("PGAS claim did not report back in time")

class DotNsQueueClearAccountUnmappedError : Exception("The wallet account has no Revive address mapping")

class DotNsReservationQueueClearer @Inject constructor(
    private val queueReader: DotNsReservationQueueReader,
    private val reviveContractApi: ReviveContractApi,
    private val extrinsicService: ExtrinsicService,
    private val accountRepository: AccountRepository,
    private val pgasClaimer: PgasClaimer,
    private val pgasChainAssetProvider: PgasChainAssetProvider,
    private val tokenBalanceTypeRegistry: TokenBalanceTypeRegistry,
    private val activePeopleCollectionUseCase: ActivePeopleCollectionUseCase,
    private val peopleCheckMemberInRingUseCase: PeopleCheckMemberInRingUseCase
) {
    // A claim clears only a bounded run of dead entries in front of it, and one that still finds a dead front
    // reverts and keeps none of that clearing. Where the controller has no bound, it clears them all inside the
    // claim, at a proof-size cost that runs a claim out of gas after a few entries.
    suspend fun prepareClaim(chain: Chain, who: AccountId, label: String, config: DotNsGatewayConfig): Result<Unit> {
        return queueReader.read(label, config).flatMap { queue ->
            val holder = queue.firstActiveHolder
            when {
                holder != null && !holder.contentEquals(who.toEvmAccountId().value) -> Result.failure(DotNsNameReservedByOtherError(label))
                queue.deadInFront == 0 -> Result.success(Unit)
                else -> queueReader.maxQueueStepsPerCall(config).flatMap { maxSteps ->
                    if (maxSteps != null && queue.deadInFront.toBigInteger() <= maxSteps) {
                        Result.success(Unit)
                    } else {
                        expireReservation(chain, who, label, config)
                    }
                }
            }
        }
    }

    // The account pays the fee and the storage deposit in PGAS. A PGAS claim funds an account that is short, and it
    // creates an account the chain has never seen, which maps it. A claim spends one of the day's slots and fixes
    // nothing else, so any other failure stops before one is spent.
    private suspend fun expireReservation(chain: Chain, who: AccountId, label: String, config: DotNsGatewayConfig): Result<Unit> {
        val input = PopContractCoder.encodeExpireReservation(label).toDataByteArray()

        return walletOrigin(chain, who).flatMap { origin ->
            val clear = QueueClear(chain, origin, who, config.popControllerAddress, input)

            reviveContractApi.isAccountMapped(chain.id, who).flatMap { mapped ->
                pgasBalance(who).flatMap { held ->
                    // An account that already holds PGAS exists, and a claim does not map an existing account.
                    if (!mapped && held > Balance.ZERO) {
                        Result.failure(DotNsQueueClearAccountUnmappedError())
                    } else {
                        price(clear).flatMap { priced ->
                            if (mapped && priced.limits.funded && held >= priced.cost) Result.success(priced) else fundAndReprice(clear)
                        }
                    }
                }
            }.flatMap { priced ->
                extrinsicService.submitExtrinsicAndAwaitExecution(chain, origin, formExtrinsic = priced.formExtrinsic)
                    .flattenExecutionFailure()
                    .coerceToUnit()
            }
        }
    }

    private suspend fun fundAndReprice(clear: QueueClear): Result<PricedClear> {
        return claimPgas(clear.who)
            .flatMap { requireMapped(clear.chain, clear.who) }
            .flatMap { price(clear) }
            .flatMap { priced ->
                pgasBalance(clear.who).flatMap { topped ->
                    if (priced.limits.funded && topped >= priced.cost) {
                        Result.success(priced)
                    } else {
                        Result.failure(DotNsQueueClearPgasShortError(priced.cost, topped))
                    }
                }
            }
    }

    private suspend fun price(clear: QueueClear): Result<PricedClear> {
        return dryRunLimits(clear).flatMap { limits ->
            val formExtrinsic: FormExtrinsic = {
                revive.call(
                    dest = clear.controller,
                    weightLimit = limits.weightRequired.withMargin(),
                    storageDepositLimit = limits.storageDepositLimit,
                    data = clear.input
                )
            }

            extrinsicService.estimateFee(clear.chain, clear.origin, formExtrinsic = formExtrinsic).map { fee ->
                PricedClear(limits, formExtrinsic, cost = limits.storageDepositLimit.intoBalance() + fee.amount)
            }
        }
    }

    private suspend fun dryRunLimits(clear: QueueClear): Result<ClearLimits> {
        val dryRun = reviveContractApi.dryRun(clear.chain.id, clear.who, clear.controller, clear.input)
        val unfunded = dryRun.exceptionOrNull() as? ReviveStorageDepositUnfunded

        return if (unfunded != null) {
            Result.success(ClearLimits(unfunded.weightRequired, unfunded.storageDeposit.withMargin(), funded = false))
        } else {
            dryRun.map { ClearLimits(it.weightRequired, it.storageDeposit.withMargin(), funded = true) }
        }
    }

    private suspend fun requireMapped(chain: Chain, who: AccountId): Result<Unit> {
        return reviveContractApi.isAccountMapped(chain.id, who).flatMap { mapped ->
            if (mapped) Result.success(Unit) else Result.failure(DotNsQueueClearAccountUnmappedError())
        }
    }

    private suspend fun walletOrigin(chain: Chain, who: AccountId): Result<TransactionOrigin> {
        return runCancellableCatching { accountRepository.getWalletAccount() }.flatMap { wallet ->
            if (wallet.accountIdIn(chain) == who) Result.success(wallet.asSignedOrigin()) else Result.failure(DotNsClaimantNotWalletError())
        }
    }

    // Claiming PGAS takes a ring proof, so the claim waits for this person to be included in the ring first.
    private suspend fun claimPgas(account: AccountId): Result<Unit> {
        val collection = activePeopleCollectionUseCase.getActivePeopleCollection()

        return peopleCheckMemberInRingUseCase.awaitIncluded(collection).flatMap {
            withTimeoutOrNull(PGAS_CLAIM_TIMEOUT) {
                with(StalenessReportCollector.NoOp) { pgasClaimer.claim(account, OnExistingAllocationStrategy.INCREASE) }
            } ?: Result.failure(DotNsQueueClearPgasClaimTimeoutError())
        }
    }

    private suspend fun pgasBalance(account: AccountId): Result<Balance> = runCancellableCatching {
        tokenBalanceTypeRegistry.typeFor(pgasChainAssetProvider.asset()).getBalance(account).transferable
    }

    // The declared limits are what the fee is charged against, so they carry a margin rather than the pallet maximum.
    private fun WeightV2.withMargin() = WeightV2(refTime = refTime.withMargin(), proofSize = proofSize.withMargin())

    private fun BigInteger.withMargin(): BigInteger = (toBigDecimal() * (BigDecimal.ONE + LIMIT_MARGIN.fraction)).toBigInteger()

    private companion object {
        val LIMIT_MARGIN: Fraction = 20.percents
        val PGAS_CLAIM_TIMEOUT = 2.minutes
    }
}

private class QueueClear(
    val chain: Chain,
    val origin: TransactionOrigin,
    val who: AccountId,
    val controller: EvmAccountId,
    val input: DataByteArray
)

private class ClearLimits(val weightRequired: WeightV2, val storageDepositLimit: BigInteger, val funded: Boolean)

private class PricedClear(val limits: ClearLimits, val formExtrinsic: FormExtrinsic, val cost: Balance)
