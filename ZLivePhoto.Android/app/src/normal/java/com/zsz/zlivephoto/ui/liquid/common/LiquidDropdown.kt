package com.zsz.zlivephoto.ui.liquid.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zsz.zlivephoto.ui.FancyEasing
import com.zsz.zlivephoto.ui.rememberHapticFeedback
import io.github.neoruaa.glasmio.core.IosTypography
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.icons.LocalSfToolbarSymbols
import io.github.neoruaa.glasmio.icons.SfIcon
import io.github.neoruaa.glasmio.ui.GlasmioListRow
import io.github.neoruaa.glasmio.ui.GlasmioMenu
import io.github.neoruaa.glasmio.ui.GlasmioMenuItem
import io.github.neoruaa.glasmio.ui.menuDragRow
import io.github.neoruaa.glasmio.ui.menuDragSelect
import io.github.neoruaa.glasmio.ui.rememberMenuDragSelection

/**
 * 设置项统一下拉行：左侧标题，右侧当前值 + Glasmio 双箭头，点击在行下方展开菜单。
 *
 * 菜单直接使用 Glasmio 的 [GlasmioMenu] / [GlasmioMenuItem]：
 * - 玻璃材质、阴影、按压高亮全部由 Glasmio 渲染；
 * - 展开/收起为 Glasmio 自带的弹簧形变动画（不再是首帧直接出现）；
 * - 菜单宽度按最长选项实测宽度计算（+ 行内留白 + 对勾位），文字完整显示；
 * - 选中项对勾放在**前置图标槽**：LTR 时在左、RTL 时自动到右（与系统菜单一致）；
 * - 菜单高度超过安全区域时由 Glasmio 自动夹紧并内部滚动，不会遮挡/溢出屏幕。
 */
@Composable
fun <T> LiquidDropdownRow(
    title: String,
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = rememberHapticFeedback()
    val labels = options.map(label)

    // 菜单宽度 = 最长选项文本 + 左右 16dp + 图标槽 20dp + 间距 12dp + 对勾 18dp
    // + GlasmioMenu 容器左右各 10dp 内边距 + 余量。
    val measurer = rememberTextMeasurer()
    val textStyle = IosTypography.body
    val density = LocalDensity.current
    val windowWidth = LocalWindowInfo.current.containerSize.width
    val menuWidth = remember(labels, textStyle, density, windowWidth) {
        val textPx = labels.maxOfOrNull { measurer.measure(AnnotatedString(it), textStyle).size.width } ?: 0
        val textDp = with(density) { textPx.toDp() }
        val screenMax = with(density) { windowWidth.toDp() } - 24.dp
        // 分屏/自由窗口下窗口宽度可能小于 224dp，此时 min > max 会让 coerceIn 抛
        // IllegalArgumentException（菜单一打开就崩）。先夹出合法的 max，再夹 min。
        val maxWidth = minOf(320.dp, screenMax).coerceAtLeast(120.dp)
        val minWidth = minOf(200.dp, maxWidth)
        (textDp + 102.dp).coerceIn(minWidth, maxWidth)
    }

    // 形变锚点只包住**右侧当前值 + 箭头**：左侧标题不参与形变
    // （用户反馈「玻璃外观」等行展开时连标题文字一起被拉伸）
    // 同时关掉默认按压指示：点击这一行只需要展开菜单，不需要整行泛出灰色点触层。
    CompositionLocalProvider(LocalIndication provides NoPressIndication) {
    GlasmioListRow(
        title = title,
        modifier = modifier,
        leading = leading,
        enabled = enabled,
        onClick = if (enabled) {
            {
                haptic.click()
                expanded = !expanded
            }
        } else null,
        trailing = {
            GlasmioMenu(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                itemCount = options.size,
                menuWidth = menuWidth,
                // 值本身是被形变的源内容，展开后不再重复显示
                keepAnchorVisible = false,
                morphFromAnchor = true,
                alignToAnchorStart = false,
                expandFromAnchor = Alignment.End,
                anchor = {
                    DropdownValue(value = label(selected), expanded = expanded)
                }
            ) { childBackdrop, close ->
                // 菜单展开时让滚动位置靠近当前选中项（每项约 44dp，末尾项由 ScrollState 自行夹紧）
                val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
                val initialScroll = with(density) { (selectedIndex * 44).dp.roundToPx() }
                // 按住拖动选择：一行按下后可跨行移动，松手落在手指所在行（形变跟随手指）
                val dragSelection = rememberMenuDragSelection()
                val rowHeightPx = with(density) { 44.dp.toPx() }
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState(initial = initialScroll))
                        .menuDragSelect(
                            selection = dragSelection,
                            indexAt = { position ->
                                (position.y / rowHeightPx).toInt().takeIf { it in options.indices }
                            },
                            onActivate = { index ->
                                haptic.click()
                                onSelected(options[index])
                                close()
                            },
                        )
                ) {
                    options.forEachIndexed { index, option ->
                        GlasmioMenuItem(
                            title = labels[index],
                            modifier = Modifier.menuDragRow(index, dragSelection),
                            onClick = {
                                haptic.click()
                                onSelected(option)
                                close()
                            },
                            backdrop = childBackdrop,
                            // 对勾走前置图标槽：LTR 在左、RTL 在右
                            systemName = if (index == options.indexOf(selected)) "checkmark" else null,
                            iconTint = LocalGlassColors.current.accent,
                        )
                    }
                }
            }
        }
    )
    }
}

/** 无按压指示：Glasmio 组件自带交互高光，普通列表行不需要额外的灰色按压层。 */
private object NoPressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : Modifier.Node(), DrawModifierNode {
            override fun ContentDrawScope.draw() {
                drawContent()
            }
        }

    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = System.identityHashCode(this)
}

/** 行右侧：当前值（切换时轻微滚动淡入）+ 单箭头（默认向下，展开时旋转向上）。 */
@Composable
private fun DropdownValue(value: String, expanded: Boolean) {
    val colors = LocalGlassColors.current
    // 展开/收起改为弹簧旋转，避免直接跳变
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f),
        label = "dropdownArrowRotation"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                (slideInVertically(tween(220, easing = FancyEasing)) { it / 2 } + fadeIn(tween(160))) togetherWith
                    (slideOutVertically(tween(220, easing = FancyEasing)) { -it / 2 } + fadeOut(tween(120)))
            },
            label = "dropdownValue"
        ) { text ->
            Text(
                text,
                // 不再固定 140dp 上限：长语言名（如「Português (Brasil)」）会被截成「…」。
                // 放宽到 200dp，并在超长时自动缩小字号，保证完整显示而不是省略号。
                modifier = Modifier.widthIn(max = 200.dp),
                style = IosTypography.body,
                color = colors.secondaryContent,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 17.sp),
            )
        }
        Spacer(Modifier.width(6.dp))
        CompositionLocalProvider(LocalSfToolbarSymbols provides true) {
            SfIcon(
                "chevron.down",
                contentDescription = null,
                modifier = Modifier
                    .size(width = 14.dp, height = 18.dp)
                    .graphicsLayer { rotationZ = rotation },
                size = 18.dp,
                fontSize = 18.sp,
                tint = colors.secondaryContent,
                weight = FontWeight.Normal,
                fontFeatureSettings = "'ss15' 1",
            )
        }
    }
}
