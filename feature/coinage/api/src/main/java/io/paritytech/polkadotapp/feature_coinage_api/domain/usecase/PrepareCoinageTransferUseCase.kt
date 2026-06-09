package io.paritytech.polkadotapp.feature_coinage_api.domain.usecase

import io.paritytech.polkadotapp.common.utils.flatMap
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.TransferMemo
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.TransferPlan
import java.math.BigDecimal

interface PrepareCoinageTransferUseCase {
    suspend fun preparePlan(amount: BigDecimal): Result<TransferPlan>

    suspend fun prepareMemo(plan: TransferPlan): Result<TransferMemo>
}

suspend fun PrepareCoinageTransferUseCase.prepareMemo(amount: BigDecimal) = preparePlan(amount).flatMap { prepareMemo(it) }
