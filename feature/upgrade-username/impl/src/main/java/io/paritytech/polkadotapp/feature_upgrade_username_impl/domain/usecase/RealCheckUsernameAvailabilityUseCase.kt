package io.paritytech.polkadotapp.feature_upgrade_username_impl.domain.usecase

import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.data.repository.getWalletAccountIdIn
import io.paritytech.polkadotapp.feature_chain_resources_api.data.repository.ResourcesRepository
import io.paritytech.polkadotapp.feature_chain_resources_api.domain.model.hasExpired
import io.paritytech.polkadotapp.feature_upgrade_username_api.domain.model.UpgradeUsernameAvailabilityState
import io.paritytech.polkadotapp.feature_upgrade_username_api.domain.usecase.CheckUsernameAvailabilityUseCase
import javax.inject.Inject

class RealCheckUsernameAvailabilityUseCase @Inject constructor(
    knownChains: KnownChains,
    private val chainRegistry: ChainRegistry,
    private val accountRepository: AccountRepository,
    private val resourcesRepository: ResourcesRepository,
) : CheckUsernameAvailabilityUseCase {
    private val chainId = knownChains.people

    override suspend fun invoke(username: String): Result<UpgradeUsernameAvailabilityState> {
        return usernameIsTaken(username).flatMap { taken ->
            if (taken) Result.success(UpgradeUsernameAvailabilityState.NotAvailable) else usernameIsReserved(username)
        }
    }

    private suspend fun usernameIsTaken(username: String): Result<Boolean> {
        return resourcesRepository.accountIdOfUsername(chainId, username).map { it != null }
    }

    private suspend fun usernameIsReserved(username: String): Result<UpgradeUsernameAvailabilityState> {
        val ourAccountId = getAccountId()

        return resourcesRepository.usernameReservationQueue(chainId, username).flatMap { queue ->
            when {
                queue.isEmpty() -> Result.success(UpgradeUsernameAvailabilityState.Free)
                queue.first().account == ourAccountId -> Result.success(UpgradeUsernameAvailabilityState.ReservedByUs)
                else -> resourcesRepository.reservationDuration(chainId).map { duration ->
                    val expiredAccounts = mutableListOf<AccountId>()

                    for (reservation in queue) {
                        if (reservation.account == ourAccountId) break
                        if (!reservation.hasExpired(duration)) return@map UpgradeUsernameAvailabilityState.NotAvailable
                        expiredAccounts.add(reservation.account)
                    }

                    UpgradeUsernameAvailabilityState.ReclaimExpiredReservation(expiredAccounts)
                }
            }
        }
    }

    private suspend fun getAccountId() = accountRepository.getWalletAccountIdIn(chainRegistry.getChain(chainId))
}
