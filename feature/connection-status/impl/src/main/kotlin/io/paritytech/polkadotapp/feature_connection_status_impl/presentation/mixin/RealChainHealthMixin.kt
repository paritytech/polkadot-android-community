package io.paritytech.polkadotapp.feature_connection_status_impl.presentation.mixin

import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.common.data.memory.ComputationalScope
import io.paritytech.polkadotapp.common.presentation.AppLifecycleObserver
import io.paritytech.polkadotapp.common.presentation.subscribeIsForeground
import io.paritytech.polkadotapp.common.utils.stateInBackground
import io.paritytech.polkadotapp.feature_connection_status_api.domain.ChainHealthMonitor
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainConnectionPresentation
import io.paritytech.polkadotapp.feature_connection_status_api.domain.model.ChainHealth
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicator
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthIndicatorsModel
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthItemModel
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.ChainHealthMixin
import io.paritytech.polkadotapp.feature_connection_status_api.presentation.mixin.IndicatorRow
import io.paritytech.polkadotapp.feature_connection_status_impl.presentation.mixin.mapper.isAnchorPending
import io.paritytech.polkadotapp.feature_connection_status_impl.presentation.mixin.mapper.statementStoreIndicator
import io.paritytech.polkadotapp.feature_connection_status_impl.presentation.mixin.mapper.toIndicator
import io.paritytech.polkadotapp.feature_statement_store_api.domain.StatementStorePeer
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.scan

@OptIn(ExperimentalCoroutinesApi::class)
internal class RealChainHealthMixin(
    scope: ComputationalScope,
    monitor: ChainHealthMonitor,
    private val peer: StatementStorePeer,
    private val knownChains: KnownChains,
    appLifecycleObserver: AppLifecycleObserver,
) : ChainHealthMixin, ComputationalScope by scope {
    override val model: StateFlow<ChainHealthIndicatorsModel> = appLifecycleObserver.subscribeIsForeground()
        .flatMapLatest { inForeground ->
            if (inForeground) {
                combine(monitor.observeChainsHealth(), peer.observeAnswered(), ::Reading)
            } else {
                emptyFlow()
            }
        }
        .scan(EMPTY_MODEL) { previous, reading -> reading.toModel(previous) }
        .stateInBackground(SharingStarted.Eagerly, EMPTY_MODEL)

    private fun Reading.toModel(previous: ChainHealthIndicatorsModel): ChainHealthIndicatorsModel {
        val chains = healths.map { health -> toItem(health, previous) }
        val store = ChainHealthItemModel(row = IndicatorRow.StatementStore, indicator = storeIndicator(previous))

        return ChainHealthIndicatorsModel((chains + store).toImmutableList())
    }

    private fun Reading.storeIndicator(previous: ChainHealthIndicatorsModel): ChainHealthIndicator {
        val health = healths.firstOrNull { it.chainId == peer.chainId }
        val stillConnected = health?.connection == ChainConnectionPresentation.Connected
        val wasAnswering = previous.rows.firstOrNull { it.row == IndicatorRow.StatementStore }?.indicator == UNMEASURED

        // One subscription handing over to its replacement leaves nothing answered for a moment; that gap
        // is not the peer going away, and only losing the chain underneath it is.
        return if (stillConnected && wasAnswering) UNMEASURED else health.statementStoreIndicator(answered)
    }

    private fun toItem(health: ChainHealth, previous: ChainHealthIndicatorsModel): ChainHealthItemModel {
        val row = rowFor(health.chainId)
        val indicator = health.toIndicator()
        val shown = previous.rows.firstOrNull { it.row == row }
            // While a connected chain is being asked how fast it has been going, what it showed before stands.
            ?.takeIf { indicator == UNMEASURED && health.isAnchorPending() && it.indicator.isProduction() }
            ?.indicator
            ?: indicator

        return ChainHealthItemModel(row = row, indicator = shown)
    }

    private fun ChainHealthIndicator.isProduction(): Boolean = when (this) {
        is ChainHealthIndicator.Healthy, is ChainHealthIndicator.Production, ChainHealthIndicator.Outage -> true
        ChainHealthIndicator.Connecting, ChainHealthIndicator.Disconnected, ChainHealthIndicator.Offline -> false
    }

    private fun rowFor(chainId: ChainId): IndicatorRow = when (chainId) {
        knownChains.assetHub -> IndicatorRow.AssetHub
        knownChains.bulletIn -> IndicatorRow.Bulletin
        else -> IndicatorRow.People
    }

    private data class Reading(val healths: List<ChainHealth>, val answered: Boolean)

    private companion object {
        val EMPTY_MODEL = ChainHealthIndicatorsModel(persistentListOf())
        val UNMEASURED = ChainHealthIndicator.Healthy(liveness = null)
    }
}
