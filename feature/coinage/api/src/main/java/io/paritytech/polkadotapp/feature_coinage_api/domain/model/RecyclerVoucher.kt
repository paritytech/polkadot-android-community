package io.paritytech.polkadotapp.feature_coinage_api.domain.model

import io.paritytech.polkadotapp.bandersnatch_crypto.BandersnatchPublicKey
import io.paritytech.polkadotapp.common.domain.model.Timestamp
import io.paritytech.polkadotapp.feature_members_api.data.model.RingIndex

typealias RingVrfIndex = Int
typealias RecyclerIndex = RingIndex

data class RecyclerVoucher(
    val ringVrfKeyIndex: RingVrfIndex,
    val ringVrfPublicKey: BandersnatchPublicKey,
    val recyclerValue: ValueExponent,
    val location: Location,
    val allocatedAt: Timestamp,
    val delayUnloadUntil: Timestamp,
    val ringHasEnoughRingMembersToWithdraw: Boolean,
    val usageState: UsageState
) {
    sealed interface Location {
        data object Unknown : Location
        data object Onboarding : Location
        data class InRecycler(
            val recyclerIndex: RecyclerIndex
        ) : Location
    }

    enum class UsageState {
        USED_LOCALLY,
        USED_ON_CHAIN,
        NOT_USED
    }
}

fun RecyclerVoucher.tokenAmount() = recyclerValue.tokenAmount()

fun RecyclerVoucher.isInRecycler() = location is RecyclerVoucher.Location.InRecycler

fun RecyclerVoucher.recyclerLocationOrThrow() = location as RecyclerVoucher.Location.InRecycler

private fun RecyclerVoucher.canBeUnloadedAt(timestamp: Timestamp) = delayUnloadUntil < timestamp

fun RecyclerVoucher.isNotUsed() = usageState == RecyclerVoucher.UsageState.NOT_USED

fun RecyclerVoucher.isReadyToUse() = isNotUsed() && isInRecycler()

fun RecyclerVoucher.isReadyToUseSecured(timestamp: Timestamp) =
    // !!! Do not forget to also enabled ignored tests in RealTotalBalanceUseCaseTest and TransferPlannerTest
    isReadyToUse() && canBeUnloadedAt(timestamp) && ringHasEnoughRingMembersToWithdraw

fun List<RecyclerVoucher>.filterReadyNowSecured(): List<RecyclerVoucher> {
    val now = System.currentTimeMillis()
    return filter { it.isReadyToUseSecured(now) }
}
