package com.wearos.wristjoker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 整套圆屏版式的公共骨架。
 *
 * 牌桌（[GameScreen]）和商店（[ShopScreen]）在上一轮已经改成「按弦长分带」，
 * 但菜单 / 牌组 / 盲注 / 结算 / 结束 / 开包 / 详情 / 信息面板这八处仍然是
 * 一条 122dp 的竖带 —— 也就是说，玩家从牌桌点进结算页的瞬间，界面会
 * **从贴着圆弧的版式缩回一个矩形**。这正是"像给圆屏套了个安全区"的来源：
 * 不是某一页没适配，而是**大多数页没适配**。
 *
 * 这个文件把分带计算收成一处：
 *
 * ```
 * val L = roundLayout(
 *     CircleFit.Band(12f, 2f),
 *     CircleFit.Band(96f, 3f),
 *     CircleFit.Band(22f, 0f),
 * )
 * RoundPage(L) { l ->
 *     BandRow(l.w[0], l.h[0]) { ... }
 *     BandGap(l.gap[0])
 *     BandRow(l.w[1], l.h[1]) { ... }
 * }
 * ```
 *
 * 宽度永远从 [CircleFit.widths] 来，取的是每条带**上下沿里离圆心更远那条**
 * 的弦长，所以中间带能到 190dp、上下带自动收到 140dp 左右 —— 每行都贴着
 * 弧走，而不是被弧切。
 */
internal class RoundLayout(val bands: List<CircleFit.Band>) {
    val w: FloatArray = CircleFit.widths(bands)
    val h: FloatArray = FloatArray(bands.size) { bands[it].h }
    val gap: FloatArray = FloatArray(bands.size) { bands[it].gap }
    val total: Float = CircleFit.totalHeight(bands)
    val width: Float get() = w.maxOrNull() ?: 0f
}

internal fun roundLayout(vararg bands: CircleFit.Band): RoundLayout = RoundLayout(bands.toList())

/** 整页容器：在圆内垂直居中，横向也居中；调用方逐带摆内容。 */
@Composable
internal fun RoundPage(
    layout: RoundLayout,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(RoundLayout) -> Unit,
) {
    Column(
        modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content(layout)
    }
}

/** 一条带：宽度由弦长给，高度由版式给。 */
@Composable
internal fun BandRow(
    width: Float,
    height: Float,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceBetween,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier.width(width.dp).height(height.dp),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

/** 带间留白。 */
@Composable
internal fun BandGap(gap: Float) {
    androidx.compose.foundation.layout.Spacer(Modifier.height(gap.dp))
}
