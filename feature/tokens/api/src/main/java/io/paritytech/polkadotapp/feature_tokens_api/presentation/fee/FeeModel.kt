package io.paritytech.polkadotapp.feature_tokens_api.presentation.fee

import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.TokenAmountModel
import io.paritytech.polkadotapp.feature_transactions.api.domain.model.Fee

class FeeModel(val fee: Fee, val displayAmount: TokenAmountModel)
