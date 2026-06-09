package io.paritytech.polkadotapp.feature_videogame_impl.domain.collectibles

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchContext
import io.paritytech.polkadotapp.chains.multiNetwork.ChainRegistry
import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.feature_account_api.data.repository.AccountRepository
import io.paritytech.polkadotapp.feature_account_api.data.repository.getCandidateAccount
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.BandersnatchSecretsStorage
import io.paritytech.polkadotapp.feature_account_api.data.storage.accountSecrets.getAliasInContext
import io.paritytech.polkadotapp.feature_usernames_api.domain.usecase.UsernameOfAccountUseCase
import io.paritytech.polkadotapp.feature_videogame_impl.data.SCORE
import io.paritytech.polkadotapp.feature_videogame_impl.data.collectibles.CollectiblesRepository
import io.paritytech.polkadotapp.feature_videogame_impl.data.models.onchain.OnChainAccountOrPerson
import javax.inject.Inject

interface CollectiblesInteractor {
    context(ComputationalScope)
    suspend fun loadCollection(): Result<CollectionInput>
}

class RealCollectiblesInteractor @Inject constructor(
    private val repository: CollectiblesRepository,
    private val usernameOfAccountUseCase: UsernameOfAccountUseCase,
    private val accountRepository: AccountRepository,
    private val chainRegistry: ChainRegistry,
    private val knownChains: KnownChains,
    private val bandersnatchSecretsStorage: BandersnatchSecretsStorage,
) : CollectiblesInteractor {
    context(ComputationalScope)
    override suspend fun loadCollection(): Result<CollectionInput> {
        val displayName = usernameOfAccountUseCase.getUsername().getOrNull()?.username?.getDisplayUsername()

        val chain = chainRegistry.getChain(knownChains.people)
        val candidateAccount = accountRepository.getCandidateAccount()
        val candidateAccountId = candidateAccount.accountIdIn(chain)
        val scoreAlias = bandersnatchSecretsStorage.getAliasInContext(candidateAccount.id, BandersnatchContext.SCORE)

        val owners = listOf(
            OnChainAccountOrPerson.Account(candidateAccountId),
            OnChainAccountOrPerson.Person(scoreAlias)
        )

        return repository.getOwnedNfts(chain.id, owners).map { owned ->
            CollectionInput(owned = owned, displayName = displayName)
        }
    }
}
