package io.paritytech.polkadotapp.feature_upgrade_username_impl.domain.interactor

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.chains.util.signing.MessageSigningContext
import io.paritytech.polkadotapp.common.data.substrate.model.MultiSignature
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.common.utils.coerceToUnit
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.data.repository.getWalletAccountIdIn
import io.paritytech.polkadotapp.feature_account_api.data.sign.AccountBytesSigner
import io.paritytech.polkadotapp.feature_chain_resources_api.data.api.registerPerson
import io.paritytech.polkadotapp.feature_chain_resources_api.data.api.removeReservation
import io.paritytech.polkadotapp.feature_chain_resources_api.data.api.resourcesCalls
import io.paritytech.polkadotapp.feature_chain_resources_api.data.model.UsernameChoice
import io.paritytech.polkadotapp.feature_chain_resources_api.data.repository.ResourcesRepository
import io.paritytech.polkadotapp.feature_people_api.data.signer.origins.PeopleOrigins
import io.paritytech.polkadotapp.feature_transactions.api.data.ExtrinsicService
import io.paritytech.polkadotapp.feature_transactions.api.data.flattenExecutionFailure
import io.paritytech.polkadotapp.feature_transactions.api.data.origins.FreeTransactionOrigins
import io.paritytech.polkadotapp.feature_upgrade_username_api.domain.model.UpgradeUsernameAvailabilityState
import io.paritytech.polkadotapp.feature_upgrade_username_api.domain.usecase.CheckUsernameAvailabilityUseCase
import io.paritytech.polkadotapp.feature_upgrade_username_impl.data.RESOURCES
import io.paritytech.polkadotapp.feature_usernames_api.domain.model.Username
import io.paritytech.polkadotapp.feature_usernames_api.domain.usecase.UsernameOfAccountUseCase
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface UsernameUpgradeInteractor {
    suspend fun liteUsername(): Username

    suspend fun upgrade(username: String, usernameState: UpgradeUsernameAvailabilityState): Result<Unit>

    suspend fun checkUsernameAvailable(username: String): Result<UpgradeUsernameAvailabilityState>
}

class RealUsernameUpgradeInteractor @Inject constructor(
    knownChains: KnownChains,
    private val chainRegistry: ChainRegistry,
    private val accountRepository: AccountRepository,
    private val extrinsicService: ExtrinsicService,
    private val resourcesRepository: ResourcesRepository,
    private val peopleOrigins: PeopleOrigins,
    private val freeTransactionOrigins: FreeTransactionOrigins,
    private val usernameOfAccountUseCase: UsernameOfAccountUseCase,
    private val accountBytesSigner: AccountBytesSigner,
    private val checkUsernameAvailabilityUseCase: CheckUsernameAvailabilityUseCase,
) : UsernameUpgradeInteractor {
    private val chainId = knownChains.people

    override suspend fun liteUsername() = usernameOfAccountUseCase().filterNotNull().map { it.liteUsername }.first()

    override suspend fun upgrade(username: String, usernameState: UpgradeUsernameAvailabilityState) =
        removeReservationIfNeeded(username, usernameState.expiredAccounts())
            .flatMap { upgradeToFullUsername(usernameState.toChoice(username)) }

    private suspend fun removeReservationIfNeeded(username: String, expiredAccounts: List<AccountId>): Result<Unit> {
        if (expiredAccounts.isEmpty()) return Result.success(Unit)

        return extrinsicService.submitExtrinsicAndAwaitExecution(
            chain = chainRegistry.getChain(chainId),
            origin = freeTransactionOrigins.freeTxFromWalletOrSigned(chainId)
        ) {
            expiredAccounts.forEach { account ->
                resourcesCalls.removeReservation(username, account)
            }
        }
            .flattenExecutionFailure()
            .coerceToUnit()
    }

    private suspend fun upgradeToFullUsername(choice: UsernameChoice): Result<Unit> {
        val aliasBytes = accountRepository.getCandidateAlias(BandersnatchContext.RESOURCES)

        return accountBytesSigner.signRawBytesByWallet(aliasBytes.value, chainId, MessageSigningContext.trustedContent())
            .flatMap { signatureWrapper ->
                peopleOrigins.asPersonalAliasWithAccountEnsuringRevision(BandersnatchContext.RESOURCES)
                    .flatMap { transactionOrigin ->
                        extrinsicService.submitExtrinsicAndAwaitExecution(
                            chain = chainRegistry.getChain(chainId),
                            origin = transactionOrigin
                        ) {
                            resourcesCalls.registerPerson(
                                usernameChoice = choice,
                                accountId = getAccountId(),
                                liteIdentityProof = MultiSignature.Sr25519(signatureWrapper.signature.toDataByteArray())
                            )
                        }
                            .flattenExecutionFailure()
                    }
            }
            .coerceToUnit()
    }

    override suspend fun checkUsernameAvailable(username: String) = checkUsernameAvailabilityUseCase(username)

    private suspend fun getAccountId() = accountRepository.getWalletAccountIdIn(chainRegistry.getChain(chainId))

    private fun UpgradeUsernameAvailabilityState.expiredAccounts(): List<AccountId> {
        return (this as? UpgradeUsernameAvailabilityState.ReclaimExpiredReservation)?.expiredAccounts.orEmpty()
    }

    private fun UpgradeUsernameAvailabilityState.hadReservation() = this is UpgradeUsernameAvailabilityState.ReclaimExpiredReservation ||
        this is UpgradeUsernameAvailabilityState.ReservedByUs

    private fun UpgradeUsernameAvailabilityState.toChoice(username: String) = if (hadReservation()) {
        UsernameChoice.Reservation(username)
    } else {
        UsernameChoice.Standalone(username)
    }
}
