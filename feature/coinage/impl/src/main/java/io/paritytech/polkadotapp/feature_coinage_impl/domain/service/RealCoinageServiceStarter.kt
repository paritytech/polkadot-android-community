package io.paritytech.polkadotapp.feature_coinage_impl.domain.service

import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.feature_coinage_api.domain.service.CoinageBackupService
import io.paritytech.polkadotapp.feature_coinage_api.domain.service.CoinageRecyclingSyncManager
import io.paritytech.polkadotapp.feature_coinage_api.domain.service.CoinageServiceStarter
import io.paritytech.polkadotapp.feature_usernames_api.domain.usecase.ObserveAccountOnboardingStatusUseCase
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

class RealCoinageServiceStarter @Inject constructor(
    private val coinageBackupService: CoinageBackupService,
    private val voucherLocationService: VoucherLocationService,
    private val coinsTrackingService: CoinsTrackingService,
    private val voucherRingMembersService: VoucherRingMembersService,
    private val coinageRecyclingSyncManager: CoinageRecyclingSyncManager,
    private val observeAccountOnboardingStatusUseCase: ObserveAccountOnboardingStatusUseCase,
) : CoinageServiceStarter {
    context(ComputationalScope)
    override fun start() {
        launch { coinsTrackingService.start() }
        launch { voucherLocationService.start() }
        launch { voucherRingMembersService.start() }
        launch {
            observeAccountOnboardingStatusUseCase().filter { it.isOnboarded }.first()
            coinageBackupService.start()
        }
//        launch { transferRecoveryService.recover() } Disabled until useful
        coinageRecyclingSyncManager.recycleAndSchedule()
    }
}
