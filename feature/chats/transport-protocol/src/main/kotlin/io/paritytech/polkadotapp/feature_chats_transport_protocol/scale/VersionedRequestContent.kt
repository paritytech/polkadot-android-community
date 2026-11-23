package io.paritytech.polkadotapp.feature_chats_transport_protocol.scale

import androidx.annotation.Keep
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.annotations.EnumIndex
import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.annotations.FixedLength
import io.paritytech.polkadotapp.common.domain.model.scale.X25519PublicKeyScale
import kotlinx.serialization.Serializable

@Serializable
sealed class VersionedRequestContent {
    sealed interface RequestVersion

    @Serializable
    @EnumIndex(0)
    class V1(val content: RequestContentV1) : VersionedRequestContent(), RequestVersion {
        companion object {
            fun new(
                pushToken: TokenContent?,
                welcomeMessage: RichTextContent?
            ): V1 {
                return V1(
                    RequestContentV1(
                        pushToken = pushToken,
                        welcomeMessage = welcomeMessage
                    )
                )
            }
        }
    }

    @Serializable
    @EnumIndex(1)
    class V2(val content: RequestContentV2) : VersionedRequestContent(), RequestVersion {
        companion object {
            fun new(
                identityProof: IdentityProofScale,
                deviceEncPubKey: X25519PublicKeyScale,
                pushToken: TokenContent?,
                welcomeMessage: RichTextContent?
            ): V2 {
                return V2(
                    RequestContentV2(
                        identityProof = identityProof,
                        deviceEncPubKey = deviceEncPubKey,
                        pushToken = pushToken,
                        welcomeMessage = welcomeMessage
                    )
                )
            }
        }
    }

    @Serializable
    @EnumIndex(2)
    class Cancel(val content: RequestContentCancel) : VersionedRequestContent()
}

@Serializable
class RequestContentV1(
    val pushToken: TokenContent?,
    val welcomeMessage: RichTextContent?
)

@Serializable
class RequestContentV2(
    val identityProof: IdentityProofScale,
    val deviceEncPubKey: X25519PublicKeyScale,
    val pushToken: TokenContent?,
    val welcomeMessage: RichTextContent?
)

@Serializable
class RequestContentCancel(
    val identityProof: IdentityProofScale,
)

@Serializable
@Keep
class IdentityProofScale(
    @FixedLength(32)
    val identityAccountId: ByteArray,
    @FixedLength(32)
    val proof: ByteArray,
)
