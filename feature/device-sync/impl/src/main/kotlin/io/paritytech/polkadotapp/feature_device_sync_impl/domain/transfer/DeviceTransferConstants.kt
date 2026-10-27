package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer

import io.paritytech.polkadotapp.common.utils.InformationSize
import io.paritytech.polkadotapp.common.utils.InformationSize.Companion.kilobytes
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

object DeviceTransferConstants {
    val STATEMENT_LIFETIME: Duration = 15.minutes
    val MAX_STATEMENT_SIZE: InformationSize = 2.kilobytes
}
