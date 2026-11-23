package io.paritytech.polkadotapp.feature_device_sync_impl.data.activeInstallation

import io.novasama.substrate_sdk_android.extensions.fromHex
import io.novasama.substrate_sdk_android.extensions.toHexString
import io.paritytech.polkadotapp.common.data.storage.preferences.Preferences
import io.paritytech.polkadotapp.common.data.storage.preferences.edit
import io.paritytech.polkadotapp.common.domain.model.toDataByteArray
import io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation.ActiveInstallation
import io.paritytech.polkadotapp.feature_device_sync_api.domain.activeInstallation.InstallationStatus
import javax.inject.Inject
import kotlin.time.Instant

class InstallationStatusStorage @Inject constructor(
    private val preferences: Preferences,
) {
    fun getStatus(): InstallationStatus? = when (preferences.getString(KEY_STATUS)) {
        STATUS_ACTIVE -> InstallationStatus.Active
        STATUS_UNREADABLE -> InstallationStatus.Unreadable
        STATUS_INACTIVE -> getActiveInstallation()?.let { InstallationStatus.Inactive(it) }
        else -> null
    }

    fun saveStatus(status: InstallationStatus) {
        when (status) {
            InstallationStatus.Unknown -> Unit
            InstallationStatus.Active -> preferences.putString(KEY_STATUS, STATUS_ACTIVE)
            InstallationStatus.Unreadable -> preferences.putString(KEY_STATUS, STATUS_UNREADABLE)
            is InstallationStatus.Inactive -> saveInactive(status.activeInstallation)
        }
    }

    private fun saveInactive(activeInstallation: ActiveInstallation) {
        preferences.edit {
            putString(KEY_STATUS, STATUS_INACTIVE)
            putString(KEY_INSTALLATION_ID, activeInstallation.installationId.value.toHexString())
            putString(KEY_DEVICE_MODEL, activeInstallation.deviceModel)
            putString(KEY_OPERATING_SYSTEM, activeInstallation.operatingSystem)
            putLong(KEY_ACTIVATED_AT, activeInstallation.activatedAt.toEpochMilliseconds())
        }
    }

    private fun getActiveInstallation(): ActiveInstallation? {
        val installationId = preferences.getString(KEY_INSTALLATION_ID) ?: return null
        val deviceModel = preferences.getString(KEY_DEVICE_MODEL) ?: return null
        val operatingSystem = preferences.getString(KEY_OPERATING_SYSTEM) ?: return null

        return ActiveInstallation(
            installationId = installationId.fromHex().toDataByteArray(),
            deviceModel = deviceModel,
            operatingSystem = operatingSystem,
            activatedAt = Instant.fromEpochMilliseconds(preferences.getLong(KEY_ACTIVATED_AT, 0)),
        )
    }

    private companion object {
        const val KEY_STATUS = "active_installation_status"
        const val KEY_INSTALLATION_ID = "active_installation_status_installation_id"
        const val KEY_DEVICE_MODEL = "active_installation_status_device_model"
        const val KEY_OPERATING_SYSTEM = "active_installation_status_operating_system"
        const val KEY_ACTIVATED_AT = "active_installation_status_activated_at"

        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_INACTIVE = "INACTIVE"
        const val STATUS_UNREADABLE = "UNREADABLE"
    }
}
