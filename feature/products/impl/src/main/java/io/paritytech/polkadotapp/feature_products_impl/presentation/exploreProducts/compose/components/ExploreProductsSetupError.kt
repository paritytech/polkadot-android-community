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
import io.paritytech.polkadotapp.feature_products_impl.domain.exploreProducts.ExploreSetupFailure
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
fun ExploreProductsSetupError(
    modifier: Modifier = Modifier,
    kind: ExploreSetupFailure,
    onRetry: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NovaText(
            text = kind.title(),
            style = PolkadotTheme.typography.headline.small,
            color = PolkadotTheme.colors.fg.primary,
            textAlign = TextAlign.Center,
        )
        VerticalSpacer { small }
        NovaText(
            text = kind.message(),
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

@Composable
private fun ExploreSetupFailure.title(): String {
    return when (this) {
        ExploreSetupFailure.NOT_FOUND -> stringResource(RCommon.string.product_load_not_found_title)
        ExploreSetupFailure.UNREACHABLE -> stringResource(RCommon.string.product_load_unreachable_title)
        ExploreSetupFailure.DAMAGED -> stringResource(RCommon.string.product_load_damaged_title)
        ExploreSetupFailure.CONNECTION -> stringResource(RCommon.string.common_error_url_title)
        ExploreSetupFailure.UNKNOWN -> stringResource(RCommon.string.common_error_general_title)
    }
}

@Composable
private fun ExploreSetupFailure.message(): String {
    return when (this) {
        ExploreSetupFailure.NOT_FOUND -> stringResource(RCommon.string.product_load_not_found_message)
        ExploreSetupFailure.UNREACHABLE -> stringResource(RCommon.string.product_load_unreachable_message)
        ExploreSetupFailure.DAMAGED -> stringResource(RCommon.string.product_load_damaged_message)
        ExploreSetupFailure.CONNECTION, ExploreSetupFailure.UNKNOWN -> stringResource(RCommon.string.common_error_try_later)
    }
}

@Composable
private fun SetupErrorPreview(kind: ExploreSetupFailure) {
    PolkadotTheme {
        PolkadotSurface {
            ExploreProductsSetupError(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = PolkadotTheme.spacings.extraLarge),
                kind = kind,
                onRetry = {},
            )
        }
    }
}

@Preview
@Composable
private fun NotFoundPreview() {
    SetupErrorPreview(ExploreSetupFailure.NOT_FOUND)
}

@Preview
@Composable
private fun UnreachablePreview() {
    SetupErrorPreview(ExploreSetupFailure.UNREACHABLE)
}

@Preview
@Composable
private fun DamagedPreview() {
    SetupErrorPreview(ExploreSetupFailure.DAMAGED)
}

@Preview
@Composable
private fun ConnectionPreview() {
    SetupErrorPreview(ExploreSetupFailure.CONNECTION)
}

@Preview
@Composable
private fun UnknownPreview() {
    SetupErrorPreview(ExploreSetupFailure.UNKNOWN)
}
