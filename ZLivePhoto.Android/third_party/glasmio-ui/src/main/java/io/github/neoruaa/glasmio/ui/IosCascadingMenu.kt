package io.github.neoruaa.glasmio.ui

import io.github.neoruaa.glasmio.core.*
import io.github.neoruaa.glasmio.glass.*
import io.github.neoruaa.glasmio.icons.*

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.rememberCoroutineScope
import com.kyant.capsule.ContinuousRoundedRectangle
import io.github.neoruaa.glasmio.glass.InteractiveHighlight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import io.github.neoruaa.glasmio.glass.render.Backdrop
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Data adapter for the shared popup components; it does not own another menu style. */
data class GlasmioCascadingMenuItem(
    val title: String,
    val systemName: String? = null,
    val destructive: Boolean = false,
    val separatorBefore: Boolean = false,
    val children: List<GlasmioCascadingMenuItem> = emptyList(),
    val enabled: Boolean = true,
    val onClick: () -> Unit = {},
    val subtitle: String? = null,
    val icon: (@Composable () -> Unit)? = null,
)

/**
 * A dropdown menu sharing the popup lifecycle, glass surface, and interactive rows.
 * Transfers a toolbar or standalone anchor into the menu by default.
 * Disable [morphFromAnchor] for a host that does not use source-preview morphing.
 */
@Composable
fun GlasmioCascadingMenu(
    anchorBounds: Rect?,
    items: List<GlasmioCascadingMenuItem>,
    title: String,
    expandedDescription: String,
    collapsedDescription: String,
    onDismiss: () -> Unit,
    backdrop: Backdrop = LocalBlurBackdrop.current,
    headerActions: List<GlasmioCascadingMenuItem> = emptyList(),
    onTriggerAlphaChanged: ((Float) -> Unit)? = null,
    menuAnchorState: GlasmioMenuAnchorState? = null,
    useAccentIcons: Boolean = true,
    centerHorizontally: Boolean = false,
    expandFromAnchor: Alignment.Horizontal? = null,
    forceBelowAnchor: Boolean = false,
    morphFromAnchor: Boolean = true,
    headerContentHeight: Dp = 0.dp,
    headerContent: (@Composable (execute: (() -> Unit) -> Unit) -> Unit)? = null,
    viewportBounds: Rect? = null,
    itemHeight: Dp = 44.dp,
    separatorHeight: Dp = 12.dp,
) {
    var open by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var childExpanded by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val child = remember { Animatable(0f) }
    val menuScale = remember(child) {
        { 1f - 0.04f * child.value.coerceIn(0f, 1f) }
    }
    var menuBounds by remember { mutableStateOf<Rect?>(null) }
    val density = LocalDensity.current
    val menuScroll = rememberScrollState()
    var selectedBounds by remember { mutableStateOf<Rect?>(null) }
    val colors = LocalGlassColors.current
    val headerRows = if (headerActions.isEmpty()) 0 else 2
    val resolvedHeaderHeight = if (headerContent != null) headerContentHeight
        else if (headerActions.isEmpty()) 0.dp else 76.dp + separatorHeight
    val itemHeights = remember(items, itemHeight) { items.map { menuItemHeight(itemHeight, it.title, it.subtitle) } }
    // 本地补丁：按住拖动选择。rowTops 是每一行在菜单内容坐标系里的顶部（含标题区与分隔线）。
    val dragSelection = rememberMenuDragSelection()
    // 二级面板的按住对象：一级行展开后，即使手指没松开，按压态也要迁移到二级面板
    val childDrag = rememberMenuDragSelection()
    // 一级菜单内容区在屏幕中的原点（用于把手指位置换算到屏幕坐标，再交给二级面板）
    var menuContentOrigin by remember { mutableStateOf(Offset.Zero) }
    // 手指在屏幕中的位置与「是否已离开过展开位置」：回到展开位置停住可收起二级面板
    var childPointer by remember { mutableStateOf<Offset?>(null) }
    var childMovedAway by remember { mutableStateOf(false) }
    val rowHeightsPx = remember(itemHeights, density) { itemHeights.map { with(density) { it.toPx() } } }
    val separatorPx = with(density) { separatorHeight.toPx() }
    val headerPx = with(density) { resolvedHeaderHeight.toPx() }
    val rowTopsPx = remember(items, rowHeightsPx, separatorPx, headerPx) {
        val tops = FloatArray(items.size)
        var y = headerPx
        items.forEachIndexed { index, item ->
            if (item.separatorBefore) y += separatorPx
            tops[index] = y
            y += rowHeightsPx[index]
        }
        tops
    }
    val textMeasurer = rememberTextMeasurer()
    val windowWidth = with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
    val menuWidth = if (headerActions.isEmpty()) {
        val longestRowWidth = items.maxOfOrNull {
            textMeasurer.measure(it.title, style = IosTypography.body, maxLines = if ('\n' in it.title) 2 else 1).size.width
        } ?: 0
        maxOf(if (headerContent != null) 250.dp else 238.dp, with(density) { longestRowWidth.toDp() } + 104.dp)
            .coerceAtMost((windowWidth - 24.dp).coerceAtLeast(1.dp))
    } else {
        val longestActionWidth = headerActions.maxOfOrNull {
            textMeasurer.measure(it.title, style = IosTypography.subheadline, maxLines = 1).size.width
        } ?: 0
        // Include both cells' inner padding and the shared menu's outer content padding.
        val requiredWidth = (with(density) { longestActionWidth.toDp() } + 32.dp) * headerActions.size + 20.dp
        maxOf(280.dp, requiredWidth + 4.dp).coerceAtMost((windowWidth - 24.dp).coerceAtLeast(1.dp))
    }

    LaunchedEffect(Unit) { started = true; open = true }
    LaunchedEffect(childExpanded) {
        child.animateTo(if (childExpanded) 1f else 0f, spring(dampingRatio = 0.78f, stiffness = 240f))
        if (!childExpanded) {
            selectedIndex = null
            selectedBounds = null
        }
    }

    fun close(action: (() -> Unit)? = null) {
        if (!open) return
        pendingAction = action
        childExpanded = false
        open = false
    }

    /** 选中某一行：叶子项直接关闭并执行；有子项的行把二级面板锚到这一行的静止位置。 */
    fun activate(index: Int) {
        val item = items.getOrNull(index) ?: return
        if (!item.enabled) return
        if (item.children.isEmpty()) {
            close(item.onClick)
            return
        }
        menuBounds?.let { bounds ->
            // Use the resting menu layout and the shared row metrics, never
            // the pressed row's transformed screen coordinates.
            val inset = with(density) { 10.dp.toPx() }
            val rowHeight = with(density) { itemHeights[index].toPx() }
            val previousRowsHeight = with(density) {
                resolvedHeaderHeight.toPx() + itemHeights.take(index).fold(0.dp) { total, height -> total + height }.toPx()
            }
            val top = bounds.top + inset + previousRowsHeight - menuScroll.value +
                with(density) { separatorHeight.toPx() } * items.take(index + 1).count { it.separatorBefore }
            selectedBounds = Rect(
                bounds.left + inset, top, bounds.right - inset, top + rowHeight,
            )
            selectedIndex = index
            childExpanded = true
        }
    }

    // 停在有子项的行上（拖动/按住）足够久就展开；二级面板展开期间由 childDrag 接管。
    LaunchedEffect(dragSelection.hoverToken) {
        if (childExpanded) return@LaunchedEffect
        if (!dragSelection.pressing) return@LaunchedEffect
        val index = dragSelection.pressedIndex
        if (index !in items.indices) return@LaunchedEffect
        if (items[index].children.isEmpty()) return@LaunchedEffect
        delay(380)
        if (!dragSelection.pressing || dragSelection.pressedIndex != index) return@LaunchedEffect
        if (selectedIndex == index && childExpanded) childExpanded = false else activate(index)
    }

    // 展开二级面板时手指仍未松开：把按住对象从一级行迁移到二级面板标题行
    LaunchedEffect(childExpanded) {
        childPointer = null
        childMovedAway = false
        if (childExpanded) {
            if (dragSelection.pressing) childDrag.start(0)
        } else if (childDrag.pressing) {
            childDrag.stop()
        }
    }
    // 手指抬起后结束二级面板的按住态（按压态由同一次按压延续，不要求重新按下）
    LaunchedEffect(dragSelection.pressing) {
        if (!dragSelection.pressing && childDrag.pressing) childDrag.stop()
    }

    GlasmioMenu(
        expanded = open,
        onExpandedChange = {
            if (!it) {
                if (childExpanded) childExpanded = false else close()
            }
        },
        itemCount = items.size + headerRows,
        heightOverride = resolvedHeaderHeight + menuContentHeight(itemHeights, items.count { it.separatorBefore }, separatorHeight),
        menuWidth = menuWidth,
        backdrop = backdrop,
        exportChildBackdrop = false,
        externalAnchorBounds = anchorBounds,
        keepAnchorVisible = true,
        fullScreen = true,
        centerHorizontally = centerHorizontally,
        expandFromAnchor = expandFromAnchor,
        forceBelowAnchor = forceBelowAnchor,
        menuScale = menuScale,
        onMenuBoundsChanged = { menuBounds = it },
        onMenuAlphaChanged = { onTriggerAlphaChanged?.invoke(1f - it) },
        menuAnchorState = menuAnchorState,
        morphFromAnchor = morphFromAnchor,
        viewportBounds = viewportBounds,
        onClosed = {
            if (started && !open) {
                onDismiss()
                pendingAction?.invoke()
            }
        },
        // Keep the child in the existing full-screen Popup. Its origin is measured
        // before selection so the parent row and child header hand off in one frame.
        overlayContent = { menuAlpha ->
            var windowOrigin by remember { mutableStateOf<Offset?>(null) }
            BoxWithConstraints(
                Modifier.fillMaxSize().onGloballyPositioned {
                    windowOrigin = it.localToScreen(Offset.Zero)
                }.semantics { paneTitle = title },
                contentAlignment = AbsoluteAlignment.TopLeft,
            ) {
                val selected = selectedIndex?.let(items::getOrNull) ?: return@BoxWithConstraints
                val bounds = selectedBounds ?: return@BoxWithConstraints
                val origin = windowOrigin ?: return@BoxWithConstraints
                val density = LocalDensity.current
                val p = child.value.coerceIn(0f, 1f)
                val topInset = with(density) {
                    (viewportBounds?.top?.minus(origin.y)?.coerceAtLeast(0f)
                        ?: androidx.compose.foundation.layout.WindowInsets.safeDrawing.getTop(this).toFloat()).toDp()
                } + 12.dp
                val bottomInset = with(density) {
                    (viewportBounds?.bottom?.let { constraints.maxHeight - (it - origin.y) }?.coerceAtLeast(0f)
                        ?: androidx.compose.foundation.layout.WindowInsets.safeDrawing.getBottom(this).toFloat()).toDp()
                } + 12.dp
                val selectedRowHeight = menuItemHeight(itemHeight, selected.title, selected.subtitle)
                val collapsedHeight = 20.dp + selectedRowHeight
                val targetHeight = minOf(
                    collapsedHeight + selected.children.fold(0.dp) { total, item -> total + menuItemHeight(itemHeight, item.title, item.subtitle) } +
                        separatorHeight * (1 + selected.children.count { it.separatorBefore }),
                    maxHeight - topInset - bottomInset,
                )
                val rowTop = with(density) { (bounds.top - origin.y).toDp() } - 10.dp
                val targetTop = rowTop.coerceIn(topInset, (maxHeight - bottomInset - targetHeight).coerceAtLeast(topInset))
                val top = rowTop + (targetTop - rowTop) * p
                val left = with(density) { (bounds.left - origin.x).toDp() } - 10.dp
                Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { childExpanded = false })
                GlasmioContextMenu(
                    visible = true,
                    modifier = Modifier.absoluteOffset(left, top).graphicsLayer { alpha = menuAlpha() },
                    backdrop = backdrop,
                    exportChildBackdrop = false,
                    compact = true,
                    menuWidth = menuWidth,
                    heightOverride = collapsedHeight + (targetHeight - collapsedHeight) * p,
                    shadowAlpha = p,
                    highlightAlpha = p,
                    itemCount = selected.children.size + 1,
                ) { childBackdrop ->
                    // 本地补丁：二级面板同样支持按住拖动选择；停在面板标题行上收起。
                    val parentRowPx = with(density) { selectedRowHeight.toPx() }
                    val childSeparatorPx = with(density) { separatorHeight.toPx() }
                    val childHeightsPx = selected.children.map {
                        with(density) { menuItemHeight(itemHeight, it.title, it.subtitle).toPx() }
                    }
                    val childTopsPx = FloatArray(selected.children.size + 1)
                    var childY = parentRowPx + childSeparatorPx
                    selected.children.forEachIndexed { index, item ->
                        if (item.separatorBefore) childY += childSeparatorPx
                        childTopsPx[index + 1] = childY
                        childY += childHeightsPx[index]
                    }
                    // 一级手势持续按住时，把手指位置映射到二级面板的行：
                    // 展开瞬间按住对象就切到二级面板标题行，之后跟随手指在子项间移动。
                    LaunchedEffect(childExpanded, childPointer) {
                        if (!childExpanded) return@LaunchedEffect
                        val window = childPointer ?: return@LaunchedEffect
                        val panelOrigin = origin + Offset(
                            with(density) { left.toPx() },
                            with(density) { top.toPx() },
                        )
                        val localY = window.y - panelOrigin.y
                        val index = when {
                            localY >= 0f && localY < parentRowPx -> 0
                            else -> selected.children.indices.firstOrNull { i ->
                                val rowTop = childTopsPx[i + 1]
                                localY >= rowTop && localY < rowTop + childHeightsPx[i]
                            }?.plus(1)
                        }
                        if (index != null) {
                            if (index != 0) childMovedAway = true
                            childDrag.moveTo(index, Offset.Zero)
                        }
                    }
                    // 手指先移到别的子项、再回到展开位置并停住 380ms → 收起二级面板
                    LaunchedEffect(childDrag.hoverToken) {
                        if (!childExpanded || !childMovedAway) return@LaunchedEffect
                        if (childDrag.pressedIndex != 0) return@LaunchedEffect
                        delay(380)
                        if (childExpanded && childDrag.pressing && childDrag.pressedIndex == 0) {
                            childExpanded = false
                        }
                    }
                    Box(
                        Modifier.weight(1f).fillMaxWidth().menuDragSelect(
                            selection = childDrag,
                            enabled = childExpanded && p > 0.95f,
                            indexAt = { position ->
                                val y = position.y
                                if (y >= 0f && y < parentRowPx) {
                                    0
                                } else {
                                    selected.children.indices.firstOrNull { index ->
                                        val top = childTopsPx[index + 1]
                                        y >= top && y < top + childHeightsPx[index]
                                    }?.plus(1)
                                }
                            },
                            onActivate = { index ->
                                if (index == 0) {
                                    childExpanded = false
                                } else {
                                    val item = selected.children.getOrNull(index - 1)
                                    if (item != null && item.enabled && open) close(item.onClick)
                                }
                            },
                        )
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            GlasmioMenuItem(
                                title = selected.title,
                                subtitle = selected.subtitle,
                                rowHeight = selectedRowHeight,
                                systemName = selected.systemName,
                                icon = selected.icon,
                                iconTint = if (selected.destructive) colors.destructive else if (useAccentIcons) colors.accent else colors.content,
                                destructive = selected.destructive,
                                fontWeight = FontWeight((400 + 200 * p).roundToInt()),
                                backdrop = childBackdrop,
                                enabled = selected.enabled && open,
                                modifier = Modifier.menuDragRow(0, childDrag),
                                trailing = { MenuChevron(90f * p, if (childExpanded) expandedDescription else collapsedDescription) },
                                onClick = { childExpanded = !childExpanded },
                            )
                            Column(Modifier.weight(1f).graphicsLayer {
                                alpha = p
                                translationY = -separatorHeight.toPx() * (1f - p)
                            }.verticalScroll(rememberScrollState())) {
                                GlasmioMenuSeparator(height = separatorHeight, horizontalInset = 12.dp)
                                selected.children.forEachIndexed { index, item ->
                                    if (item.separatorBefore) {
                                        GlasmioMenuSeparator(height = separatorHeight, horizontalInset = 12.dp)
                                    }
                                    GlasmioMenuItem(
                                        title = item.title,
                                        subtitle = item.subtitle,
                                        rowHeight = menuItemHeight(itemHeight, item.title, item.subtitle),
                                        systemName = item.systemName,
                                        icon = item.icon,
                                        iconTint = if (item.destructive) colors.destructive else if (useAccentIcons) colors.accent else colors.content,
                                        backdrop = childBackdrop,
                                        enabled = item.enabled && open && childExpanded && p > 0.95f,
                                        modifier = Modifier.menuDragRow(index + 1, childDrag),
                                        onClick = { close(item.onClick) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        anchor = { Spacer(Modifier.size(1.dp)) },
    ) { childBackdrop, _ ->
        Column(
            Modifier.fillMaxSize()
                .onGloballyPositioned { menuContentOrigin = it.localToScreen(Offset.Zero) }
                .verticalScroll(menuScroll).menuDragSelect(
                selection = dragSelection,
                indexAt = { position ->
                    val y = position.y + menuScroll.value
                    items.indices.firstOrNull { y >= rowTopsPx[it] && y < rowTopsPx[it] + rowHeightsPx[it] }
                },
                onActivate = { index ->
                    if (childExpanded) {
                        // 松开时按住对象已迁移到二级面板：按 childDrag 的行执行
                        val childIndex = childDrag.pressedIndex
                        val parent = selectedIndex?.let(items::getOrNull)
                        if (childIndex != null && parent != null) {
                            if (childIndex == 0) {
                                childExpanded = false
                            } else {
                                parent.children.getOrNull(childIndex - 1)
                                    ?.takeIf { it.enabled }
                                    ?.let { close(it.onClick) }
                            }
                        }
                    } else if (selectedIndex == null) {
                        activate(index)
                    }
                },
                // 手指位置换算到屏幕坐标，供二级面板判断「移到哪个子项 / 回到展开位置」
                onMove = { position ->
                    if (childExpanded) childPointer = menuContentOrigin + position
                },
            )
        ) {
            if (headerContent != null) {
                Column(Modifier.graphicsLayer {
                    alpha = 1f - 0.58f * child.value.coerceIn(0f, 1f)
                }.then(if (selectedIndex != null) Modifier.semantics { hideFromAccessibility() } else Modifier)) {
                    headerContent { action -> close(action) }
                }
            } else if (headerActions.isNotEmpty()) {
                Column(Modifier.graphicsLayer {
                    alpha = 1f - 0.58f * child.value.coerceIn(0f, 1f)
                }.then(if (selectedIndex != null) Modifier.semantics { hideFromAccessibility() } else Modifier)) {
                    Row(Modifier.fillMaxWidth().height(76.dp)) {
                        headerActions.forEach { action ->
                            MenuHeaderAction(
                                action = action,
                                iconTint = if (action.destructive) colors.destructive else if (useAccentIcons) colors.accent else colors.content,
                                enabled = action.enabled && open && selectedIndex == null,
                                modifier = Modifier.weight(1f),
                                onClick = { close(action.onClick) },
                            )
                        }
                    }
                    GlasmioMenuSeparator(height = separatorHeight)
                }
            }
            items.forEachIndexed { index, item ->
                if (item.separatorBefore) {
                    GlasmioMenuSeparator(Modifier.graphicsLayer {
                        alpha = 1f - 0.58f * child.value.coerceIn(0f, 1f)
                    }, height = separatorHeight)
                }
                GlasmioMenuItem(
                    title = item.title,
                    subtitle = item.subtitle,
                    rowHeight = itemHeights[index],
                    systemName = item.systemName,
                    icon = item.icon,
                    destructive = item.destructive,
                    backdrop = childBackdrop,
                    iconTint = if (item.destructive) colors.destructive else if (useAccentIcons) colors.accent else colors.content,
                    enabled = item.enabled && selectedIndex == null,
                    modifier = Modifier.graphicsLayer {
                        alpha = if (selectedIndex == index) 0f else 1f - 0.58f * child.value.coerceIn(0f, 1f)
                    }.then(Modifier.menuDragRow(index, dragSelection, suppressed = childExpanded))
                        .then(if (selectedIndex != null) Modifier.semantics { hideFromAccessibility() } else Modifier),
                    trailing = if (item.children.isEmpty()) null else {
                        { MenuChevron(0f, collapsedDescription) }
                    },
                    onClick = { activate(index) },
                )
            }
        }
    }

}

@Composable
internal fun GlasmioMenuSeparator(
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    horizontalInset: Dp = 16.dp,
) {
    val color = LocalGlassColors.current.content.copy(alpha = 0.16f)
    Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
        HorizontalDivider(
            Modifier.padding(horizontal = horizontalInset).graphicsLayer {
                blendMode = BlendMode.Luminosity
                compositingStrategy = CompositingStrategy.Offscreen
            },
            thickness = 0.5.dp,
            color = color,
        )
    }
}

@Composable
private fun MenuChevron(rotation: Float, state: String) {
    SfIcon(
        "chevron.right", null, size = 14.dp, tint = LocalGlassColors.current.content,
        modifier = Modifier.semantics { stateDescription = state }.graphicsLayer { rotationZ = rotation },
    )
}

/** The active shape is inset from the 34dp menu radius by its 10dp content padding. */
@Composable
private fun MenuHeaderAction(
    action: GlasmioCascadingMenuItem,
    iconTint: Color,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalGlassColors.current
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { InteractiveHighlight(scope) }
    val shape = ContinuousRoundedRectangle(34.dp - 10.dp)
    Column(
        modifier.height(76.dp).clip(shape)
            .background(Color.Black.copy(alpha = 0.15f * highlight.pressProgress), shape)
            .then(if (enabled) Modifier.clickable(
                interactionSource = null, indication = null, role = Role.Button, onClick = onClick,
            ).then(highlight.gestureModifier) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        if (action.icon != null) {
            Box(Modifier.size(24.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                action.icon.invoke()
            }
        } else {
            action.systemName?.let { SfIcon(it, null, size = 24.dp, tint = iconTint) }
        }
        Spacer(Modifier.height(6.dp))
        Text(action.title, style = IosTypography.subheadline, color = colors.content,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
