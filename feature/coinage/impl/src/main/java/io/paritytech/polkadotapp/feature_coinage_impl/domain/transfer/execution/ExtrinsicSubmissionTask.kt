package io.paritytech.polkadotapp.feature_coinage_impl.domain.transfer.execution

import io.paritytech.polkadotapp.feature_coinage_impl.domain.model.CoinageTransaction
import io.paritytech.polkadotapp.feature_transactions.api.data.FormExtrinsic
import io.paritytech.polkadotapp.feature_transactions.api.domain.model.TransactionOrigin

class ExtrinsicSubmissionTask(
    val walId: String,
    val origin: TransactionOrigin,
    val formExtrinsic: FormExtrinsic,
    val transaction: CoinageTransaction
)
