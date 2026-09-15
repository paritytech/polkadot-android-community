package io.paritytech.polkadotapp.app.logging

import io.paritytech.polkadotapp.common.data.storage.file.FileProvider
import io.paritytech.polkadotapp.common.utils.CoroutineDispatchers
import io.paritytech.polkadotapp.common.utils.logging.CoroutineFileDebugTree
import io.paritytech.polkadotapp.common.utils.logging.LoggerConstants
import io.paritytech.polkadotapp.feature_coinage_impl.domain.COINAGE_LOG_TAG
import io.paritytech.polkadotapp.feature_transactions.api.domain.durable.DURABILITY_LOG_TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoinageFileDebugTree @Inject constructor(
    fileProvider: FileProvider,
    coroutineDispatchers: CoroutineDispatchers
) : CoroutineFileDebugTree(
    coroutineScope = CoroutineScope(SupervisorJob() + coroutineDispatchers.io),
    fallbackLogTag = "Coinage",
    logFile = fileProvider.getFileInScopedStorage("${LoggerConstants.LOGS_DIR}/${LoggerConstants.COINAGE_LOGS_FILE_NAME}")
) {
    private companion object {
        // Durability lines carry the engine's tag: the verdicts and the reads behind them live there, and a
        // coinage log without them shows a status changing for no reason.
        val ROUTED_TAGS = setOf(COINAGE_LOG_TAG, DURABILITY_LOG_TAG)
    }

    override fun isLoggable(tag: String?, priority: Int): Boolean = tag in ROUTED_TAGS
}
