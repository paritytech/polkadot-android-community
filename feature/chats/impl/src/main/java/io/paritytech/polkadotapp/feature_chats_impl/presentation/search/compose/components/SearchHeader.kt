package io.paritytech.polkadotapp.feature_chats_impl.presentation.search.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.paritytech.polkadotapp.design.components.button.common.PolkadotButtonStyle
import io.paritytech.polkadotapp.design.components.button.default.PolkadotButtonSize
import io.paritytech.polkadotapp.design.components.button.default.PolkadotTextButton
import io.paritytech.polkadotapp.design.components.button.icon.PolkadotIconButton
import io.paritytech.polkadotapp.design.components.button.icon.PolkadotIconButtonSize
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.Scanner
import io.paritytech.polkadotapp.design.components.icon.vectors.Search
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.components.text.NovaTextField
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.common.R as RCommon

@Composable
internal fun SearchHeader(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onScanClick: () -> Unit,
    onCancelClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PolkadotTheme.spacings.mediumIncreased),
        horizontalArrangement = Arrangement.spacedBy(PolkadotTheme.spacings.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NovaTextField(
            modifier = Modifier.weight(1f),
            value = searchQuery,
            onValueChange = onSearchChange,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search
            ),
            leadingIcon = {
                NovaIcon(
                    imageVector = NovaIcons.Search,
                    contentDescription = "Search",
                    tint = PolkadotTheme.colors.fg.tertiary
                )
            },
            placeholder = {
                NovaText(
                    text = stringResource(RCommon.string.add_contact_search_placeholder),
                    style = PolkadotTheme.typography.body.large,
                    color = PolkadotTheme.colors.fg.tertiary
                )
            }
        )

        PolkadotIconButton(
            icon = NovaIcons.Scanner,
            onClick = onScanClick,
            style = PolkadotButtonStyle.ghost(),
            size = PolkadotIconButtonSize.small()
        )

        PolkadotTextButton(
            text = stringResource(RCommon.string.common_cancel),
            style = PolkadotButtonStyle.ghost(),
            onClick = onCancelClick,
            size = PolkadotButtonSize.medium()
        )
    }
}
