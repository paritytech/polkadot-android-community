package io.paritytech.polkadotapp.common.utils

import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers as BuiltInDispatchers

@Singleton
class CoroutineDispatchers @Inject constructor() {
    val main: CoroutineDispatcher = BuiltInDispatchers.Main
    val io: CoroutineDispatcher = BuiltInDispatchers.IO
    val computation: CoroutineDispatcher = BuiltInDispatchers.Default
}
