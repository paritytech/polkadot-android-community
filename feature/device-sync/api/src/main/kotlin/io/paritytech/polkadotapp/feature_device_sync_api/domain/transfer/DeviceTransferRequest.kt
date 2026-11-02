package io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer

import java.util.UUID
import kotlin.time.Instant

data class DeviceTransferRequest(
    val requestId: UUID,
    val deviceModel: String,
    val operatingSystem: String,
    val createdAt: Instant,
)
