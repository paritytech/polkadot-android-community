package io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.debug

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.paritytech.polkadotapp.design.components.spacer.HorizontalSpacer
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import io.paritytech.polkadotapp.feature_wallet_impl.presentation.pocket.compose.components.digitalDollar.CoinageWidgetCard

/**
 * TEMPORARY — TODO: delete with the rest of the `debug` package. See [CoinageTestDataMode].
 *
 * Material's radio button rather than a design-system component: there is no `Polkadot*` or `Nova*` radio,
 * and throwaway scaffolding is not a reason to add one.
 */
@Composable
internal fun CoinageTestDataSwitch(
    modifier: Modifier = Modifier,
    selected: CoinageTestDataMode,
    onSelected: (CoinageTestDataMode) -> Unit
) {
    CoinageWidgetCard(
        modifier = modifier,
        title = "Test data",
        subtitle = "Debug only. Replaces the balance and holdings below with generated ones."
    ) {
        CoinageTestDataMode.entries.forEach { mode ->
            TestDataOption(
                label = mode.label,
                selected = mode == selected,
                onClick = { onSelected(mode) }
            )
        }
    }
}

@Composable
private fun TestDataOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Null handler: the whole row is the target, and a second one would double-report the selection.
        RadioButton(selected = selected, onClick = null)

        HorizontalSpacer { small }

        NovaText(
            text = label,
            style = PolkadotTheme.typography.body.medium,
            color = PolkadotTheme.colors.fg.primary
        )
    }
}
