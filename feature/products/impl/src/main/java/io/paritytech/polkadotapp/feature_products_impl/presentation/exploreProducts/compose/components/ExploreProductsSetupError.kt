package io.paritytech.polkadotapp.feature_products_impl.presentation.exploreProducts.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonStyle
import io.paritytech.polkadotapp.design.components.button.default.PolkadotTextButton
import io.paritytech.polkadotapp.design.components.spacer.VerticalSpacer
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
fun ExploreProductsSetupError(
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NovaText(
            text = stringResource(RCommon.string.common_error_general_title),
            style = PolkadotTheme.typography.headline.small,
            color = PolkadotTheme.colors.fg.primary,
            textAlign = TextAlign.Center,
        )
        VerticalSpacer { small }
        NovaText(
            text = stringResource(RCommon.string.common_error_try_later),
            style = PolkadotTheme.typography.paragraph.large,
            color = PolkadotTheme.colors.fg.secondary,
            textAlign = TextAlign.Center,
        )
        VerticalSpacer { large }
        PolkadotTextButton(
            text = stringResource(RCommon.string.common_retry),
            style = PolkadotButtonStyle.secondary(),
            onClick = onRetry,
        )
    }
}

@Preview
@Composable
private fun ExploreProductsSetupErrorPreview() {
    PolkadotTheme {
        PolkadotSurface {
            ExploreProductsSetupError(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = PolkadotTheme.spacings.extraLarge),
                onRetry = {},
            )
        }
    }
}
