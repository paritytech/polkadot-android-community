package io.paritytech.polkadotapp.feature_coinage_impl.domain.externalPayment.state

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.paritytech.polkadotapp.chains.network.binding.Balance
import io.paritytech.polkadotapp.chains.network.binding.intoBalance
import io.paritytech.polkadotapp.common.data.worker.stateMachine.WorkerStateMachineState.TransitionResult
import io.paritytech.polkadotapp.feature_coinage_api.domain.externalPayment.PaymentContext
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RingVrfIndex
import io.paritytech.polkadotapp.feature_coinage_impl.data.repository.VoucherRepository
import io.paritytech.polkadotapp.feature_coinage_impl.domain.externalPayment.usecase.UnloadRecyclerIntoExternalAssetUseCase
import java.math.BigInteger

/**
 * Offboards a selected subset of recycler vouchers straight into the destination account's
 * external-asset balance. When `surplusPlanks` is positive, the surplus is folded back into
 * freshly-minted vouchers inside the same call via
 * Coinage.unload_recycler_into_external_asset_and_vouchers; otherwise
 * Coinage.unload_recycler_into_external_asset is used.
 */
class OffboardVouchersPaymentState @AssistedInject constructor(
    @Assisted override val context: PaymentContext,
    @Assisted val selected: List<RingVrfIndex>,
    @Assisted val surplusPlanks: BigInteger,
    private val voucherRepository: VoucherRepository,
    private val unloadIntoExternalAsset: UnloadRecyclerIntoExternalAssetUseCase,
) : ExternalPaymentState {
    override val id: String = "OffboardVouchers"

    val surplus: Balance get() = surplusPlanks.intoBalance()

    @AssistedFactory
    interface Factory {
        fun create(
            context: PaymentContext,
            selected: List<RingVrfIndex>,
            surplusPlanks: BigInteger,
        ): OffboardVouchersPaymentState
    }

    context(NoContext)
    override suspend fun performTransition(): TransitionResult<ExternalPaymentState> = runTransition {
        val activeByIndex = voucherRepository.getActiveVouchers().associateBy { it.ringVrfKeyIndex }
        val vouchers = selected.mapNotNull { activeByIndex[it] }

        if (vouchers.size != selected.size) {
            return@runTransition FailedPaymentState(context, "selected voucher(s) no longer available")
        }

        unloadIntoExternalAsset.unload(
            vouchers = vouchers,
            destination = context.destination,
            surplus = surplus,
        ).getOrThrow()

        CompletedPaymentState(context)
    }
}
