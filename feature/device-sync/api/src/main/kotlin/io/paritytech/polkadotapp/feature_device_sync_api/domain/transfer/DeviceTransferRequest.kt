package io.paritytech.polkadotapp.feature_device_sync_api.domain.transfer

import kotlin.time.Instant

data class DeviceTransferRequest(
    val requestId: String,
    val deviceModel: String,
    val operatingSystem: String,
    val createdAt: Instant,
)
