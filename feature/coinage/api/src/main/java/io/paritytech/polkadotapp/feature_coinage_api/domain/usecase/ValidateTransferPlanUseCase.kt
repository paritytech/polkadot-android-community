package io.paritytech.polkadotapp.feature_coinage_api.domain.usecase

import io.paritytech.polkadotapp.feature_coinage_api.domain.model.TransferPlan
import java.math.BigDecimal

interface ValidateTransferPlanUseCase {
    suspend fun validate(amount: BigDecimal): TransferPlan?
}
