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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
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

private enum class NavBarSlot { Center, Items, Indicator }

@Composable
fun PolkadotNavigationBar(
    selectedIndex: Int,
    itemCount: Int,
    modifier: Modifier = Modifier,
    shape: Shape = PolkadotTheme.shapes.full,
    centerContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    require(centerContent == null || itemCount % 2 == 0) {
        "PolkadotNavigationBar centerContent requires an even itemCount, but was $itemCount"
    }

    val animatedIndex = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = 700f
        ),
        label = "PolkadotNavigationBarSelectedIndex"
    )

    PolkadotSurface(
        modifier = modifier,
        shape = shape,
        color = PolkadotTheme.colors.bg.surface.container,
        border = BorderStroke(
            width = PolkadotTheme.borders.default,
            color = PolkadotTheme.colors.stroke.primary
        )
    ) {
        val indicatorColor = PolkadotTheme.colors.bg.surface.nested
        val indicatorShape = PolkadotTheme.shapes.full
        val overshoot = PolkadotTheme.spacings.tiny

        SubcomposeLayout(
            modifier = Modifier.padding(
                horizontal = PolkadotTheme.spacings.small,
                vertical = PolkadotTheme.spacings.tiny
            )
        ) { constraints ->
            val overshootPx = overshoot.roundToPx()
            val width = constraints.maxWidth

            val centerPlaceable = centerContent?.let {
                subcompose(NavBarSlot.Center, it).first()
                    .measure(constraints.copy(minWidth = 0, minHeight = 0))
            }
            val centerWidth = centerPlaceable?.width ?: 0
            val halfWidth = (width - centerWidth) / 2
            val itemsWidth = if (centerPlaceable != null) halfWidth * 2 else width
            val slotWidth = itemsWidth.toFloat() / itemCount.coerceAtLeast(1)

            val itemSlotWidth = slotWidth.roundToInt()
            val itemPlaceables = subcompose(NavBarSlot.Items, content).map {
                it.measure(constraints.copy(minWidth = itemSlotWidth, maxWidth = itemSlotWidth))
            }

            val height = maxOf(
                itemPlaceables.maxOfOrNull { it.height } ?: 0,
                centerPlaceable?.height ?: 0
            )

            val indicatorWidth = (slotWidth + overshootPx * 2).roundToInt().coerceAtLeast(0)
            val indicatorPlaceable = subcompose(NavBarSlot.Indicator) {
                Box(modifier = Modifier.background(color = indicatorColor, shape = indicatorShape))
            }.first().measure(Constraints.fixed(indicatorWidth, height))

            val halfCount = itemCount / 2

            layout(width, height) {
                val gapOffset = if (centerPlaceable != null) {
                    val gapFraction = (animatedIndex.value - (halfCount - 1)).coerceIn(0f, 1f)
                    centerWidth * gapFraction
                } else {
                    0f
                }
                val indicatorX = (slotWidth * animatedIndex.value + gapOffset - overshootPx).roundToInt()
                indicatorPlaceable.place(indicatorX, 0)

                itemPlaceables.forEachIndexed { index, placeable ->
                    val gap = if (centerPlaceable != null && index >= halfCount) centerWidth else 0
                    placeable.place((slotWidth * index).roundToInt() + gap, (height - placeable.height) / 2)
                }

                centerPlaceable?.place(halfWidth, (height - centerPlaceable.height) / 2)
            }
        }
    }
}

@Composable
fun PolkadotNavigationBarItem(
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

@Preview
@Composable
private fun PolkadotNavigationBarCenterPreview() {
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
                itemCount = tabs.size,
                centerContent = {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = PolkadotTheme.spacings.small)
                            .size(IconSize)
                            .background(
                                color = PolkadotTheme.colors.bg.surface.nested,
                                shape = CircleShape
                            )
                    )
                }
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
