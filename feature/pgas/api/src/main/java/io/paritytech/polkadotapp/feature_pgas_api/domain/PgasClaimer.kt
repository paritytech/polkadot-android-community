package io.paritytech.polkadotapp.feature_pgas_api.domain

import io.paritytech.polkadotapp.common.domain.model.AccountId

interface PgasClaimer {
    suspend fun claim(destinationAccountId: AccountId, strategy: OnExistingAllocationStrategy): Result<Unit>
}

sealed class PgasClaimError(cause: Throwable?) : Throwable(cause) {
    class NoAllocationAvailable(cause: Throwable) : PgasClaimError(cause)
    class Unknown(cause: Throwable) : PgasClaimError(cause)
}
