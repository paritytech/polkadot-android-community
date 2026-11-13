package io.paritytech.polkadotapp.feature_coinage_impl.domain.transaction.submission

import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.utils.logFailure
import io.paritytech.polkadotapp.feature_coinage_impl.data.config.CoinageInstanceIdProvider
import io.paritytech.polkadotapp.feature_coinage_impl.data.config.instanceIdOrNull
import io.paritytech.polkadotapp.feature_coinage_impl.data.model.OnChainCoinInfo
import io.paritytech.polkadotapp.feature_coinage_impl.data.model.belongsTo
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/** The coins of [accountIds] the chain holds, on every look that could be taken. */
internal suspend fun CoinRepository.subscribeCoinPresence(chainId: ChainId, accountIds: List<AccountId>): Flow<Set<AccountId>> =
    subscribeCoinLooks(chainId, accountIds).map { infos -> infos.filterValues { it != null }.keys }

internal suspend fun CoinRepository.subscribeCoinPresenceInConfiguredInstance(
    chainId: ChainId,
    accountIds: List<AccountId>,
    instanceIdProvider: CoinageInstanceIdProvider,
): Flow<Set<AccountId>> =
    subscribeCoinLooks(chainId, accountIds).mapNotNull { infos ->
        val nothingOnChain = infos.values.all { it == null }
        if (nothingOnChain) return@mapNotNull emptySet()

        val instance = instanceIdProvider.instanceIdOrNull() ?: return@mapNotNull null

        infos.filterValues { it?.belongsTo(instance) == true }.keys
    }

private suspend fun CoinRepository.subscribeCoinLooks(chainId: ChainId, accountIds: List<AccountId>): Flow<Map<AccountId, OnChainCoinInfo?>> =
    subscribeCoinsInfoFor(chainId, accountIds).mapNotNull { read ->
        read.logFailure("Can't fetch coin presence for a rebuild").getOrNull()
    }
