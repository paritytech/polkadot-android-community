package io.paritytech.polkadotapp.feature_products_impl.presentation.spaBrowser.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonStyle
import io.paritytech.polkadotapp.design.components.button.default.PolkadotTextButton
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.ChatFilled
import io.paritytech.polkadotapp.design.components.icon.vectors.Retry
import io.paritytech.polkadotapp.design.components.icon.vectors.Share
import io.paritytech.polkadotapp.design.components.spacer.VerticalSpacer
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
fun BrowserMenuContent(
    canOpenChat: Boolean,
    onDismiss: () -> Unit,
    onOpenChatClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onShareClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = PolkadotTheme.spacings.mediumIncreased),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (canOpenChat) {
            BrowserMenuOption(
                text = stringResource(RCommon.string.spa_browser_menu_open_chat),
                icon = NovaIcons.ChatFilled,
                color = PolkadotTheme.colors.fg.primary,
                onClick = onOpenChatClick,
            )
        }

        BrowserMenuOption(
            text = stringResource(RCommon.string.spa_browser_menu_refresh),
            icon = NovaIcons.Retry,
            color = PolkadotTheme.colors.fg.primary,
            onClick = onRefreshClick,
        )

        BrowserMenuOption(
            text = stringResource(RCommon.string.spa_browser_menu_share),
            icon = NovaIcons.Share,
            color = PolkadotTheme.colors.fg.primary,
            onClick = onShareClick,
        )

        VerticalSpacer { mediumIncreased }

        PolkadotTextButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PolkadotTheme.spacings.large),
            style = PolkadotButtonStyle.ghost(),
            text = stringResource(RCommon.string.common_cancel),
            onClick = onDismiss,
        )
    }
}

@Composable
private fun BrowserMenuOption(
    text: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                vertical = 10.dp,
                horizontal = PolkadotTheme.spacings.mediumIncreased,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.extraMedium),
    ) {
        NovaIcon(imageVector = icon, tint = color)
        NovaText(text = text, style = PolkadotTheme.typography.body.large, color = color)
    }
}
