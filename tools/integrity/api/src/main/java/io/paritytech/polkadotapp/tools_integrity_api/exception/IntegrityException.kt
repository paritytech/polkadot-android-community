package io.paritytech.polkadotapp.tools_integrity_api.exception

import retrofit2.HttpException

class IntegrityException : Throwable("Failed to validate app integrity")

fun mapToIntegrityIfNeeded(throwable: Throwable) = if (throwable is HttpException && throwable.response()?.code() == 401) IntegrityException() else throwable
