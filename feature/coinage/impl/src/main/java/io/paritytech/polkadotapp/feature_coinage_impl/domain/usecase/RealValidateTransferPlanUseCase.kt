package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.paritytech.polkadotapp.feature_coinage_api.domain.model.TransferPlan
import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.ValidateTransferPlanUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.CoinRepository
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.TransferPlannerFactory
import java.math.BigDecimal
import javax.inject.Inject

class RealValidateTransferPlanUseCase @Inject constructor(
    private val plannerFactory: TransferPlannerFactory,
    private val coinRepository: CoinRepository,
    private val voucherRepository: VoucherRepository
) : ValidateTransferPlanUseCase {
    override suspend fun validate(amount: BigDecimal): TransferPlan? {
        val planner = plannerFactory.create()
            .getOrNull() ?: return null

        val coins = coinRepository.getActiveCoins()
        val vouchers = voucherRepository.getActiveVouchers()

        return runCatching {
            planner.plan(amount, coins, vouchers)
        }.getOrNull()
    }
}
