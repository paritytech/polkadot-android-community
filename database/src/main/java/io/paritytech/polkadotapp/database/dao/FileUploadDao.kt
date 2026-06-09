package io.paritytech.polkadotapp.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.paritytech.polkadotapp.database.model.FileUploadLocal

@Dao
interface FileUploadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(upload: FileUploadLocal)

    @Query("SELECT * FROM file_uploads WHERE status IN ('PENDING', 'IN_PROGRESS') ORDER BY createdAt ASC LIMIT 1")
    suspend fun getNextPending(): FileUploadLocal?

    @Query("UPDATE file_uploads SET status = 'IN_PROGRESS' WHERE messageId = :messageId")
    suspend fun markInProgress(messageId: String)

    @Query("UPDATE file_uploads SET fileSize = :fileSize, totalChunks = :totalChunks WHERE messageId = :messageId")
    suspend fun updateFileInfo(messageId: String, fileSize: Long, totalChunks: Int)

    @Query("UPDATE file_uploads SET uploadedChunks = :uploadedChunks, chunkHashes = :chunkHashes WHERE messageId = :messageId")
    suspend fun updateProgress(messageId: String, uploadedChunks: Int, chunkHashes: String)

    @Query("UPDATE file_uploads SET status = 'DONE' WHERE messageId = :messageId")
    suspend fun markDone(messageId: String)

    @Query("UPDATE file_uploads SET status = 'FAILED', errorCategory = :errorCategory, errorCause = :errorCause WHERE messageId = :messageId")
    suspend fun markFailed(messageId: String, errorCategory: FileUploadLocal.ErrorCategory, errorCause: String)
}
