package io.paritytech.polkadotapp.feature_device_sync_impl.domain.transfer

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select

suspend fun <T> raceFirst(vararg blocks: suspend () -> T): T = coroutineScope {
    val racers = blocks.map { block -> async { block() } }
    val winner = select { racers.forEach { racer -> racer.onAwait { it } } }
    racers.forEach { it.cancel() }
    winner
}
