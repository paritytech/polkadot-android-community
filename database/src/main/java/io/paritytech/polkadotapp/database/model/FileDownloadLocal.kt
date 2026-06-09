package io.paritytech.polkadotapp.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "file_downloads",
    indices = [
        Index(value = ["chatId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = ChatMessageLocal::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ChatRoomLocal::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
class FileDownloadLocal(
    @PrimaryKey val messageId: String,
    val chatId: ByteArray,
    val identifier: ByteArray,
    val ticket: ByteArray,
    @ColumnInfo(defaultValue = "")
    val nodeUrl: String,
    val mimeType: String,
    val filePath: String?,
    val downloadedChunks: Int,
    /**
     * Comma-separated hex-encoded blake2b-256 hashes of all chunks.
     * Populated once after metadata claim. Use [downloadedChunks] as offset on resume.
     */
    val chunkHashes: String?,
    val status: Status,
    val errorCategory: ErrorCategory?,
    val errorCause: String?,
    val createdAt: Long
) {
    enum class Status {
        PENDING, IN_PROGRESS, DONE, FAILED
    }

    enum class ErrorCategory {
        NETWORK,
        HOP_ERROR,
        FILE_WRITE_ERROR,
        UNKNOWN
    }
}
