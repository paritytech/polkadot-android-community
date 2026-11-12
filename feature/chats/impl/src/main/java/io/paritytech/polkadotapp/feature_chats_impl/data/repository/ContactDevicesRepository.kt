package io.paritytech.polkadotapp.feature_chats_impl.data.repository

import io.paritytech.polkadotapp.common.domain.model.AccountId
import io.paritytech.polkadotapp.common.domain.model.intoAccountId
import io.paritytech.polkadotapp.common.domain.model.requireX25519PublicKey
import io.paritytech.polkadotapp.common.utils.mapList
import io.paritytech.polkadotapp.database.dao.ContactDeviceDao
import io.paritytech.polkadotapp.database.model.ContactDeviceLocal
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ContactAccountId
import io.paritytech.polkadotapp.feature_chats_api.domain.model.ContactDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface ContactDevicesRepository {
    suspend fun getDevices(contactAccountId: ContactAccountId): List<ContactDevice>

    fun subscribeDevices(contactAccountId: ContactAccountId): Flow<List<ContactDevice>>

    fun subscribeAllDevices(): Flow<Map<ContactAccountId, List<ContactDevice>>>

    suspend fun addDevice(device: ContactDevice, changedAt: Long)

    suspend fun removeDevice(contactAccountId: ContactAccountId, statementAccountId: AccountId, changedAt: Long)

    suspend fun clearDevicesFor(contactAccountId: ContactAccountId)
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
}

private fun ContactDeviceLocal.toDomain(): ContactDevice {
    return ContactDevice(
        contactAccountId = contactAccountId.intoAccountId(),
        statementAccountId = statementAccountId.intoAccountId(),
        encryptionPublicKey = encryptionPublicKey.requireX25519PublicKey(),
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
