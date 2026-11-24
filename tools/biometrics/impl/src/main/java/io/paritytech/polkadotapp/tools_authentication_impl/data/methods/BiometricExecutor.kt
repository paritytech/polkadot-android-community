package io.paritytech.polkadotapp.tools_authentication_impl.data.methods

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import io.paritytech.polkadotapp.common.utils.ActivityResultExecutor
import io.paritytech.polkadotapp.tools_authentication_api.domain.AuthenticationFailedException

class BiometricExecutor(
    private val activity: ComponentActivity,
) : ActivityResultExecutor<Unit>(activity) {
    override fun createIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(
                    Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    BIOMETRIC_WEAK or DEVICE_CREDENTIAL
                )
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    override fun handleResult(result: ActivityResult): Result<Unit> {
        val keyguardManager = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        return if (keyguardManager.isDeviceSecure) {
            Result.success(Unit)
        } else {
            Result.failure(AuthenticationFailedException("Cannot use the app without device lock set"))
        }
    }
}
