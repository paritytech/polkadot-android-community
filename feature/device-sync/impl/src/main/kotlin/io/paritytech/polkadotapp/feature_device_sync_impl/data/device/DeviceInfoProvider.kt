package io.paritytech.polkadotapp.feature_device_sync_impl.data.device

import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class DeviceInfo(
    val model: String,
    val operatingSystem: String,
)

interface DeviceInfoProvider {
    fun current(): DeviceInfo
}

class RealDeviceInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceInfoProvider {
    override fun current(): DeviceInfo {
        return DeviceInfo(
            model = userVisibleDeviceName() ?: Build.MODEL,
            operatingSystem = "$OPERATING_SYSTEM_NAME ${Build.VERSION.RELEASE}",
        )
    }

    private fun userVisibleDeviceName(): String? {
        return Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val OPERATING_SYSTEM_NAME = "Android"
    }
}
