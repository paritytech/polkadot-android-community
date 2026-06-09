package io.paritytech.polkadotapp.feature_chats_transport_protocol.scale

import androidx.annotation.Keep
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.annotations.FixedLength
import kotlinx.serialization.Serializable

@Serializable
@Keep
class DeviceInfoScale(
    @FixedLength(32)
    val statementAccountId: ByteArray,
    @FixedLength(65)
    val encryptionPublicKey: ByteArray,
)
