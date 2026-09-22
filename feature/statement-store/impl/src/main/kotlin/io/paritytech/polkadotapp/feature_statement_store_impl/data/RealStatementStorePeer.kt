package io.paritytech.polkadotapp.feature_statement_store_impl.data

import io.paritytech.polkadotapp.chains.multiNetwork.KnownChains
import io.paritytech.polkadotapp.chains.multiNetwork.chain.model.ChainId
import io.paritytech.polkadotapp.feature_statement_store_api.domain.StatementStorePeer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealStatementStorePeer @Inject constructor(knownChains: KnownChains) : StatementStorePeer {
    override val chainId: ChainId = knownChains.people

    private val answering = MutableStateFlow(0)

    override fun observeAnswered(): Flow<Boolean> = answering.map { it > 0 }.distinctUntilChanged()

    fun <T> track(pages: Flow<Result<T>>): Flow<Result<T>> = flow {
        var counted = false

        try {
            pages.collect { page ->
                if (!counted && page.isSuccess) {
                    counted = true
                    answering.update { it + 1 }
                }

                emit(page)
            }
        } finally {
            if (counted) answering.update { it - 1 }
        }
    }
}
