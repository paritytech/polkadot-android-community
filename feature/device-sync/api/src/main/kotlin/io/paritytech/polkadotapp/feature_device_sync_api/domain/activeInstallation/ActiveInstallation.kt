package io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation

import io.paritytech.polkadotapp.common.domain.model.DataByteArray
import kotlin.time.Instant

data class ActiveInstallation(
    val installationId: DataByteArray,
    val deviceModel: String,
    val operatingSystem: String,
    val activatedAt: Instant,
)
