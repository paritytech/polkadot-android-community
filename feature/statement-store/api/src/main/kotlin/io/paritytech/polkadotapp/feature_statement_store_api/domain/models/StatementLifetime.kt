package io.paritytech.polkadotapp.feature_statement_store_api.domain.models

import kotlin.time.Duration

sealed interface StatementLifetime {
    data object Permanent : StatementLifetime

    data class Limited(val duration: Duration) : StatementLifetime
}
