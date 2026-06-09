package io.paritytech.polkadotapp.feature_usernames_impl.domain.interactor

import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.data.storage.newaccount.NewAccountStorage
import io.paritytech.polkadotapp.feature_backup_api.domain.usecase.TryRecoverFromBackupAndCreateAccountUseCase
import io.paritytech.polkadotapp.feature_usernames_api.data.LocalUsernameStorage
import io.paritytech.polkadotapp.feature_usernames_api.domain.model.AccountOnboardingStatus
import io.paritytech.polkadotapp.feature_usernames_api.domain.model.Username
import io.paritytech.polkadotapp.feature_usernames_api.domain.usecase.ObserveAccountOnboardingStatusUseCase
import io.paritytech.polkadotapp.feature_usernames_api.domain.usecase.RecoverUsernameUseCase
import io.paritytech.polkadotapp.feature_usernames_impl.data.claim.UsernameRepository
import io.paritytech.polkadotapp.feature_usernames_impl.domain.model.UsernameAvailabilityState
import io.paritytech.polkadotapp.feature_usernames_impl.domain.usecase.CreateClaimParamsUseCase
import io.paritytech.polkadotapp.tools_backup_api.domain.model.BackupOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface UsernamesClaimInteractor {
    suspend fun checkUsernameAvailable(username: Username): Result<UsernameAvailabilityState>

    suspend fun claimUsername(username: Username, preferredDigits: String): Result<Unit>

    suspend fun tryRecoverBackupOrCreateAccount(): Result<BackupOutcome>

    suspend fun areAccountsInitialized(): Boolean

    fun observeAccountOnboardingStatus(): Flow<AccountOnboardingStatus>

    suspend fun recoverUsername(): Result<Boolean>

    suspend fun saveIsNewAccount()
}

class RealUsernamesClaimInteractor @Inject constructor(
    private val newAccountStorage: NewAccountStorage,
    private val usernameRepository: UsernameRepository,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val localUsernameStorage: LocalUsernameStorage,
    private val createClaimParamsUseCase: CreateClaimParamsUseCase,
    private val observeAccountOnboardingStatusUseCase: ObserveAccountOnboardingStatusUseCase,
    private val tryRecoverFromBackupAndCreateAccountUseCase: TryRecoverFromBackupAndCreateAccountUseCase,
    private val accountRepository: AccountRepository,
    private val recoverUsernameUseCase: RecoverUsernameUseCase,
) : UsernamesClaimInteractor {
    override suspend fun tryRecoverBackupOrCreateAccount(): Result<BackupOutcome> {
        return tryRecoverFromBackupAndCreateAccountUseCase()
    }

    override suspend fun checkUsernameAvailable(username: Username): Result<UsernameAvailabilityState> {
        return usernameRepository.checkUsernameAvailable(username)
    }

    override suspend fun claimUsername(username: Username, preferredDigits: String): Result<Unit> {
        return withContext(coroutineDispatchers.io) {
            usernameRepository.getVerifier()
                .flatMap {
                    createClaimParamsUseCase(username, it, preferredDigits)
                }
                .flatMap {
                    usernameRepository.claimUsername(it)
                }
                .map {
                    localUsernameStorage.saveValue(it)
                }
        }
    }

    override suspend fun areAccountsInitialized(): Boolean {
        return accountRepository.areAccountsInitialized()
    }

    override fun observeAccountOnboardingStatus() = observeAccountOnboardingStatusUseCase()

    override suspend fun recoverUsername() = recoverUsernameUseCase()

    override suspend fun saveIsNewAccount() {
        newAccountStorage.saveValue(true)
    }
}
