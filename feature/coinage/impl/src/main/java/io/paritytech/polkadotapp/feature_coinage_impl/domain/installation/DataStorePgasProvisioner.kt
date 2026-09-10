package io.paritytech.polkadotapp.feature_coinage_impl.domain.installation

import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.common.utils.progressStallReport.StalenessReportCollector
import io.paritytech.polkadotapp.feature_balances_api.data.type.TokenBalanceTypeRegistry
import io.paritytech.polkadotapp.feature_people_api.domain.PeopleCheckMemberInRingUseCase
import io.paritytech.polkadotapp.feature_people_api.domain.useCase.ActivePeopleCollectionUseCase
import io.paritytech.polkadotapp.feature_pgas_api.domain.OnExistingAllocationStrategy
import io.paritytech.polkadotapp.feature_pgas_api.domain.PgasChainAssetProvider
import io.paritytech.polkadotapp.feature_pgas_api.domain.PgasClaimer
import javax.inject.Inject

class DataStorePgasShortError(required: Balance, available: Balance) :
    Exception("Data store account holds $available PGAS after a top-up, $required required")

// The data store account pays both the fee and the storage deposit in PGAS. Claiming takes a ring proof, so a
// claim waits for this person to be included in the ring first — a fresh account simply waits here.
class DataStorePgasProvisioner @Inject constructor(
    private val activePeopleCollectionUseCase: ActivePeopleCollectionUseCase,
    private val peopleCheckMemberInRingUseCase: PeopleCheckMemberInRingUseCase,
    private val pgasClaimer: PgasClaimer,
    private val pgasChainAssetProvider: PgasChainAssetProvider,
    private val tokenBalanceTypeRegistry: TokenBalanceTypeRegistry,
) {
    // An empty account cannot even be simulated against: the dry-run fails on the deposit it cannot reserve.
    suspend fun ensureFunded(account: AccountId): Result<Unit> {
        return pgasBalance(account).flatMap { transferable ->
            if (transferable > Balance.ZERO) Result.success(Unit) else claim(account, OnExistingAllocationStrategy.IGNORE)
        }
    }

    suspend fun ensureCovers(account: AccountId, required: Balance): Result<Unit> {
        return pgasBalance(account).flatMap { transferable ->
            if (transferable >= required) {
                Result.success(Unit)
            } else {
                claim(account, OnExistingAllocationStrategy.INCREASE)
                    .flatMap { pgasBalance(account) }
                    .flatMap { topped ->
                        if (topped >= required) Result.success(Unit) else Result.failure(DataStorePgasShortError(required, topped))
                    }
            }
        }
    }

    private suspend fun claim(account: AccountId, strategy: OnExistingAllocationStrategy): Result<Unit> {
        val collection = activePeopleCollectionUseCase.getActivePeopleCollection()

        return peopleCheckMemberInRingUseCase.awaitIncluded(collection).flatMap {
            with(StalenessReportCollector.NoOp) { pgasClaimer.claim(account, strategy) }
        }
    }

    private suspend fun pgasBalance(account: AccountId): Result<Balance> = runCatching {
        tokenBalanceTypeRegistry.typeFor(pgasChainAssetProvider.asset()).getBalance(account).transferable
    }
}
