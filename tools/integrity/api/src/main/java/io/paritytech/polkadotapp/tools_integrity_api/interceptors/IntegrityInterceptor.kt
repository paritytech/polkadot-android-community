package io.paritytech.polkadotapp.tools_integrity_api.interceptors

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import retrofit2.Invocation

abstract class IntegrityInterceptor : Interceptor {
    abstract fun buildInterceptedRequest(request: Request): Request

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val newRequest =
            if (checkIfNeedToIntercept(chain)) buildInterceptedRequest(request) else request
        return chain.proceed(newRequest)
    }

    private fun checkIfNeedToIntercept(chain: Interceptor.Chain): Boolean {
        val request = chain.request()
        val invocation = request.tag(Invocation::class.java)
        return CallWithIntegrity() in (invocation?.method()?.annotations ?: arrayOf())
    }
}
