package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.StrategyType
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.TransferPlan
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageAssetsUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.PrepareCoinageTransferUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.PreparedTransferMemo
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogD
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogE
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogI
import io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.TransferMemoBuilder
import io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.TransferPlannerFactory
import io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies.ExactMatchStrategyFactory
import io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies.SplitCoinStrategyFactory
import io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies.UnloadAndSplitVouchersStrategyFactory
import io.paritytech.polkadotapp.feature_people_api.domain.useCase.ActivePeopleCollectionUseCase
import io.paritytech.polkadotapp.feature_tokens_api.di.DigitalDollarChainAssetProvider
import io.paritytech.polkadotapp.feature_tokens_api.domain.ChainAssetProvider
import java.math.BigDecimal
import javax.inject.Inject

class RealPrepareCoinageTransferUseCase @Inject constructor(
    private val coinageAssetsUseCase: CoinageAssetsUseCase,
    private val plannerFactory: TransferPlannerFactory,
    private val exactMatchStrategyFactory: ExactMatchStrategyFactory,
    private val splitStrategyFactory: SplitCoinStrategyFactory,
    private val unloadAndSplitStrategyFactory: UnloadAndSplitVouchersStrategyFactory,
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val activePeopleCollectionUseCase: ActivePeopleCollectionUseCase,
    private val memoBuilder: TransferMemoBuilder
) : PrepareCoinageTransferUseCase {
    override suspend fun preparePlan(amount: BigDecimal): Result<TransferPlan> {
        val allCoins = coinageAssetsUseCase.getSelectableCoins()
        val allVouchers = coinageAssetsUseCase.getSelectableVouchers()

        return plannerFactory.create()
            .flatMap { it.plan(amount, allCoins, allVouchers) }
            .onSuccess { coinageLogI("Outgoing TransferPlan: $it") }
            .onFailure { coinageLogE("Failed to construct transfer plan for amount: $amount", it) }
    }

    override suspend fun prepareMemo(plan: TransferPlan): Result<PreparedTransferMemo> {
        val chain = chainAssetProvider.chain()

        val peopleCollection = activePeopleCollectionUseCase.getActivePeopleCollection()
        val strategy = when (val strategyType = plan.strategyType) {
            is StrategyType.ExactCoins -> exactMatchStrategyFactory.create(strategyType)
            is StrategyType.Split -> splitStrategyFactory.create(strategyType, chain)
            is StrategyType.UnloadAndSplit -> unloadAndSplitStrategyFactory.create(strategyType, peopleCollection, chain)
        }

        return strategy.run()
            .flatMap { prepared ->
                memoBuilder.buildMemo(prepared.entries)
                    .map { memo -> PreparedTransferMemo(memo, prepared.handoffCommit) }
            }
            .onSuccess { coinageLogD("TransferMemo built: coins=${it.memo.coins.size}, total=${it.memo.totalValue}") }
    }
}
