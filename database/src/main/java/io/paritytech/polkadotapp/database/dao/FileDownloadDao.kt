package io.paritytech.polkadotapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.paritytech.polkadotapp.database.model.FileDownloadLocal

@Dao
interface FileDownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: FileDownloadLocal)

    @Query("SELECT * FROM file_downloads WHERE status IN ('PENDING', 'IN_PROGRESS') ORDER BY createdAt ASC LIMIT 1")
    suspend fun getNextPending(): FileDownloadLocal?

    @Query("UPDATE file_downloads SET status = 'IN_PROGRESS' WHERE messageId = :messageId")
    suspend fun markInProgress(messageId: String)

    @Query(
        """UPDATE file_downloads
        SET chunkHashes = :chunkHashes
        WHERE messageId = :messageId"""
    )
    suspend fun saveMetadata(
        messageId: String,
        chunkHashes: String
    )

    @Query(
        """UPDATE file_downloads
        SET downloadedChunks = :downloadedChunks,
            filePath = :filePath
        WHERE messageId = :messageId"""
    )
    suspend fun updateProgress(
        messageId: String,
        downloadedChunks: Int,
        filePath: String
    )

    @Query("UPDATE file_downloads SET status = 'DONE' WHERE messageId = :messageId")
    suspend fun markDone(messageId: String)

    @Query("UPDATE file_downloads SET status = 'FAILED', errorCategory = :errorCategory, errorCause = :errorCause WHERE messageId = :messageId")
    suspend fun markFailed(messageId: String, errorCategory: FileDownloadLocal.ErrorCategory, errorCause: String)
}
