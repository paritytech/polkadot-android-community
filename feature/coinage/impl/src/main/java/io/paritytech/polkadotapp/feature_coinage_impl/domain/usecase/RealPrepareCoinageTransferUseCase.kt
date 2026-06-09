package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.StrategyType
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.TransferMemo
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.TransferPlan
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.PrepareCoinageTransferUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import io.paritytech.polkadotapp.feature_coinage_impl.domain.coinageLogD
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
    private val coinRepository: CoinRepository,
    private val voucherRepository: VoucherRepository,
    private val plannerFactory: TransferPlannerFactory,
    private val exactMatchStrategyFactory: ExactMatchStrategyFactory,
    private val splitStrategyFactory: SplitCoinStrategyFactory,
    private val unloadAndSplitStrategyFactory: UnloadAndSplitVouchersStrategyFactory,
    @param:DigitalDollarChainAssetProvider private val chainAssetProvider: ChainAssetProvider,
    private val activePeopleCollectionUseCase: ActivePeopleCollectionUseCase,
    private val memoBuilder: TransferMemoBuilder
) : PrepareCoinageTransferUseCase {
    override suspend fun preparePlan(amount: BigDecimal): Result<TransferPlan> {
        val allCoins = coinRepository.getActiveCoins()
        val allVouchers = voucherRepository.getActiveVouchers()

        return plannerFactory.create()
            .map { it.plan(amount, allCoins, allVouchers) }
            .onSuccess { coinageLogD("Outgoing TransferPlan: $it") }
    }

    override suspend fun prepareMemo(plan: TransferPlan): Result<TransferMemo> {
        val chain = chainAssetProvider.chain()

        val peopleCollection = activePeopleCollectionUseCase.getActivePeopleCollection()
        val strategy = when (val strategyType = plan.strategyType) {
            is StrategyType.ExactCoins -> exactMatchStrategyFactory.create(strategyType)
            is StrategyType.Split -> splitStrategyFactory.create(strategyType, chain)
            is StrategyType.UnloadAndSplit -> unloadAndSplitStrategyFactory.create(strategyType, peopleCollection, chain)
        }

        return strategy.run()
            .flatMap { memoEntries -> memoBuilder.buildMemo(memoEntries) }
            .onSuccess { memo -> coinageLogD("TransferMemo built: coins=${memo.coins.size}, totalValue=${memo.totalValue}") }
    }
}
