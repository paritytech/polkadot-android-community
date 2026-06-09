package io.paritytech.polkadotapp.feature_usernames_impl.domain.usecase

import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_usernames_api.domain.model.AccountOnboardingStatus
import io.paritytech.polkadotapp.feature_usernames_api.domain.usecase.ObserveAccountOnboardingStatusUseCase
import io.paritytech.polkadotapp.feature_usernames_api.domain.usecase.UsernameOfAccountUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

class RealObserveAccountOnboardingStatusUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val usernameOfAccountUseCase: UsernameOfAccountUseCase,
) : ObserveAccountOnboardingStatusUseCase {
    override fun invoke(): Flow<AccountOnboardingStatus> =
        combine(
            accountRepository.areAccountsInitializedFlow(),
            usernameOfAccountUseCase.initiallyClaimedLightUsername()
        ) { hasAccount, initiallyClaimedLightUsername ->
            if (!hasAccount) return@combine AccountOnboardingStatus.EMPTY

            if (initiallyClaimedLightUsername == null) return@combine AccountOnboardingStatus(true, null)

            return@combine AccountOnboardingStatus(true, initiallyClaimedLightUsername)
        }
            .distinctUntilChanged()
}
