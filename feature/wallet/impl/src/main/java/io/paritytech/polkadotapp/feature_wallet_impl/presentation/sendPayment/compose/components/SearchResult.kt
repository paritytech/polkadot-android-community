package io.paritytech.polkadotapp.feature_wallet_impl.presentation.sendPayment.compose.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.paritytech.polkadotapp.common.presentation.loading.LoadingState
import io.paritytech.polkadotapp.design.components.avatar.NovaContactItem
import io.paritytech.polkadotapp.design.components.avatar.NovaContactItemType
import io.paritytech.polkadotapp.feature_account_api.presentation.address.model.ExtractedAddress
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.sendPayment.PaymentSearchResultUiModel
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun SearchResult(
    loadingState: LoadingState.Loaded<ImmutableList<PaymentSearchResultUiModel>>,
    onRecipientSelect: (PaymentSearchResultUiModel) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth()
    ) {
        itemsIndexed(
            items = loadingState.data,
            key = { _, v -> v.extractedAddress.display }
        ) { index, uiModel ->
            val candidate = uiModel.extractedAddress
            val onClick = remember(uiModel) { { onRecipientSelect(uiModel) } }

            val itemType = when (candidate.type) {
                ExtractedAddress.DisplayType.USERNAME -> NovaContactItemType.User
                ExtractedAddress.DisplayType.ADDRESS -> NovaContactItemType.Address
            }
            NovaContactItem(
                title = candidate.display,
                type = itemType,
                avatarModel = uiModel.avatarModel,
                onClick = onClick
            )
        }
    }
}
