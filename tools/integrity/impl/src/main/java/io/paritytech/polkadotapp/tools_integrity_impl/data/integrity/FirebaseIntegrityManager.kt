package io.paritytech.polkadotapp.tools_integrity_impl.data.integrity

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import io.paritytech.polkadotapp.tools_integrity_impl.BuildConfig
import jakarta.inject.Inject
import kotlinx.coroutines.tasks.await

interface FirebaseIntegrityManager {
    fun init()

    suspend fun getToken(): Result<String>
}

class RealFirebaseIntegrityManager @Inject constructor() : FirebaseIntegrityManager {
    override fun init() {
        val factory = if (BuildConfig.DEBUG) {
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }

        Firebase.appCheck.installAppCheckProviderFactory(factory)
    }

    override suspend fun getToken(): Result<String> {
        val result = Firebase.appCheck.limitedUseToken.await()
        val error = result.error
        return if (error != null) {
            Result.failure(error)
        } else {
            Result.success(result.token)
        }
    }
}
