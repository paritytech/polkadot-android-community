package io.paritytech.polkadotapp.common.utils.permissions

import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.paritytech.polkadotapp.common.data.storage.preferences.Preferences
import io.paritytech.polkadotapp.common.presentation.resources.ContextManager
import javax.inject.Inject

class PermissionStateManager @Inject constructor(
    private val contextManager: ContextManager,
    private val preferences: Preferences
) {
    fun getPermissionState(permission: String): PermissionResult {
        if (ContextCompat.checkSelfPermission(contextManager.applicationContext, permission) == PackageManager.PERMISSION_GRANTED) {
            return PermissionResult.GRANTED
        }

        return resolveDenial(shouldShowRationale(permission), wasDeniedBefore(permission))
    }

    fun onPermissionResult(permission: String, isGranted: Boolean): PermissionResult {
        if (isGranted) {
            setDeniedBefore(permission, false)
            return PermissionResult.GRANTED
        }

        val result = resolveDenial(shouldShowRationale(permission), wasDeniedBefore(permission))
        setDeniedBefore(permission, true)
        return result
    }

    // No rationale means either never asked or permanently denied; only an earlier denial tells them apart.
    private fun resolveDenial(shouldShowRationale: Boolean, wasDeniedBefore: Boolean): PermissionResult {
        return when {
            shouldShowRationale -> PermissionResult.DENIED

            wasDeniedBefore -> PermissionResult.DENIED_FOREVER

            else -> PermissionResult.DENIED
        }
    }

    private fun shouldShowRationale(permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(contextManager.requireActivity(), permission)
    }

    private fun setDeniedBefore(permission: String, denied: Boolean) {
        preferences.putBoolean(getKey(permission), denied)
    }

    private fun wasDeniedBefore(permission: String): Boolean {
        return preferences.getBoolean(getKey(permission), false)
    }

    private fun getKey(permission: String) = "permission_denied ($permission)"
}
