package io.paritytech.polkadotapp.feature_settings_impl.presentation.forceReclaim.compose

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonShape
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonStyle
import io.paritytech.polkadotapp.design.components.button.default.PolkadotTextButton
import io.paritytech.polkadotapp.design.components.spacer.VerticalSpacer
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.components.topbar.PolkadotTopBar
import io.paritytech.polkadotapp.design.components.topbar.TopBarTitleAlignment
import io.paritytech.polkadotapp.design.components.topbar.rememberTopBarAction
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.design.utils.collectAsEffect
import io.paritytech.polkadotapp.feature_settings_impl.presentation.forceReclaim.ForceReclaimContract
import io.paritytech.polkadotapp.feature_settings_impl.presentation.forceReclaim.ForceReclaimEvent
import io.paritytech.polkadotapp.feature_settings_impl.presentation.forceReclaim.ForceReclaimUiState
import io.paritytech.polkadotapp.feature_tokens_api.presentation.formatter.LocalTokenAmountFormatter
import io.paritytech.polkadotapp.feature_tokens_api.presentation.model.RoundPrecision
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
fun ForceReclaimScreen(contract: ForceReclaimContract) {
    val state by contract.state.collectAsStateWithLifecycle()
    val tokenAmountFormatter = LocalTokenAmountFormatter.current

    contract.reclaimEvents.collectAsEffect { context, event ->
        val message = when (event) {
            is ForceReclaimEvent.Reclaimed -> context.getString(
                RCommon.string.revoke_payments_success,
                tokenAmountFormatter.formatTokenAmount(event.amount, RoundPrecision.DEFAULT),
            )

            ForceReclaimEvent.NothingToReclaim -> context.getString(RCommon.string.revoke_payments_nothing)

            ForceReclaimEvent.Error -> context.getString(RCommon.string.generic_error_notification)
        }

        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    ForceReclaimScreenInternal(
        state = state,
        onBackClick = contract::onBackClick,
        onReclaimClick = contract::onReclaimClick,
    )
}

@Composable
private fun ForceReclaimScreenInternal(
    state: ForceReclaimUiState,
    onBackClick: () -> Unit,
    onReclaimClick: () -> Unit,
) {
    PolkadotSurface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            PolkadotTopBar(
                title = stringResource(RCommon.string.revoke_payments_toolbar_title),
                titleAlignment = TopBarTitleAlignment.Center,
                navigationAction = rememberTopBarAction(onBackClick),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = PolkadotTheme.spacings.mediumIncreased),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                NovaText(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(RCommon.string.revoke_payments_title),
                    style = PolkadotTheme.typography.headline.small,
                    color = PolkadotTheme.colors.fg.primary,
                    textAlign = TextAlign.Center,
                )

                VerticalSpacer { large }

                NovaText(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(RCommon.string.revoke_payments_description),
                    style = PolkadotTheme.typography.paragraph.large,
                    color = PolkadotTheme.colors.fg.secondary,
                    textAlign = TextAlign.Center,
                )

                VerticalSpacer { large }

                NovaText(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(RCommon.string.revoke_payments_disclaimer),
                    style = PolkadotTheme.typography.paragraph.large,
                    color = PolkadotTheme.colors.fg.tertiary,
                    textAlign = TextAlign.Center,
                )

                VerticalSpacer { large }

                PolkadotTextButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = stringResource(RCommon.string.revoke_payments_button),
                    style = PolkadotButtonStyle.primary(),
                    shape = PolkadotButtonShape.pill,
                    loading = state.isReclaiming,
                    onClick = onReclaimClick,
                )
            }
        }
    }
}

@Preview
@Composable
private fun ForceReclaimScreenPreview() {
    PolkadotTheme {
        ForceReclaimScreenInternal(
            state = ForceReclaimUiState(isReclaiming = false),
            onBackClick = {},
            onReclaimClick = {},
        )
    }
}
