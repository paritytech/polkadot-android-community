package io.paritytech.polkadotapp.feature_chats_impl.data.model

import io.paritytech.polkadotapp.chains.network.binding.intoBalance
import io.paritytech.polkadotapp.chains.network.binding.orZero
import io.paritytech.polkadotapp.database.model.CoinageTransferDetectionLocal
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatMessageId
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.CoinageTransferDetection
import io.paritytech.polkadotapp.feature_coinage_api.domain.model.detectedOrNull

fun CoinageTransferDetection.toLocal(messageId: ChatMessageId): CoinageTransferDetectionLocal {
    return CoinageTransferDetectionLocal(
        messageId = messageId,
        status = localStatus(),
        transferredPlanks = detectedOrNull()?.value
    )
}

fun CoinageTransferDetectionLocal.toDomain(): CoinageTransferDetection {
    return when (status) {
        CoinageTransferDetectionLocal.Status.DETECTED ->
            CoinageTransferDetection.Detected(transferredPlanks?.intoBalance().orZero())

        CoinageTransferDetectionLocal.Status.TRANSFERRED ->
            CoinageTransferDetection.Transferred(transferredPlanks?.intoBalance().orZero())
        CoinageTransferDetectionLocal.Status.FAILED_DETECTION -> CoinageTransferDetection.Error.Detection
        CoinageTransferDetectionLocal.Status.FAILED_TRANSFER -> CoinageTransferDetection.Error.Transfer
    }
}

private fun CoinageTransferDetection.localStatus(): CoinageTransferDetectionLocal.Status {
    return when (this) {
        CoinageTransferDetection.Detecting -> error("Cannot save Detecting state to database")
        is CoinageTransferDetection.Detected -> CoinageTransferDetectionLocal.Status.DETECTED
        is CoinageTransferDetection.Transferred -> CoinageTransferDetectionLocal.Status.TRANSFERRED
        CoinageTransferDetection.Error.Detection -> CoinageTransferDetectionLocal.Status.FAILED_DETECTION
        CoinageTransferDetection.Error.Transfer -> CoinageTransferDetectionLocal.Status.FAILED_TRANSFER
    }
}
