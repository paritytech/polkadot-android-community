package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.paritytech.polkadotapp.feature_coinage_api.domain.model.TransferPlan
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageAssetsUseCase
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.ValidateTransferPlanUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.TransferPlannerFactory
import java.math.BigDecimal
import javax.inject.Inject

class RealValidateTransferPlanUseCase @Inject constructor(
    private val plannerFactory: TransferPlannerFactory,
    private val coinageAssetsUseCase: CoinageAssetsUseCase,
) : ValidateTransferPlanUseCase {
    override suspend fun validate(amount: BigDecimal): TransferPlan? {
        val planner = plannerFactory.create()
            .getOrNull() ?: return null

        val coins = coinageAssetsUseCase.getSelectableCoins()
        val vouchers = coinageAssetsUseCase.getSelectableVouchers()

        return planner.plan(amount, coins, vouchers).getOrNull()
    }
}
