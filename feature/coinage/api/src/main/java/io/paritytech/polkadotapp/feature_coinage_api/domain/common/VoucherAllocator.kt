package io.paritytech.polkadotapp.feature_coinage_api.domain.common

import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.DerivationIndex
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.RecyclerVoucher
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.ValueExponent

interface VoucherAllocator {
    suspend fun allocate(valueExponent: ValueExponent): Result<RecyclerVoucher>

    suspend fun allocateAll(valueExponents: List<ValueExponent>): Result<List<RecyclerVoucher>>

    suspend fun deallocate(indexes: List<DerivationIndex>)
}

suspend fun <T> VoucherAllocator.withTransactionalAllocation(
    valueExponents: List<ValueExponent>,
    process: suspend (List<RecyclerVoucher>) -> Result<T>,
): Result<T> {
    return allocateAll(valueExponents).flatMap { vouchers ->
        process(vouchers).onFailure { deallocate(vouchers.map { it.ringVrfKeyIndex }) }
    }
}
