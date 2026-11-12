package io.paritytech.polkadotapp.feature_chats_impl.data.repository

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.intoAccountId
import io.paritytech.polkadotapp.common.domain.model.requireX25519PublicKey
import io.paritytech.polkadotapp.common.utils.mapList
import io.paritytech.polkadotapp.common.utils.mapToUnit
import io.paritytech.polkadotapp.database.dao.ContactDeviceDao
import io.paritytech.polkadotapp.database.model.ContactDeviceLocal
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ChatStateForSync
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ContactAccountId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ContactDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import kotlin.time.Instant

interface ContactDevicesRepository {
    suspend fun getDevices(contactAccountId: ContactAccountId): List<ContactDevice>

    fun subscribeDevices(contactAccountId: ContactAccountId): Flow<List<ContactDevice>>

    fun subscribeAllDevices(): Flow<Map<ContactAccountId, List<ContactDevice>>>

    suspend fun addDevice(device: ContactDevice, changedAt: Long)

    suspend fun removeDevice(contactAccountId: ContactAccountId, statementAccountId: AccountId, changedAt: Long)

    suspend fun clearDevicesFor(contactAccountId: ContactAccountId)

    suspend fun getDevicesForSync(changedAfter: Instant?): List<ChatStateForSync.ContactDeviceChange>

    fun observeModifications(): Flow<Unit>

    suspend fun applySyncedDevice(change: ChatStateForSync.ContactDeviceChange)

    suspend fun markDevicesModified(contactAccountId: ContactAccountId)
}

class RealContactDevicesRepository @Inject constructor(
    private val dao: ContactDeviceDao
) : ContactDevicesRepository {
    override suspend fun getDevices(contactAccountId: ContactAccountId): List<ContactDevice> {
        return dao.getActiveByContact(contactAccountId.value).map { it.toDomain() }
    }

    override fun subscribeDevices(contactAccountId: ContactAccountId): Flow<List<ContactDevice>> {
        return dao.subscribeActiveByContact(contactAccountId.value)
            .mapList { it.toDomain() }
    }

    override fun subscribeAllDevices(): Flow<Map<ContactAccountId, List<ContactDevice>>> {
        return dao.subscribeAllActive()
            .mapList { it.toDomain() }
            .map { devices -> devices.groupBy { it.contactAccountId } }
    }

    override suspend fun addDevice(device: ContactDevice, changedAt: Long) {
        dao.upsertIfNewer(device.toLocal(changedAt, modifiedAt = System.currentTimeMillis()))
    }

    override suspend fun removeDevice(
        contactAccountId: ContactAccountId,
        statementAccountId: AccountId,
        changedAt: Long,
    ) {
        dao.deactivateIfOlder(contactAccountId.value, statementAccountId.value, changedAt, modifiedAt = System.currentTimeMillis())
    }

    override suspend fun clearDevicesFor(contactAccountId: ContactAccountId) {
        dao.deleteAllForContact(contactAccountId.value)
    }

    override suspend fun getDevicesForSync(changedAfter: Instant?): List<ChatStateForSync.ContactDeviceChange> {
        return dao.getForSync(modifiedAfter = changedAfter?.toEpochMilliseconds()).map { it.toSyncChange() }
    }

    override fun observeModifications(): Flow<Unit> {
        return dao.observeLastModifiedAt().distinctUntilChanged().mapToUnit()
    }

    override suspend fun applySyncedDevice(change: ChatStateForSync.ContactDeviceChange) {
        dao.upsertSyncedIfNewer(change.toSyncedLocal())
    }

    override suspend fun markDevicesModified(contactAccountId: ContactAccountId) {
        dao.markModified(contactAccountId.value, modifiedAt = System.currentTimeMillis())
    }
}

private fun ContactDeviceLocal.toDomain(): ContactDevice {
    return ContactDevice(
        contactAccountId = contactAccountId.intoAccountId(),
        statementAccountId = statementAccountId.intoAccountId(),
        encryptionPublicKey = encryptionPublicKey.requireX25519PublicKey(),
    )
}

private fun ContactDeviceLocal.toSyncChange(): ChatStateForSync.ContactDeviceChange {
    return ChatStateForSync.ContactDeviceChange(toDomain(), isActive = status == ContactDeviceLocal.Status.ACTIVE, changedAt = changedAt)
}

private fun ChatStateForSync.ContactDeviceChange.toSyncedLocal(): ContactDeviceDao.SyncedDeviceLocal {
    return ContactDeviceDao.SyncedDeviceLocal(
        contactAccountId = device.contactAccountId.value,
        statementAccountId = device.statementAccountId.value,
        encryptionPublicKey = device.encryptionPublicKey.bytes.value,
        status = if (isActive) ContactDeviceLocal.Status.ACTIVE else ContactDeviceLocal.Status.INACTIVE,
        changedAt = changedAt,
    )
}

private fun ContactDevice.toLocal(changedAt: Long, modifiedAt: Long): ContactDeviceLocal {
    return ContactDeviceLocal(
        contactAccountId = contactAccountId.value,
        statementAccountId = statementAccountId.value,
        encryptionPublicKey = encryptionPublicKey.bytes.value,
        status = ContactDeviceLocal.Status.ACTIVE,
        changedAt = changedAt,
        modifiedAt = modifiedAt,
    )
}
