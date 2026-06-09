package io.paritytech.polkadotapp.feature_coinage_impl.domain.usecase

import io.paritytech.polkadotapp.feature_coinage_api.domain.usecase.CoinageTestHelperUseCase
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import javax.inject.Inject

class RealCoinageTestHelperUseCase @Inject constructor(
    private val voucherRepository: VoucherRepository
) : CoinageTestHelperUseCase {
    override suspend fun makeAllVouchersReady() {
        voucherRepository.getActiveVouchers()
            .forEach {
                voucherRepository.save(
                    it.copy(
                        delayUnloadUntil = System.currentTimeMillis(),
                        ringHasEnoughRingMembersToWithdraw = true
                    )
                )
            }
    }
}
