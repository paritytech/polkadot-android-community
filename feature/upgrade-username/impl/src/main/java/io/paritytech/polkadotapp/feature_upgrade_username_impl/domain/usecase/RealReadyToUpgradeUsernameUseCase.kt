package io.paritytech.polkadotapp.feature_upgrade_username_impl.domain.usecase

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.feature_people_api.domain.useCase.PersonStatusUseCase
import io.paritytech.polkadotapp.feature_upgrade_username_api.domain.model.UpgradeToFullUsernameState
import io.paritytech.polkadotapp.feature_upgrade_username_api.domain.usecase.ReadyToUpgradeUsernameUseCase
import io.paritytech.polkadotapp.feature_upgrade_username_impl.data.RESOURCES
import io.paritytech.polkadotapp.feature_usernames_api.domain.usecase.UsernameOfAccountUseCase
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import javax.inject.Inject

class RealReadyToUpgradeUsernameUseCase @Inject constructor(
    private val personStatusUseCase: PersonStatusUseCase,
    private val usernameOfAccountUseCase: UsernameOfAccountUseCase
) : ReadyToUpgradeUsernameUseCase {
    override operator fun invoke() = combine(
        personStatusUseCase.canUseAliasFlow(BandersnatchContext.RESOURCES),
        usernameOfAccountUseCase().filterNotNull()
    ) { canUseResourcesAlias, stored ->
        val fullUsername = stored.fullUsername
        when {
            !stored.isOnChain || !canUseResourcesAlias -> UpgradeToFullUsernameState.NotReady
            fullUsername == null -> UpgradeToFullUsernameState.Ready(stored.liteUsername)
            else -> UpgradeToFullUsernameState.Completed(stored.liteUsername, fullUsername)
        }
    }
}
