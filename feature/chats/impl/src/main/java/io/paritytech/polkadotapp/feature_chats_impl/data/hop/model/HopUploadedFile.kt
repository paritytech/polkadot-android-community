package io.paritytech.polkadotapp.feature_chats_impl.data.hop.model

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.serializers.ByteArraySerializable
import kotlinx.serialization.Serializable

/**
 * Metadata blob stored in HOP pool that references all chunk hashes.
 * SCALE-encoded, encrypted, and submitted as the final pool entry.
 * Its hash becomes the `identifier` in [P2PMixnetFile].
 */
@Serializable
class HopUploadedFile(
    val totalSize: ULong,
    val chunksHashes: List<ByteArraySerializable>
)
