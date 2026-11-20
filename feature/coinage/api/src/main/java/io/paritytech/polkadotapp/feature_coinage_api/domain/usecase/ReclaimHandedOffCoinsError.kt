package io.paritytech.polkadotapp.feature_coinage_api.domain.usecase

sealed class ReclaimHandedOffCoinsError(message: String) : Throwable(message) {
    data object ClaimWindowOpen : ReclaimHandedOffCoinsError("the recipient can still claim the coins")

    data object NothingToReclaim : ReclaimHandedOffCoinsError("none of the coins can be reclaimed")

    class Unknown(override val cause: Throwable) : ReclaimHandedOffCoinsError("failed to reclaim the coins")
}

fun Throwable.asReclaimHandedOffCoinsError(): ReclaimHandedOffCoinsError =
    this as? ReclaimHandedOffCoinsError ?: ReclaimHandedOffCoinsError.Unknown(this)
