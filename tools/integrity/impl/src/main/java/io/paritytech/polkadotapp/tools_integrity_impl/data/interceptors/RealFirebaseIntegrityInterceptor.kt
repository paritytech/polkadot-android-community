package io.paritytech.polkadotapp.tools_integrity_impl.data.interceptors

import io.paritytech.polkadotapp.tools_integrity_api.interceptors.FirebaseIntegrityInterceptor
import io.paritytech.polkadotapp.tools_integrity_impl.data.integrity.FirebaseIntegrityManager
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import timber.log.Timber

class RealFirebaseIntegrityInterceptor(
    private val firebaseIntegrityManger: FirebaseIntegrityManager
) : FirebaseIntegrityInterceptor() {
    companion object {
        private const val HEADER_TOKEN = "X-Access-Token"
    }

    override fun buildInterceptedRequest(request: Request): Request {
        return runBlocking {
            val newRequest = request.newBuilder()
            try {
                newRequest.addHeader(HEADER_TOKEN, firebaseIntegrityManger.getToken().getOrThrow())
            } catch (e: Exception) {
                Timber.e(e)
            }

            newRequest.build()
        }
    }
}
