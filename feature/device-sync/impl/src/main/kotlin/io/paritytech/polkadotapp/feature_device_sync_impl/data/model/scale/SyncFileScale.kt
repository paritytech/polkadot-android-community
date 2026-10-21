package io.paritytech.polkadotapp.feature_device_sync_impl.data.model.scale

import io.novasama.substrate_sdk_android.koltinx_serialization_scale.binary.annotations.EnumIndex
import kotlinx.serialization.Serializable

/** Payload of the `device-sync-file` data channel use case (File Transfer RFC). */
@Serializable
sealed interface SyncFileMessageScale {
    @Serializable
    @EnumIndex(0)
    class FilesRequest(val request: FilesRequestScale) : SyncFileMessageScale

    @Serializable
    @EnumIndex(1)
    class Header(val header: FileHeaderScale) : SyncFileMessageScale

    @Serializable
    @EnumIndex(2)
    class Chunk(val chunk: FileChunkScale) : SyncFileMessageScale

    @Serializable
    @EnumIndex(3)
    class Unavailable(val unavailable: FileUnavailableScale) : SyncFileMessageScale

    @Serializable
    @EnumIndex(4)
    class CancelFilesRequest(val requestId: UInt) : SyncFileMessageScale

    @Serializable
    @EnumIndex(5)
    class Completed(val requestId: UInt) : SyncFileMessageScale

    @Serializable
    @EnumIndex(6)
    class AvailabilityRequest(val request: FilesAvailabilityRequestScale) : SyncFileMessageScale

    @Serializable
    @EnumIndex(7)
    class AvailabilityResponse(val response: FilesAvailabilityResponseScale) : SyncFileMessageScale
}

@Serializable
sealed interface FileReferenceScale {
    @Serializable
    @EnumIndex(0)
    class MessageAttachment(val attachment: MessageAttachmentIdScale) : FileReferenceScale
}

@Serializable
class MessageAttachmentIdScale(
    val messageId: String,
    val identifier: ByteArray,
)

@Serializable
class FilesRequestScale(
    val requestId: UInt,
    val files: List<FileRequestItemScale>,
)

@Serializable
class FileRequestItemScale(
    val file: FileReferenceScale,
    val offset: ULong,
)

@Serializable
class FileHeaderScale(
    val requestId: UInt,
    val fileIndex: UInt,
    val totalSize: ULong,
)

@Serializable
class FileChunkScale(
    val requestId: UInt,
    val fileIndex: UInt,
    val offset: ULong,
    val data: ByteArray,
)

@Serializable
class FileUnavailableScale(
    val requestId: UInt,
    val fileIndex: UInt,
    val reason: FileUnavailableReasonScale,
)

@Serializable
enum class FileUnavailableReasonScale {
    @EnumIndex(0)
    NOT_STORED,

    @EnumIndex(1)
    DOWNLOADING,
}

@Serializable
class FilesAvailabilityRequestScale(
    val requestId: UInt,
    val files: List<FileReferenceScale>,
)

@Serializable
class FilesAvailabilityResponseScale(
    val requestId: UInt,
    val availability: List<FileAvailabilityScale>,
)

@Serializable
sealed interface FileAvailabilityScale {
    @Serializable
    @EnumIndex(0)
    class Available(val totalSize: ULong) : FileAvailabilityScale

    @Serializable
    @EnumIndex(1)
    class Unavailable(val reason: FileUnavailableReasonScale) : FileAvailabilityScale
}
