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
) {
    sealed interface Location {
        data object Unknown : Location
        data object Onboarding : Location
        data class InRecycler(
            val recyclerIndex: RecyclerIndex
        ) : Location
    }
}

fun RecyclerVoucher.tokenAmount() = recyclerValue.tokenAmount()

fun RecyclerVoucher.isInRecycler() = location is RecyclerVoucher.Location.InRecycler

fun RecyclerVoucher.recyclerLocationOrThrow() = location as RecyclerVoucher.Location.InRecycler

private fun RecyclerVoucher.canBeUnloadedAt(timestamp: Timestamp) = delayUnloadUntil < timestamp

/**
 * Spendable without giving anything away. Neither missing half stops the voucher being used — an unload
 * before its delay, or from a ring too small to hide it, only makes it easier to link back to the coin it
 * came from — so both merely lower it to degraded.
 */
fun RecyclerVoucher.isReadyToUseSecured(timestamp: Timestamp) =
    isInRecycler() && canBeUnloadedAt(timestamp) && ringHasEnoughRingMembersToWithdraw

fun List<RecyclerVoucher>.filterInRecycler(): List<RecyclerVoucher> {
    return filter { it.isInRecycler() }
}
