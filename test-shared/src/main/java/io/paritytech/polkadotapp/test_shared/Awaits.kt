package io.paritytech.polkadotapp.test_shared

import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val AWAIT_TIMEOUT = 2.seconds

val LONG_WAIT = 10.minutes

suspend fun <T> awaitCompletes(block: suspend () -> T): T = withTimeout(AWAIT_TIMEOUT) { block() }
