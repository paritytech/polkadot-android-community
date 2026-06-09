package io.paritytech.polkadotapp.feature_products_impl.presentation.resourceAllocationRequest.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.paritytech.polkadotapp.common.presentation.loading.onLoaded
import io.paritytech.polkadotapp.design.components.bottomsheet.NovaBottomSheetSurface
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonStyle
import io.paritytech.polkadotapp.design.components.button.default.PolkadotTextButton
import io.paritytech.polkadotapp.design.components.spacer.VerticalSpacer
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_products_impl.presentation.resourceAllocationRequest.ResourceAllocationRequestContract
import io.paritytech.polkadotapp.feature_products_impl.presentation.resourceAllocationRequest.ResourceAllocationRequestUiState
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
fun ResourceAllocationRequestScreen(contract: ResourceAllocationRequestContract) {
    val state by contract.state.collectAsStateWithLifecycle()

    state.onLoaded { data ->
        ResourceAllocationRequestScreenInternal(
            state = data,
            onApprove = contract::onApproveClicked,
            onReject = contract::onRejectClicked,
        )
    }
}

@Composable
private fun ResourceAllocationRequestScreenInternal(
    state: ResourceAllocationRequestUiState,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    NovaBottomSheetSurface {
        Column(
            modifier = Modifier.padding(
                top = PolkadotTheme.spacings.large,
                bottom = PolkadotTheme.spacings.mediumIncreased,
                start = PolkadotTheme.spacings.mediumIncreased,
                end = PolkadotTheme.spacings.mediumIncreased,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NovaText(
                text = stringResource(
                    RCommon.string.product_resource_allocation_title,
                    state.productId,
                ),
                style = PolkadotTheme.typography.title.large,
                color = PolkadotTheme.colors.fg.primary,
            )

            VerticalSpacer { mediumIncreased }

            Column(verticalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.tiny)) {
                state.resourceLabels.forEach { labelRes ->
                    NovaText(
                        text = stringResource(RCommon.string.product_resource_allocation_bullet, stringResource(labelRes)),
                        style = PolkadotTheme.typography.body.medium,
                        color = PolkadotTheme.colors.fg.secondary,
                    )
                }
            }

            VerticalSpacer { extraLarge }

            PolkadotTextButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(RCommon.string.product_resource_allocation_approve),
                style = PolkadotButtonStyle.secondary(),
                onClick = onApprove,
            )

            VerticalSpacer { small }

            PolkadotTextButton(
                modifier = Modifier.fillMaxWidth(),
                text = stringResource(RCommon.string.product_resource_allocation_reject),
                style = PolkadotButtonStyle.ghost(),
                onClick = onReject,
            )
        }
    }
}

@Preview
@Composable
private fun ResourceAllocationRequestScreenPreview() {
    PolkadotTheme {
        ResourceAllocationRequestScreenInternal(
            state = ResourceAllocationRequestUiState(
                productId = "alice.dot",
                resourceLabels = listOf(
                    RCommon.string.product_resource_allocation_bulletin,
                    RCommon.string.product_resource_allocation_statement_store,
                ),
            ),
            onApprove = {},
            onReject = {},
        )
    }
}
