package io.paritytech.polkadotapp.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import io.paritytech.polkadotapp.database.model.ContactDeviceLocal
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ContactDeviceDao {
    @Upsert
    protected abstract suspend fun upsert(device: ContactDeviceLocal)

    @Query("SELECT * FROM contact_devices WHERE contactAccountId = :contactAccountId AND statementAccountId = :statementAccountId")
    protected abstract suspend fun get(contactAccountId: ByteArray, statementAccountId: ByteArray): ContactDeviceLocal?

    @Transaction
    open suspend fun upsertIfNewer(device: ContactDeviceLocal) {
        val stored = get(device.contactAccountId, device.statementAccountId)
        if (stored == null || stored.changedAt < device.changedAt) upsert(device)
    }

    @Upsert(entity = ContactDeviceLocal::class)
    protected abstract suspend fun upsertSynced(device: SyncedDeviceLocal)

    @Transaction
    open suspend fun upsertSyncedIfNewer(device: SyncedDeviceLocal) {
        val stored = get(device.contactAccountId, device.statementAccountId)
        if (stored == null || stored.changedAt < device.changedAt) upsertSynced(device)
    }

    @Query("UPDATE contact_devices SET modifiedAt = :modifiedAt WHERE contactAccountId = :contactAccountId")
    abstract suspend fun markModified(contactAccountId: ByteArray, modifiedAt: Long)

    @Query(
        """
        SELECT contact_devices.* FROM contact_devices
        INNER JOIN contacts ON contacts.accountId = contact_devices.contactAccountId
        WHERE contacts.chatRequestId IS NULL
          AND (:modifiedAfter IS NULL OR contact_devices.modifiedAt > :modifiedAfter)
        """
    )
    abstract suspend fun getForSync(modifiedAfter: Long?): List<ContactDeviceLocal>

    @Query("SELECT MAX(modifiedAt) FROM contact_devices")
    abstract fun observeLastModifiedAt(): Flow<Long?>

    @Query(
        """
        UPDATE contact_devices
        SET status = 'INACTIVE', changedAt = :changedAt, modifiedAt = :modifiedAt
        WHERE contactAccountId = :contactAccountId
          AND statementAccountId = :statementAccountId
          AND changedAt < :changedAt
        """
    )
    abstract suspend fun deactivateIfOlder(
        contactAccountId: ByteArray,
        statementAccountId: ByteArray,
        changedAt: Long,
        modifiedAt: Long,
    )

    @Query("SELECT * FROM contact_devices WHERE contactAccountId = :contactAccountId AND status = 'ACTIVE'")
    abstract suspend fun getActiveByContact(contactAccountId: ByteArray): List<ContactDeviceLocal>

    @Query("SELECT * FROM contact_devices WHERE contactAccountId = :contactAccountId AND status = 'ACTIVE'")
    abstract fun subscribeActiveByContact(contactAccountId: ByteArray): Flow<List<ContactDeviceLocal>>

    @Query("SELECT * FROM contact_devices WHERE status = 'ACTIVE'")
    abstract fun subscribeAllActive(): Flow<List<ContactDeviceLocal>>

    @Query("DELETE FROM contact_devices WHERE contactAccountId = :contactAccountId")
    abstract suspend fun deleteAllForContact(contactAccountId: ByteArray)

    class SyncedDeviceLocal(
        val contactAccountId: ByteArray,
        val statementAccountId: ByteArray,
        val encryptionPublicKey: ByteArray,
        val status: ContactDeviceLocal.Status,
        val changedAt: Long,
    )
}
