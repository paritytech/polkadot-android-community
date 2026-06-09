package io.paritytech.polkadotapp.design.components.navigationbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import io.paritytech.polkadotapp.design.components.icon.NovaIcon
import io.paritytech.polkadotapp.design.components.icon.NovaIcons
import io.paritytech.polkadotapp.design.components.icon.vectors.ChatFilled
import io.paritytech.polkadotapp.design.components.icon.vectors.MoneyFilled
import io.paritytech.polkadotapp.design.components.icon.vectors.Search
import io.paritytech.polkadotapp.design.components.icon.vectors.Settings
import io.paritytech.polkadotapp.design.components.spacer.VerticalSpacer
import io.paritytech.polkadotapp.design.components.surface.PolkadotSurface
import io.paritytech.polkadotapp.design.components.text.NovaText
import io.paritytech.polkadotapp.design.theme.PolkadotTheme
import kotlin.math.roundToInt

private val IconSize = 28.dp
private val NotificationDotSize = 8.dp

@Composable
fun PolkadotNavigationBar(
    selectedIndex: Int,
    itemCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val animatedIndex = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "PolkadotNavigationBarSelectedIndex"
    )

    PolkadotSurface(
        modifier = modifier,
        shape = PolkadotTheme.shapes.full,
        color = PolkadotTheme.colors.bg.surface.container,
        border = BorderStroke(
            width = PolkadotTheme.borders.default,
            color = PolkadotTheme.colors.stroke.primary
        )
    ) {
        val indicatorColor = PolkadotTheme.colors.bg.surface.nested
        val indicatorShape = PolkadotTheme.shapes.full
        val overshoot = PolkadotTheme.spacings.tiny

        Layout(
            modifier = Modifier.padding(
                horizontal = PolkadotTheme.spacings.small,
                vertical = PolkadotTheme.spacings.tiny
            ),
            content = {
                Box(
                    modifier = Modifier.background(color = indicatorColor, shape = indicatorShape)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }
        ) { measurables, constraints ->
            val overshootPx = overshoot.roundToPx()
            val itemsPlaceable = measurables[1].measure(constraints)
            val height = itemsPlaceable.height
            val slotWidth = itemsPlaceable.width.toFloat() / itemCount.coerceAtLeast(1)
            val indicatorWidth = (slotWidth + overshootPx * 2).roundToInt().coerceAtLeast(0)
            val indicatorPlaceable = measurables[0].measure(Constraints.fixed(indicatorWidth, height))

            layout(itemsPlaceable.width, height) {
                val x = (slotWidth * animatedIndex.value - overshootPx).roundToInt()
                indicatorPlaceable.place(x, 0)
                itemsPlaceable.place(0, 0)
            }
        }
    }
}

@Composable
fun RowScope.PolkadotNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    hasNotification: Boolean = false
) {
    val targetColor = if (selected) PolkadotTheme.colors.fg.primary else PolkadotTheme.colors.fg.secondary
    val contentColor by animateColorAsState(
        targetValue = targetColor,
        label = "PolkadotNavigationBarItemContentColor"
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(
                horizontal = PolkadotTheme.spacings.small,
                vertical = PolkadotTheme.spacings.tiny
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box {
            NovaIcon(
                modifier = Modifier
                    .padding(horizontal = PolkadotTheme.spacings.small)
                    .size(IconSize),
                imageVector = icon,
                tint = contentColor
            )

            NotificationDot(
                visible = hasNotification,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
        VerticalSpacer { extraTiny }
        NovaText(
            text = label,
            style = PolkadotTheme.typography.label.smallEmphasized,
            color = contentColor
        )
    }
}

@Composable
private fun NotificationDot(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        modifier = modifier,
        visible = visible,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .size(NotificationDotSize)
                .background(
                    color = PolkadotTheme.colors.bg.status.warning,
                    shape = CircleShape
                )
        )
    }
}

@Preview
@Composable
private fun PolkadotNavigationBarPreview() {
    val tabs = listOf(
        "Chats" to NovaIcons.ChatFilled,
        "Pocket" to NovaIcons.MoneyFilled,
        "Explore" to NovaIcons.Search,
        "Settings" to NovaIcons.Settings
    )
    var selectedIndex by remember { mutableIntStateOf(0) }

    PolkadotTheme {
        Box(
            modifier = Modifier.background(Color.Black)
        ) {
            PolkadotNavigationBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PolkadotTheme.spacings.extraMedium),
                selectedIndex = selectedIndex,
                itemCount = tabs.size
            ) {
                tabs.forEachIndexed { index, (title, icon) ->
                    PolkadotNavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        icon = icon,
                        label = title,
                        hasNotification = title == "Settings"
                    )
                }
            }
        }
    }
}
