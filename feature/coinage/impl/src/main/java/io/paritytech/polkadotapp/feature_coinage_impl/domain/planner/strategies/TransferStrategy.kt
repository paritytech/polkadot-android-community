package io.paritytech.polkadotapp.feature_coinage_impl.domain.planner.strategies

import io.paritytech.polkadotapp.feature_coinage_api.domain.model.PlannedMemoEntry

sealed interface TransferStrategy {
    /**
     * Executes the transfer, owning every mutation (allocation, marking coins spent, marking vouchers used) via
     * [io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CoinageTransaction]. Returns the memo entries
     * describing the coins handed to the recipient, built from the coins actually allocated here.
     */
    suspend fun run(): Result<List<PlannedMemoEntry>>
}
